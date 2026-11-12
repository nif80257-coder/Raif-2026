package com.nuvio.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.DeviceLinkAuthRepository
import com.nuvio.app.core.auth.DeviceLinkAuthState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_auth_already_have_account
import nuvio.composeapp.generated.resources.compose_auth_access_code_continue
import nuvio.composeapp.generated.resources.compose_auth_access_code_error
import nuvio.composeapp.generated.resources.compose_auth_access_code_label
import nuvio.composeapp.generated.resources.compose_auth_access_code_subtitle
import nuvio.composeapp.generated.resources.compose_auth_access_code_title
import nuvio.composeapp.generated.resources.compose_auth_continue_without_account
import nuvio.composeapp.generated.resources.compose_auth_create_account
import nuvio.composeapp.generated.resources.compose_auth_dont_have_account
import nuvio.composeapp.generated.resources.compose_auth_email
import nuvio.composeapp.generated.resources.compose_auth_back_to_sign_in
import nuvio.composeapp.generated.resources.compose_auth_forgot_password
import nuvio.composeapp.generated.resources.compose_auth_new_password
import nuvio.composeapp.generated.resources.compose_auth_recovery_new_password_subtitle
import nuvio.composeapp.generated.resources.compose_auth_recovery_new_password_title
import nuvio.composeapp.generated.resources.compose_auth_recovery_subtitle
import nuvio.composeapp.generated.resources.compose_auth_recovery_title
import nuvio.composeapp.generated.resources.compose_auth_reset_email_sent
import nuvio.composeapp.generated.resources.compose_auth_reset_password
import nuvio.composeapp.generated.resources.compose_auth_update_password
import nuvio.composeapp.generated.resources.compose_auth_or_separator
import nuvio.composeapp.generated.resources.compose_auth_password
import nuvio.composeapp.generated.resources.compose_auth_sign_in
import nuvio.composeapp.generated.resources.compose_auth_sign_in_subtitle
import nuvio.composeapp.generated.resources.compose_auth_signup_confirmation
import nuvio.composeapp.generated.resources.compose_auth_sign_up
import nuvio.composeapp.generated.resources.compose_auth_sign_up_subtitle
import nuvio.composeapp.generated.resources.compose_auth_store_locally
import nuvio.composeapp.generated.resources.compose_auth_tagline
import nuvio.composeapp.generated.resources.compose_auth_terms_link
import nuvio.composeapp.generated.resources.compose_auth_terms_prefix
import nuvio.composeapp.generated.resources.compose_auth_welcome_back
import org.jetbrains.compose.resources.stringResource

internal val AuthTextPrimary = Color(0xFFF5F7F8)
internal val AuthTextSecondary = Color(0xFF969CA3)
private val AuthTextMuted = Color(0xFF6E7178)
private val AuthPrimaryButtonBackground = Color(0xFFF5F5F5)
private val AuthPrimaryButtonText = Color(0xFF111111)
internal val AuthFieldBackground = Color.White.copy(alpha = 0.04f)
private val AuthFieldBackgroundMobile = Color.White.copy(alpha = 0.035f)
internal val AuthFieldBorder = Color.White.copy(alpha = 0.08f)
private val AuthPaneBackground = Color.White.copy(alpha = 0.022f)
private val AuthPaneBorder = Color.White.copy(alpha = 0.07f)
private val AuthDividerColor = Color.White.copy(alpha = 0.10f)
private val AuthSecondaryButtonBackground = Color.White.copy(alpha = 0.05f)
private val AuthSecondaryButtonBorder = Color.White.copy(alpha = 0.09f)

private data class AuthFormMetrics(
    val fieldHeight: Dp,
    val fieldHorizontalPadding: Dp,
    val iconSize: Dp,
    val passwordIconSize: Dp,
    val primaryTop: Dp,
    val primaryHeight: Dp,
    val toggleTop: Dp,
    val dividerTop: Dp,
    val secondaryTop: Dp,
    val secondaryHeight: Dp,
    val fieldBackground: Color,
)

