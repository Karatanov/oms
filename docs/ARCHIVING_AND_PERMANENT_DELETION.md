# Archiving and permanent deletion

## Scope

This policy applies to user accounts and the project hierarchy (projects,
subprojects and subproject parts). These are the long-lived records that own
or are referenced by operational history.

## Archive

Archiving is the normal removal operation. It sets `is_archived`,
`archived_at`, and `archived_by` without deleting the row. Project hierarchy
archiving and restore include descendants so that a subproject part is not
left active below an archived parent.

Active records are the default for project and user lists, selectors, and the
map. Administrators can switch user and project registries between Active,
Archived, and All, and restore archived entries. An archived user is disabled
and cannot authenticate. Existing reports, financial records, documents,
audit events, and other historical links remain readable.

## Permanent deletion

Permanent deletion is an administrator-only API operation. Before it is
enabled, OMS returns a dependency summary. The server repeats that check at
deletion time and returns `409 HAS_DEPENDENCIES` when related records exist.
It never relies on legacy database `CASCADE` constraints to erase history.

Project dependency checks include descendants, financial records, inspection
reports, documents, incidents, project amounts, monitoring details,
procurement records, and programme details. User dependency checks include
projects managed or created by the user, documents, inspection reports, and
audit records. This deliberately makes permanent deletion rare for real
operational records.

## Audit

`user_archived`, `user_restored`, `project_archived`, `project_restored`, and
`permanent_delete` are written to the audit log. The actor and affected entity
are retained for archive and restore operations.

## Related record deletion review

Inspection reports, documents, photos, findings, financial records, and
procurement records are operational content rather than owning registry
entities. Their existing dedicated delete workflows were reviewed but are not
silently changed by this migration: changing them requires entity-specific
retention periods and file-storage handling. They block permanent deletion of
their owning project, so neither they nor their files can be accidentally
removed through a project delete.

## Database migration

`V58__add_entity_archiving.sql` adds archival metadata and indexes for
`users` and `projects`. Existing rows are active by default. The archive actor
is protected by a restrictive foreign key to preserve accountability.
