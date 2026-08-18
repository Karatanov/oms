package oms.model

/*
   Статуси проектів.

   Використання enum:
   - уникає помилок у строках
   - спрощує стилізацію UI
*/

enum class ProjectStatus {

    PLANNED,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    ARCHIVED,
    DLP

}
