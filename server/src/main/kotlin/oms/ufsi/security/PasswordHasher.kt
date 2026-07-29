package oms.ufsi.security

/**
 * BCrypt спеціально створений
 * для зберігання паролів.
 *
 * Його особливості:
 * - автоматичне використання salt;
 * - повільне обчислення;
 * - захист від rainbow-таблиць;
 * - можливість поступового збільшення
 *   складності обчислень.
 */
import org.mindrot.jbcrypt.BCrypt

/**
 * Сервіс для роботи з паролями.
 *
 * Паролі ніколи не повинні зберігатися
 * у відкритому вигляді.
 *
 * Замість цього зберігається лише
 * криптографічний хеш.
 */
object PasswordHasher {

    /**
     * Перетворює відкритий пароль на BCrypt-хеш.
     *
     * Один і той самий пароль щоразу
     * генерує різний результат завдяки
     * випадковій "солі" (salt).
     */
    fun hash(password: String): String {

        return BCrypt.hashpw(
            password,
            BCrypt.gensalt()
        )
    }

    /**
     * Перевіряє відповідність пароля
     * збереженому хешу.
     */
    fun verify(
        password: String,
        hash: String
    ): Boolean {

        return BCrypt.checkpw(
            password,
            hash
        )
    }
}