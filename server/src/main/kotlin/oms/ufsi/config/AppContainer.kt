package oms.ufsi.config

import oms.ufsi.repository.ExposedRoleRepository
import oms.ufsi.repository.ExposedUserRepository
import oms.ufsi.repository.RoleRepository
import oms.ufsi.repository.UserRepository
import oms.ufsi.service.RoleService
import oms.ufsi.service.UserService

/**
 * Найпростіший контейнер залежностей застосунку.
 *
 * Об'єкти створюються один раз та повторно
 * використовуються всією системою.
 */
object AppContainer {

    /**
     * Шар доступу до даних.
     */
    val roleRepository: RoleRepository =
        ExposedRoleRepository()

    /**
     * Шар бізнес-логіки.
     */
    val roleService =
        RoleService(roleRepository)

    /**
     * Шар доступу до користувачів.
     */
    val userRepository: UserRepository =
        ExposedUserRepository()

    /**
     * Бізнес-логіка роботи з користувачами.
     */
    val userService =
        UserService(
            userRepository,
            roleService
        )
}