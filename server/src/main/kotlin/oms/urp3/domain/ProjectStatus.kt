package oms.urp3.domain

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