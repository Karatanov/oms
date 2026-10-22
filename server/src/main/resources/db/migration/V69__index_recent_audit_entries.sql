-- Administration requests the newest audit entries first.  Without this
-- index, the database must sort the whole immutable audit trail before it
-- can return the small recent-activity window.
CREATE INDEX idx_audit_log_recent ON audit_log (created_at);
