package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.storage.LocalAccountDataCleaner
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.parseSessionFromUrl
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.http.Url
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Every Supabase email link (sign-up confirmation, magic link, password recovery, invite and email
 * change) comes back to the app through this deep link instead of a web page, so confirming an
 * account never lands the user on a URL that has no page behind it.
 */
const val SUPABASE_AUTH_REDIRECT_URL = "raif://auth/callback"
const val SUPABASE_PASSWORD_RECOVERY_REDIRECT_URL = SUPABASE_AUTH_REDIRECT_URL

object AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AuthRepository")

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _passwordRecoveryPending = MutableStateFlow(false)
    val passwordRecoveryPending: StateFlow<Boolean> = _passwordRecoveryPending.asStateFlow()

    private var initialized = false
    private var sessionStatusJob: Job? = null
    private var validatedRemoteUserId: String? = null
    private var lastRemoteUserId: String? = null

    fun initialize() {
        if (initialized) return
        initialized = true

        val savedAnonId = AuthStorage.loadAnonymousUserId()
        if (savedAnonId != null) {
            _state.value = AuthState.Authenticated(
                userId = savedAnonId,
                email = null,
                isAnonymous = true,
            )
        }

        sessionStatusJob = scope.launch {
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                if (AuthStorage.loadAnonymousUserId() != null) return@collect
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        val userId = user?.id.orEmpty()
                        if (!validateRemoteSession(userId)) return@collect
                        val previousUserId = lastRemoteUserId ?: ProfileRepository.storedAccountUserId()
                        if (previousUserId != null && previousUserId != userId) {
                            log.i { "Supabase account changed; clearing the previous account's local cache" }
                            LocalAccountDataCleaner.wipe()
                        }
                        lastRemoteUserId = userId.takeIf(String::isNotBlank)
                        _state.value = AuthState.Authenticated(
                            userId = userId,
                            email = user?.email,
                            isAnonymous = false,
                        )
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _state.value = AuthState.Unauthenticated
                    }
                    is SessionStatus.Initializing -> {
                        if (AuthStorage.loadAnonymousUserId() == null) {
                            _state.value = AuthState.Loading
                        }
                    }
                    is SessionStatus.RefreshFailure -> {
                        _state.value = AuthState.Unauthenticated
                    }
                }
            }
        }
    }

    private suspend fun validateRemoteSession(userId: String): Boolean {
        if (userId.isBlank() || validatedRemoteUserId == userId) return true

        return try {
            SupabaseProvider.client.auth.retrieveUserForCurrentSession(false)
            validatedRemoteUserId = userId
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isInvalidRemoteSessionError(error)) {
                log.w(error) { "Stored Supabase session no longer belongs to an active account; clearing local auth" }
                clearLocalSessionAfterRemoteInvalidation()
                false
            } else {
                log.w(error) { "Unable to validate stored Supabase session; keeping cached auth state" }
                true
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun signInAnonymously() {
        _error.value = null
        _passwordRecoveryPending.value = false
        val userId = Uuid.random().toString()
        AuthStorage.saveAnonymousUserId(userId)
        _state.value = AuthState.Authenticated(
            userId = userId,
            email = null,
            isAnonymous = true,
        )
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        _passwordRecoveryPending.value = false
        return runAuthRequest(Res.string.auth_sign_up_failed) {
            SupabaseProvider.client.auth.signUpWith(
                Email,
                redirectUrl = SUPABASE_AUTH_REDIRECT_URL,
            ) {
                this.email = email.trim()
                this.password = password
            }
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        _passwordRecoveryPending.value = false
        return runAuthRequest(Res.string.auth_sign_in_failed) {
            SupabaseProvider.client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }
    }

    suspend fun requestPasswordRecovery(email: String): Result<Unit> {
        _passwordRecoveryPending.value = false
        return runAuthRequest(fallbackMessage = "Unable to send a password reset email.") {
            SupabaseProvider.client.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = SUPABASE_PASSWORD_RECOVERY_REDIRECT_URL,
            )
        }
    }

    suspend fun updateRecoveredPassword(password: String): Result<Unit> {
        return runAuthRequest(fallbackMessage = "Unable to update the password.") {
            check(_passwordRecoveryPending.value) { "The password recovery session is no longer active." }
            SupabaseProvider.client.auth.updateUser {
                this.password = password
            }
            _passwordRecoveryPending.value = false
        }
    }

    private suspend fun runAuthRequest(
        fallbackMessage: String,
        action: suspend () -> Unit,
    ): Result<Unit> {
        _error.value = null
        return try {
            action()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _error.value = error.safeAuthErrorDescription() ?: fallbackMessage
            Result.failure(error)
        }
    }

    private suspend fun runAuthRequest(
        fallbackMessage: org.jetbrains.compose.resources.StringResource,
        action: suspend () -> Unit,
    ): Result<Unit> = runAuthRequest(getString(fallbackMessage), action)

    suspend fun cancelPasswordRecovery() {
        _error.value = null
        _passwordRecoveryPending.value = false
        var cancellation: CancellationException? = null
        try {
            SupabaseProvider.client.auth.clearSession()
        } catch (error: CancellationException) {
            cancellation = error
        } catch (error: Throwable) {
            log.w(error) { "Could not clear the local password recovery session" }
        }
        _state.value = AuthState.Unauthenticated
        cancellation?.let { throw it }
    }

    /**
     * Handles every Supabase email link that comes back through [SUPABASE_AUTH_REDIRECT_URL]:
     * sign-up confirmation, magic link, password recovery, invite and email change.
     *
     * The link may arrive as a `token_hash` query (what the current Supabase email templates
     * generate), as a PKCE `code`, or as the older `#access_token=...` fragment. All three end in an
     * imported session, and only a real recovery link opens the "new password" screen.
     */
    fun handleAuthCallback(url: String) {
        initialize()
        val callback = parseSupabaseAuthCallback(url)
        val errorDescription = callback.errorDescription
        if (errorDescription != null) {
            _passwordRecoveryPending.value = false
            val cancelled = errorDescription.equals("Access denied", ignoreCase = true)
            scope.launch {
                _error.value = getString(
                    if (cancelled) {
                        Res.string.auth_email_link_cancelled
                    } else {
                        Res.string.auth_email_link_invalid
                    },
                )
            }
            return
        }

        _passwordRecoveryPending.value = callback.otpType == OtpType.Email.RECOVERY
        scope.launch {
            try {
                val auth = SupabaseProvider.client.auth
                val tokenHash = callback.tokenHash
                val otpType = callback.otpType
                val code = callback.code
                when {
                    tokenHash != null && otpType != null -> auth.verifyEmailOtp(otpType, tokenHash)
                    code != null -> auth.exchangeCodeForSession(code)
                    else -> auth.importSession(auth.parseSessionFromUrl(url))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _passwordRecoveryPending.value = false
                log.e { "Supabase auth callback could not be processed" }
                _error.value = error.safeAuthErrorDescription()
                    ?: getString(Res.string.auth_email_link_invalid)
            }
        }
    }

    suspend fun signOut(): Result<Unit> {
        _error.value = null
        _passwordRecoveryPending.value = false
        val anonymousRead = runCatching { AuthStorage.loadAnonymousUserId() }
        val wasAnonymous = anonymousRead.getOrNull() != null
        val anonymousClear = runCatching { AuthStorage.clearAnonymousUserId() }
        validatedRemoteUserId = null
        lastRemoteUserId = null
        val remoteSignOut = if (wasAnonymous) {
            Result.success(Unit)
        } else {
            runCatching { SupabaseProvider.client.auth.signOut() }
        }

        val fallbackSessionClear = if (remoteSignOut.isFailure) {
            runCatching { SupabaseProvider.client.auth.clearSession() }
                .onFailure { error -> log.w(error) { "Failed to clear Supabase session after sign-out failure" } }
        } else {
            Result.success(Unit)
        }
        val localCleanup = runCatching { LocalAccountDataCleaner.wipe() }
        _state.value = AuthState.Unauthenticated

        val failure = anonymousRead.exceptionOrNull()
            ?: anonymousClear.exceptionOrNull()
            ?: remoteSignOut.exceptionOrNull()
            ?: fallbackSessionClear.exceptionOrNull()
            ?: localCleanup.exceptionOrNull()
        val cancellation = anonymousRead.exceptionOrNull() as? CancellationException
            ?: anonymousClear.exceptionOrNull() as? CancellationException
            ?: remoteSignOut.exceptionOrNull() as? CancellationException
            ?: fallbackSessionClear.exceptionOrNull() as? CancellationException
            ?: localCleanup.exceptionOrNull() as? CancellationException
        if (cancellation != null) throw cancellation
        return if (failure == null) {
            Result.success(Unit)
        } else {
            log.e(failure) { "Sign-out did not complete cleanly; all local cleanup steps were attempted" }
            _error.value = failure.message ?: runCatching {
                getString(Res.string.auth_sign_out_failed)
            }.getOrDefault("Sign out failed")
            Result.failure(failure)
        }
    }

    suspend fun prepareForServerSwitch(): Result<Unit> {
        _error.value = null
        _passwordRecoveryPending.value = false
        val anonymousClear = runCatching { AuthStorage.clearAnonymousUserId() }
        validatedRemoteUserId = null
        lastRemoteUserId = null
        val sessionClear = runCatching { SupabaseProvider.client.auth.clearSession() }
        _state.value = AuthState.Unauthenticated
        val failure = anonymousClear.exceptionOrNull() ?: sessionClear.exceptionOrNull()
        val cancellation = anonymousClear.exceptionOrNull() as? CancellationException
            ?: sessionClear.exceptionOrNull() as? CancellationException
        if (cancellation != null) throw cancellation
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    fun reinitialize() {
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        initialized = false
        validatedRemoteUserId = null
        lastRemoteUserId = null
        _state.value = AuthState.Loading
        initialize()
    }

    suspend fun signOutIfSessionInvalid(error: Throwable, source: String): Boolean {
        if (!isInvalidRemoteSessionError(error)) return false

        log.w(error) { "$source failed because the current Supabase account/session is no longer valid; clearing local auth" }
        clearLocalSessionAfterRemoteInvalidation()
        return true
    }

    private suspend fun clearLocalSessionAfterRemoteInvalidation() {
        _error.value = null
        _passwordRecoveryPending.value = false
        AuthStorage.clearAnonymousUserId()
        validatedRemoteUserId = null
        lastRemoteUserId = null
        val sessionClear = runCatching {
            SupabaseProvider.client.auth.clearSession()
        }
        sessionClear.onFailure { error ->
            if (error !is CancellationException) {
                log.w(error) { "Failed to clear Supabase session after remote invalidation; continuing local reset" }
            }
        }
        val localCleanup = runCatching { LocalAccountDataCleaner.wipe() }
        _state.value = AuthState.Unauthenticated
        localCleanup.onFailure { error ->
            if (error !is CancellationException) {
                log.e(error) { "Local account cleanup failed after remote session invalidation" }
            }
        }
        val cancellation = sessionClear.exceptionOrNull() as? CancellationException
            ?: localCleanup.exceptionOrNull() as? CancellationException
        cancellation?.let { throw it }
    }

    suspend fun deleteAccount(): Result<Unit> {
        _error.value = null
        return try {
            SupabaseProvider.client.functions.invoke("delete-account")
            SupabaseProvider.client.auth.signOut()
            validatedRemoteUserId = null
            lastRemoteUserId = null
            try {
                LocalAccountDataCleaner.wipe()
            } finally {
                _state.value = AuthState.Unauthenticated
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.e(error) { "Account deletion failed" }
            _error.value = error.message ?: getString(Res.string.auth_account_deletion_failed)
            Result.failure(error)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun isInvalidRemoteSessionError(error: Throwable): Boolean {
        val restError = error.findCause<RestException>()
        if (restError?.statusCode == 401) return true

        val message = buildString {
            append(error.message.orEmpty())
            if (restError != null) {
                append(' ')
                append(restError.error)
                append(' ')
                append(restError.description)
            }
        }.lowercase()

        return (
            "jwt" in message &&
                ("invalid" in message || "expired" in message || "malformed" in message)
            ) || (
            "user" in message &&
                ("does not exist" in message || "not found" in message || "deleted" in message)
            ) || (
            "foreign key" in message &&
                ("auth.users" in message || "user_id" in message)
            )
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun Throwable.safeAuthErrorDescription(): String? =
        findCause<AuthRestException>()
            ?.errorDescription
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: findCause<RestException>()
                ?.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
}


internal data class SupabaseAuthCallback(
    val otpType: OtpType.Email?,
    val tokenHash: String?,
    val code: String?,
    val errorDescription: String?,
)

/**
 * Reads what a Supabase email link carries without touching the network, so the callback itself can
 * be unit tested. Links carry either a `token_hash` (and a `type`), a PKCE `code`, or nothing at all
 * when the session arrives in the `#...` fragment handled by `parseSessionFromUrl`.
 */
internal fun parseSupabaseAuthCallback(url: String): SupabaseAuthCallback {
    val parsedUrl = runCatching { Url(url) }.getOrNull()
        ?: return SupabaseAuthCallback(null, null, null, null)
    return SupabaseAuthCallback(
        otpType = emailOtpType(parsedUrl.queryParameter("type")),
        tokenHash = parsedUrl.queryParameter("token_hash"),
        code = parsedUrl.queryParameter("code"),
        errorDescription = parsedUrl.queryParameter("error_description")
            ?: parsedUrl.queryParameter("error")
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercaseChar() },
    )
}

private fun Url.queryParameter(key: String): String? =
    parameters[key]?.trim()?.takeIf(String::isNotBlank)

private fun emailOtpType(value: String?): OtpType.Email? = when (value?.lowercase()) {
    "signup", "magiclink" -> OtpType.Email.EMAIL
    "recovery" -> OtpType.Email.RECOVERY
    "invite" -> OtpType.Email.INVITE
    "email_change" -> OtpType.Email.EMAIL_CHANGE
    else -> null
}
