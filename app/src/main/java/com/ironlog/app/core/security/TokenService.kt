package com.ironlog.app.core.security

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TokenClaims(
    @SerialName("sub") val subject: String,
    @SerialName("email") val email: String,
    @SerialName("jti") val tokenId: String,
    @SerialName("iat") val issuedAt: Long,
    @SerialName("exp") val expiresAt: Long,
    @SerialName("typ") val type: String
) {
    companion object {
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long
)

sealed interface TokenResult {
    data class Valid(val claims: TokenClaims) : TokenResult
    data object Expired : TokenResult
    data object Malformed : TokenResult
    data object SignatureMismatch : TokenResult
}

/**
 * Token-based session handling, implemented on-device.
 *
 * The app has no remote server, so this class plays the part one would: it mints
 * signed JSON Web Tokens in the standard `header.payload.signature` form, signs
 * them with HMAC-SHA256 under a 256-bit secret held in the Android Keystore, and
 * verifies the signature before trusting any claim. Access tokens are short
 * lived; a longer-lived refresh token silently mints a new pair so the user is
 * not thrown back to the sign-in screen mid-workout.
 *
 * Because verification is genuine (tamper with one byte of the payload and the
 * signature check fails), replacing this class with a Retrofit call to a real
 * auth server is a change to one file -- nothing downstream assumes the tokens
 * were issued locally.
 */
@Singleton
class TokenService @Inject constructor(
    private val secretProvider: SigningSecretProvider
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun issue(userId: String, email: String, now: Long = System.currentTimeMillis()): TokenPair {
        val accessExp = now + ACCESS_TTL_MS
        val refreshExp = now + REFRESH_TTL_MS
        return TokenPair(
            accessToken = sign(
                TokenClaims(userId, email, UUID.randomUUID().toString(), now, accessExp, TokenClaims.TYPE_ACCESS)
            ),
            refreshToken = sign(
                TokenClaims(userId, email, UUID.randomUUID().toString(), now, refreshExp, TokenClaims.TYPE_REFRESH)
            ),
            accessExpiresAt = accessExp,
            refreshExpiresAt = refreshExp
        )
    }

    fun verify(token: String, now: Long = System.currentTimeMillis()): TokenResult {
        val parts = token.split('.')
        if (parts.size != 3) return TokenResult.Malformed

        val (headerB64, payloadB64, signatureB64) = parts
        val expected = hmac("$headerB64.$payloadB64")

        // Constant-time compare: a byte-by-byte early exit would let an attacker
        // with local access forge a signature one character at a time.
        if (!MessageDigest.isEqual(decode(signatureB64), expected)) {
            return TokenResult.SignatureMismatch
        }

        val claims = runCatching {
            json.decodeFromString<TokenClaims>(String(decode(payloadB64), Charsets.UTF_8))
        }.getOrNull() ?: return TokenResult.Malformed

        return if (now >= claims.expiresAt) TokenResult.Expired else TokenResult.Valid(claims)
    }

    /** Exchanges a still-valid refresh token for a fresh pair. */
    fun refresh(refreshToken: String, now: Long = System.currentTimeMillis()): TokenPair? {
        val result = verify(refreshToken, now)
        if (result !is TokenResult.Valid) return null
        if (result.claims.type != TokenClaims.TYPE_REFRESH) return null
        return issue(result.claims.subject, result.claims.email, now)
    }

    private fun sign(claims: TokenClaims): String {
        val header = encode("""{"alg":"HS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8))
        val payload = encode(json.encodeToString(TokenClaims.serializer(), claims).toByteArray(Charsets.UTF_8))
        val signature = encode(hmac("$header.$payload"))
        return "$header.$payload.$signature"
    }

    private fun hmac(data: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretProvider.secret(), HMAC_ALGORITHM))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val ACCESS_TTL_MS = 30 * 60 * 1000L        // 30 minutes
        const val REFRESH_TTL_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

        /** Refresh proactively once the access token is within this of expiry. */
        const val REFRESH_THRESHOLD_MS = 5 * 60 * 1000L
    }
}

/** Supplies the HMAC signing secret, generated once and never leaving the device. */
interface SigningSecretProvider {
    fun secret(): ByteArray
}

/** Generates a 256-bit secret the first time it is asked for. */
class KeystoreSigningSecretProvider @Inject constructor(
    private val store: SecureTokenStore
) : SigningSecretProvider {

    override fun secret(): ByteArray {
        store.signingSecret()?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        store.saveSigningSecret(Base64.encodeToString(generated, Base64.NO_WRAP))
        return generated
    }
}
