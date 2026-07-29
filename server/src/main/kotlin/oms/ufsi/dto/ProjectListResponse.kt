package oms.ufsi.dto

import kotlinx.serialization.Serializable

/**
 * Відповідь REST API
 * зі списком проєктів.
 */
@Serializable
data class ProjectListResponse(

    /**
     * Дані поточної сторінки.
     */
    val data: List<ProjectResponse>,

    /**
     * Інформація про пагінацію.
     */
    val meta: PageMetadata
)