private val MobileAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 56.dp,
    fieldHorizontalPadding = 16.dp,
    iconSize = 19.dp,
    passwordIconSize = 20.dp,
    primaryTop = 22.dp,
    primaryHeight = 54.dp,
    toggleTop = 18.dp,
    dividerTop = 28.dp,
    secondaryTop = 24.dp,
    secondaryHeight = 54.dp,
    fieldBackground = AuthFieldBackgroundMobile,
)

private val LargeAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 58.dp,
    fieldHorizontalPadding = 18.dp,
    iconSize = 20.dp,
    passwordIconSize = 21.dp,
    primaryTop = 24.dp,
    primaryHeight = 56.dp,
    toggleTop = 18.dp,
    dividerTop = 30.dp,
    secondaryTop = 26.dp,
    secondaryHeight = 56.dp,
    fieldBackground = AuthFieldBackground,
)

private fun largeAuthScale(screenWidth: Dp): Float =
    (screenWidth.value / 1194f).coerceIn(1f, 1.32f)

private fun largeAuthFormMetrics(scale: Float): AuthFormMetrics = LargeAuthFormMetrics.copy(
    fieldHeight = 58.dp * scale,
    fieldHorizontalPadding = 18.dp * scale,
    iconSize = 20.dp * scale,
    passwordIconSize = 21.dp * scale,
    primaryTop = 24.dp * scale,
    primaryHeight = 56.dp * scale,
    toggleTop = 18.dp * scale,
    dividerTop = 30.dp * scale,
    secondaryTop = 26.dp * scale,
    secondaryHeight = 56.dp * scale,
)

@Composable
fun AuthScreen(modifier: Modifier = Modifier) {
    // Entry is the access code alone: a correct code opens a local session, no email needed.
    AccessCodeGate(
        onUnlocked = { AuthRepository.signInAnonymously() },
        modifier = modifier,
    )
}

/**
 * The email sign-in and sign-up form. The app now enters with the access code instead, so this is
 * kept unreferenced rather than deleted, in case accounts come back later.
 */
