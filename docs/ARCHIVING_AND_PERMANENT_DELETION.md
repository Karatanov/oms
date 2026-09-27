# Archiving and permanent deletion

## Scope

This policy applies to user accounts and the project hierarchy (projects,
subprojects and subproject parts). These are the long-lived records that own
or are referenced by operational history.

## Project status

For projects, subprojects and subproject parts, **Archived** is a value of the
existing `projects.status` field. OMS does not use a separate project archive
flag or a second archive filter. The registry opens with the Status filter set
to **Active**; users choose **Archived** through that same status filter when
they need to review archived records. Archived hierarchy entries are excluded
from the map.

User accounts continue to use their separate archival metadata because user
status is not the project lifecycle status and an archived account must not be
able to authenticate. Existing reports, financial records, documents, audit
events, and other historical links remain readable.

## Permanent deletion

Permanent deletion is an administrator-only operation, exposed as **Delete
permanently** in the project registry and project details. The server repeats
its dependency check at deletion time and returns `409 HAS_DEPENDENCIES` when
related records exist.
It never relies on legacy database `CASCADE` constraints to erase history.

Project dependency checks include descendants, financial records, inspection
reports, documents, incidents, project amounts, monitoring details,
procurement records, and programme details. User dependency checks include
projects managed or created by the user, documents, inspection reports, and
audit records. This deliberately makes permanent deletion rare for real
operational records.

## Audit

`user_archived`, `user_restored`, project lifecycle status changes, and
`permanent_delete` are written to the audit log.

## Related record deletion review

Inspection reports, documents, photos, findings, financial records, and
procurement records are operational content rather than owning registry
entities. Their existing dedicated delete workflows were reviewed but are not
silently changed by this migration: changing them requires entity-specific
retention periods and file-storage handling. They block permanent deletion of
their owning project, so neither they nor their files can be accidentally
removed through a project delete.

## Database migration

`V59__migrate_project_archive_to_status.sql` maps any temporary project
archive flags written by the earlier implementation to `projects.status =
'archived'`. Application code then uses the lifecycle status exclusively.
