package oms.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsName
import kotlin.js.unsafeCast
import kotlin.js.toJsString
import org.w3c.fetch.RequestCredentials

@JsName("setOmsAccessToken")
private external fun setOmsAccessToken(value: String)
@JsName("omsAuthorizationFor")
private external fun omsAuthorizationFor(url: String): String

@OptIn(ExperimentalWasmJsInterop::class)
object OmsApiClient {
    private val baseUrl = omsApiBaseUrl

    private val client = HttpClient(Js) {
        engine {
            configureRequest {
                credentials = "include".toJsString().unsafeCast<RequestCredentials>()
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // Browser fetch can remain pending indefinitely after a stalled proxy
        // or broken network transition. Bound ordinary API calls so every
        // screen can leave its loading state and surface its existing retry UI.
        // Large imports use asynchronous server jobs and polling, while file
        // downloads bypass this client, so this ceiling does not cut off them.
        install(HttpTimeout) {
            requestTimeoutMillis = 75_000
        }
    }

    init {
        client.plugin(HttpSend).intercept { request ->
            val authorization = omsAuthorizationFor(request.url.build().toString())
            if (authorization.isNotEmpty()) request.headers.append("Authorization", authorization)
            // Loading belongs to the requesting screen/component, not transport.
            execute(request)
        }
    }

    suspend fun login(username: String, password: String): ApiUser {
        val response = client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            val message = runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorPayload>(body).message
            }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: body.ifBlank { "Authentication failed." })
        }
        val payload = response.body<LoginPayload>()
        // Older deployments can return a JWT without the browser-bearer marker.
        setOmsAccessToken(if (payload.browserBearerSupported) payload.accessToken.orEmpty() else "")
        return payload.user
    }

    /**
     * Returns the active server session without showing a global loader.
     *
     * This request is the only network operation that runs before Compose can
     * reveal the login screen. A network stack may leave `fetch` pending
     * indefinitely, so bound this bootstrap check and fall back to login.
     */
    suspend fun currentSessionUser(): ApiUser? = withTimeoutOrNull(12_000) {
        val response = client.get("$baseUrl/auth/session?background=true")
        when {
            response.status.value == 401 -> {
                setOmsAccessToken("")
                null
            }
            !response.status.isSuccess() -> throw IllegalStateException(response.bodyAsText())
            else -> {
                // The verified guest marker is a UI identity, never a persisted user record.
                val body = response.bodyAsText()
                if (body.contains("\"guest\":true")) ApiUser(0L, "Guest", "", role = ApiRole(0L, "GUEST", "Guest"))
                else Json { ignoreUnknownKeys = true }.decodeFromString(body)
            }
        }
    }

    suspend fun startGuestSession() {
        val response = client.post("$baseUrl/auth/guest")
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
        setOmsAccessToken(if (response.status.value == 204) "" else response.body<GuestTokenPayload>().accessToken.orEmpty())
    }

    suspend fun activate(token: String, password: String) {
        val response = client.post("$baseUrl/auth/activate") {
            contentType(ContentType.Application.Json)
            setBody(ActivateAccountRequest(token, password))
        }
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
    }

    suspend fun requestPasswordReset(identifier: String) {
        val response = client.post("$baseUrl/auth/password-reset") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetRequest(identifier))
        }
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
    }

    suspend fun projects(): List<ApiProject> {
        // The complete project tree is a shared in-memory client snapshot.
        // A single moderately sized response avoids serial round-trips once
        // the registry exceeds the former 100-row page size.
        // The shared snapshot currently contains just over 300 rows. One
        // request avoids rebuilding the server-side financial/map metadata a
        // second time before a user can navigate to another workspace.
        val pageSize = 500
        val projects = mutableListOf<ApiProject>()
        var page = 1
        var hasNextPage = true
        while (hasNextPage) {
            val payload = client.get("$baseUrl/projects?page=$page&pageSize=$pageSize")
                .body<ProjectListPayload>()
            projects += payload.data
            // Newer OMS servers provide an exact page count. Keep the old
            // size-based fallback during a rolling deploy, but avoid a known
            // redundant empty request whenever the registry has exactly a
            // multiple of the configured page size.
            hasNextPage = hasNextProjectPage(page, payload, pageSize)
            page += 1
        }
        return projects
    }

    suspend fun users(archive: String = "active"): List<ApiUser> = client.get("$baseUrl/users") { parameter("archive", archive) }.body()

    suspend fun roles(): List<ApiRole> = client.get("$baseUrl/roles").body()

    suspend fun updateUser(id: Long, request: UpdateUserRequest): ApiUser {
        val response = client.patch("$baseUrl/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            val message = runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorPayload>(body).message
            }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: body.ifBlank { "Could not save user." })
        }
        return response.body()
    }

    suspend fun createUser(request: CreateUserRequest): ApiUser =
        client.post("$baseUrl/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun createProject(request: CreateProjectRequest): ApiProject =
        client.post("$baseUrl/projects") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** A currency rate enriches a form; it must not block navigation to that form. */
    suspend fun projectExchangeRate(date: String? = null): ProjectExchangeRate = client.get("$baseUrl/exchange-rates/eur?background=true") {
        date?.let { parameter("date", it) }
    }.body<ProjectExchangeRate>().also { rate ->
        // An older API ignores date and returns today's rate. Never silently
        // substitute it for a historical record during a rolling deployment.
        require(date == null || rate.date <= date) { "Historical exchange rate unavailable." }
    }

    suspend fun geocodeAddress(address: String, city: String, region: String): GeocodeAddressResponse {
        val response = client.post("$baseUrl/geocode/address") {
            contentType(ContentType.Application.Json)
            setBody(GeocodeAddressRequest(address, city, region))
        }
        if (!response.status.isSuccess()) throw IllegalStateException(
            if (response.status.value == 404) "GEOCODE_NOT_FOUND" else "GEOCODE_UNAVAILABLE")
        return response.body()
    }

    suspend fun updateProject(projectUuid: String, request: UpdateProjectRequest): ApiProject =
        client.patch("$baseUrl/projects/$projectUuid") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun bulkUpdateProjectStatus(projectUuids: List<String>, status: String): Int =
        client.patch("$baseUrl/projects/bulk-status") {
            contentType(ContentType.Application.Json)
            setBody(BulkProjectUpdateRequest(projectUuids, status))
        }.body<BulkProjectUpdateResponse>().updated

    suspend fun bulkReassignProjects(projectUuids: List<String>, managerId: Long): Int =
        client.patch("$baseUrl/projects/bulk-reassign") {
            contentType(ContentType.Application.Json)
            setBody(BulkProjectReassignRequest(projectUuids, managerId))
        }.body<BulkProjectUpdateResponse>().updated

    fun projectExportUrl(projectUuids: Collection<String>): String =
        "$baseUrl/projects/export" + projectUuids.joinToString(prefix = "?", separator = "&") { "uuid=$it" }

    /**
     * Sends the filtered table order in the request body rather than the URL.
     * A registry can contain hundreds of UUIDs, exceeding common proxy URL
     * limits when a status or construction-type filter selects many rows.
     */
    fun downloadProjectTablePdf(projectUuids: Collection<String>, language: String, filterSummary: String) {
        val body = buildJsonObject {
            put("projectUuids", JsonArray(projectUuids.map(::JsonPrimitive)))
            put("language", language)
            put("filterSummary", filterSummary)
        }.toString()
        openOmsJsonDownload("$baseUrl/projects/export-table-pdf", body)
    }

    /** Mirrors the active Financial Monitoring view without serializing every record UUID into the URL. */
    fun financialTablePdfExportUrl(
        language: String,
        currency: String,
        recordType: String?,
        paymentPurpose: String?,
        tranche: Int?,
        subprojectUuid: String?,
        region: String?,
        month: String?,
        year: Int?,
        sort: String,
        ascending: Boolean,
        filterSummary: String
    ): String {
        val parameters = buildList {
            add("lang=$language")
            add("currency=${currency.encodeURLParameter()}")
            recordType?.let { add("recordType=${it.encodeURLParameter()}") }
            paymentPurpose?.let { add("paymentPurpose=${it.encodeURLParameter()}") }
            tranche?.let { add("tranche=$it") }
            subprojectUuid?.let { add("subproject=${it.encodeURLParameter()}") }
            region?.let { add("region=${it.encodeURLParameter()}") }
            month?.let { add("month=${it.encodeURLParameter()}") }
            year?.let { add("year=$it") }
            add("sort=${sort.encodeURLParameter()}")
            add("ascending=$ascending")
            filterSummary.takeIf(String::isNotBlank)?.let { add("filters=${it.encodeURLParameter()}") }
        }
        return "$baseUrl/financials/export-table-pdf?${parameters.joinToString("&")}"
    }

    suspend fun dashboard(trancheNumber: Int? = null): ApiDashboard = client.get("$baseUrl/dashboard") {
        trancheNumber?.let { parameter("tranche", it) }
    }.body()

    suspend fun dashboardOverview(trancheNumber: Int? = null): ApiDashboardOverview {
        // Keep exactly one overview request per opened Dashboard.  A former
        // HTML-level prefetch could still be running when Compose started,
        // causing two expensive aggregations to contend for the same database.
        // Do not fall back to the larger legacy endpoint either: this screen
        // already provides a visible retry state for a genuine API failure.
        return client.get("$baseUrl/dashboard/overview") {
            trancheNumber?.let { parameter("tranche", it) }
        }.body()
    }

    suspend fun inspectionAnalytics(): ApiInspectionAnalytics =
        client.get("$baseUrl/inspection-reports/analytics").body()

    suspend fun procurements(subprojectCode: String? = null, lotCode: String? = null): List<ApiProcurementRecord> =
        client.get("$baseUrl/procurements") {
            subprojectCode?.takeIf(String::isNotBlank)?.let { parameter("subprojectCode", it) }
            lotCode?.takeIf(String::isNotBlank)?.let { parameter("lotCode", it) }
        }.body()

    /** Ordered IDs retain the exact filtered/sorted procurement table in the PDF. */
    fun procurementTablePdfExportUrl(recordIds: Collection<Long>, language: String, filterSummary: String): String =
        "$baseUrl/procurements/export-table-pdf?lang=$language&filters=${filterSummary.encodeURLParameter()}&empty=${recordIds.isEmpty()}" +
            recordIds.joinToString(separator = "", prefix = "") { "&id=$it" }

    suspend fun startProcurementRefresh(): ApiProcurementRefresh =
        client.post("$baseUrl/procurements/refresh").body()

    suspend fun procurementRefresh(id: String): ApiProcurementRefresh =
        client.get("$baseUrl/procurements/refresh/$id").body()

    suspend fun cancelProcurementRefresh(id: String): ApiProcurementRefresh =
        client.post("$baseUrl/procurements/refresh/$id/cancel").body()

    suspend fun subprojectRegistryRefresh(id: String): ApiSubprojectRegistryRefresh =
        client.get("$baseUrl/projects/registry/refresh/$id").body()

    suspend fun cancelSubprojectRegistryRefresh(id: String): ApiSubprojectRegistryRefresh =
        client.post("$baseUrl/projects/registry/refresh/$id/cancel").body()

    suspend fun projectDetails(uuid: String): ApiProjectDetails =
        client.get("$baseUrl/projects/$uuid").body()

    suspend fun projectReports(projectUuid: String): List<ApiInspectionReport> =
        client.get("$baseUrl/projects/$projectUuid/inspection-reports").body()

    suspend fun inspectionReports(): List<ApiProjectInspectionReport> =
        client.get("$baseUrl/inspection-reports").body()

    suspend fun inspectionReportPreview(reportUuid: String): ApiInspectionReportPreview =
        client.get("$baseUrl/inspection-reports/$reportUuid/preview").body()

    suspend fun healthSafetyObservations(projectUuid: String): ApiHealthSafetyObservations =
        client.get("$baseUrl/projects/$projectUuid/health-safety-observations").body()

    suspend fun inspectionPhotos(reportUuid: String): List<ApiInspectionPhoto> {
        // Older imported SIR workbooks may need one background extraction of
        // embedded photos. A 202 response means the report itself remains
        // usable and this independent request may poll without blocking it.
        // A report can contain up to 30 high-resolution pictures and several
        // reports can be queued for the single server-side extractor. Keep
        // retrying long enough for that bounded background job to finish;
        // callers run this independently from the preview/editor payload.
        repeat(90) {
            val response = client.get("$baseUrl/inspection-reports/$reportUuid/photos?background=true")
            val photos = response.body<List<ApiInspectionPhoto>>()
            if (response.status != HttpStatusCode.Accepted) return photos
            delay(1_000)
        }
        return emptyList()
    }

    suspend fun createAndSubmitInspectionReport(
        projectUuid: String,
        inspectionDate: String,
        summary: String,
        inspectionType: String = "planned",
        latitude: Double? = null,
        longitude: Double? = null
    ): ApiInspectionReport {
        val draft: ApiInspectionReport = client.post("$baseUrl/projects/$projectUuid/inspection-reports") {
            contentType(ContentType.Application.Json)
            setBody(CreateInspectionReportRequest(inspectionDate, summary, inspectionType = inspectionType, latitude = latitude, longitude = longitude))
        }.body()
        return client.post("$baseUrl/inspection-reports/${draft.uuid}/submit").body()
    }

    /** Creates a report in DRAFT status without submitting it for review. */
    suspend fun createInspectionReportDraft(
        projectUuid: String,
        inspectionDate: String,
        summary: String,
        inspectionType: String = "planned",
        latitude: Double? = null,
        longitude: Double? = null
    ): ApiInspectionReport =
        client.post("$baseUrl/projects/$projectUuid/inspection-reports") {
            contentType(ContentType.Application.Json)
            setBody(CreateInspectionReportRequest(inspectionDate, summary, inspectionType = inspectionType, latitude = latitude, longitude = longitude))
        }.body()

    suspend fun updateInspectionReport(
        reportUuid: String,
        inspectionDate: String,
        summary: String,
        reportCode: String?,
        inspectionType: String,
        latitude: Double?,
        longitude: Double?
    ): ApiInspectionReport =
        client.put("$baseUrl/inspection-reports/$reportUuid") {
            contentType(ContentType.Application.Json)
            setBody(UpdateInspectionReportRequest(inspectionDate, summary, reportCode, inspectionType, latitude, longitude))
        }.body()

    suspend fun updateInspectionReportStatus(
        reportUuid: String,
        status: String
    ): ApiInspectionReport =
        client.patch("$baseUrl/inspection-reports/$reportUuid/status") {
            contentType(ContentType.Application.Json)
            setBody(UpdateInspectionReportStatusRequest(status))
        }.body()

    suspend fun createManualInspectionReport(projectUuid: String, request: ManualInspectionReportRequest): ApiInspectionReport {
        val response = client.post("$baseUrl/projects/$projectUuid/inspection-reports/manual") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
        return runCatching { response.body<ApiInspectionReport>() }.getOrElse {
            projectReports(projectUuid)
                .lastOrNull { it.inspectionDate == request.inspectionDate && it.summary == "Manual SIR: ${request.contractor}" }
                ?: throw IllegalStateException("Created inspection report was not returned by the server.")
        }
    }

    suspend fun manualInspectionReport(reportUuid: String): ApiManualInspectionEditor =
        // Parsing an XLSX belongs to the report screen's inline loader. A
        // global blocking overlay here made every navigation button appear
        // frozen until Apache POI had finished reading an imported report.
        client.get("$baseUrl/inspection-reports/$reportUuid/manual?background=true").let { response ->
            if (!response.status.isSuccess()) throw response.asApiException()
            response.body()
        }

    suspend fun updateManualInspectionReport(
        reportUuid: String,
        projectUuid: String,
        status: String?,
        manual: ManualInspectionReportRequest
    ): ApiInspectionReport {
        val response = client.put("$baseUrl/inspection-reports/$reportUuid/manual") {
            contentType(ContentType.Application.Json)
            setBody(UpdateManualInspectionReportRequest(projectUuid, status, manual))
        }
        // Error responses have a different JSON shape ({ error, message }).
        // Do not try to deserialize one as an inspection report: doing so hid
        // the useful server-side validation message behind a misleading
        // "uuid, inspectionDate, status are required" serialization error.
        if (!response.status.isSuccess()) throw response.asApiException()
        // Some older backend instances acknowledged this update with an
        // empty/legacy response body.  The update has already succeeded in
        // that case, so refresh the saved report rather than leaving the form
        // with a false error.
        return runCatching { response.body<ApiInspectionReport>() }.getOrElse {
            projectReports(projectUuid).firstOrNull { it.uuid == reportUuid }
                ?: throw IllegalStateException("Inspection report was saved but could not be refreshed.")
        }
    }

    suspend fun reviewInspectionReport(
        reportUuid: String,
        action: String,
        rejectionReason: String? = null
    ): ApiInspectionReport =
        client.post("$baseUrl/inspection-reports/$reportUuid/review") {
            contentType(ContentType.Application.Json)
            setBody(ReviewInspectionReportRequest(action, rejectionReason))
        }.body()

    suspend fun projectDocuments(projectUuid: String): List<ApiProjectDocument> =
        client.get("$baseUrl/projects/$projectUuid/documents").body()

    suspend fun allProjectDocuments(): List<ApiProjectDocumentListItem> =
        client.get("$baseUrl/documents").body()

    suspend fun financials(projectUuid: String): ApiFinancialRecords =
        client.get("$baseUrl/projects/$projectUuid/financials").body()

    suspend fun allFinancialRecords(): List<ApiProjectFinancialRecord> =
        client.get("$baseUrl/financials").body()

    suspend fun financialProgress(
        region: String? = null,
        tranche: Int? = null,
        subprojectUuid: String? = null,
        year: Int? = null
    ): ApiFinancialProgress =
        client.get("$baseUrl/financials/progress") {
            region?.takeIf(String::isNotBlank)?.let { parameter("region", it) }
            tranche?.let { parameter("tranche", it) }
            subprojectUuid?.takeIf(String::isNotBlank)?.let { parameter("subprojectUuid", it) }
            year?.let { parameter("year", it) }
        }.body()

    /** Returns source-level aggregate indicators without importing financial records. */
    suspend fun paymentMonitoring(): ApiPaymentMonitoring =
        client.get("$baseUrl/financials/payment-monitoring").body()

    /** URL keeps the browser's authenticated download flow for the read-only payment register. */
    fun paymentMonitoringPdfExportUrl(language: String): String =
        "$baseUrl/financials/payment-monitoring/export-pdf?lang=${language.encodeURLParameter()}"

    suspend fun createFinancialRecord(projectUuid: String, request: FinancialRecordRequest): ApiFinancialRecord =
        client.post("$baseUrl/projects/$projectUuid/financials") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateFinancialRecord(sourceProjectUuid: String, recordUuid: String, targetProjectUuid: String, request: FinancialRecordRequest): ApiFinancialRecord =
        client.put("$baseUrl/projects/$sourceProjectUuid/financials/$recordUuid") {
            contentType(ContentType.Application.Json)
            setBody(FinancialRecordUpdateRequest(request, targetProjectUuid))
        }.body()

    suspend fun deleteFinancialRecord(projectUuid: String, recordUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid/financials/$recordUuid").status.isSuccess()

    suspend fun archiveProject(projectUuid: String): Boolean =
        client.post("$baseUrl/projects/$projectUuid/archive").status.isSuccess()
    suspend fun restoreProject(projectUuid: String): Boolean =
        client.post("$baseUrl/projects/$projectUuid/restore").status.isSuccess()
    suspend fun projectPermanentDeleteDependencies(projectUuid: String): Map<String, Long> =
        client.get("$baseUrl/projects/$projectUuid/permanent-delete-dependencies").body()
    suspend fun permanentlyDeleteProject(projectUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid/permanent").status.isSuccess()

    suspend fun archiveUser(id: Long): Boolean = client.post("$baseUrl/users/$id/archive").status.isSuccess()
    suspend fun restoreUser(id: Long): Boolean = client.post("$baseUrl/users/$id/restore").status.isSuccess()
    suspend fun userPermanentDeleteDependencies(id: Long): Map<String, Long> =
        client.get("$baseUrl/users/$id/permanent-delete-dependencies").body()
    suspend fun permanentlyDeleteUser(id: Long): Boolean = client.delete("$baseUrl/users/$id/permanent").status.isSuccess()

    suspend fun deleteInspectionReport(reportUuid: String): Boolean =
        client.delete("$baseUrl/inspection-reports/$reportUuid").status.isSuccess()

    suspend fun moveInspectionReport(reportUuid: String, targetProjectUuid: String): Boolean =
        client.patch("$baseUrl/inspection-reports/$reportUuid/project") {
            contentType(ContentType.Application.Json)
            setBody(MoveInspectionReportRequest(targetProjectUuid))
        }.status.isSuccess()

    suspend fun inspectionFindings(reportUuid: String): List<ApiInspectionFinding> =
        client.get("$baseUrl/inspection-reports/$reportUuid/findings").body()

    suspend fun createInspectionFinding(reportUuid: String, request: CreateInspectionFindingRequest): ApiInspectionFinding =
        client.post("$baseUrl/inspection-reports/$reportUuid/findings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateInspectionFinding(reportUuid: String, findingUuid: String, request: UpdateInspectionFindingRequest): ApiInspectionFinding =
        client.put("$baseUrl/inspection-reports/$reportUuid/findings/$findingUuid") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteInspectionFinding(reportUuid: String, findingUuid: String): Boolean =
        client.delete("$baseUrl/inspection-reports/$reportUuid/findings/$findingUuid").status.isSuccess()

    suspend fun deleteProjectDocument(projectUuid: String, documentUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid/documents/$documentUuid").status.isSuccess()
}

@Serializable
data class LoginRequest(val username: String, val password: String)
@Serializable data class ActivateAccountRequest(val token: String, val password: String)
@Serializable data class PasswordResetRequest(val identifier: String)

@Serializable
data class LoginPayload(val user: ApiUser, val accessToken: String? = null, val browserBearerSupported: Boolean = false)
@Serializable
private data class GuestTokenPayload(val accessToken: String? = null)

@Serializable
private data class ApiErrorPayload(val error: String? = null, val message: String? = null)

/** Extract the server's actual error instead of attempting to decode it as a success DTO. */
private suspend fun HttpResponse.asApiException(): IllegalStateException {
    val body = bodyAsText()
    val message = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorPayload>(body).message
    }.getOrNull()
    return IllegalStateException(message?.takeIf { it.isNotBlank() } ?: body.ifBlank { "Request failed (${status.value})." })
}

// Server JSON intentionally omits default-valued cells from Excel templates.
// Defaults here keep imported/older SIR files readable and editable even when
// a whole activity, HSE answer, or signature cell was left blank.
@Serializable
data class ManualActivityRequest(val location: String = "", val description: String = "", val onSchedule: String = "no", val remarks: String? = null)
@Serializable data class ManualPurchasedMaterialRequest(val materialsAndEquipment: String = "", val characteristics: String? = null, val perDed: String? = null, val notes: String? = null)
@Serializable data class ManualHseObservationRequest(val observation: String = "", val answer: String? = null, val comment: String? = null)
@Serializable data class ManualRemarkRequest(
    val work: String = "",
    val comment: String = "",
    val rectification: String? = null,
    val status: String? = null
)
@Serializable data class ManualInspectionReportRequest(
    val inspectionDate: String = "", val inspectionType: String = "planned", val contractor: String = "", val contractorRepresentative: String? = null, val projectName: String? = null, val siteReference: String? = null, val qaStaff: String? = null, val mctdRepresentative: String? = null,
    val skilledLabor: String? = null, val unskilledLabor: String? = null, val siteManagement: String? = null, val weather: String? = null,
    val activities: List<ManualActivityRequest> = emptyList(), val purchasedMaterials: List<ManualPurchasedMaterialRequest> = emptyList(), val ongoingObservations: List<String> = emptyList(), val hseObservations: List<ManualHseObservationRequest> = emptyList(),
    val qualityRemarks: List<ManualRemarkRequest> = emptyList(), val progressComment: String? = null, val scheduleRemark: String? = null, val inspectorName: String = "", val inspectorTitle: String? = null,
    val latitude: Double? = null, val longitude: Double? = null
)

@Serializable
data class ApiManualInspectionEditor(
    val projectUuid: String,
    val status: String,
    val manual: ManualInspectionReportRequest
)

@Serializable
data class UpdateManualInspectionReportRequest(
    val projectUuid: String,
    val status: String? = null,
    val manual: ManualInspectionReportRequest
)

@Serializable
data class CreateInspectionReportRequest(
    val inspectionDate: String,
    val summary: String,
    val reportCode: String? = null,
    val inspectionType: String = "planned",
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class UpdateInspectionReportRequest(
    val inspectionDate: String,
    val summary: String,
    val reportCode: String? = null,
    val inspectionType: String = "planned",
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class UpdateInspectionReportStatusRequest(val status: String)

@Serializable
data class MoveInspectionReportRequest(val projectUuid: String)

@Serializable
data class ReviewInspectionReportRequest(val action: String, val rejectionReason: String? = null)

@Serializable
data class CreateInspectionFindingRequest(val category: String, val severity: String, val description: String, val recommendation: String? = null)

@Serializable
data class UpdateInspectionFindingRequest(val category: String, val severity: String, val description: String, val recommendation: String? = null, val isResolved: Boolean)

@Serializable
data class FinancialRecordRequest(
    val recordType: String,
    val referenceNumber: String,
    val amount: Double,
    val currency: String = "EUR",
    val recordDate: String,
    val description: String? = null,
    val milestone: String? = null,
    val paymentPurpose: String = "works"
)

@Serializable
data class FinancialRecordUpdateRequest(
    val recordType: String,
    val referenceNumber: String,
    val amount: Double,
    val currency: String,
    val recordDate: String,
    val description: String? = null,
    val milestone: String? = null,
    val paymentPurpose: String = "works",
    val targetProjectUuid: String? = null
) {
    constructor(request: FinancialRecordRequest, targetProjectUuid: String) : this(
        request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate,
        request.description, request.milestone, request.paymentPurpose, targetProjectUuid
    )
}

@Serializable
data class CreateProjectRequest(
    val name: String,
    val status: String? = null,
    val siteName: String,
    val siteNumber: String,
    val description: String? = null,
    val address: String,
    val region: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val sector: String,
    val constructionType: String,
    val budgetPlanned: Long,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val managerId: Long? = null,
    val projectType: String = "project",
    val parentProjectUuid: String? = null,
    val trancheNumber: Int = 1,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val endDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null,
    val constructionContractNumber: String? = null,
    val technicalSupervisionName: String? = null,
    val technicalSupervisionContractNumber: String? = null,
    val technicalSupervisionContractDate: String? = null,
    val technicalSupervisionStartDate: String? = null,
    val technicalSupervisionPlannedEndDate: String? = null,
    val engineerConsultantName: String? = null,
    val engineerConsultantContractNumber: String? = null,
    val engineerConsultantContractDate: String? = null,
    val engineerConsultantStartDate: String? = null,
    val engineerConsultantPlannedEndDate: String? = null,
    val amounts: Map<String, ProjectAmountDto>? = null
)

@Serializable
data class GeocodeAddressRequest(val address: String, val city: String, val region: String)

@Serializable
data class GeocodeAddressResponse(val latitude: Double, val longitude: Double)

@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val siteName: String? = null,
    val siteNumber: String? = null,
    val description: String? = null,
    val address: String? = null,
    val region: String? = null,
    val city: String? = null,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sector: String? = null,
    val constructionType: String? = null,
    val trancheNumber: Int? = null,
    val budgetPlanned: Long? = null,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null,
    val constructionContractNumber: String? = null,
    val technicalSupervisionName: String? = null,
    val technicalSupervisionContractNumber: String? = null,
    val technicalSupervisionContractDate: String? = null,
    val technicalSupervisionStartDate: String? = null,
    val technicalSupervisionPlannedEndDate: String? = null,
    val engineerConsultantName: String? = null,
    val engineerConsultantContractNumber: String? = null,
    val engineerConsultantContractDate: String? = null,
    val engineerConsultantStartDate: String? = null,
    val engineerConsultantPlannedEndDate: String? = null,
    val amounts: Map<String, ProjectAmountDto>? = null
)

@Serializable
data class BulkProjectUpdateRequest(val projectUuids: List<String>, val status: String)

@Serializable
data class BulkProjectUpdateResponse(val updated: Int)
@Serializable data class BulkProjectReassignRequest(val projectUuids: List<String>, val managerId: Long)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val roleCode: String? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val status: String? = null,
    val region: String? = null,
    val department: String? = null,
    val preferredLang: String? = null
)

@Serializable
data class CreateUserRequest(
    val username: String, val email: String, val password: String = "", val roleCode: String,
    val firstName: String = "", val lastName: String = "", val status: String = "active",
    val region: String? = null, val department: String? = null, val preferredLang: String = "uk"
)

@Serializable
data class ProjectListPayload(
    val data: List<ApiProject>,
    val meta: ProjectPageMetadata? = null
)

@Serializable
data class ProjectPageMetadata(
    val page: Int,
    val perPage: Int,
    val total: Long,
    val totalPages: Int
)

/** Exact server metadata wins; the size check keeps mixed-version clients working. */
internal fun hasNextProjectPage(page: Int, payload: ProjectListPayload, pageSize: Int): Boolean =
    payload.meta?.let { page < it.totalPages } ?: (payload.data.size == pageSize)

@Serializable
data class ApiProject(
    val uuid: String,
    val projectType: String = "project",
    val trancheNumber: Int = 1,
    val parentProjectUuid: String? = null,
    val name: String,
    val nameEn: String? = null,
    val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null,
    val siteNumber: String,
    val region: String,
    val city: String,
    val cityEn: String? = null,
    val sector: String,
    val constructionType: String = "reconstruction",
    val budgetPlanned: Long,
    val currency: String = "UAH",
    val amounts: Map<String, ProjectAmountDto> = emptyMap(),
    val financingDisbursedEurCents: Long = 0L,
    val registry: Map<String, String> = emptyMap(),
    val contractorName: String? = null,
    val startDate: String? = null,
    val plannedEndDate: String? = null,
    val contractDurationDays: Long? = null,
    val status: String,
    val isArchived: Boolean = false,
    val archivedAt: String? = null,
    val archivedBy: Long? = null,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ApiUser(
    val id: Long,
    val username: String,
    val email: String,
    val firstName: String = "",
    val lastName: String = "",
    val status: String = "active",
    val region: String? = null,
    val department: String? = null,
    val preferredLang: String = "uk",
    val lastLoginAt: String? = null,
    val failedLoginCount: Int = 0,
    val lockedUntil: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isArchived: Boolean = false,
    val archivedAt: String? = null,
    val archivedBy: Long? = null,
    val role: ApiRole
)

@Serializable
data class ApiRole(val id: Long, val code: String, val name: String)

@Serializable
data class ApiDashboard(
    val projectsTotal: Long,
    val projectsActive: Long,
    val projectsCompletedThisMonth: Long,
    val budgetPlanned: Long,
    val amountSpent: Long,
    val inspectionsTotal: Long,
    val pendingInspections: Long,
    val findingsTotal: Long,
    val recentInspections: List<ApiInspectionReport>,
    val activities: List<ApiActivity> = emptyList(),
    val monthlyActPayments: List<ApiMonthlyActPayment> = emptyList(),
    val subprojectFunding: List<ApiSubprojectFunding> = emptyList(),
    val subprojectProgress: List<ApiSubprojectProgress> = emptyList(),
    val procurementStatusCounts: List<ApiDashboardMetric> = emptyList(),
    val monthlyInspectionCounts: List<ApiDashboardMetric> = emptyList(),
    val monthlyEshsViolations: List<ApiDashboardMetric> = emptyList(),
    val monthlyEquipmentPayments: List<ApiMonthlyActPayment> = emptyList(),
    val monthlySignedConstructionContracts: List<ApiDashboardMetric> = emptyList()
)

@Serializable
data class ApiDashboardOverview(
    val recentInspections: List<ApiInspectionReport> = emptyList(),
    val monthlyActPayments: List<ApiMonthlyActPayment> = emptyList(),
    val subprojectFunding: List<ApiSubprojectFunding> = emptyList(),
    val subprojectProgress: List<ApiSubprojectProgress> = emptyList(),
    val procurementStatusCounts: List<ApiDashboardMetric> = emptyList(),
    val signedContractsByType: List<ApiDashboardContractTypeMetric> = emptyList(),
    val nationalSummary: ApiDashboardNationalSummary? = null
)

private fun ApiDashboard.toOverview() = ApiDashboardOverview(
    recentInspections = recentInspections,
    monthlyActPayments = monthlyActPayments,
    subprojectFunding = subprojectFunding,
    subprojectProgress = subprojectProgress,
    procurementStatusCounts = procurementStatusCounts
)

@Serializable
data class ApiMonthlyActPayment(val month: String, val amountEurCents: Long, val amountUahCents: Long? = null)
@Serializable data class ApiSubprojectFunding(
    val projectUuid: String,
    val name: String,
    val region: String,
    val amountUah: Double,
    val amountEur: Double
)
@Serializable data class ApiSubprojectProgress(val projectUuid: String, val code: String = "", val name: String, val nameEn: String? = null, val region: String = "", val completionPct: Double)
@Serializable data class ApiDashboardMetric(val label: String, val value: Long)
@Serializable data class ApiDashboardContractTypeMetric(
    val type: String,
    val contractsCount: Long,
    val contractAmountUah: Double
)
@Serializable data class ApiDashboardNationalSummary(
    val subprojectsCount: Long,
    val regionsCount: Long,
    val municipalitiesCount: Long,
    val settlementsCount: Long,
    val approvedFundingUah: Double,
    val approvedFundingEur: Double,
    val signedContractsCount: Long,
    val signedContractsAmountUah: Double,
    val servicesPaidEurCents: Long,
    val servicesPaidUahCents: Long? = null
)
@Serializable data class ApiFinancialProgressItem(
    val label: String,
    val contractAmountUah: Double,
    val completedAmountUah: Double,
    val completionPct: Double
)
@Serializable data class ApiFinancialProgress(
    val byRegion: List<ApiFinancialProgressItem> = emptyList(),
    val byConstructionType: List<ApiFinancialProgressItem> = emptyList(),
    val bySector: List<ApiFinancialProgressItem> = emptyList()
)
@Serializable data class ApiPaymentMonitoringItem(
    val code: String,
    val totalInvestmentUah: Double? = null,
    val eibInvestmentUah: Double? = null,
    val localCoFinancingUah: Double? = null,
    val paidAndInProgressUah: Double? = null,
    val paidUah: Double? = null,
    val remainingUah: Double? = null,
    val plannedEibSharePct: Double? = null,
    val factualEibSharePct: Double? = null,
    val eibShareCompliance: String? = null
)
@Serializable data class ApiPaymentMonitoring(
    val refreshedAt: String,
    val items: List<ApiPaymentMonitoringItem> = emptyList()
)
@Serializable data class ApiInspectionAnalytics(
    val monthlyInspectionCounts: List<ApiDashboardMetric> = emptyList(),
    val monthlyEshsViolations: List<ApiDashboardMetric> = emptyList()
)

@Serializable
data class ApiActivity(
    val action: String,
    val entityType: String,
    val entityId: Long,
    val userLogin: String? = null,
    val createdAt: String
)

@Serializable
data class ApiProcurementRecord(
    val id: Long, val batchId: Int,
    val oblastName: String, val oblastId: String, val subProjectId: String, val subProjectLotId: String? = null,
    val purchaseStatus: String? = null, val tenderId: String? = null, val prozorroTenderId: String? = null,
    val contractorNameUkr: String? = null, val contractorNameEng: String? = null, val contractorId: String? = null,
    val contractDate: String? = null, val contractEndDate: String? = null, val contractDurationMonths: Int? = null,
    val contractAmountUah: Double? = null, val contractAmountEur: Double? = null,
    val financingContractDifferencePct: Double? = null,
    val promotorName: String? = null, val fbName: String? = null, val subprojectNameUk: String? = null, val subprojectNameEn: String? = null,
    val sourceContractType: String? = null,
    val subprojectTotalCostUah: Double? = null, val subprojectEibFinancingUah: Double? = null, val subprojectLocalFinancingUah: Double? = null,
    val estimatedTotalEur: Double? = null, val estimatedTotalUah: Double? = null, val estimatedEibEur: Double? = null, val estimatedEibUah: Double? = null,
    val estimatedLocalEur: Double? = null, val estimatedLocalUah: Double? = null, val procurementMethod: String? = null, val tenderDocumentType: String? = null,
    val publishedInOjeu: String? = null, val estimatedProzorroDate: String? = null, val estimatedBidSubmissionDate: String? = null,
    val estimatedContractDate: String? = null, val estimatedContractEndDate: String? = null, val actualisedContractEndDate: String? = null, val localFinancingPct: Double? = null,
    val tenderAttemptCount: Int? = null, val pigViolations: String? = null, val contractAmountUahWithoutVat: Double? = null,
    val dreamCoFinancingPct: Double? = null, val realLocalCoFinancingPct: Double? = null, val realEibFinancingUah: Double? = null,
    val bankGuarantee: Double? = null, val contractLink: String? = null, val comments: String? = null, val sourceStatusCode: String? = null, val projectId: Long? = null
)

@Serializable
data class ApiProcurementRefresh(
    val id: String,
    val status: String,
    val progress: Int,
    val phase: String,
    val importedCount: Int? = null,
    val error: String? = null
)

@Serializable
data class ApiSubprojectRegistryRefresh(
    val id: String,
    val status: String,
    val progress: Int,
    val phase: String,
    val importedCount: Int? = null,
    val error: String? = null
)

@Serializable
data class ApiInspectionReport(
    val uuid: String,
    val inspectionCode: String = "",
    val reportCode: String? = null,
    val inspectionType: String = "planned",
    val inspectionDate: String,
    val summary: String? = null,
    val status: String,
    val rejectionReason: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val authorUsername: String? = null,
    val subprojectCode: String? = null
)

@Serializable
data class ApiInspectionReportPreview(
    val fileName: String,
    val sheets: List<ApiInspectionReportPreviewSheet>,
    val manual: ManualInspectionReportRequest? = null
)

@Serializable
data class ApiInspectionReportPreviewSheet(
    val name: String,
    val rows: List<ApiInspectionReportPreviewRow>
)

@Serializable
data class ApiInspectionReportPreviewRow(
    val rowNumber: Int,
    val cells: List<String>
)

@Serializable
data class ApiProjectInspectionReport(
    val projectUuid: String,
    val report: ApiInspectionReport
)

@Serializable
data class ApiProjectFinancialRecord(
    val projectUuid: String,
    val record: ApiFinancialRecord
)

@Serializable
data class ApiHealthSafetyObservations(
    val uploadedReportsCount: Int,
    val observations: List<ApiHealthSafetyObservation>
)

@Serializable
data class ApiHealthSafetyObservation(
    val reportUuid: String,
    val inspectionDate: String,
    val observation: String,
    val answer: String? = null,
    val comment: String? = null
)

@Serializable
data class ApiInspectionPhoto(
    val uuid: String,
    val fileName: String,
    val contentType: String,
    val fileSizeBytes: Long,
    val isMain: Boolean,
    val downloadUrl: String,
    val thumbnailUrl: String
)

@Serializable
data class ApiInspectionFinding(
    val uuid: String,
    val category: String,
    val severity: String,
    val description: String,
    val recommendation: String? = null,
    val isResolved: Boolean
)

@Serializable
data class ApiProjectDetails(
    val data: ApiProjectDetailsData,
    val financialSummary: ApiFinancialSummary,
    val monitoringDetails: ApiProjectMonitoringDetails? = null,
    val contractorNameEn: String? = null
)

@Serializable
data class ApiProjectMonitoringDetails(
    val nameEn: String? = null,
    val municipalityNameUk: String? = null,
    val municipalityNameEn: String? = null,
    val settlementNameEn: String? = null,
    val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null,
    val constructionProcurementStatus: String? = null,
    val constructionWorkStatus: String? = null,
    val dreamProjectUrl: String? = null,
    val dedUrl: String? = null,
    val estimateUrl: String? = null,
    val expertiseUrl: String? = null,
    val permitUrl: String? = null,
    val constructionActNumber: String? = null,
    val constructionActDate: String? = null,
    val completedWorkPct: String? = null,
    val financingUsedPct: String? = null,
    val paidFinancingPct: String? = null,
    val constructionWorkSummary: String? = null,
    val constructionWorkComments: String? = null
)

@Serializable
data class ApiProjectDetailsData(
    val name: String,
    val nameEn: String? = null,
    val projectType: String = "project",
    val trancheNumber: Int = 1,
    val parentProjectUuid: String? = null,
    val siteName: String,
    val siteNumber: String,
    val description: String? = null,
    val address: String,
    val region: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val status: String = "planned",
    val sector: String,
    val constructionType: String,
    val budgetPlanned: Long,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val currency: String = "UAH",
    val contractorName: String? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val contractDurationDays: Long? = null,
    val constructionContractNumber: String? = null,
    val technicalSupervisionName: String? = null,
    val technicalSupervisionContractNumber: String? = null,
    val technicalSupervisionContractDate: String? = null,
    val technicalSupervisionStartDate: String? = null,
    val technicalSupervisionPlannedEndDate: String? = null,
    val technicalSupervisionDurationDays: Long? = null,
    val engineerConsultantName: String? = null,
    val engineerConsultantContractNumber: String? = null,
    val engineerConsultantContractDate: String? = null,
    val engineerConsultantStartDate: String? = null,
    val engineerConsultantPlannedEndDate: String? = null,
    val engineerConsultantDurationDays: Long? = null,
    val amounts: Map<String, ProjectAmountDto> = emptyMap()
)

@Serializable
data class ApiFinancialSummary(
    val budgetPlanned: Double,
    val constructionContractAmount: Double = budgetPlanned,
    val amountSpent: Double,
    val financialDocumentsAmount: Double = amountSpent,
    val budgetRemaining: Double,
    val completionPct: Double = 0.0,
    val financialCompletionPct: Double = completionPct
)

@Serializable
data class ApiProjectDocument(
    val uuid: String,
    val docType: String,
    val fileName: String,
    val contentType: String,
    val fileSizeBytes: Long,
    val relatedEntity: String? = null,
    val relatedId: Long? = null,
    val description: String? = null
)

@Serializable
data class ApiProjectDocumentListItem(val projectUuid: String, val document: ApiProjectDocument)

@Serializable
data class ApiFinancialRecords(val data: List<ApiFinancialRecord>, val summary: ApiFinancialSummary)

@Serializable
data class ApiFinancialRecord(
    val uuid: String,
    val recordType: String,
    val referenceNumber: String,
    val amount: Double,
    val currency: String,
    val recordDate: String,
    val paymentDate: String? = null,
    val description: String? = null,
    val milestone: String? = null,
    val paymentPurpose: String = "works",
    val eurExchangeRate: Double? = null,
    val eurExchangeDate: String? = null,
    val amountEurCents: Long? = null
)