@Suppress("unused")
@Composable
private fun AuthFormScreen(modifier: Modifier = Modifier) {
    val accessGranted = true

    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val passwordRecoveryPending by AuthRepository.passwordRecoveryPending.collectAsStateWithLifecycle()
    val deviceLinkAuthState by DeviceLinkAuthRepository.state.collectAsStateWithLifecycle()
    val serverConnectionState by ServerConnectionController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var isRecoveryRequest by rememberSaveable { mutableStateOf(false) }
    var recoveryEmailSent by rememberSaveable { mutableStateOf(false) }
    var signUpConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var emailFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var passwordFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var showServerSheet by rememberSaveable { mutableStateOf(false) }
    var showOfficialServerDialog by rememberSaveable { mutableStateOf(false) }

    fun submitAuth() {
        if (isLoading) return
        if ((isRecoveryRequest || !passwordRecoveryPending) && email.isBlank()) return
        if (!isRecoveryRequest && password.length < 6) return
        DeviceLinkAuthRepository.cancel()
        isLoading = true
        signUpConfirmationVisible = false
        focusManager.clearFocus(force = true)
        scope.launch {
            try {
                when {
                    isRecoveryRequest -> {
                        recoveryEmailSent = AuthRepository.requestPasswordRecovery(email).isSuccess
                    }
                    passwordRecoveryPending -> {
                        val result = AuthRepository.updateRecoveredPassword(password)
                        if (result.isSuccess) password = ""
                    }
                    isSignUp -> {
                        signUpConfirmationVisible = AuthRepository.signUpWithEmail(email, password).isSuccess
                    }
                    else -> AuthRepository.signInWithEmail(email, password)
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleAuthMode() {
        DeviceLinkAuthRepository.cancel()
        isSignUp = !isSignUp
        isRecoveryRequest = false
        recoveryEmailSent = false
        signUpConfirmationVisible = false
        AuthRepository.clearError()
    }

    fun showRecoveryRequest() {
        if (isLoading || passwordRecoveryPending) return
        DeviceLinkAuthRepository.cancel()
        isSignUp = false
        isRecoveryRequest = true
        recoveryEmailSent = false
        signUpConfirmationVisible = false
        password = ""
        AuthRepository.clearError()
    }

    fun backToSignIn() {
        if (passwordRecoveryPending) {
            scope.launch { AuthRepository.cancelPasswordRecovery() }
        }
        isRecoveryRequest = false
        recoveryEmailSent = false
        signUpConfirmationVisible = false
        password = ""
        AuthRepository.clearError()
    }

    fun startDeviceLink() {
        if (isLoading) return
        focusManager.clearFocus(force = true)
        AuthRepository.clearError()
        DeviceLinkAuthRepository.start()
    }

    LaunchedEffect(passwordRecoveryPending) {
        if (passwordRecoveryPending) {
            isSignUp = false
            isRecoveryRequest = false
            recoveryEmailSent = false
            password = ""
            passwordFieldBounds = null
            DeviceLinkAuthRepository.cancel()
        }
    }

    LaunchedEffect(isRecoveryRequest, passwordRecoveryPending) {
        if (isRecoveryRequest || passwordRecoveryPending) passwordFieldBounds = null
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(serverConnectionState.activeServer.backendUrl) {
        DeviceLinkAuthRepository.cancel()
        if (!serverConnectionState.activeServer.isCustom) {
            showOfficialServerDialog = false
        }
    }

    DisposableEffect(Unit) {
        onDispose(DeviceLinkAuthRepository::cancel)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(emailFieldBounds, passwordFieldBounds) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val tappedTextField = listOfNotNull(emailFieldBounds, passwordFieldBounds)
                        .any { bounds -> bounds.contains(down.position) }
                    if (!tappedTextField) {
                        focusManager.clearFocus(force = true)
                    }
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val screenWidth = maxWidth
            val largeScreen = screenWidth >= 900.dp
            val compactLargeScreen = screenWidth < 1100.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .authGradientBackground(largeScreen = largeScreen),
            ) {
                if (largeScreen) {
                    val largeScale = largeAuthScale(screenWidth)
                    val formPaneWidth = (screenWidth * 0.30f).coerceIn(460.dp * largeScale, 720.dp * largeScale)
                    val brandHorizontalPadding = (screenWidth * 0.055f).coerceIn(56.dp * largeScale, 128.dp * largeScale)
                    val formHorizontalPadding = (formPaneWidth * 0.14f).coerceIn(48.dp, 96.dp)
                    val formMetrics = largeAuthFormMetrics(largeScale)

                    AuthLargeLayout(
                        modifier = Modifier.fillMaxSize(),
                        isSignUp = isSignUp,
                        recoveryRequest = isRecoveryRequest,
                        recoveryPending = passwordRecoveryPending,
                        recoveryEmailSent = recoveryEmailSent,
                        signUpConfirmationVisible = signUpConfirmationVisible,
                        email = email,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authError,
                        deviceLinkAuthState = deviceLinkAuthState,
                        deviceLinkEnabled = serverConnectionState.activeServer.capabilities.tvLogin,
                        formPaneWidth = if (compactLargeScreen) 460.dp else formPaneWidth,
                        brandHorizontalPadding = brandHorizontalPadding,
                        formHorizontalPadding = formHorizontalPadding,
                        formMetrics = formMetrics,
                        scale = largeScale,
                        onEmailChange = {
                            email = it
                            recoveryEmailSent = false
                            signUpConfirmationVisible = false
                            AuthRepository.clearError()
                        },
                        onPasswordChange = {
                            password = it
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onToggleAuthMode = ::toggleAuthMode,
                        onForgotPassword = ::showRecoveryRequest,
                        onBackToSignIn = ::backToSignIn,
                        onContinueWithoutAccount = {
                            focusManager.clearFocus(force = true)
                            DeviceLinkAuthRepository.cancel()
                            AuthRepository.signInAnonymously()
                        },
                        onStartDeviceLink = ::startDeviceLink,
                        onCancelDeviceLink = DeviceLinkAuthRepository::cancel,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                } else {
                    AuthMobileLayout(
                        isSignUp = isSignUp,
                        recoveryRequest = isRecoveryRequest,
                        recoveryPending = passwordRecoveryPending,
                        recoveryEmailSent = recoveryEmailSent,
                        signUpConfirmationVisible = signUpConfirmationVisible,
                        email = email,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authError,
                        deviceLinkAuthState = deviceLinkAuthState,
                        deviceLinkEnabled = serverConnectionState.activeServer.capabilities.tvLogin,
                        statusBarTop = statusBarTop,
                        onEmailChange = {
                            email = it
                            recoveryEmailSent = false
                            signUpConfirmationVisible = false
                            AuthRepository.clearError()
                        },
                        onPasswordChange = {
                            password = it
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onToggleAuthMode = ::toggleAuthMode,
                        onForgotPassword = ::showRecoveryRequest,
                        onBackToSignIn = ::backToSignIn,
                        onContinueWithoutAccount = {
                            focusManager.clearFocus(force = true)
                            DeviceLinkAuthRepository.cancel()
                            AuthRepository.signInAnonymously()
                        },
                        onStartDeviceLink = ::startDeviceLink,
                        onCancelDeviceLink = DeviceLinkAuthRepository::cancel,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                }
            }
        }

        if (AppFeaturePolicy.customServerConnectionsEnabled) {
            ServerConnectionMenu(
                activeServer = serverConnectionState.activeServer,
                onUseOfficial = {
                    ServerConnectionController.resetDiscovery()
                    showOfficialServerDialog = true
                },
                onConnectCustom = {
                    ServerConnectionController.resetDiscovery()
                    showServerSheet = true
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = statusBarTop + 4.dp),
            )
        }
    }

    if (
        AppFeaturePolicy.customServerConnectionsEnabled &&
        showServerSheet &&
        serverConnectionState.discoveredServer == null
    ) {
        ServerConnectionSheet(
            state = serverConnectionState,
            onDiscover = ServerConnectionController::discover,
            onDismiss = {
                showServerSheet = false
                ServerConnectionController.resetDiscovery()
            },
        )
    }

    serverConnectionState.discoveredServer?.let { server ->
        if (AppFeaturePolicy.customServerConnectionsEnabled) {
            ServerTrustDialog(
                server = server,
                isSwitching = serverConnectionState.isSwitching,
                switchFailure = serverConnectionState.switchFailure,
                onConfirm = ServerConnectionController::connectDiscovered,
                onDismiss = {
                    showServerSheet = false
                    ServerConnectionController.resetDiscovery()
                },
            )
        }
    }

    if (AppFeaturePolicy.customServerConnectionsEnabled && showOfficialServerDialog) {
        OfficialServerDialog(
            isSwitching = serverConnectionState.isSwitching,
            switchFailure = serverConnectionState.switchFailure,
            onConfirm = ServerConnectionController::useOfficial,
            onDismiss = {
                showOfficialServerDialog = false
                ServerConnectionController.resetDiscovery()
            },
        )
    }
}

@Composable
private fun AuthMobileLayout(
    isSignUp: Boolean,
    recoveryRequest: Boolean,
    recoveryPending: Boolean,
    recoveryEmailSent: Boolean,
    signUpConfirmationVisible: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    statusBarTop: Dp,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onBackToSignIn: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 30.dp,
                top = statusBarTop + 48.dp,
                end = 30.dp,
                bottom = 40.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 342.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthBrandLockup(logoHeight = 38.dp)

            Spacer(modifier = Modifier.height(64.dp))

            AuthHeading(
                isSignUp = isSignUp,
                recoveryRequest = recoveryRequest,
                recoveryPending = recoveryPending,
                headingStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )

            Spacer(modifier = Modifier.height(28.dp))

            AuthForm(
                isSignUp = isSignUp,
                recoveryRequest = recoveryRequest,
                recoveryPending = recoveryPending,
                recoveryEmailSent = recoveryEmailSent,
                signUpConfirmationVisible = signUpConfirmationVisible,
                email = email,
                password = password,
                passwordVisible = passwordVisible,
                isLoading = isLoading,
                authError = authError,
                deviceLinkAuthState = deviceLinkAuthState,
                deviceLinkEnabled = deviceLinkEnabled,
                metrics = MobileAuthFormMetrics,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                onSubmit = onSubmit,
                onToggleAuthMode = onToggleAuthMode,
                onForgotPassword = onForgotPassword,
                onBackToSignIn = onBackToSignIn,
                onContinueWithoutAccount = onContinueWithoutAccount,
                onStartDeviceLink = onStartDeviceLink,
                onCancelDeviceLink = onCancelDeviceLink,
                onEmailBoundsChange = onEmailBoundsChange,
                onPasswordBoundsChange = onPasswordBoundsChange,
            )
        }
    }
}

@Composable
private fun AuthLargeLayout(
    modifier: Modifier = Modifier,
    isSignUp: Boolean,
    recoveryRequest: Boolean,
    recoveryPending: Boolean,
    recoveryEmailSent: Boolean,
    signUpConfirmationVisible: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    formPaneWidth: Dp,
    brandHorizontalPadding: Dp,
    formHorizontalPadding: Dp,
    formMetrics: AuthFormMetrics,
    scale: Float,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onBackToSignIn: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = brandHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            AppBrandWordmark(
                contentDescription = null,
                modifier = Modifier.height(60.dp * scale),
            )
            Spacer(modifier = Modifier.height(32.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_tagline),
                modifier = Modifier.widthIn(max = 440.dp * scale),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = (40f * scale).sp,
                    lineHeight = (45f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(18.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_sign_up_subtitle),
                modifier = Modifier.widthIn(max = 400.dp * scale),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = (17f * scale).sp,
                    lineHeight = (26f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }

        Box(
            modifier = Modifier
                .width(formPaneWidth)
                .fillMaxHeight()
                .background(AuthPaneBackground)
                .drawBehind {
                    drawLine(
                        color = AuthPaneBorder,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = formHorizontalPadding),
            ) {
                AuthHeading(
                    isSignUp = isSignUp,
                    recoveryRequest = recoveryRequest,
                    recoveryPending = recoveryPending,
                    headingStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = AuthTextPrimary,
                        fontSize = (30f * scale).sp,
                        lineHeight = (33f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AuthTextSecondary,
                        fontSize = (15f * scale).sp,
                        lineHeight = (21f * scale).sp,
                        fontWeight = FontWeight.Normal,
                    ),
                )

                Spacer(modifier = Modifier.height(32.dp * scale))

                AuthForm(
                    isSignUp = isSignUp,
                    recoveryRequest = recoveryRequest,
                    recoveryPending = recoveryPending,
                    recoveryEmailSent = recoveryEmailSent,
                    signUpConfirmationVisible = signUpConfirmationVisible,
                    email = email,
                    password = password,
                    passwordVisible = passwordVisible,
                    isLoading = isLoading,
                    authError = authError,
                    deviceLinkAuthState = deviceLinkAuthState,
                    deviceLinkEnabled = deviceLinkEnabled,
                    metrics = formMetrics,
                    scale = scale,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onSubmit = onSubmit,
                    onToggleAuthMode = onToggleAuthMode,
                    onForgotPassword = onForgotPassword,
                    onBackToSignIn = onBackToSignIn,
                    onContinueWithoutAccount = onContinueWithoutAccount,
                    onStartDeviceLink = onStartDeviceLink,
                    onCancelDeviceLink = onCancelDeviceLink,
                    onEmailBoundsChange = onEmailBoundsChange,
                    onPasswordBoundsChange = onPasswordBoundsChange,
                )
            }
        }
    }
}

@Composable
private fun AuthBrandLockup(
    logoHeight: Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBrandWordmark(
            contentDescription = null,
            modifier = Modifier.height(logoHeight),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.compose_auth_tagline),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun AuthHeading(
    isSignUp: Boolean,
    recoveryRequest: Boolean,
    recoveryPending: Boolean,
    headingStyle: TextStyle,
    subtitleStyle: TextStyle,
) {
    val mode = when {
        recoveryPending -> 3
        recoveryRequest -> 2
        isSignUp -> 1
        else -> 0
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authHeading",
        ) { currentMode ->
            Text(
                text = stringResource(
                    when (currentMode) {
                        1 -> Res.string.compose_auth_create_account
                        2 -> Res.string.compose_auth_recovery_title
                        3 -> Res.string.compose_auth_recovery_new_password_title
                        else -> Res.string.compose_auth_welcome_back
                    },
                ),
                style = headingStyle,
                color = AuthTextPrimary,
            )
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authSubtitle",
        ) { currentMode ->
            Text(
                text = stringResource(
                    when (currentMode) {
                        1 -> Res.string.compose_auth_sign_up_subtitle
                        2 -> Res.string.compose_auth_recovery_subtitle
                        3 -> Res.string.compose_auth_recovery_new_password_subtitle
                        else -> Res.string.compose_auth_sign_in_subtitle
                    },
                ),
                style = subtitleStyle,
                color = AuthTextSecondary,
            )
        }
    }
}

@Composable
private fun AuthForm(
    isSignUp: Boolean,
    recoveryRequest: Boolean,
    recoveryPending: Boolean,
    recoveryEmailSent: Boolean,
    signUpConfirmationVisible: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    metrics: AuthFormMetrics,
    scale: Float = 1f,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onBackToSignIn: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val recoveryMode = recoveryRequest || recoveryPending
    val canSubmit = when {
        recoveryPending -> password.length >= 6
        recoveryRequest -> email.isNotBlank()
        else -> email.isNotBlank() && password.length >= 6
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!recoveryPending) {
            AuthTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = stringResource(Res.string.compose_auth_email),
                icon = Icons.Rounded.Email,
                metrics = metrics,
                scale = scale,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onEmailBoundsChange(coordinates.boundsInRoot())
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (recoveryRequest) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (recoveryRequest) onSubmit() },
                ),
            )
        }

        if (!recoveryRequest) {
            Spacer(modifier = Modifier.height(14.dp))
            AuthTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(
                    if (recoveryPending) Res.string.compose_auth_new_password
                    else Res.string.compose_auth_password,
                ),
                icon = Icons.Rounded.Lock,
                metrics = metrics,
                scale = scale,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onPasswordBoundsChange(coordinates.boundsInRoot())
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit() },
                ),
            )
        }

        authError?.let { errorText ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        if (signUpConfirmationVisible && isSignUp) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.compose_auth_signup_confirmation),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        if (recoveryEmailSent && recoveryRequest) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.compose_auth_reset_email_sent),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        if (isSignUp) {
            Spacer(modifier = Modifier.height(14.dp * scale))
            AuthTermsAcknowledgement(
                scale = scale,
                onTermsClick = { uriHandler.openUri("https://nuvio.tv/terms") },
            )
        }

        if (!recoveryMode && !isSignUp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(Res.string.compose_auth_forgot_password),
                    modifier = Modifier.clickable(
                        enabled = !isLoading,
                        onClick = onForgotPassword,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AuthTextPrimary,
                        fontSize = (13f * scale).sp,
                        lineHeight = (18f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(metrics.primaryTop))

        AuthPrimaryButton(
            text = when {
                recoveryPending -> stringResource(Res.string.compose_auth_update_password)
                recoveryRequest -> stringResource(Res.string.compose_auth_reset_password)
                isSignUp -> stringResource(Res.string.compose_auth_create_account)
                else -> stringResource(Res.string.compose_auth_sign_in)
            },
            isLoading = isLoading,
            enabled = !isLoading && canSubmit,
            height = metrics.primaryHeight,
            scale = scale,
            onClick = onSubmit,
        )

        if (recoveryMode) {
            Spacer(modifier = Modifier.height(metrics.toggleTop))
            Text(
                text = stringResource(Res.string.compose_auth_back_to_sign_in),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading, onClick = onBackToSignIn)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextPrimary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(modifier = Modifier.height(metrics.toggleTop))

            AuthModeToggle(
                isSignUp = isSignUp,
                scale = scale,
                onToggleAuthMode = onToggleAuthMode,
            )

            Spacer(modifier = Modifier.height(metrics.dividerTop))

            AuthDivider(scale = scale)

            Spacer(modifier = Modifier.height(metrics.secondaryTop))

            if (!isSignUp && deviceLinkEnabled) {
                DeviceLinkAuthSection(
                    state = deviceLinkAuthState,
                    enabled = !isLoading,
                    height = metrics.secondaryHeight,
                    scale = scale,
                    onStart = onStartDeviceLink,
                    onCancel = onCancelDeviceLink,
                )

                Spacer(modifier = Modifier.height(14.dp * scale))
            }

            AuthSecondaryButton(
                text = stringResource(Res.string.compose_auth_continue_without_account),
                enabled = !isLoading,
                height = metrics.secondaryHeight,
                scale = scale,
                onClick = onContinueWithoutAccount,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(Res.string.compose_auth_store_locally),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextMuted,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AuthTermsAcknowledgement(
    scale: Float,
    onTermsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.compose_auth_terms_prefix),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Spacer(modifier = Modifier.width(4.dp * scale))
        Text(
            text = stringResource(Res.string.compose_auth_terms_link),
            modifier = Modifier.clickable(onClick = onTermsClick),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextPrimary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    metrics: AuthFormMetrics,
    scale: Float,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.fieldHeight)
            .background(metrics.fieldBackground, shape)
            .border(1.dp, AuthFieldBorder, shape)
            .padding(horizontal = metrics.fieldHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(metrics.iconSize),
            tint = AuthTextMuted,
        )
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = AuthTextPrimary,
                fontSize = (16f * scale).sp,
                lineHeight = (22f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(AuthTextPrimary),
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AuthTextMuted,
                                fontSize = (16f * scale).sp,
                                lineHeight = (22f * scale).sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (isPassword) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onPasswordVisibilityToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(metrics.passwordIconSize),
                    tint = AuthTextSecondary,
                )
            }
        }
    }
}

