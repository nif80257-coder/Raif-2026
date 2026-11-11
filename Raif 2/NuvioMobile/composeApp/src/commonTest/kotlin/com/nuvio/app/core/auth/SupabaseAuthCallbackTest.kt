package com.nuvio.app.core.auth

import io.github.jan.supabase.auth.OtpType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseAuthCallbackTest {

    @Test
    fun `reads a sign-up confirmation token hash`() {
        val callback = parseSupabaseAuthCallback("raif://auth/callback?token_hash=abc123&type=signup")
        assertEquals(OtpType.Email.EMAIL, callback.otpType)
        assertEquals("abc123", callback.tokenHash)
        assertEquals(null, callback.code)
        assertNull(callback.errorDescription)
    }

    @Test
    fun `reads a recovery link so only that one opens the password screen`() {
        val callback = parseSupabaseAuthCallback("raif://auth/callback?token_hash=def456&type=recovery")
        assertEquals(OtpType.Email.RECOVERY, callback.otpType)
        assertEquals("def456", callback.tokenHash)
    }

    @Test
    fun `reads a pkce code`() {
        val callback = parseSupabaseAuthCallback("raif://auth/callback?code=abc-def_ghi")
        assertNull(callback.otpType)
        assertNull(callback.tokenHash)
        assertEquals("abc-def_ghi", callback.code)
    }

    @Test
    fun `reads an expired link error`() {
        val callback = parseSupabaseAuthCallback(
            "raif://auth/callback?error=access_denied&error_code=otp_expired" +
                "&error_description=Email%20link%20is%20invalid%20or%20has%20expired",
        )
        assertNull(callback.tokenHash)
        assertEquals("Email link is invalid or has expired", callback.errorDescription)
    }

    @Test
    fun `falls back to the bare error code when no description is sent`() {
        val callback = parseSupabaseAuthCallback("raif://auth/callback?error=access_denied")
        assertEquals("Access denied", callback.errorDescription)
    }

    @Test
    fun `ignores links that carry nothing`() {
        val callback = parseSupabaseAuthCallback("raif://auth/callback")
        assertNull(callback.otpType)
        assertNull(callback.tokenHash)
        assertNull(callback.code)
        assertNull(callback.errorDescription)
    }
}
