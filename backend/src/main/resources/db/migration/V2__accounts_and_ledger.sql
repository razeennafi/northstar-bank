CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    customer_id uuid REFERENCES customers(id) ON DELETE RESTRICT,
    account_type varchar(10) NOT NULL CHECK (account_type IN ('CHEQUING', 'SAVINGS', 'SYSTEM')),
    currency varchar(3) NOT NULL CHECK (currency = 'CAD'),
    status varchar(10) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at timestamptz NOT NULL,
    CHECK ((account_type = 'SYSTEM') = (customer_id IS NULL))
);

CREATE INDEX accounts_owner_index ON accounts (customer_id, created_at, id);
CREATE UNIQUE INDEX accounts_single_system ON accounts (account_type) WHERE account_type = 'SYSTEM';

-- This internal account supplies CAD 500 of simulated funds per customer account.
INSERT INTO accounts VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM', 'CAD', 'ACTIVE', now());

CREATE TABLE transfers (
    id uuid PRIMARY KEY,
    requester_id uuid NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    source_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    destination_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount numeric(19,2) NOT NULL CHECK (amount > 0 AND amount <> 'NaN'::numeric),
    currency varchar(3) NOT NULL CHECK (currency = 'CAD'),
    kind varchar(12) NOT NULL CHECK (kind IN ('DEMO_FUNDING', 'TRANSFER')),
    status varchar(10) NOT NULL CHECK (status = 'COMPLETED'),
    created_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL CHECK (completed_at >= created_at),
    CHECK (source_account_id <> destination_account_id),
    CHECK (destination_account_id <> '00000000-0000-0000-0000-000000000001'),
    CHECK (
        (kind = 'DEMO_FUNDING' AND source_account_id = '00000000-0000-0000-0000-000000000001' AND amount = 500)
        OR (kind = 'TRANSFER' AND source_account_id <> '00000000-0000-0000-0000-000000000001')
    )
);

CREATE UNIQUE INDEX transfers_one_demo_fund ON transfers (destination_account_id) WHERE kind = 'DEMO_FUNDING';

CREATE TABLE ledger_entries (
    id uuid PRIMARY KEY,
    transfer_id uuid NOT NULL REFERENCES transfers(id) ON DELETE RESTRICT,
    account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    direction varchar(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount numeric(19,2) NOT NULL CHECK (amount > 0 AND amount <> 'NaN'::numeric),
    created_at timestamptz NOT NULL,
    UNIQUE (transfer_id, direction)
);

-- A directional posting index supports account history, balance and statement queries.
CREATE INDEX ledger_account_date_index ON ledger_entries (account_id, created_at, id);

CREATE FUNCTION prohibit_financial_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Financial records are append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER immutable_transfers BEFORE UPDATE OR DELETE OR TRUNCATE ON transfers
FOR EACH STATEMENT EXECUTE FUNCTION prohibit_financial_rewrite();
CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE OR TRUNCATE ON ledger_entries
FOR EACH STATEMENT EXECUTE FUNCTION prohibit_financial_rewrite();

-- A deferred trigger checks the completed pair at commit, after both INSERTs.
CREATE FUNCTION ensure_ledger_pair() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    posting_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'transfers' THEN
        posting_id := NEW.id;
    ELSE
        posting_id := NEW.transfer_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM transfers t
        JOIN ledger_entries d ON d.transfer_id = t.id AND d.direction = 'DEBIT'
        JOIN ledger_entries c ON c.transfer_id = t.id AND c.direction = 'CREDIT'
        WHERE t.id = posting_id AND d.account_id = t.source_account_id
          AND c.account_id = t.destination_account_id
          AND d.amount = t.amount AND c.amount = t.amount
          AND d.created_at = t.completed_at AND c.created_at = t.completed_at
    ) THEN
        RAISE EXCEPTION 'Posting requires matching debit and credit entries' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER transfer_requires_pair AFTER INSERT ON transfers
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ensure_ledger_pair();
CREATE CONSTRAINT TRIGGER entry_requires_pair AFTER INSERT ON ledger_entries
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ensure_ledger_pair();
