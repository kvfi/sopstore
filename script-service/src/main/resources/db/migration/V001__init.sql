-- Versioned script repository. A `script` holds metadata + a pointer to its current version;
-- `script_version` holds immutable content snapshots (auto-versioned on every save).
CREATE TABLE script (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    name            varchar(200) NOT NULL,
    language        varchar(40) NOT NULL DEFAULT 'text',
    description     text,
    current_version integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE script_version (
    id          uuid PRIMARY KEY,
    script_id   uuid NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    tenant_id   uuid NOT NULL,
    version_no  integer NOT NULL,
    content     text NOT NULL,
    note        varchar(300),
    created_by  varchar(200),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (script_id, version_no)
);

CREATE INDEX idx_script_tenant ON script (tenant_id);
CREATE INDEX idx_script_version_script ON script_version (script_id, version_no DESC);
