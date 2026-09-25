package com.ironlog.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ironlog.app.ui.navigation.Routes
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironlog.app.core.audio.WelcomeSound
import com.ironlog.app.core.security.BiometricGate
import com.ironlog.app.data.repository.AuthRepository
import com.ironlog.app.data.repository.ProfileRepository
import com.ironlog.app.data.repository.SessionState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.LaunchScreen
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.navigation.AuthNavHost
import com.ironlog.app.ui.screens.rewards.EquippedViewModel
import com.ironlog.app.ui.screens.rewards.backgroundColour
import com.ironlog.app.ui.navigation.MainNavHost
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.IronLogTheme
import com.ironlog.app.ui.theme.Space
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ironlog.app.ui.screens.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single activity for the whole app.
 *
 * [FragmentActivity] rather than ComponentActivity because BiometricPrompt needs
 * a FragmentManager to host its dialog -- there is no Compose-native equivalent.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var biometricGate: BiometricGate

    private val mainViewModel: MainViewModel by viewModels()
    private val equippedViewModel: EquippedViewModel by viewModels()

    /** Route handed over by a notification tap, consumed once by the nav host. */
    private var deepLinkRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, which is where the system expects to be told. The mark
        // then stays on screen until the session has been read, so the app is
        // never caught mid-decision with a spinner showing.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { mainViewModel.sessionState.value is SessionState.Loading }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            deepLinkRoute = intent?.getStringExtra(EXTRA_DEEP_LINK_ROUTE) ?: incomingFileRoute(intent)
            // Cold start only. A rotation is not an arrival, and neither is
            // coming back from the camera, so neither one greets the user again.
            mainViewModel.playWelcomeSound()
        }

        // Screenshots and the recents thumbnail follow one preference for the
        // whole window. Applied here, at the root, so a screen that sets the
        // flag for itself does not have to know about the setting.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                mainViewModel.screenshotsBlocked.collect { blocked ->
                    if (blocked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            // The equipped background is read here, at the root, so a colour
            // bought in the store applies to every screen at once.
            val equipped by equippedViewModel.equipped.collectAsStateWithLifecycle()
            IronLogTheme(background = backgroundColour(equipped.backgroundId)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IronLogApp(
                        activity = this,
                        biometricGate = biometricGate,
                        viewModel = mainViewModel,
                        deepLinkRoute = deepLinkRoute,
                        onDeepLinkConsumed = { deepLinkRoute = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (intent.getStringExtra(EXTRA_DEEP_LINK_ROUTE) ?: incomingFileRoute(intent))?.let { deepLinkRoute = it }
    }

    /**
     * A file shared or opened into the app -- a backup, or a Strong or Hevy
     * export -- goes to the settings screen, which already knows how to ask
     * what to do with it. The read grant on the URI lasts for this task, which
     * is long enough for the user to confirm.
     */
    private fun incomingFileRoute(intent: Intent?): String? {
        intent ?: return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        return uri?.let { Routes.settings(it) }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // A rotation is not the user leaving the app.
        if (!isChangingConfigurations) mainViewModel.onAppBackgrounded()
    }

    companion object {
        const val EXTRA_DEEP_LINK_ROUTE = "deep_link_route"
    }
}

/**
 * Decides which of the three top-level states the user sees. The session is
 * resolved before anything renders, so the app never flashes the sign-in screen
 * at an already-authenticated user on a cold start.
 */
@Composable
private fun IronLogApp(
    activity: FragmentActivity,
    biometricGate: BiometricGate,
    viewModel: MainViewModel,
    deepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()

    when (val state = session) {
        SessionState.Loading -> LaunchScreen(Modifier.fillMaxSize())

        SessionState.SignedOut -> when (needsOnboarding) {
            // The intro comes before the first sign-in, once per device.
            null -> LaunchScreen(Modifier.fillMaxSize())
            true -> OnboardingScreen(onDone = viewModel::finishOnboarding)
            false -> AuthNavHost(onAuthenticated = { viewModel.onAuthenticated() })
        }

        is SessionState.SignedIn -> {
            // The lock is drawn *over* the app rather than replacing it. Swapping
            // the nav host out would throw away the back stack and any in-flight
            // activity result, such as a photo the camera is about to return.
            // key() rebuilds the graph if a different account signs in.
            Box(Modifier.fillMaxSize()) {
                key(state.userId) {
                    MainNavHost(
                        deepLinkRoute = deepLinkRoute.takeIf { !state.requiresBiometricUnlock },
                        onDeepLinkConsumed = onDeepLinkConsumed
                    )
                }
                if (state.requiresBiometricUnlock) {
                    BiometricLockScreen(
                        activity = activity,
                        biometricGate = biometricGate,
                        onUnlocked = viewModel::onUnlocked,
                        onSignOut = viewModel::signOut
                    )
                }
            }
        }
    }
}

/**
 * Shown when the app lock is on. The prompt fires automatically on arrival; the
 * manual button exists for the case where the user dismissed it and needs a way
 * back in without force-quitting.
 */
@Composable
private fun BiometricLockScreen(
    activity: FragmentActivity,
    biometricGate: BiometricGate,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var promptShown by remember { mutableStateOf(false) }

    fun authenticate() {
        error = null
        biometricGate.prompt(
            activity = activity,
            onSuccess = onUnlocked,
            onFailure = { error = it },
            onCancelled = { error = "Unlock cancelled" }
        )
    }

    LaunchedEffect(Unit) {
        if (!promptShown) {
            promptShown = true
            authenticate()
        }
    }

    // Back leaves the app rather than reaching the locked content underneath.
    BackHandler { activity.moveTaskToBack(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so nothing under the lock can be operated.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = ChalkDim,
            modifier = Modifier.height(40.dp)
        )
        Spacer(Modifier.height(Space.lg))
        Text("Liftosaur is locked", style = MaterialTheme.typography.headlineMedium, color = Chalk)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Unlock to reach your training log.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkDim
        )
        Spacer(Modifier.height(Space.xl))
        ErrorBanner(error)
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(text = "Unlock", onClick = ::authenticate)
        Spacer(Modifier.height(Space.md))
        TextButton(onClick = onSignOut) {
            Text(
                text = "Sign out instead",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkDim
            )
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val welcomeSound: WelcomeSound
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState

    /** Eager, so the window flag is right before the first frame is drawn. */
    val screenshotsBlocked: StateFlow<Boolean> = profileRepository.screenshotsBlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until the preference has been read, so the intro is neither flashed nor skipped by accident. */
    val needsOnboarding: StateFlow<Boolean?> = profileRepository.onboardingDone
        .map { done -> !done }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() {
        viewModelScope.launch {
            profileRepository.setOnboardingDone(true).onFailure {
                // There is no screen to tell: the introduction simply stays. The
                // cause must reach logcat, or a failure here is invisible.
                Log.e("IronLog", "Could not record that the introduction was seen", it)
            }
        }
    }

    private var backgroundedAt: Long? = null

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }

    /**
     * Greets the user, if they have left the sound on. The preference lives on
     * the device rather than the account, so this works on the sign-in screen
     * too -- there is no user to read a setting from at that point.
     */
    fun playWelcomeSound() {
        viewModelScope.launch {
            if (profileRepository.welcomeSoundEnabled.first()) welcomeSound.play()
        }
    }

    /**
     * The clip is held until the activity is finished for good. Releasing it
     * when playback starts would cut the sting off mid-note, because releasing
     * a SoundPool stops whatever it is playing.
     */
    override fun onCleared() {
        super.onCleared()
        welcomeSound.release()
    }

    /**
     * Only an *unlocked* session is timed. If the app goes to the background while
     * the lock is showing -- which happens when the device-PIN fallback opens its
     * own activity -- that must not re-lock the app the moment the user unlocks.
     */
    fun onAppBackgrounded() {
        val state = sessionState.value
        backgroundedAt = if (state is SessionState.SignedIn && !state.requiresBiometricUnlock) {
            System.currentTimeMillis()
        } else {
            null
        }
    }

    fun onAppForegrounded() {
        val leftAt = backgroundedAt ?: return
        backgroundedAt = null
        viewModelScope.launch {
            if (sessionState.value !is SessionState.SignedIn) return@launch
            // Renew the access token if it is close to expiry; a refresh token that
            // has itself expired signs the user out here rather than mid-workout.
            if (!authRepository.refreshIfNeeded()) return@launch
            val lockEnabled = authRepository.currentUser()?.biometricLockEnabled == true
            // The grace is the user's: straight away for a shared phone, a few
            // minutes for one that only ever leaves their pocket in the gym.
            val graceMs = profileRepository.lockGraceSeconds.first() * 1000L
            if (lockEnabled && System.currentTimeMillis() - leftAt >= graceMs) {
                authRepository.lock()
            }
        }
    }

    fun onAuthenticated() {
        viewModelScope.launch { authRepository.restoreSession() }
    }

    fun onUnlocked() = authRepository.markBiometricUnlocked()

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private companion object {
        /** A glance at a notification should not demand a fingerprint on return. */
    }
}
