@NullMarked
@ApplicationModule(
    displayName = "dataops",
    allowedDependencies = {"platform", "tenancy", "identity", "audit :: audit-port"})
package com.rightcrowd.sopstore.dataops;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
