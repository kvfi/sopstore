package com.rightcrowd.sopstore.notification.internal;

import com.rightcrowd.sopstore.identity.UserDirectory;
import com.rightcrowd.sopstore.notification.NotificationPort;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Channel router for notifications. Renders a {@code templateKey} + model into a title/body/link,
 * then dispatches per channel:
 *
 * <ul>
 *   <li>{@code IN_APP} — persists a {@link NotificationInbox} row the user sees at {@code
 *       /notifications}.
 *   <li>{@code EMAIL} — resolves the recipient's address and sends via {@link JavaMailSender} when
 *       SMTP is configured; degrades to a logged, FAILED delivery otherwise (e.g. local dev).
 * </ul>
 *
 * <p>Every dispatch also records a {@link NotificationDelivery} row for delivery tracking. Slack,
 * Teams, and webhook channels remain logged (tracked in PHASE-STATUS).
 */
@Service
@Transactional
public class NotificationService implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationDeliveryRepository deliveries;
  private final NotificationInboxRepository inbox;
  private final UserDirectory users;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final String fromAddress;

  /** Creates a notification service with its repositories, the directory, and mail transport. */
  public NotificationService(
      NotificationDeliveryRepository deliveries,
      NotificationInboxRepository inbox,
      UserDirectory users,
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${sopstore.notification.from:sopstore@localhost}") String fromAddress) {
    this.deliveries = deliveries;
    this.inbox = inbox;
    this.users = users;
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  @Override
  public void send(
      UUID recipientUserId, String templateKey, Map<String, Object> model, Channel channel) {
    // Respect the recipient's notification preferences: a muted category is skipped entirely, and
    // email is skipped when the user has turned email off. Defaults (no prefs) keep everything on.
    if (users.isNotificationCategoryMuted(recipientUserId, categoryOf(templateKey))) {
      return;
    }
    if (channel == Channel.EMAIL && !users.emailNotificationsEnabled(recipientUserId)) {
      return;
    }
    UUID tenant = TenantContext.current().value();
    Rendered r = render(templateKey, model);
    NotificationDelivery delivery =
        new NotificationDelivery(
            UUID.randomUUID(), tenant, recipientUserId, templateKey, channel);

    switch (channel) {
      case IN_APP -> {
        inbox.save(
            new NotificationInbox(
                UUID.randomUUID(),
                tenant,
                recipientUserId,
                templateKey,
                r.title(),
                r.body(),
                r.link()));
        delivery.markSent();
      }
      case EMAIL -> sendEmail(recipientUserId, r, delivery);
      default -> {
        // SLACK / TEAMS / WEBHOOK adapters not implemented (tracked in PHASE-STATUS).
        log.info("notify(stub) user={} template={} ch={}", recipientUserId, templateKey, channel);
        delivery.markFailed(channel + " adapter not implemented");
      }
    }
    deliveries.save(delivery);
  }

  /** The notification category for preference-muting: the template key's prefix before any dot. */
  private static String categoryOf(String templateKey) {
    if (templateKey == null || templateKey.isBlank()) {
      return "";
    }
    int dot = templateKey.indexOf('.');
    return dot < 0 ? templateKey : templateKey.substring(0, dot);
  }

  private void sendEmail(UUID recipientUserId, Rendered r, NotificationDelivery delivery) {
    JavaMailSender sender = mailSender.getIfAvailable();
    String to = users.emailById(recipientUserId).orElse(null);
    if (to == null) {
      delivery.markFailed("recipient has no email address");
      return;
    }
    if (sender == null) {
      // No SMTP configured (e.g. local dev / air-gapped without a relay). Honest, not silent.
      log.info("email not sent (no SMTP configured): to={} subject={}", to, r.title());
      delivery.markFailed("no SMTP transport configured");
      return;
    }
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(fromAddress);
      msg.setTo(to);
      msg.setSubject(r.title());
      msg.setText(r.link() == null ? r.body() : r.body() + "\n\n" + r.link());
      sender.send(msg);
      delivery.markSent();
    } catch (RuntimeException e) {
      // Never let a transport failure roll back the originating business transaction.
      log.warn("email send failed to {}: {}", to, e.getMessage());
      delivery.markFailed(e.getMessage() == null ? "send failed" : e.getMessage());
    }
  }

  /** Renders a notification from its template key and model. Tenant template overrides are TODO. */
  private static Rendered render(String key, Map<String, Object> model) {
    String title = str(model, "title");
    String stage = str(model, "stage");
    String procLink =
        model.containsKey("procedureId") ? "/procedures/" + model.get("procedureId") : null;
    return switch (key) {
      case "workflow.task.assigned" ->
          new Rendered(
              "Approval needed: " + title,
              "You have an approval task at stage \"" + stage + "\".",
              "/lifecycle/tasks");
      case "workflow.task.overdue" ->
          new Rendered(
              "Overdue approval: " + title,
              "An approval task at stage \"" + stage + "\" is past its due date.",
              "/lifecycle/tasks");
      case "workflow.completed" ->
          new Rendered(
              "Approved: " + title,
              "Change request \"" + title + "\" completed approval and is ready to publish.",
              procLink);
      case "workflow.rejected" ->
          new Rendered(
              "Changes requested: " + title,
              "Your change request \"" + title + "\" was rejected at stage \"" + stage + "\".",
              procLink);
      default -> new Rendered(key, model.toString(), procLink);
    };
  }

  private static String str(Map<String, Object> model, String k) {
    Object v = model.get(k);
    return v == null ? "" : v.toString();
  }

  /** A rendered notification ready to dispatch over any channel. */
  private record Rendered(String title, String body, @Nullable String link) {}
}
