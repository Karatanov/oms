# API Handover Reference

The API base is `/api/v1`; JSON is the default representation. `server/requests.http` contains development examples. There is no generated OpenAPI endpoint in Phase 1, so routes and DTOs in `server/src/main/kotlin/oms/ufsi` are the code-level source of truth.

## Authentication and errors

- **POST** `/auth/login`, **POST** `/auth/logout`, **POST** `/auth/activate`.
- Browser calls use the HTTP-only session cookie. Other clients may send `Authorization: Bearer <token>`.
- Errors use an HTTP status plus `{ "code": "...", "message": "..." }`. `400` is validation/parsing, `401` is unauthenticated or inactive, `403` is RBAC/project-scope denial, `404` is missing resource, and `409` is a state/conflict condition.

## Endpoint groups

Inspection report responses include optional `authorUsername`, resolved from the report creator. Clients may display an em dash if it is absent. This is an additive field; UUIDs and existing workflow/authorization contracts are unchanged.

- Health: **GET** `/health`, **GET** `/api/v1/health`.
- Dashboard/map: **GET** `/dashboard`, **GET** `/projects/map`.
- Projects: list/detail/create/update/delete, bulk status/reassignment, XLSX export and health/safety observations under `/projects`.
- Inspections/SIR: manual create, XLS/XLSX import, report edit/delete/move/submit/review, findings, photos and original source-file download. Report editing supports the SIR code, date, title/summary, planned/unplanned/final type and optional GPS coordinates; report assignment is changed with the dedicated move operation. Workflow status remains controlled by submit/review rather than direct editing.
- Financials: CRUD, import and export under `/projects/{projectUuid}/financials`.
- Documents: list/upload/download/delete under `/projects/{projectUuid}/documents`.
- Procurement: list and Admin/PM mutations under `/procurements`.
- Administration: Admin-only users CRUD and role lookup under `/users` and `/roles`.

List routes accept the filters/pagination implemented by their route DTO parsing; response metadata is represented by `PageMetadata`. Upload routes use multipart form data, validate size/type in their services, save bytes under `OMS_UPLOAD_DIR`, and persist metadata only after storage succeeds. Download performs authorization before resolving the stored file and returns controlled `404` for absent metadata or bytes.

Backend authorization is authoritative: Admin has full access; Project Manager mutates only assigned projects; Inspector writes SIR/photo content; Viewer is the authenticated read-only role. `POST /auth/guest` creates an anonymous browser session, not a user account or role; it is limited to the public map, project registry and general project-information endpoint. Financial and document mutations are Admin/PM only, and user administration is Admin only.
