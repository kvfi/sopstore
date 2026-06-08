package com.rightcrowd.sopstore.identity;

/**
 * System roles. Tenant-defined custom roles are stored separately in {@code custom_role} and mapped
 * to permissions via {@code role_permission}.
 */
public enum Role {
  SUPER_ADMIN,
  TENANT_ADMIN,
  QUALITY_MANAGER,
  AUTHOR,
  REVIEWER,
  APPROVER,
  TRAINER,
  TRAINEE,
  AUDITOR,
  VIEWER,
  SCIM_PROVISIONER,
  PLATFORM_OPS
}
