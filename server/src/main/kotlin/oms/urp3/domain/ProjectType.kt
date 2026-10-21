package oms.urp3.domain

/**
 * Тип запису в реєстрі проєктів.
 */
enum class ProjectType {

    /**
     * Звичайний проєкт.
     */
    PROJECT,

    /**
     * Підпроєкт.
     */
    SUBPROJECT,

    /** A constituent part of a subproject. */
    SUBPROJECT_PART
}
