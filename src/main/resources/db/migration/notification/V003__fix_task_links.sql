-- Repair stale in-app deep links left by the retired Thymeleaf approval-task page
-- (/lifecycle/tasks), which the React SPA has no route for and redirects to the dashboard. New
-- notifications now link straight to the procedure; existing inbox rows never stored the procedure
-- id, so point them at the approval queue (a valid SPA route) instead of the dead path.
UPDATE notification_inbox SET link = '/approvals' WHERE link = '/lifecycle/tasks';
