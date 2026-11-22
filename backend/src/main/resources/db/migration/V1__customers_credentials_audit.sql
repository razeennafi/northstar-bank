CREATE TABLE customers (
    id uuid PRIMARY KEY,
    full_name varchar(120) NOT NULL CHECK (length(btrim(full_name)) > 0),
    email varchar(254) NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz NOT NULL,
    CONSTRAINT customers_email_unique UNIQUE (email),
    CONSTRAINT customers_email_canonical CHECK (email = lower(btrim(email)))
);

-- Sharing the customer key models exactly one credential for each provisioned user.
CREATE TABLE credentials (
    customer_id uuid PRIMARY KEY REFERENCES customers(id) ON DELETE RESTRICT,
    password_hash varchar(255) NOT NULL,
    password_updated_at timestamptz NOT NULL
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    actor_id uuid REFERENCES customers(id) ON DELETE RESTRICT,
    action varchar(40) NOT NULL,
    resource_type varchar(24) NOT NULL,
    resource_id uuid,
    outcome varchar(10) NOT NULL CHECK (outcome IN ('SUCCESS', 'REJECTED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    request_id uuid NOT NULL
);

CREATE INDEX audit_events_date_index ON audit_events (created_at DESC, id);
