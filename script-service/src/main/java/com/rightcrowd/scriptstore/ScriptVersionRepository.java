package com.rightcrowd.scriptstore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for immutable {@link ScriptVersion} snapshots. */
public interface ScriptVersionRepository extends JpaRepository<ScriptVersion, UUID> {

  List<ScriptVersion> findByScriptIdOrderByVersionNoDesc(UUID scriptId);

  Optional<ScriptVersion> findByScriptIdAndVersionNo(UUID scriptId, int versionNo);
}
