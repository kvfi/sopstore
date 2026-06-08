@NullMarked
@ApplicationModule(
    displayName = "execution",
    allowedDependencies = {
      "platform", "tenancy", "identity", "procedure", "training :: training-api", "audit"
    })
package com.rightcrowd.sopstore.execution;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
