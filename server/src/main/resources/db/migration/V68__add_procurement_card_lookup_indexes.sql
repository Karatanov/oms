-- Subproject-code lookup is already covered by idx_procurement_subproject.
-- Add the matching lot-code index so part cards remain equally narrow as the
-- approved procurement registry grows.
CREATE INDEX idx_procurement_lot_card
    ON procurement_records (sub_project_lot_id);
