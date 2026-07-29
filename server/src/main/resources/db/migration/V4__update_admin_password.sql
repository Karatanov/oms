/*
 * Оновлення тестового адміністратора.
 *
 * Початковий пароль:
 * admin
 *
 * BCrypt-хеш було згенеровано
 * окремою утилітою.
 */
UPDATE users
SET password =
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9l/6i5F0v8L8nA7R8Y6G2K'
WHERE username = 'admin';