/*
 * Перша міграція проєкту.
 *
 * Flyway виконує всі файли у порядку їх номерів:
 * V1__, V2__, V3__ ...
 *
 * Виконана міграція більше ніколи не змінюється.
 * Для подальших змін створюються нові файли.
 */

CREATE TABLE health_checks (

                               id BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Час створення запису.
     */
                               created_at TIMESTAMP NOT NULL
                                   DEFAULT CURRENT_TIMESTAMP
);