package com.rightcrowd.sopstore.scripts.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcrowd.sopstore.scripts.ScriptContentPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link ScriptContentPort} implementation that fetches a version's content from the script-service
 * via {@link ScriptServiceClient} and unwraps the {@code content} field of its JSON response. Any
 * failure (service down, not found, malformed body) is swallowed into an empty result so callers
 * (notably the SOP bundle builder) never fail because the optional script-service is absent.
 */
@Service
class ScriptContentService implements ScriptContentPort {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ScriptServiceClient client;

  ScriptContentService(ScriptServiceClient client) {
    this.client = client;
  }

  @Override
  public Optional<String> versionContent(UUID scriptId, int versionNo) {
    if (scriptId == null || versionNo <= 0) {
      return Optional.empty();
    }
    try {
      String body = client.versionContent(scriptId, versionNo, null);
      return Optional.of(JSON.readTree(body).path("content").asText(""));
    } catch (JsonProcessingException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
