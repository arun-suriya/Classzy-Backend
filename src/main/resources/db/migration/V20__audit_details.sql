-- Audit Logs page: who/where an action came from, and (when a caller provides them) the before/after values.
ALTER TABLE audit_logs
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(300),
    ADD COLUMN old_value  VARCHAR(500),
    ADD COLUMN new_value  VARCHAR(500);
CREATE INDEX ix_audit_action ON audit_logs (action);
CREATE INDEX ix_audit_actor ON audit_logs (actor_email);
