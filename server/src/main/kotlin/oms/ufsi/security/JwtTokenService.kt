package oms.ufsi.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Minimal HMAC-SHA256 JWT issuer/verifier for API clients.
 * A deployment must set OMS_JWT_SECRET; a random per-process development key
 * prevents a hard-coded secret from being shipped but invalidates tokens after restart.
 */
object JwtTokenService {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val secret = System.getenv("OMS_JWT_SECRET")?.takeIf { it.length >= 32 }
        ?: ByteArray(48).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private const val lifetimeSeconds = 60L * 60L

    fun issue(userId: Long, roleCode: String): String {
        val header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}")
        val expiresAt = Instant.now().epochSecond + lifetimeSeconds
        val payload = encode("{\"sub\":\"$userId\",\"role\":\"${roleCode.uppercase()}\",\"exp\":$expiresAt}")
        val unsigned = "$header.$payload"
        return "$unsigned.${signature(unsigned)}"
    }

    fun verify(token: String): UserSession? = runCatching {
        val parts = token.split('.')
        require(parts.size == 3)
        val unsigned = "${parts[0]}.${parts[1]}"
        require(MessageDigest.isEqual(decoder.decode(parts[2]), decoder.decode(signature(unsigned))))
        val payload = String(decoder.decode(parts[1]), StandardCharsets.UTF_8)
        val userId = Regex("\\\"sub\\\":\\\"(\\d+)\\\"").find(payload)?.groupValues?.get(1)?.toLong() ?: return null
        val role = Regex("\\\"role\\\":\\\"([A-Z_]+)\\\"").find(payload)?.groupValues?.get(1) ?: return null
        val expiry = Regex("\\\"exp\\\":(\\d+)").find(payload)?.groupValues?.get(1)?.toLong() ?: return null
        require(expiry > Instant.now().epochSecond)
        UserSession(userId, role)
    }.getOrNull()

    private fun encode(value: String) = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun signature(unsigned: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return encoder.encodeToString(mac.doFinal(unsigned.toByteArray(StandardCharsets.UTF_8)))
    }
}
