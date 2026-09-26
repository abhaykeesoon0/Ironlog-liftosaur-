package com.ironlog.app.core

import com.ironlog.app.core.auth.EmailVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verification service's answers, parsed without a network. What the
 * service says on the wire is the part most likely to surprise, so the
 * parsers are exercised against the shapes it actually returns.
 */
class EmailVerifierTest {

    @Test
    fun aSignUpAnswerYieldsTheTokens() {
        val session = EmailVerifier.parseSession(
            """{"kind":"identitytoolkit#SignupNewUserResponse","idToken":"id.token","email":"a@b.c",
                "refreshToken":"refresh.token","expiresIn":"3600","localId":"uid123"}"""
        )
        assertEquals("id.token", session.idToken)
        assertEquals("refresh.token", session.refreshToken)
        assertEquals("uid123", session.localId)
    }

    @Test
    fun aRefreshAnswerUsesItsOwnFieldNames() {
        val session = EmailVerifier.parseRefreshed(
            """{"access_token":"x","expires_in":"3600","token_type":"Bearer","refresh_token":"r2","id_token":"id2","user_id":"uid","project_id":"p"}"""
        )
        assertEquals("id2", session.idToken)
        assertEquals("r2", session.refreshToken)
        assertEquals("uid", session.localId)
    }

    @Test
    fun lookupReportsTheVerifiedFlagAndNothingElseCountsAsVerified() {
        assertTrue(EmailVerifier.parseVerified("""{"users":[{"localId":"u","email":"a@b.c","emailVerified":true}]}"""))
        assertFalse(EmailVerifier.parseVerified("""{"users":[{"localId":"u","email":"a@b.c","emailVerified":false}]}"""))
        assertFalse("no user in the answer is not verified", EmailVerifier.parseVerified("""{"users":[]}"""))
        assertFalse(EmailVerifier.parseVerified("""{"kind":"x"}"""))
    }

    @Test
    fun serviceErrorsBecomeSentencesAndKeepTheirCode() {
        val exists = EmailVerifier.errorFrom(400, """{"error":{"code":400,"message":"EMAIL_EXISTS","errors":[]}}""")
        assertEquals("EMAIL_EXISTS", exists.code)
        assertTrue(exists.message!!.contains("already"))

        // Firebase suffixes some codes with a colon and detail; only the code matters.
        val throttled = EmailVerifier.errorFrom(400, """{"error":{"message":"TOO_MANY_ATTEMPTS_TRY_LATER : Access blocked."}}""")
        assertEquals("TOO_MANY_ATTEMPTS_TRY_LATER", throttled.code)

        val unknown = EmailVerifier.errorFrom(500, "not json at all")
        assertEquals("HTTP_500", unknown.code)
        assertTrue(unknown.message!!.contains("HTTP_500"))
    }

    @Test
    fun aMalformedSessionAnswerIsRefusedNotHalfRead() {
        val result = runCatching { EmailVerifier.parseSession("""{"kind":"x"}""") }
        assertTrue(result.exceptionOrNull() is EmailVerifier.VerifierException)
    }
}
