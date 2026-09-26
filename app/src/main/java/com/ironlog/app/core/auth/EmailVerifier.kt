package com.ironlog.app.core.auth

import com.ironlog.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Email verification through Firebase Authentication's REST API.
 *
 * The one job a phone cannot do for itself is prove that an email address
 * belongs to the person typing it: something has to send the message and
 * something has to be told when the link is opened. Firebase does both. It
 * is spoken to directly over HTTPS with the app's own HTTP code, so there is
 * no SDK and no Google Services plugin; the account of record stays on the
 * phone, and Firebase holds only the email, a credential and the verified
 * flag.
 *
 * The project's web API key identifies the Firebase project; it is not a
 * secret and Google documents it as safe to ship. It comes from
 * local.properties at build time. When it is absent this class reports
 * itself unconfigured and the app skips verification, so a clone without a
 * Firebase project still builds and runs.
 *
 * Passwords arrive as char arrays and are written straight into the request
 * body bytes, which are zeroed after the call; no String of the password is
 * ever made here.
 */
@Singleton
class EmailVerifier @Inject constructor() {

    class VerifierException(val code: String, message: String) : Exception(message)

    /** Firebase's tokens for one account: the id token is short-lived, the refresh token is not. */
    data class Session(val idToken: String, val refreshToken: String, val localId: String)

    val isConfigured: Boolean get() = BuildConfig.FIREBASE_WEB_API_KEY.isNotBlank()

    suspend fun signUp(email: String, password: CharArray): Session = withContext(Dispatchers.IO) {
        parseSession(post("$IDENTITY/accounts:signUp", credentialBody(email, password)))
    }

    suspend fun signIn(email: String, password: CharArray): Session = withContext(Dispatchers.IO) {
        parseSession(post("$IDENTITY/accounts:signInWithPassword", credentialBody(email, password)))
    }

    suspend fun sendVerificationEmail(idToken: String) = withContext(Dispatchers.IO) {
        post("$IDENTITY/accounts:sendOobCode", jsonBytes("""{"requestType":"VERIFY_EMAIL","idToken":${quote(idToken)}}"""))
        Unit
    }

    suspend fun isVerified(idToken: String): Boolean = withContext(Dispatchers.IO) {
        parseVerified(post("$IDENTITY/accounts:lookup", jsonBytes("""{"idToken":${quote(idToken)}}""")))
    }

    suspend fun refresh(refreshToken: String): Session = withContext(Dispatchers.IO) {
        parseRefreshed(post("$SECURE_TOKEN/token", jsonBytes("""{"grant_type":"refresh_token","refresh_token":${quote(refreshToken)}}""")))
    }

    /** Keeps Firebase's copy of the credential in step when the password changes on the phone. */
    suspend fun updatePassword(idToken: String, password: CharArray) = withContext(Dispatchers.IO) {
        val body = ByteArrayOutputStream().apply {
            write("""{"idToken":${quote(idToken)},"password":"""".toByteArray())
            writeEscaped(this, password)
            write("""","returnSecureToken":false}""".toByteArray())
        }.toByteArray()
        try { post("$IDENTITY/accounts:update", body) } finally { body.fill(0) }
        Unit
    }

    // ------------------------------------------------------------------ http

    private fun credentialBody(email: String, password: CharArray): ByteArray =
        ByteArrayOutputStream().apply {
            write("""{"email":${quote(email)},"password":"""".toByteArray())
            writeEscaped(this, password)
            write("""","returnSecureToken":true}""".toByteArray())
        }.toByteArray()

    private fun post(url: String, body: ByteArray): String {
        val connection = (URL("$url?key=${BuildConfig.FIREBASE_WEB_API_KEY}").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "application/json")
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) throw errorFrom(status, text)
            return text
        } catch (e: VerifierException) {
            throw e
        } catch (e: Exception) {
            throw VerifierException("NETWORK", "Could not reach the verification service. Check the connection and try again.")
        } finally {
            body.fill(0)
            connection.disconnect()
        }
    }

    private fun jsonBytes(text: String): ByteArray = text.toByteArray()

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /** The password goes into the body char by char, escaped for JSON, without ever becoming a String. */
    private fun writeEscaped(out: ByteArrayOutputStream, chars: CharArray) {
        val one = CharArray(1)
        chars.forEach { c ->
            when (c) {
                '"' -> out.write("\\\"".toByteArray())
                '\\' -> out.write("\\\\".toByteArray())
                else -> {
                    if (c < ' ') out.write("\\u%04x".format(c.code).toByteArray())
                    else { one[0] = c; out.write(String(one).toByteArray()) }
                }
            }
        }
        one.fill('\u0000')
    }

    companion object {
        private const val IDENTITY = "https://identitytoolkit.googleapis.com/v1"
        private const val SECURE_TOKEN = "https://securetoken.googleapis.com/v1"
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT = "Liftosaur/1.0 (Android)"

        private val json = Json { ignoreUnknownKeys = true }

        // The parsers are plain functions of text so they can be tested
        // without a network, which is where the format surprises live.

        fun parseSession(payload: String): Session {
            val o = json.parseToJsonElement(payload).jsonObject
            return Session(
                idToken = o.string("idToken") ?: throw VerifierException("BAD_RESPONSE", "The verification service gave an unexpected answer."),
                refreshToken = o.string("refreshToken").orEmpty(),
                localId = o.string("localId").orEmpty()
            )
        }

        fun parseRefreshed(payload: String): Session {
            val o = json.parseToJsonElement(payload).jsonObject
            return Session(
                idToken = o.string("id_token") ?: throw VerifierException("BAD_RESPONSE", "The verification service gave an unexpected answer."),
                refreshToken = o.string("refresh_token").orEmpty(),
                localId = o.string("user_id").orEmpty()
            )
        }

        fun parseVerified(payload: String): Boolean {
            val o = json.parseToJsonElement(payload).jsonObject
            val user = o["users"]?.jsonArray?.firstOrNull()?.jsonObject ?: return false
            return user["emailVerified"]?.jsonPrimitive?.content == "true"
        }

        /** Firebase's error codes, turned into sentences a person can act on. */
        fun errorFrom(status: Int, payload: String): VerifierException {
            val code = runCatching {
                json.parseToJsonElement(payload).jsonObject["error"]?.jsonObject?.string("message")
            }.getOrNull().orEmpty().substringBefore(" ").ifBlank { "HTTP_$status" }
            val message = when (code) {
                "EMAIL_EXISTS" -> "That email already has a verified identity. Sign in with its password."
                "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" ->
                    "The verification service did not accept that email and password."
                "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Wait a few minutes and try again."
                "INVALID_EMAIL" -> "The verification service did not accept that email address."
                "WEAK_PASSWORD" -> "The verification service wants a password of at least six characters."
                "USER_DISABLED" -> "That identity has been disabled."
                "TOKEN_EXPIRED", "INVALID_ID_TOKEN", "INVALID_REFRESH_TOKEN", "USER_NOT_FOUND" ->
                    "Your verification session has expired. Sign in again to continue."
                "CONFIGURATION_NOT_FOUND", "OPERATION_NOT_ALLOWED" ->
                    "Email sign-in is not switched on in the Firebase project."
                else -> "The verification service returned an error ($code)."
            }
            return VerifierException(code, message)
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.takeIf { it.isString }?.content
    }
}
