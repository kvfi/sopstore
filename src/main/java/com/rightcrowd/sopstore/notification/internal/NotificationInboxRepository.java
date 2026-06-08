package com.rightcrowd.sopstore.notification.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for in-app notification inbox messages. */
public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, UUID> {

  /** Returns a recipient's messages, newest first. */
  List<NotificationInbox> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

  /** Counts a recipient's unread messages. */
  long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
