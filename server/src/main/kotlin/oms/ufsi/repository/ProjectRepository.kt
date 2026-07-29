package oms.ufsi.repository

import oms.ufsi.domain.Project

/**
 * Контракт доступу до проєктів.
 *
 * Репозиторій відповідає виключно
 * за отримання та збереження даних.
 */
interface ProjectRepository {

    /**
     * Повертає всі доступні проєкти.
     *
     * На поточній ітерації фільтрація,
     * сортування та пагінація ще
     * не реалізовані.
     */
    fun findAll(): List<Project>

    /**
     * Створює новий проєкт.
     */
    fun create(
        name: String,
        siteName: String,
        siteNumber: String,
        address: String,
        region: String,
        city: String,
        latitude: Double,
        longitude: Double,
        sector: String,
        constructionType: String,
        budgetPlanned: Long,
        managerId: Long
    ): Project

    /**
     * Повертає проєкт за його публічним UUID.
     */
    fun findByUuid(
        uuid: String
    ): Project?
}