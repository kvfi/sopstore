package com.rightcrowd.sopstore.integration.internal;

import com.rightcrowd.sopstore.integration.WebhookEndpoint;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches signed webhook payloads. Headers per spec:
 *
 * <pre>
 * X-Sopstore-Event: procedure-version-published
 * X-Sopstore-Delivery: {uuid}
 * X-Sopstore-Timestamp: {epochMs}
 * X-Sopstore-Signature: sha256={hex}
 * </pre>
 *
 * <p>Receivers verify by recomputing HMAC-SHA256 over the body with their shared secret and
 * comparing in constant time.
 *
 * <p>Retries: exponential backoff (1s, 5s, 30s, 2m, 10m). After the last attempt the delivery moves
 * to {@code DLQ} state for manual replay.
 */
@Service
public class WebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  /** Signs and sends the webhook payload to the endpoint, returning the delivery result. */
  public Result dispatch(WebhookEndpoint endpoint, String eventName, byte[] body) {
    String sig = HmacSigner.sign(endpoint.secret(), body);
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint.url()))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-Sopstore-Event", eventName)
            .header("X-Sopstore-Delivery", java.util.UUID.randomUUID().toString())
            .header("X-Sopstore-Timestamp", String.valueOf(Instant.now().toEpochMilli()))
            .header("X-Sopstore-Signature", "sha256=" + sig)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    try {
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      return new Result(res.statusCode(), res.statusCode() / 100 == 2, res.body());
    } catch (Exception ex) {
      log.warn("webhook dispatch failed url={}: {}", endpoint.url(), ex.toString());
      return new Result(-1, false, ex.toString());
    }
  }

  /** Outcome of a webhook dispatch attempt. */
  public record Result(int statusCode, boolean success, String responseSummary) {}
}
