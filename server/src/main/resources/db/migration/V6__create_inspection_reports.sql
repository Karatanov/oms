/*
 * Звіти про інспекції об'єктів.
 */
CREATE TABLE inspection_reports
(

    /*
     * Внутрішній ідентифікатор.
     */
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Публічний UUID звіту.
     */
    uuid            CHAR(36)      NOT NULL UNIQUE,

    /*
     * Проєкт, до якого належить інспекція.
     */
    project_id      BIGINT        NOT NULL,

    /*
     * Дата проведення інспекції.
     */
    inspection_date DATE          NOT NULL,

    /*
     * Загальний відсоток готовності.
     */
    completion_pct  DECIMAL(5, 2) NOT NULL,

    /*
     * Короткий підсумок інспекції.
     */
    summary         TEXT,

    /*
     * Користувач, який створив звіт.
     */
    created_by      BIGINT        NOT NULL,

    created_at      DATETIME
        DEFAULT CURRENT_TIMESTAMP,

    updated_at      DATETIME
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_inspection_project
        FOREIGN KEY (project_id)
            REFERENCES projects (id),

    CONSTRAINT fk_inspection_creator
        FOREIGN KEY (created_by)
            REFERENCES users (id)
);

CREATE INDEX idx_inspection_project
    ON inspection_reports (project_id);

CREATE INDEX idx_inspection_date
    ON inspection_reports (inspection_date);