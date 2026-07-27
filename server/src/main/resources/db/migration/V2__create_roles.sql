/*
 * Довідник ролей користувачів системи.
 *
 * Ролі визначають права доступу до функціональності
 * та бізнес-процесів застосунку.
 */
CREATE TABLE roles
(

    /*
     * Унікальний ідентифікатор ролі.
     */
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,

    /*
     * Системне ім'я ролі.
     *
     * Використовується в коді та не повинно змінюватися.
     */
    code VARCHAR(50)  NOT NULL UNIQUE,

    /*
     * Людинозрозуміла назва ролі.
     */
    name VARCHAR(100) NOT NULL
);


/*
 * Початковий набір ролей MVP.
 */
INSERT INTO roles(code, name)
VALUES ('ADMIN', 'Адміністратор'),
       ('PROJECT_MANAGER', 'Керівник проєкту'),
       ('INSPECTOR', 'Інспектор'),
       ('CONTRACTOR', 'Підрядник');