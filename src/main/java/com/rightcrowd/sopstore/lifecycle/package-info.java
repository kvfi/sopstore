@NullMarked
@ApplicationModule(
    displayName = "lifecycle",
    allowedDependencies = {
      "platform",
      "tenancy",
      "identity",
      "procedure",
      "audit :: audit-port",
      "notification :: notification-port"
    })
package com.rightcrowd.sopstore.lifecycle;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
