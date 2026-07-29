package oms.ufsi.dto

/**
 * Архітектурне правило проєкту:
 *
 * Domain-моделі не повинні залежати від HTTP,
 * JSON або механізмів серіалізації.
 *
 * Саме DTO визначають зовнішній контракт API.
 */
import oms.ufsi.domain.Role
import oms.ufsi.domain.User

/**
 * Перетворення доменних моделей у DTO.
 *
 * Виділення маперів в окремий файл дозволяє
 * не змішувати бізнес-логіку та форматування
 * HTTP-відповідей.
 */

/**
 * Перетворює доменну модель ролі у DTO.
 */
fun Role.toResponse(): RoleResponse {

    return RoleResponse(
        id = id,
        code = code,
        name = name
    )
}

/**
 * Перетворює доменну модель користувача у DTO.
 */
fun User.toResponse(): UserResponse {

    return UserResponse(
        id = id,
        username = username,
        email = email,
        role = role.toResponse()
    )
}