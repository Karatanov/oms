package oms.urp3.service

import com.google.auth.oauth2.ServiceAccountCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import oms.urp3.FeedbackOptions
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
data class FeedbackRequest(val requestId: String, val page: String, val type: String, val description: String)

@Serializable
data class FeedbackReceipt(val number: Int, val date: String)

@Serializable
data class FeedbackAvailability(val configured: Boolean, val date: String)

/** One instance per server; serializes numbering and retries against the shared sheet. */
class FeedbackService(
    private val gateway: FeedbackSheetGateway = GoogleFeedbackSheetGateway(),
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Kyiv"))
) {
    private val mutex = Mutex()
    fun availability() = FeedbackAvailability(gateway.configured, LocalDate.now(clock).toString())

    suspend fun submit(request: FeedbackRequest, userId: Long): FeedbackReceipt {
        require(request.requestId.matches(Regex("[A-Za-z0-9-]{16,80}"))) { "Invalid request ID" }
        require(request.page in FeedbackOptions.pages) { "Invalid page" }
        require(request.type in FeedbackOptions.types) { "Invalid type" }
        require(request.description.trim().isNotEmpty() && request.description.length <= FeedbackOptions.maxDescriptionLength) { "Invalid description" }
        check(gateway.configured) { "Feedback is not configured" }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val rows = gateway.readIdentifiers()
                // The marker is stored with the row in the same atomic Google request.
                // A lost HTTP response can be retried without creating another issue.
                val marker = "OMS feedback:$userId:${request.requestId}"
                rows.firstOrNull { it.note.startsWith("$marker\n") }?.let {
                    return@withContext FeedbackReceipt(it.number, it.note.substringAfter('\n'))
                }
                val number = (rows.maxOfOrNull { it.number } ?: 0) + 1
                val date = LocalDate.now(clock)
                gateway.append(buildFeedbackRow(request, number, date, "$marker\n$date"))
                FeedbackReceipt(number, date.toString())
            }
        }
    }
}

data class FeedbackIdentifier(val number: Int, val note: String)

interface FeedbackSheetGateway {
    val configured: Boolean
    fun readIdentifiers(): List<FeedbackIdentifier>
    fun append(row: JsonObject)
}

internal fun buildFeedbackRow(request: FeedbackRequest, number: Int, date: LocalDate, marker: String): JsonObject {
    fun text(value: String) = buildJsonObject {
        // Explicit stringValue prevents a description beginning with '=' from becoming a formula.
        putJsonObject("userEnteredValue") { put("stringValue", value) }
        putJsonObject("userEnteredFormat") { put("wrapStrategy", "WRAP"); put("verticalAlignment", "TOP") }
    }
    return buildJsonObject {
        putJsonArray("values") {
            add(buildJsonObject {
                putJsonObject("userEnteredValue") { put("numberValue", number) }
                put("note", marker)
            })
            add(text(request.type))
            add(buildJsonObject {
                putJsonObject("userEnteredValue") { put("numberValue", ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date)) }
                putJsonObject("userEnteredFormat") { putJsonObject("numberFormat") { put("type", "DATE"); put("pattern", "dd.MM.yyyy") } }
            })
            add(text(request.page)); add(text(request.description.trim()))
            add(text("")); add(text("")); add(text("")); add(text("New")); add(text("")); add(text(""))
        }
    }
}

/** Credentials remain on the server. The destination cannot be overridden by a browser request. */
class GoogleFeedbackSheetGateway : FeedbackSheetGateway {
    private val credentialPath = System.getenv("OMS_FEEDBACK_GOOGLE_CREDENTIALS")?.takeIf { it.isNotBlank() }
        ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.takeIf { it.isNotBlank() }
    override val configured get() = credentialPath?.let { File(it).isFile } == true
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val base = "https://sheets.googleapis.com/v4/spreadsheets/1dSOFZ1tzuzLUIaK6LcaklqZTXkmAwRqby_TOCZx1dMk"
    private val sheetId = 1327224389
    private val sheetTitle = "OMS Issues & Improvements"
    private val credentials by lazy {
        File(checkNotNull(credentialPath)).inputStream().use {
            ServiceAccountCredentials.fromStream(it).createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))
        }
    }

    private fun execute(url: String, body: JsonObject? = null): JsonObject {
        credentials.refreshIfExpired()
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(25))
            .header("Authorization", "Bearer ${credentials.accessToken.tokenValue}")
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Google Sheets request failed (${response.statusCode()})" }
        return Json.parseToJsonElement(response.body()).jsonObject
    }

    override fun readIdentifiers(): List<FeedbackIdentifier> {
        val headerRange = URLEncoder.encode("'$sheetTitle'!A1:K1", Charsets.UTF_8).replace("+", "%20")
        val headers = execute("$base/values/$headerRange")["values"]!!.jsonArray.single().jsonArray.map { it.jsonPrimitive.content }
        check(headers == listOf("№", "Type", "Date", "Module / Page", "Remark / Request", "Proposed Solution",
            "Comments IT specialist", "Priority", "Status", "Estimated Effort", "Realization")) { "Tracker columns have changed" }
        val range = URLEncoder.encode("'$sheetTitle'!A:A", Charsets.UTF_8)
        val fields = URLEncoder.encode("sheets(properties(sheetId),data(rowData(values(userEnteredValue,note))))", Charsets.UTF_8)
        val sheet = execute("$base?ranges=$range&fields=$fields")["sheets"]!!.jsonArray.single().jsonObject
        check(sheet["properties"]!!.jsonObject["sheetId"]!!.jsonPrimitive.int == sheetId)
        return sheet["data"]!!.jsonArray.flatMap { data ->
            data.jsonObject["rowData"]?.jsonArray.orEmpty().mapNotNull { row ->
                val cell = row.jsonObject["values"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
                val value = cell["userEnteredValue"]?.jsonObject ?: return@mapNotNull null
                val number = value["numberValue"]?.jsonPrimitive?.doubleOrNull?.toInt()
                    ?: value["stringValue"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                FeedbackIdentifier(number, cell["note"]?.jsonPrimitive?.content.orEmpty())
            }
        }
    }

    override fun append(row: JsonObject) {
        execute("$base:batchUpdate", buildJsonObject {
            putJsonArray("requests") { addJsonObject {
                putJsonObject("appendCells") {
                    put("sheetId", sheetId)
                    put("fields", "userEnteredValue,userEnteredFormat,note")
                    putJsonArray("rows") { add(row) }
                }
            } }
        })
    }
}
