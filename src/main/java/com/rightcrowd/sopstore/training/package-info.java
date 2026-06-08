@NullMarked
@ApplicationModule(
    displayName = "training",
    allowedDependencies = {
      "platform", "tenancy", "identity", "procedure :: events", "notification"
    })
package com.rightcrowd.sopstore.training;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
