-- Spring Modulith Event Publication Registry table (spring-modulith-events-jpa).
-- The JpaEventPublication entity is validated by Hibernate (ddl-auto: validate), so the
-- table must exist with matching column types. This is Modulith's canonical v2 Postgres
-- schema (matches the 2.0.0 entity, which maps `status` and `completion_attempts`).
CREATE TABLE IF NOT EXISTS event_publication
(
    id                     uuid NOT NULL,
    listener_id            text NOT NULL,
    event_type             text NOT NULL,
    serialized_event       text NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamptz,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
