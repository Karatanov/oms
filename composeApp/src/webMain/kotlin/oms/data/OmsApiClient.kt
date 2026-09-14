package oms.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsName
import kotlin.js.unsafeCast
import kotlin.js.toJsString
import org.w3c.fetch.RequestCredentials
import oms.localization.LocalizationManager

@JsName("showOmsLoading")
private external fun showOmsLoading(message: String)

@JsName("hideOmsLoading")
private external fun hideOmsLoading()

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
    }

    init {
        client.plugin(HttpSend).intercept { request ->
            val isBackgroundRequest = request.url.parameters["background"] == "true"
            if (!isBackgroundRequest) showOmsLoading(request.url.build().encodedPath.toOmsLoadingMessage())
            try {
                execute(request)
            } finally {
                if (!isBackgroundRequest) hideOmsLoading()
            }
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
        return response.body<LoginPayload>().user
    }

    /** Returns the active server session without showing the global loader. */
    suspend fun currentSessionUser(): ApiUser? {
        val response = client.get("$baseUrl/auth/session?background=true")
        if (response.status.value == 401) return null
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
        // Guest sessions are restored through their own lightweight marker;
        // authenticated users use the normal user payload.
        val body = response.bodyAsText()
        if (body.contains("\"guest\":true")) return null
        return Json { ignoreUnknownKeys = true }.decodeFromString(body)
    }

    suspend fun startGuestSession() {
        val response = client.post("$baseUrl/auth/guest")
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
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
        val pageSize = 250
        val projects = mutableListOf<ApiProject>()
        var page = 1
        do {
            val batch = client.get("$baseUrl/projects?page=$page&pageSize=$pageSize")
                .body<ProjectListPayload>()
                .data
            projects += batch
            page += 1
        } while (batch.size == pageSize)
        return projects
    }

    suspend fun users(): List<ApiUser> = client.get("$baseUrl/users").body()

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
    suspend fun projectExchangeRate(): ProjectExchangeRate = client.get("$baseUrl/exchange-rates/eur?background=true").body()

    suspend fun geocodeAddress(address: String, city: String, region: String): GeocodeAddressResponse {
        val response = client.post("$baseUrl/geocode/address") {
            contentType(ContentType.Application.Json)
            setBody(GeocodeAddressRequest(address, city, region))
        }
        if (!response.status.isSuccess()) throw IllegalStateException(response.bodyAsText())
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

    suspend fun dashboard(trancheNumber: Int? = null): ApiDashboard = client.get("$baseUrl/dashboard") {
        trancheNumber?.let { parameter("tranche", it) }
    }.body()

    suspend fun dashboardOverview(trancheNumber: Int? = null): ApiDashboardOverview = try {
        client.get("$baseUrl/dashboard/overview") {
            trancheNumber?.let { parameter("tranche", it) }
        }.body()
    } catch (_: Exception) {
        // Keep the dashboard useful while an older database or a partially
        // migrated Render instance cannot serve the lightweight endpoint.
        // The established endpoint carries the same chart data.
        dashboard(trancheNumber).toOverview()
    }

    suspend fun inspectionAnalytics(): ApiInspectionAnalytics =
        client.get("$baseUrl/inspection-reports/analytics").body()

    suspend fun procurements(): List<ApiProcurementRecord> = client.get("$baseUrl/procurements").body()

    suspend fun createProcurement(request: ProcurementRecordRequest): ApiProcurementRecord =
        client.post("$baseUrl/procurements") { contentType(ContentType.Application.Json); setBody(request) }.body()

    suspend fun updateProcurement(id: Long, request: ProcurementRecordRequest): ApiProcurementRecord =
        client.patch("$baseUrl/procurements/$id") { contentType(ContentType.Application.Json); setBody(request) }.body()

    suspend fun deleteProcurement(id: Long): Boolean = client.delete("$baseUrl/procurements/$id").status.isSuccess()

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

    suspend fun inspectionPhotos(reportUuid: String): List<ApiInspectionPhoto> =
        client.get("$baseUrl/inspection-reports/$reportUuid/photos").body()

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
        client.get("$baseUrl/inspection-reports/$reportUuid/manual").body()

    suspend fun updateManualInspectionReport(
        reportUuid: String,
        projectUuid: String,
        status: String?,
        manual: ManualInspectionReportRequest
    ): ApiInspectionReport =
        client.put("$baseUrl/inspection-reports/$reportUuid/manual") {
            contentType(ContentType.Application.Json)
            setBody(UpdateManualInspectionReportRequest(projectUuid, status, manual))
        }.body()

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

    suspend fun deleteProject(projectUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid").status.isSuccess()

    suspend fun deleteUser(id: Long): Boolean = client.delete("$baseUrl/users/$id").status.isSuccess()

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

private fun String.toOmsLoadingMessage(): String = when {
    contains("/dashboard") -> LocalizationManager.t("loading_dashboard")
    contains("/inspection-reports") -> LocalizationManager.t("loading_reports")
    contains("/financial") -> LocalizationManager.t("loading_financials")
    contains("/documents") -> LocalizationManager.t("loading_documents")
    contains("/photos") -> LocalizationManager.t("loading_photos")
    contains("/users") -> LocalizationManager.t("loading_users")
    contains("/projects") -> LocalizationManager.t("loading_projects")
    else -> LocalizationManager.t("loading_data")
}

@Serializable
data class LoginRequest(val username: String, val password: String)
@Serializable data class ActivateAccountRequest(val token: String, val password: String)
@Serializable data class PasswordResetRequest(val identifier: String)

@Serializable
data class LoginPayload(val user: ApiUser)

@Serializable
private data class ApiErrorPayload(val error: String? = null, val message: String? = null)

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
    val inspectionDate: String = "", val inspectionType: String = "planned", val contractor: String = "", val contractorRepresentative: String? = null, val projectName: String? = null, val siteReference: String? = null, val qaStaff: String? = null, val usifRepresentative: String? = null,
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
    val paymentDate: String? = null,
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
    val paymentDate: String? = null,
    val description: String? = null,
    val milestone: String? = null,
    val paymentPurpose: String = "works",
    val targetProjectUuid: String? = null
) {
    constructor(request: FinancialRecordRequest, targetProjectUuid: String) : this(
        request.recordType, request.referenceNumber, request.amount, request.currency, request.recordDate,
        request.paymentDate, request.description, request.milestone, request.paymentPurpose, targetProjectUuid
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
    val designContractSigningDate: String? = null,
    val designStartDate: String? = null,
    val designPlannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null,
    val designerName: String? = null,
    val designContractNumber: String? = null,
    val designContractTerm: String? = null,
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
    val designContractSigningDate: String? = null,
    val designStartDate: String? = null,
    val designPlannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val currency: String? = null,
    val contractorName: String? = null,
    val designerName: String? = null,
    val designContractNumber: String? = null,
    val designContractTerm: String? = null,
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
data class ProjectListPayload(val data: List<ApiProject>)

@Serializable
data class ApiProject(
    val uuid: String,
    val projectType: String = "project",
    val trancheNumber: Int = 1,
    val parentProjectUuid: String? = null,
    val name: String,
    val nameEn: String? = null,
    val siteNumber: String,
    val region: String,
    val city: String,
    val cityEn: String? = null,
    val sector: String,
    val constructionType: String = "reconstruction",
    val budgetPlanned: Long,
    val currency: String = "UAH",
    val amounts: Map<String, ProjectAmountDto> = emptyMap(),
    val contractorName: String? = null,
    val startDate: String? = null,
    val status: String,
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
    val procurementStatusCounts: List<ApiDashboardMetric> = emptyList()
)

private fun ApiDashboard.toOverview() = ApiDashboardOverview(
    recentInspections = recentInspections,
    monthlyActPayments = monthlyActPayments,
    subprojectFunding = subprojectFunding,
    subprojectProgress = subprojectProgress,
    procurementStatusCounts = procurementStatusCounts
)

@Serializable
data class ApiMonthlyActPayment(val month: String, val amountEurCents: Long)
@Serializable data class ApiSubprojectFunding(
    val projectUuid: String,
    val name: String,
    val region: String,
    val amountUah: Double,
    val amountEur: Double
)
@Serializable data class ApiSubprojectProgress(val projectUuid: String, val code: String = "", val name: String, val nameEn: String? = null, val region: String = "", val completionPct: Double)
@Serializable data class ApiDashboardMetric(val label: String, val value: Long)
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
    val id: Long, val recordNumber: Int, val batchId: Int,
    val oblastName: String, val oblastId: String, val subProjectId: String, val subProjectLotId: String? = null,
    val purchaseStatus: String? = null, val tenderId: String? = null, val prozorroTenderId: String? = null,
    val contractorNameUkr: String? = null, val contractorNameEng: String? = null, val contractorId: String? = null,
    val contractDate: String? = null, val contractEndDate: String? = null, val contractDurationMonths: Int? = null,
    val contractAmountUah: Double? = null, val contractAmountEur: Double? = null,
    val financingContractDifferencePct: Double? = null,
    val promotorName: String? = null, val subprojectNameUk: String? = null, val subprojectNameEn: String? = null,
    val spId: String? = null, val sourceContractType: String? = null, val sourceType: String? = null, val procurementId: String? = null,
    val subprojectTotalCostUah: Double? = null, val subprojectEibFinancingUah: Double? = null, val subprojectLocalFinancingUah: Double? = null,
    val estimatedTotalEur: Double? = null, val estimatedTotalUah: Double? = null, val estimatedEibEur: Double? = null, val estimatedEibUah: Double? = null,
    val estimatedLocalEur: Double? = null, val estimatedLocalUah: Double? = null, val procurementMethod: String? = null, val tenderDocumentType: String? = null,
    val publishedInOjeu: String? = null, val estimatedProzorroDate: String? = null, val estimatedBidSubmissionDate: String? = null,
    val estimatedContractDate: String? = null, val estimatedContractEndDate: String? = null, val localFinancingPct: Double? = null,
    val comments: String? = null, val sourceStatusCode: String? = null, val projectId: Long? = null
)

@Serializable
data class ProcurementRecordRequest(
    val recordNumber: Int, val batchId: Int, val oblastName: String, val oblastId: String,
    val subProjectId: String, val subProjectLotId: String, val purchaseStatus: String,
    val tenderId: String? = null, val prozorroTenderId: String? = null,
    val contractorNameUkr: String? = null, val contractorNameEng: String? = null, val contractorId: String? = null,
    val contractDate: String? = null, val contractEndDate: String? = null, val contractDurationMonths: Int? = null,
    val contractAmountUah: Double? = null, val contractAmountEur: Double? = null,
    val financingContractDifferencePct: Double? = null
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
    val authorUsername: String? = null
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
    val programmeDetails: ApiProgrammeDetails? = null,
    val monitoringDetails: ApiProjectMonitoringDetails? = null,
    val contractorNameEn: String? = null
)

@Serializable
data class ApiProgrammeDetails(
    val implementor: String, val financingInstitution: String, val financeContractNumber: String,
    val serapisNumber: String, val agreementDate: String, val loanAmount: String,
    val loanCurrency: String, val sourceWorkbook: String, val sourceSnapshotDate: String? = null
)

@Serializable
data class ApiProjectMonitoringDetails(
    val sourceBatchId: Int? = null, val sourceSubprojectId: String, val sourceLotId: String,
    val nameEn: String? = null, val oblastCode: String? = null,
    val municipalityNameUk: String? = null, val municipalityNameEn: String? = null,
    val settlementNameEn: String? = null, val priorityAreaSource: String? = null,
    val projectManagerNameUk: String? = null, val projectManagerNameEn: String? = null,
    val projectManagerOrgId: String? = null, val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null, val beneficiaryOrgId: String? = null,
    val dreamProjectId: String? = null, val dreamProjectUrl: String? = null,
    val applicationId: String? = null, val dreamApplicationId: String? = null,
    val constructionProcurementStatus: String? = null, val constructionWorkStatus: String? = null,
    val geocodeAccuracy: String? = null, val geocodeQuery: String? = null,
    val geocodeDisplayName: String? = null, val sourceRows: String, val sourceWorkbook: String
)

@Serializable
data class ApiProjectDetailsData(
    val name: String,
    val nameEn: String? = null,
    val projectType: String = "project",
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
    val designContractSigningDate: String? = null,
    val designStartDate: String? = null,
    val designPlannedEndDate: String? = null,
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val contractDurationDays: Long? = null,
    val designDurationDays: Long? = null,
    val designerName: String? = null,
    val designContractNumber: String? = null,
    val designContractTerm: String? = null,
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
data class ApiProjectDocument(val uuid: String, val docType: String, val fileName: String, val contentType: String, val fileSizeBytes: Long)

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
