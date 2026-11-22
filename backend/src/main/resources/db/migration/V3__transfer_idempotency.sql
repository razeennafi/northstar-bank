CREATE TABLE transfer_requests (
    requester_id uuid NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    request_key uuid NOT NULL,
    fingerprint varchar(64) NOT NULL,
    transfer_id uuid NOT NULL UNIQUE REFERENCES transfers(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (requester_id, request_key)
);

-- A key and its outcome are committed together. Rewriting a key would let
-- an old client retry post money again.
CREATE TRIGGER immutable_transfer_requests BEFORE UPDATE OR DELETE OR TRUNCATE ON transfer_requests
FOR EACH STATEMENT EXECUTE FUNCTION prohibit_financial_rewrite();
