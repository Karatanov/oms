package oms.ufsi.repository

import oms.ufsi.domain.Role

/**
 * Контракт доступу до ролей користувачів.
 *
 * Інші частини системи не повинні знати,
 * де саме зберігаються дані:
 * - у MySQL;
 * - у файлі;
 * - у зовнішньому сервісі.
 *
 * Вони працюють лише через цей інтерфейс.
 */
interface RoleRepository {

    /**
     * Повертає всі ролі системи.
     */
    fun findAll(): List<Role>
}