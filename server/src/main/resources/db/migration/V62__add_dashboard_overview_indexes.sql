-- The lightweight Dashboard overview filters these three registries by the
-- visible project set and then groups their records by date/type.  Individual
-- indexes existed for some columns, but these composite indexes keep the
-- overview query index-backed as the operational data grows.
CREATE INDEX idx_financial_dashboard_overview
    ON financial_records (project_id, record_type, record_date);

CREATE INDEX idx_reports_dashboard_overview
    ON inspection_reports (project_id, inspection_date);

CREATE INDEX idx_project_amounts_dashboard_overview
    ON project_amounts (project_id, amount_kind);
