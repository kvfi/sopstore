package com.rightcrowd.sopstore.notification;

import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Sibling-module entry point for sending notifications. */
@NamedInterface("notification-port")
public interface NotificationPort {

  /** Delivery channels available for sending notifications. */
  enum Channel {
    IN_APP,
    EMAIL,
    SLACK,
    TEAMS,
    WEBHOOK
  }

  /** Sends a notification rendered from the given template to the recipient over the channel. */
  void send(UUID recipientUserId, String templateKey, Map<String, Object> model, Channel channel);
}
