package oms.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
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

    suspend fun login(username: String, password: String): Boolean =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }.status.isSuccess()

    suspend fun projects(): List<ApiProject> =
        client.get("$baseUrl/projects?page=1&pageSize=100").body<ProjectListPayload>().data

    suspend fun dashboard(): ApiDashboard = client.get("$baseUrl/dashboard").body()

    suspend fun projectDetails(uuid: String): ApiProjectDetails =
        client.get("$baseUrl/projects/$uuid").body()

    suspend fun projectReports(projectUuid: String): List<ApiInspectionReport> =
        client.get("$baseUrl/projects/$projectUuid/inspection-reports").body()

    suspend fun projectDocuments(projectUuid: String): List<ApiProjectDocument> =
        client.get("$baseUrl/projects/$projectUuid/documents").body()

    suspend fun financials(projectUuid: String): ApiFinancialRecords =
        client.get("$baseUrl/projects/$projectUuid/financials").body()
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class ProjectListPayload(val data: List<ApiProject>)

@Serializable
data class ApiProject(
    val uuid: String,
    val name: String,
    val region: String,
    val status: String,
    val latitude: Double,
    val longitude: Double
)

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
    val completionPct: Double,
    val summary: String? = null,
    val status: String
)

@Serializable
data class ApiProjectDetails(val data: ApiProjectDetailsData, val financialSummary: ApiFinancialSummary)

@Serializable
data class ApiProjectDetailsData(val address: String, val sector: String, val constructionType: String)

@Serializable
data class ApiFinancialSummary(val budgetPlanned: Long, val amountSpent: Long, val budgetRemaining: Long)

@Serializable
data class ApiProjectDocument(val uuid: String, val docType: String, val fileName: String, val contentType: String, val fileSizeBytes: Long)

@Serializable
data class ApiFinancialRecords(val data: List<ApiFinancialRecord>, val summary: ApiFinancialSummary)

@Serializable
data class ApiFinancialRecord(val uuid: String, val recordType: String, val referenceNumber: String, val amount: Long, val currency: String, val recordDate: String)
