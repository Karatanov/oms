/*
 * Зауваження, виявлені під час інспекції.
 */
CREATE TABLE inspection_findings
(

    /*
     * Внутрішній ідентифікатор.
     */
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Публічний UUID запису.
     */
    uuid                 CHAR(36)    NOT NULL UNIQUE,

    /*
     * Звіт інспекції, до якого
     * належить зауваження.
     */
    inspection_report_id BIGINT      NOT NULL,

    /*
     * Категорія зауваження.
     *
     * Наприклад:
     * safety,
     * quality,
     * progress.
     */
    category             VARCHAR(50) NOT NULL,

    /*
     * Рівень критичності.
     */
    severity             ENUM (
        'low',
        'medium',
        'high',
        'critical'
        )                            NOT NULL,

    /*
     * Опис проблеми.
     */
    description          TEXT        NOT NULL,

    /*
     * Рекомендовані дії.
     */
    recommendation       TEXT,

    /*
     * Ознака усунення проблеми.
     */
    is_resolved          BOOLEAN
        DEFAULT FALSE,

    created_at           DATETIME
        DEFAULT CURRENT_TIMESTAMP,

    updated_at           DATETIME
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_finding_report
        FOREIGN KEY (inspection_report_id)
            REFERENCES inspection_reports (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_findings_report
    ON inspection_findings (
                            inspection_report_id
        );

CREATE INDEX idx_findings_severity
    ON inspection_findings (
                            severity
        );