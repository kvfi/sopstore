package com.rightcrowd.sopstore.procedure.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcrowd.sopstore.procedure.Attachment;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.scripts.ScriptContentPort;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upload, list, download, delete procedure attachments, and bundle an SOP as a ZIP. */
@Service
@Transactional
public class AttachmentService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProcedureRepository procedures;
  private final AttachmentRepository attachments;
  private final AttachmentContentRepository contents;
  private final ProcedureService procedureService;
  private final ScriptContentPort scriptContent;
  private final ScriptBundleSettingsService bundleSettings;

  /** Creates the service with its repositories, the procedure service, and script-content port. */
  public AttachmentService(
      ProcedureRepository procedures,
      AttachmentRepository attachments,
      AttachmentContentRepository contents,
      ProcedureService procedureService,
      ScriptContentPort scriptContent,
      ScriptBundleSettingsService bundleSettings) {
    this.procedures = procedures;
    this.attachments = attachments;
    this.contents = contents;
    this.procedureService = procedureService;
    this.scriptContent = scriptContent;
    this.bundleSettings = bundleSettings;
  }

  /** Attachment metadata as the API exposes it. */
  public record AttachmentMeta(
      UUID id, String filename, String mime, long size, String sha256, String uploadedAt) {
    static AttachmentMeta from(Attachment a) {
      return new AttachmentMeta(
          a.id(),
          a.filename(),
          a.mime(),
          a.sizeBytes(),
          a.sha256(),
          a.uploadedAt().toString());
    }
  }

  /** A downloadable attachment: metadata + bytes. */
  @SuppressWarnings("ArrayRecordComponent") // raw file bytes are intentionally an array
  public record AttachmentFile(String filename, String mime, byte[] content) {}

  /** Stores a file against the procedure's current version. */
  public AttachmentMeta upload(UUID procedureId, String filename, String mime, byte[] bytes) {
    UUID tenant = TenantContext.current().value();
    UUID versionId = currentVersionId(procedureId);
    UUID id = UUID.randomUUID();
    Attachment a =
        new Attachment(
            id, tenant, versionId, filename, "db:" + id, mime, bytes.length, sha256Hex(bytes));
    attachments.save(a);
    contents.save(new AttachmentContent(id, tenant, bytes));
    return AttachmentMeta.from(a);
  }

  /** Lists the procedure's current-version attachments, newest first. */
  @Transactional(readOnly = true)
  public List<AttachmentMeta> list(UUID procedureId) {
    UUID versionId = currentVersionIdOrNull(procedureId);
    if (versionId == null) {
      return List.of();
    }
    return attachments.findByProcedureVersionIdOrderByUploadedAtDesc(versionId).stream()
        .map(AttachmentMeta::from)
        .toList();
  }

  /** Returns one attachment's filename, mime, and bytes for download. */
  @Transactional(readOnly = true)
  public AttachmentFile download(UUID attachmentId) {
    Attachment a = attachments.findById(attachmentId).orElseThrow();
    byte[] bytes =
        contents.findById(attachmentId).map(AttachmentContent::content).orElse(new byte[0]);
    return new AttachmentFile(a.filename(), a.mime(), bytes);
  }

  /** Deletes an attachment and its content. */
  public void delete(UUID attachmentId) {
    contents.deleteById(attachmentId);
    attachments.deleteById(attachmentId);
  }

  /** Bundles the procedure (PDF document + every attachment) into a single ZIP. */
  @Transactional(readOnly = true)
  public byte[] bundleZip(UUID procedureId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    UUID versionId = currentVersionIdOrNull(procedureId);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out)) {
      // The procedure as a PDF document.
      zip.putNextEntry(new ZipEntry(safe(p.documentNumber()) + ".pdf"));
      zip.write(procedureService.exportPdf(procedureId));
      zip.closeEntry();
      // Every attachment, under attachments/.
      if (versionId != null) {
        for (Attachment a : attachments.findByProcedureVersionIdOrderByUploadedAtDesc(versionId)) {
          byte[] bytes =
              contents.findById(a.id()).map(AttachmentContent::content).orElse(new byte[0]);
          zip.putNextEntry(new ZipEntry("attachments/" + safe(a.filename())));
          zip.write(bytes);
          zip.closeEntry();
        }
      }
      // The pinned scripts each RUN_SCRIPT step runs, under the configured folder — matching the
      // PDF's links.
      ScriptBundleConfig cfg = bundleSettings.effectiveConfig();
      writeScripts(zip, procedureService.bodyOrEmpty(procedureId), cfg);
      zip.finish();
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("failed to build SOP bundle", e);
    }
  }

  /** Returns the filename for a procedure's downloaded bundle, per the tenant's bundle settings. */
  @Transactional(readOnly = true)
  public String bundleFileName(UUID procedureId) {
    String doc = procedures.findById(procedureId).map(Procedure::documentNumber).orElse("sop");
    return ScriptBundleNaming.bundleFileName(bundleSettings.effectiveConfig(), doc);
  }

  /**
   * Writes each RUN_SCRIPT step's pinned script@version into the configured bundle folder, fetched
   * from the script-service. Entries are named by {@link ScriptBundleNaming} so they line up with
   * hyperlinks in the exported PDF. Duplicate references are written once; a script the service
   * can't supply is silently skipped (the bundle still builds without the optional service).
   */
  private void writeScripts(ZipOutputStream zip, String bodyJson, ScriptBundleConfig cfg)
      throws IOException {
    JsonNode steps;
    try {
      steps = JSON.readTree(bodyJson == null || bodyJson.isBlank() ? "{}" : bodyJson).path("steps");
    } catch (JsonProcessingException e) {
      return;
    }
    if (!steps.isArray()) {
      return;
    }
    Set<String> written = new HashSet<>();
    for (JsonNode s : steps) {
      boolean runScript = "RUN_SCRIPT".equals(s.path("type").asText(""));
      if (!runScript || s.path("scriptId").asText("").isBlank()) {
        continue;
      }
      int ver = s.path("scriptVersionNo").asInt(0);
      String path =
          ScriptBundleNaming.path(
              cfg,
              s.path("scriptRefCode").asText(""),
              s.path("scriptName").asText(""),
              ver,
              s.path("scriptLanguage").asText(""));
      if (!written.add(path)) {
        continue; // identical script@version already written
      }
      UUID scriptId;
      try {
        scriptId = UUID.fromString(s.path("scriptId").asText(""));
      } catch (IllegalArgumentException e) {
        continue;
      }
      Optional<String> content = scriptContent.versionContent(scriptId, ver);
      if (content.isEmpty()) {
        continue; // script-service unavailable or version missing — omit this file
      }
      zip.putNextEntry(new ZipEntry(path));
      zip.write(content.get().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }

  private UUID currentVersionId(UUID procedureId) {
    UUID v = currentVersionIdOrNull(procedureId);
    if (v == null) {
      throw new IllegalStateException("procedure " + procedureId + " has no current version");
    }
    return v;
  }

  private @Nullable UUID currentVersionIdOrNull(UUID procedureId) {
    return procedures.findById(procedureId).orElseThrow().currentVersionId();
  }

  private static String safe(String name) {
    String s = name == null || name.isBlank() ? "file" : name;
    return s.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  private static String sha256Hex(byte[] data) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
