package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.internal.AttachmentService;
import com.rightcrowd.sopstore.procedure.internal.AttachmentService.AttachmentFile;
import com.rightcrowd.sopstore.procedure.internal.AttachmentService.AttachmentMeta;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Attachment upload/list/download/delete and the SOP ZIP bundle. */
@RestController
@RequestMapping("/api/v1/procedures/{id}")
@Tag(name = "Attachments")
public class AttachmentApiController {

  private final AttachmentService attachments;

  /** Creates the controller with the attachment service. */
  public AttachmentApiController(AttachmentService attachments) {
    this.attachments = attachments;
  }

  /** Lists the procedure's attachments. */
  @GetMapping("/attachments")
  public List<AttachmentMeta> list(@PathVariable UUID id) {
    return attachments.list(id);
  }

  /** Uploads a file (any type) as an attachment of the procedure's current version. */
  @PostMapping("/attachments")
  public AttachmentMeta upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file)
      throws IOException {
    String name = file.getOriginalFilename();
    String mime = file.getContentType();
    return attachments.upload(
        id,
        name == null || name.isBlank() ? "file" : name,
        mime == null || mime.isBlank() ? "application/octet-stream" : mime,
        file.getBytes());
  }

  /** Downloads one attachment. */
  @GetMapping("/attachments/{attId}/download")
  public ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable UUID attId) {
    AttachmentFile f = attachments.download(attId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.mime()))
        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(f.filename()))
        .body(new ByteArrayResource(f.content()));
  }

  /** Deletes one attachment. */
  @DeleteMapping("/attachments/{attId}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID attId) {
    attachments.delete(attId);
    return ResponseEntity.noContent().build();
  }

  /** Downloads the procedure bundled with all attachments as a ZIP. */
  @GetMapping("/bundle.zip")
  public ResponseEntity<Resource> bundle(@PathVariable UUID id) {
    byte[] zip = attachments.bundleZip(id);
    String filename = attachments.bundleFileName(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(filename))
        .body(new ByteArrayResource(zip));
  }

  private static String attachmentDisposition(String filename) {
    return ContentDisposition.attachment()
        .filename(filename, StandardCharsets.UTF_8)
        .build()
        .toString();
  }
}
