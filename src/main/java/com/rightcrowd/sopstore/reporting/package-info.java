@NullMarked
@ApplicationModule(
    displayName = "reporting",
    allowedDependencies = {
      "platform",
      "tenancy",
      "procedure",
      "lifecycle",
      "training",
      "execution",
      "audit"
    })
package com.rightcrowd.sopstore.reporting;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
