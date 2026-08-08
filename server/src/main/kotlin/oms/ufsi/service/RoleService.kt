package oms.ufsi.service

import oms.ufsi.domain.Role
import oms.ufsi.repository.RoleRepository

/**
 * Сервіс роботи з ролями користувачів.
 *
 * Саме тут повинна розміщуватися бізнес-логіка,
 * пов'язана з ролями.
 *
 * API-маршрути не повинні напряму працювати
 * з репозиторіями або базою даних.
 */
class RoleService(
    private val roleRepository: RoleRepository
) {

    /**
     * Повертає всі ролі системи.
     *
     * Наразі метод лише делегує виклик репозиторію,
     * але в майбутньому тут можуть з'явитися:
     * - перевірки прав доступу;
     * - кешування;
     * - аудит операцій;
     * - додаткові бізнес-правила.
     */
    fun getAllRoles(): List<Role> {

        return roleRepository.findAll()
    }

    /**
     * Повертає роль за її системним кодом.
     *
     * Якщо роль не знайдена, повертається null.
     */
    fun getRoleByCode(code: String): Role? {

        return roleRepository.findByCode(code.trim())
            ?: roleRepository.findAll().firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}
