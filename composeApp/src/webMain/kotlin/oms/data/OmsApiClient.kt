package oms.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast
import kotlin.js.toJsString
import org.w3c.fetch.RequestCredentials

@OptIn(ExperimentalWasmJsInterop::class)
object OmsApiClient {
    private const val baseUrl = "http://localhost:8080/api/v1"

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

    suspend fun login(username: String, password: String): ApiUser =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.body<LoginPayload>().user

    suspend fun projects(): List<ApiProject> =
        client.get("$baseUrl/projects?page=1&pageSize=100").body<ProjectListPayload>().data

    suspend fun users(): List<ApiUser> = client.get("$baseUrl/users").body()

    suspend fun roles(): List<ApiRole> = client.get("$baseUrl/roles").body()

    suspend fun updateUser(id: Long, request: UpdateUserRequest): ApiUser =
        client.patch("$baseUrl/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

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

    suspend fun updateProject(projectUuid: String, request: UpdateProjectRequest): ApiProject =
        client.patch("$baseUrl/projects/$projectUuid") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun dashboard(): ApiDashboard = client.get("$baseUrl/dashboard").body()

    suspend fun projectDetails(uuid: String): ApiProjectDetails =
        client.get("$baseUrl/projects/$uuid").body()

    suspend fun projectReports(projectUuid: String): List<ApiInspectionReport> =
        client.get("$baseUrl/projects/$projectUuid/inspection-reports").body()

    suspend fun inspectionPhotos(reportUuid: String): List<ApiInspectionPhoto> =
        client.get("$baseUrl/inspection-reports/$reportUuid/photos").body()

    suspend fun createAndSubmitInspectionReport(
        projectUuid: String,
        inspectionDate: String,
        summary: String
    ): ApiInspectionReport {
        val draft: ApiInspectionReport = client.post("$baseUrl/projects/$projectUuid/inspection-reports") {
            contentType(ContentType.Application.Json)
            setBody(CreateInspectionReportRequest(inspectionDate, summary))
        }.body()
        return client.post("$baseUrl/inspection-reports/${draft.uuid}/submit").body()
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

    suspend fun financials(projectUuid: String): ApiFinancialRecords =
        client.get("$baseUrl/projects/$projectUuid/financials").body()

    suspend fun createFinancialRecord(projectUuid: String, request: FinancialRecordRequest): ApiFinancialRecord =
        client.post("$baseUrl/projects/$projectUuid/financials") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateFinancialRecord(projectUuid: String, recordUuid: String, request: FinancialRecordRequest): ApiFinancialRecord =
        client.put("$baseUrl/projects/$projectUuid/financials/$recordUuid") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteFinancialRecord(projectUuid: String, recordUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid/financials/$recordUuid").status.isSuccess()

    suspend fun deleteProject(projectUuid: String): Boolean =
        client.delete("$baseUrl/projects/$projectUuid").status.isSuccess()

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

@Serializable
data class LoginPayload(val user: ApiUser)

@Serializable
data class CreateInspectionReportRequest(
    val inspectionDate: String,
    val summary: String
)

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
    val amount: Long,
    val currency: String = "UAH",
    val recordDate: String,
    val paymentDate: String? = null,
    val description: String? = null,
    val milestone: String? = null
)

@Serializable
data class CreateProjectRequest(
    val name: String,
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
    val managerId: Long,
    val projectType: String = "project",
    val parentProjectUuid: String? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null
)

@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val siteName: String? = null,
    val siteNumber: String? = null,
    val address: String? = null,
    val region: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sector: String? = null,
    val constructionType: String? = null,
    val budgetPlanned: Long? = null,
    val engineerConsultantContractAmount: Long? = null,
    val technicalSupervisionAmount: Long? = null,
    val subprojectContractAmount: Long? = null,
    val startDate: String? = null,
    val contractSignedDate: String? = null,
    val plannedEndDate: String? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val roleCode: String? = null,
    val password: String? = null
)

@Serializable
data class CreateUserRequest(val username: String, val email: String, val password: String, val roleCode: String)

@Serializable
data class ProjectListPayload(val data: List<ApiProject>)

@Serializable
data class ApiProject(
    val uuid: String,
    val projectType: String = "project",
    val parentProjectUuid: String? = null,
    val name: String,
    val region: String,
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
    val budgetPlanned: Long,
    val amountSpent: Long,
    val inspectionsTotal: Long,
    val findingsTotal: Long,
    val recentInspections: List<ApiInspectionReport>
)

@Serializable
data class ApiInspectionReport(
    val uuid: String,
    val inspectionDate: String,
    val summary: String? = null,
    val status: String,
    val rejectionReason: String? = null
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
data class ApiProjectDetails(val data: ApiProjectDetailsData, val financialSummary: ApiFinancialSummary)

@Serializable
data class ApiProjectDetailsData(
    val name: String,
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
    val constructionContractSigningDate: String? = null,
    val constructionStartDate: String? = null,
    val projectedCompletionTime: String? = null,
    val contractDurationDays: Long? = null
)

@Serializable
data class ApiFinancialSummary(val budgetPlanned: Long, val amountSpent: Long, val budgetRemaining: Long)

@Serializable
data class ApiProjectDocument(val uuid: String, val docType: String, val fileName: String, val contentType: String, val fileSizeBytes: Long)

@Serializable
data class ApiFinancialRecords(val data: List<ApiFinancialRecord>, val summary: ApiFinancialSummary)

@Serializable
data class ApiFinancialRecord(
    val uuid: String,
    val recordType: String,
    val referenceNumber: String,
    val amount: Long,
    val currency: String,
    val recordDate: String,
    val paymentDate: String? = null,
    val description: String? = null,
    val milestone: String? = null
)