@Composable
internal fun AuthPrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthPrimaryButtonBackground,
            contentColor = AuthPrimaryButtonText,
            disabledContainerColor = if (isLoading) {
                AuthPrimaryButtonBackground
            } else {
                AuthPrimaryButtonBackground.copy(alpha = 0.45f)
            },
            disabledContentColor = AuthPrimaryButtonText.copy(alpha = 0.55f),
        ),
    ) {
        if (isLoading) {
            NuvioLoadingIndicator(
                modifier = Modifier.size(20.dp * scale),
                color = AuthPrimaryButtonText,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (16f * scale).sp,
                    lineHeight = (22f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun AuthModeToggle(
    isSignUp: Boolean,
    scale: Float,
    onToggleAuthMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authTogglePrompt",
        ) { signUp ->
            Text(
                text = if (signUp) {
                    stringResource(Res.string.compose_auth_already_have_account).trimEnd()
                } else {
                    stringResource(Res.string.compose_auth_dont_have_account).trimEnd()
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authToggleAction",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_sign_in)
                else stringResource(Res.string.compose_auth_sign_up),
                modifier = Modifier.clickable(onClick = onToggleAuthMode),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextPrimary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun AuthDivider(scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor),
        )
        Text(
            text = stringResource(Res.string.compose_auth_or_separator).trim(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextMuted,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthDividerColor),
        )
    }
}

@Composable
internal fun AuthSecondaryButton(
    text: String,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AuthSecondaryButtonBorder),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthSecondaryButtonBackground,
            contentColor = AuthTextPrimary,
            disabledContainerColor = AuthSecondaryButtonBackground.copy(alpha = 0.45f),
            disabledContentColor = AuthTextPrimary.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (15f * scale).sp,
                lineHeight = (21f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.authGradientBackground(largeScreen: Boolean): Modifier = drawWithCache {
    val angleDegrees = if (largeScreen) 122.0 else 148.0
    val angleRadians = angleDegrees * PI / 180.0
    val directionX = sin(angleRadians).toFloat()
    val directionY = (-cos(angleRadians)).toFloat()
    val halfLength = (abs(size.width * directionX) + abs(size.height * directionY)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(
        x = center.x - directionX * halfLength,
        y = center.y - directionY * halfLength,
    )
    val end = Offset(
        x = center.x + directionX * halfLength,
        y = center.y + directionY * halfLength,
    )
    val colorStops = if (largeScreen) {
        arrayOf(
            0f to Color(0xFF21113B),
            0.14f to Color(0xFF21113B),
            0.26f to Color(0xFF1A0E2F),
            0.36f to Color(0xFF130A23),
            0.48f to Color(0xFF0A060F),
            0.60f to Color(0xFF050408),
            0.70f to Color.Black,
            1f to Color.Black,
        )
    } else {
        arrayOf(
            0f to Color(0xFF21113B),
            0.12f to Color(0xFF21113B),
            0.24f to Color(0xFF1A0E2F),
            0.34f to Color(0xFF130A23),
            0.44f to Color(0xFF0A060F),
            0.58f to Color(0xFF050408),
            0.64f to Color.Black,
            1f to Color.Black,
        )
    }
    val brush = Brush.linearGradient(
        colorStops = colorStops,
        start = start,
        end = end,
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

private fun LayoutCoordinates.boundsInRoot(): Rect {
    val position = positionInRoot()
    return Rect(
        left = position.x,
        top = position.y,
        right = position.x + size.width,
        bottom = position.y + size.height,
    )
}


/**
 * The first screen of the app: a private access code has to be entered before the sign-in and
 * sign-up form is shown.
 */
@Composable
private fun AccessCodeGate(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    var code by rememberSaveable { mutableStateOf("") }
    var hasError by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (code == ACCESS_CODE) onUnlocked() else hasError = true
    }

    fun press(digit: Char) {
        if (code.length >= ACCESS_CODE.length) return
        hasError = false
        val next = code + digit
        code = next
        if (next.length == ACCESS_CODE.length) submit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to tokens.colors.accent.copy(alpha = 0.10f),
                        0.5f to Color.Transparent,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tokens.colors.accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.16f),
                        radius = size.minDimension * 0.95f,
                    ),
                    radius = size.minDimension * 0.95f,
                    center = Offset(size.width / 2f, size.height * 0.16f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Řaif",
                style = MaterialTheme.typography.displayLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tokens.colors.accent),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(Res.string.compose_auth_access_code_title),
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.compose_auth_access_code_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(ACCESS_CODE.length) { index ->
                    val filled = index < code.length
                    val active = index == code.length && !hasError
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 70.dp)
                            .clip(shape)
                            .background(tokens.colors.surfaceCard)
                            .border(
                                width = if (filled || active) 1.5.dp else 1.dp,
                                color = when {
                                    hasError -> tokens.colors.danger
                                    filled -> tokens.colors.accent
                                    active -> tokens.colors.accent.copy(alpha = 0.6f)
                                    else -> tokens.colors.borderDefault
                                },
                                shape = shape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (filled) {
                            Text(
                                text = code[index].toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = tokens.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (active) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(tokens.colors.accent),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (hasError) {
                    Text(
                        text = stringResource(Res.string.compose_auth_access_code_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.danger,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { key ->
                        KeypadKey(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "C" -> {
                                        code = ""
                                        hasError = false
                                    }
                                    "⌫" -> {
                                        code = code.dropLast(1)
                                        hasError = false
                                    }
                                    else -> press(key.first())
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "keypad_key_press",
    )
    Box(
        modifier = modifier
            .height(62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.thin, tokens.colors.borderDefault, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (label == "⌫" || label == "C") tokens.colors.textSecondary else tokens.colors.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

private const val ACCESS_CODE = "305"
