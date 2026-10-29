# Feedback to the OMS tracker

The sidebar footer opens a Ukrainian/English feedback form. On phones a feedback
icon is also available in a reserved bottom-left footer beside the table navigation controls.
The current section is preselected; users can change the page/category, select
Correction or Improvement, and enter 1–5000 characters. The server supplies the
current calendar date in Europe/Kyiv and recalculates it at submission.

Destination: spreadsheet `1dSOFZ1tzuzLUIaK6LcaklqZTXkmAwRqby_TOCZx1dMk`,
sheet ID `1327224389`, tab `OMS Issues & Improvements`.
Columns A–K: next issue number, type, date, page, description, blank proposed
solution, blank IT comments, blank priority, `New`, blank effort, blank realization.
Descriptions are Google `stringValue` cells, never interpreted as formulas.
Existing rows are not edited.

## Server setup

1. Enable Google Sheets API for the service account's Google Cloud project.
2. Grant that service account Editor access to the tracker.
3. Mount its JSON key outside the repository and set
   `OMS_FEEDBACK_GOOGLE_CREDENTIALS` to the path **inside** the container.
   `GOOGLE_APPLICATION_CREDENTIALS` is the fallback. The existing user OAuth
   web-client JSON used for procurement is not a service-account key and cannot
   be used here.
4. Restart/redeploy the server and frontend together. The optional Compose overlay
   `deploy/vps/docker-compose.feedback.yml` mounts the key.

No Google secrets or tokens are delivered to the browser. GET `/api/v1/feedback`
returns configuration presence and today's date; POST accepts `requestId`,
`page`, `type`, `description`. Both require a valid OMS session (including guest).
Presence of a key does not prove that Google granted write access: failed requests
display an error and preserve the form for retry. Success is displayed only after
Google acknowledges the write or a previously written request is found.

The row's number-cell note stores a request marker and date for retry detection,
including after a server restart. Keep that note when manually editing the tracker.
The service serializes writes and numbering within the existing single-server
deployment. Multiple server replicas or concurrent external writers allocating
issue numbers require a shared lock/sequence before scaling this integration.

## Verification

`FeedbackServiceTest` covers input validation, literal formula-like text, column
mapping, Kyiv midnight, concurrent numbering, and retry after a lost response.
Run `:server:test --tests oms.urp3.service.FeedbackServiceTest` and
`:composeApp:compileKotlinJs` (and `:composeApp:compileKotlinWasmJs` for Wasm).
After credentials are configured, submit a real issue through the UI and check
the new row in the tracker. No synthetic issues are added to the live tracker by tests.

Browser regression: build `:composeApp:jsBrowserDevelopmentWebpack`, copy the
processed `index.html` into the bundle directory as `app.html`, set
`OMS_SMOKE_ASSETS` to that directory, and run `node tools/test-feedback.cjs`.
It uses installed Chrome by default (`CHROME_BIN` overrides the executable),
intercepts feedback requests, and checks page/type selection, text input, failed
submission and identical retry payloads at 1440×1000, 390×844 and 320×568.
Screenshots are saved under `build/feedback-screenshots/`.
