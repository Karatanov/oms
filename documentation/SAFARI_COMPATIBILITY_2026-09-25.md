# Safari data-loading failure

## Reproduction before application changes

Fresh browser contexts on the live `https://karatanov.github.io/oms/` frontend,
calling the live `https://oms-3j46.onrender.com/api/v1` API:

| Engine | POST auth/guest | Stored API cookie | GET projects/map |
| --- | --- | --- | --- |
| Chromium 153 | 204 | oms_session, Secure, SameSite=None | 200 |
| Playwright WebKit | 204 | none | 401 AUTHENTICATION_REQUIRED |

WebKit emitted no page JavaScript errors during this reproduction. Firefox's
local executable could not launch (`spawn UNKNOWN`); Linux CI covers it instead.
No production user credentials were used, and no project records were changed.
The customer clarified the observed failure was authenticated login. Source
inspection confirms the same failure point: login returned accessToken but
LoginPayload only decoded user, so all subsequent API requests depended on the
same rejected cross-site cookie. A real authenticated-login fixture is included
in the disposable MySQL backend integration test.

## Root cause and scope

GitHub Pages and Render are different sites. Safari's third-party cookie policy
prevents this authentication design from working even with credentials:include
and SameSite=None; it is not repaired by loosening those attributes. Relevant
upstream reference: https://webkit.org/blog/10218/full-third-party-cookie-blocking-and-more/
The public build is Kotlin/JS (`jsBrowserProductionWebpack`, composeApp.js),
not the Kotlin/Wasm target. Skiko uses WebAssembly for rendering, but the
reproduced failure is an HTTP 401, not a WasmGC/Compose initialization error.
The customer's exact Safari/OS version remains unknown. No exact oldest
supported Safari version is asserted. The cookie restriction affects modern
Safari regardless of support for WasmGC.

## Minimal authentication-path repair

- Decode the existing login JWT and send Authorization only to the configured API.
- The login response explicitly advertises browser bearer/CORS support. Until
  Render is updated, a newly deployed Pages build retains the old cookie path
  rather than breaking working Chrome sessions with a rejected preflight.
- Guest entry also issues a signed, one-hour JWT; role restrictions are unchanged.
- Restore sessions through the same validated bearer/cookie authorization path.
- Allow the Authorization CORS header for existing permitted origins, not a wildcard.
- Store tokens in first-party sessionStorage (one tab), with a memory fallback
  when storage is unavailable. Do not store tokens in localStorage or URLs.
- Logout clears the local token and asks the server to clear its cookie.
- Native upload fetches use the same header. Protected images/downloads use
  authenticated fetch plus blob URLs, since img/navigation cannot set a bearer
  header. Expose Content-Disposition so original download names are retained.

JavaScript-accessible sessionStorage is not HttpOnly: existing XSS protections
remain important. The server's existing one-hour JWT expiry is retained; there
is no new refresh-token protocol. A new tab/storage denial/expired token can
require sign-in again. A stable OMS_JWT_SECRET is required to retain JWTs across
backend restarts. No Safari tracking-protection setting needs to be disabled.

## Changed files

- auth.js, index.html: scoped browser auth, native fetch/image/download transport.
- OmsApiClient.kt, OmsApiUrl.kt, AppState.kt: login token, API headers, restore/logout.
- DocumentsScreen.kt, ProjectDetailScreen.kt, ProjectsScreen.kt, ReportsScreen.kt:
  replace cookie-only API navigation with authenticated downloads.
- AuthRoutes.kt, LoginResponse.kt, Cors.kt: guest JWT, authenticated restoration,
  rolling-deployment capability and preflight headers.
- ApplicationTest.kt, FindingDatabaseIntegrationTest.kt: real server auth regressions.
- tools/test-browser-auth.cjs and project-form-tests.yml: cookie-free user/guest
  flows in Chromium, WebKit and Firefox; downloads, images, reload, logout and
  prevention of token forwarding to another origin.

## Limits

WebKit is engine coverage, not a claim that every Safari/macOS/iOS release was
tested. Full production authenticated UI validation requires deploying the
backend changes; Render was not deployed automatically. No unrelated UI or
business rules were changed. The broader performance audit remains paused.
