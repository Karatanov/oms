ALTER TABLE inspection_reports
    ADD COLUMN status ENUM('draft', 'pending_review', 'completed') NOT NULL DEFAULT 'draft' AFTER created_by,
    ADD COLUMN rejection_reason TEXT NULL AFTER status,
    ADD COLUMN submitted_at DATETIME NULL AFTER rejection_reason,
    ADD COLUMN reviewed_at DATETIME NULL AFTER submitted_at;

CREATE INDEX idx_inspection_status ON inspection_reports (status);
