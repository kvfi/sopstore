-- Per-user notification preferences, surfaced in the profile section. email_notifications gates
-- email delivery; muted_notification_categories is a comma-separated list of category keys (the
-- prefix of a notification template key, e.g. "workflow") the user has opted out of entirely.
-- Defaults keep existing behaviour (all notifications on). The app_user table already enforces RLS.
ALTER TABLE app_user
    ADD COLUMN email_notifications boolean NOT NULL DEFAULT true,
    ADD COLUMN muted_notification_categories text NOT NULL DEFAULT '';
