package com.rightcrowd.sopstore.scripts;

import java.util.Optional;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * Read-only access to a pinned script version's source text, for embedding the actual scripts a
 * procedure runs into its exported SOP bundle. Backed by the standalone script-service; calls
 * degrade to {@link Optional#empty()} when it is unreachable so a bundle still builds without it.
 */
@NamedInterface("script-content-port")
public interface ScriptContentPort {

  /** Returns the raw source of the given script version, or empty if it cannot be retrieved. */
  Optional<String> versionContent(UUID scriptId, int versionNo);
}
