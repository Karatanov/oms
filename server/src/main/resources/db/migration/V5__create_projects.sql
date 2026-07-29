/*
 * Таблиця інфраструктурних проєктів.
 *
 * Відповідає специфікації OMS MVP.
 */
CREATE TABLE projects
(

    /*
     * Внутрішній ідентифікатор.
     */
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Публічний UUID.
     *
     * Саме він буде використовуватися
     * у REST API замість числового ID.
     */
    uuid              CHAR(36)       NOT NULL UNIQUE,

    /*
     * Тип запису:
     * project    - звичайний проєкт;
     * subproject - підпроєкт.
     */
    project_type      ENUM (
        'project',
        'subproject'
        )                            NOT NULL DEFAULT 'project',

    /*
     * Посилання на батьківський проєкт.
     *
     * Використовується лише для subproject.
     */
    parent_project_id BIGINT         NULL,

    /*
     * Повна назва проєкту.
     */
    name              VARCHAR(255)   NOT NULL,

    /*
     * Короткий код майданчика.
     *
     * Наприклад: KYIV.
     */
    site_name         VARCHAR(100)   NOT NULL,

    /*
     * Номер об'єкта.
     *
     * Наприклад: 042.
     */
    site_number       VARCHAR(50)    NOT NULL,

    /*
     * Повна адреса.
     */
    address           VARCHAR(500)   NOT NULL,

    /*
     * Область.
     */
    region            VARCHAR(100)   NOT NULL,

    /*
     * Населений пункт.
     */
    city              VARCHAR(100)   NOT NULL,

    /*
     * Географічні координати.
     */
    latitude          DECIMAL(10, 7) NOT NULL,
    longitude         DECIMAL(10, 7) NOT NULL,

    /*
     * Поточний статус проєкту.
     */
    status            ENUM (
        'planned',
        'active',
        'suspended',
        'completed',
        'archived',
        'DLP'
        )                            NOT NULL,

    /*
     * Галузь.
     *
     * Наприклад:
     * Education,
     * Healthcare,
     * Roads.
     */
    sector            VARCHAR(100)   NOT NULL,

    /*
     * Тип будівництва.
     */
    construction_type VARCHAR(100)   NOT NULL,

    /*
     * Загальний бюджет.
     *
     * Згідно з рішенням MVP,
     * усі розрахунки виконуються в UAH.
     */
    budget_planned    BIGINT         NOT NULL,

    /*
     * Валюта відображення.
     *
     * За замовчуванням UAH.
     */
    currency          CHAR(3)        NOT NULL DEFAULT 'UAH',

    /*
     * Основний підрядник.
     */
    contractor_name   VARCHAR(255),

    /*
     * Керівник проєкту.
     */
    manager_id        BIGINT,

    /*
     * Хто створив запис.
     */
    created_by        BIGINT,

    created_at        DATETIME
                                              DEFAULT CURRENT_TIMESTAMP,

    updated_at        DATETIME
                                              DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_parent
        FOREIGN KEY (parent_project_id)
            REFERENCES projects (id),

    CONSTRAINT fk_project_manager
        FOREIGN KEY (manager_id)
            REFERENCES users (id),

    CONSTRAINT fk_project_creator
        FOREIGN KEY (created_by)
            REFERENCES users (id)
);


/*
 * Індекси, визначені специфікацією.
 */
CREATE INDEX idx_projects_status
    ON projects (status);

CREATE INDEX idx_projects_region
    ON projects (region);

CREATE INDEX idx_projects_sector
    ON projects (sector);

CREATE INDEX idx_projects_manager
    ON projects (manager_id);