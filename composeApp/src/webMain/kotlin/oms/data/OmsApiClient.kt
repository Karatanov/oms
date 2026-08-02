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

object OmsApiClient {
    private const val baseUrl = "http://localhost:8080/api/v1"

    private val client = HttpClient(Js) {
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
