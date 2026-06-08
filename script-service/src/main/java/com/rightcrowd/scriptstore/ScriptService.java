package com.rightcrowd.scriptstore;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Script repository logic with auto-versioning: every content save appends an immutable
 * {@link ScriptVersion} and advances the script's current version. All operations are tenant-scoped.
 */
@Service
@Transactional
public class ScriptService {

  private final ScriptRepository scripts;
  private final ScriptVersionRepository versions;

  public ScriptService(ScriptRepository scripts, ScriptVersionRepository versions) {
    this.scripts = scripts;
    this.versions = versions;
  }

  @Transactional(readOnly = true)
  public List<Script> list(UUID tenant) {
    return scripts.findByTenantIdOrderByNameAsc(tenant);
  }

  @Transactional(readOnly = true)
  public Script get(UUID tenant, UUID id) {
    return scripts
        .findByIdAndTenantId(id, tenant)
        .orElseThrow(() -> new NoSuchElementException("script not found"));
  }

  /** Creates a script and seeds version 1 from the given content. */
  public Script create(
      UUID tenant, String name, String language, String description, String content, String author) {
    Script s = scripts.save(new Script(UUID.randomUUID(), tenant, name, language, description));
    addVersion(s, content, "Initial version", author);
    return s;
  }

  /** Updates metadata only (no new version). */
  public Script updateMeta(UUID tenant, UUID id, String name, String language, String description) {
    Script s = get(tenant, id);
    s.setName(name);
    s.setLanguage(language);
    s.setDescription(description);
    return scripts.save(s);
  }

  /** Saves new content as the next immutable version and makes it current. */
  public ScriptVersion saveContent(UUID tenant, UUID id, String content, String note, String author) {
    return addVersion(get(tenant, id), content, note, author);
  }

  /** Re-publishes an older version's content as a new current version. */
  public ScriptVersion restore(UUID tenant, UUID id, int versionNo, String author) {
    Script s = get(tenant, id);
    ScriptVersion src =
        versions
            .findByScriptIdAndVersionNo(id, versionNo)
            .orElseThrow(() -> new NoSuchElementException("version not found"));
    return addVersion(s, src.content(), "Restored from v" + versionNo, author);
  }

  @Transactional(readOnly = true)
  public List<ScriptVersion> versions(UUID tenant, UUID id) {
    get(tenant, id); // tenant check
    return versions.findByScriptIdOrderByVersionNoDesc(id);
  }

  @Transactional(readOnly = true)
  public ScriptVersion version(UUID tenant, UUID id, int versionNo) {
    get(tenant, id); // tenant check
    return versions
        .findByScriptIdAndVersionNo(id, versionNo)
        .orElseThrow(() -> new NoSuchElementException("version not found"));
  }

  public void delete(UUID tenant, UUID id) {
    scripts.delete(get(tenant, id));
  }

  private ScriptVersion addVersion(Script s, String content, String note, String author) {
    int next = s.currentVersion() + 1;
    ScriptVersion v =
        versions.save(
            new ScriptVersion(UUID.randomUUID(), s.id(), s.tenantId(), next, content, note, author));
    s.setCurrentVersion(next);
    scripts.save(s);
    return v;
  }
}
