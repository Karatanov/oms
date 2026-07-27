/*
 * Таблиця користувачів системи.
 */
CREATE TABLE users
(

    /*
     * Унікальний ідентифікатор користувача.
     */
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Логін користувача.
     */
    username VARCHAR(100) NOT NULL UNIQUE,

    /*
     * Email користувача.
     */
    email    VARCHAR(255) NOT NULL UNIQUE,

    /*
     * Пароль користувача.
     *
     * Наразі зберігається у відкритому вигляді
     * лише для навчальних цілей.
     */
    password VARCHAR(255) NOT NULL,

    /*
     * Роль користувача.
     */
    role_id  BIGINT       NOT NULL,

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id)
            REFERENCES roles (id)
);