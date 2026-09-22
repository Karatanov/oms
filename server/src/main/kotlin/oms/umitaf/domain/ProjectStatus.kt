package oms.umitaf.domain

/**
 * Статуси життєвого циклу проєкту.
 */
enum class ProjectStatus {

    PLANNED,

    ACTIVE,

    SUSPENDED,

    COMPLETED,

    ARCHIVED,

    /**
     * Defects Liability Period.
     */
    DLP
}