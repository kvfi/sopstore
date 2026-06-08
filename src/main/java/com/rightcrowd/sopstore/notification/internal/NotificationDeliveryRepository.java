package com.rightcrowd.sopstore.notification.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing {@link NotificationDelivery} entities. */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {}
