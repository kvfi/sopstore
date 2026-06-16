package com.rightcrowd.sopstore.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Offline checks for {@link NotificationService#render}: in-app deep links must point at real React
 * SPA routes (a dead path is redirected to the dashboard by the SPA catch-all). No DB/Spring needed.
 */
class NotificationServiceTest {

  private static final UUID PROC = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static Map<String, Object> model() {
    return Map.of(
        "procedureId", PROC,
        "changeRequestId", UUID.randomUUID(),
        "title", "Cleaning SOP",
        "stage", "QA review");
  }

  /** An assigned approval task opens the procedure under review, not a dead route. */
  @Test
  void assignedTaskLinksToTheProcedure() {
    NotificationService.Rendered r = NotificationService.render("workflow.task.assigned", model());
    assertThat(r.link()).isEqualTo("/procedures/" + PROC);
  }

  /** An overdue approval task opens the procedure under review. */
  @Test
  void overdueTaskLinksToTheProcedure() {
    NotificationService.Rendered r = NotificationService.render("workflow.task.overdue", model());
    assertThat(r.link()).isEqualTo("/procedures/" + PROC);
  }

  /** No workflow notification links to the retired Thymeleaf {@code /lifecycle/tasks} path. */
  @Test
  void noNotificationLinksToTheDeadLifecycleRoute() {
    for (String key :
        new String[] {
          "workflow.task.assigned",
          "workflow.task.overdue",
          "workflow.completed",
          "workflow.rejected"
        }) {
      assertThat(NotificationService.render(key, model()).link()).isNotEqualTo("/lifecycle/tasks");
    }
  }
}
