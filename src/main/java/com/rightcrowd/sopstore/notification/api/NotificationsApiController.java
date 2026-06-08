package com.rightcrowd.sopstore.notification.api;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.notification.internal.NotificationInbox;
import com.rightcrowd.sopstore.notification.internal.NotificationInboxRepository;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API for the in-app notification inbox, backing the SvelteKit portal. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsApiController {

  private final NotificationInboxRepository inbox;

  /** Creates the controller with the inbox repository. */
  public NotificationsApiController(NotificationInboxRepository inbox) {
    this.inbox = inbox;
  }

  /** A single inbox message. */
  public record MessageDto(
      UUID id,
      String title,
      String body,
      @Nullable String link,
      String createdAt,
      boolean unread) {}

  /** The inbox payload: unread count + messages. */
  public record Inbox(long unread, List<MessageDto> items) {}

  /** Returns the signed-in user's inbox, newest first. */
  @GetMapping
  @Transactional(readOnly = true)
  public Inbox list(@AuthenticationPrincipal AuthenticatedUser user) {
    UUID me = user.user().id();
    List<MessageDto> items =
        inbox.findByRecipientIdOrderByCreatedAtDesc(me).stream()
            .map(NotificationsApiController::toDto)
            .toList();
    return new Inbox(inbox.countByRecipientIdAndReadAtIsNull(me), items);
  }

  /** Marks one message read. */
  @PostMapping("/{id}/read")
  @Transactional
  public void markRead(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
    inbox
        .findById(id)
        .filter(m -> m.recipientIsAccessibleBy(user.user().id()))
        .ifPresent(NotificationInbox::markRead);
  }

  private static MessageDto toDto(NotificationInbox m) {
    return new MessageDto(
        m.id(), m.title(), m.body(), m.link(), m.createdAt().toString(), m.unread());
  }
}
