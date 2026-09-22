package oms.umitaf.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

/**
 * Налаштовує логування HTTP-запитів.
 *
 * Логи допомагають зрозуміти:
 * - які маршрути викликаються;
 * - які помилки виникають;
 * - скільки запитів надходить до сервера.
 */
fun Application.configureMonitoring() {

    install(CallLogging) {

        /**
         * Логуємо лише повідомлення рівня INFO та вище.
         *
         * DEBUG-логування поки що не використовуємо,
         * щоб не перевантажувати консоль.
         */
        level = Level.INFO

        /**
         * У майбутньому тут можна буде додати:
         * - логування ідентифікатора користувача;
         * - часу виконання запиту;
         * - correlation id для трасування.
         */
    }
}