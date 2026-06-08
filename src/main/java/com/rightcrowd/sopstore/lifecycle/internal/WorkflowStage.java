package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.lifecycle.SignatureMeaning;
import java.util.List;

/**
 * One stage of an approval workflow. Stages run sequentially; within a stage every {@link
 * #approverRoles() role} must approve (parallel, all-required). Each approval is an e-signature
 * carrying {@link #meaning()}.
 *
 * @param name human-readable stage name shown in the approval queue
 * @param approverRoles roles whose holders may act on this stage; one task is opened per role
 * @param meaning the e-signature meaning recorded for an approval at this stage
 * @param slaHours hours after which a pending task is considered overdue and escalated
 * @param condition gates whether this stage applies to a given change request
 */
public record WorkflowStage(
    String name,
    List<Role> approverRoles,
    SignatureMeaning meaning,
    int slaHours,
    StageCondition condition) {

  /** Normalizes the approver-role list to an immutable copy so the stage cannot be mutated. */
  public WorkflowStage {
    approverRoles = List.copyOf(approverRoles);
  }

  /** Returns the approver roles for this stage as an immutable list. */
  @Override
  public List<Role> approverRoles() {
    return List.copyOf(approverRoles);
  }
}
