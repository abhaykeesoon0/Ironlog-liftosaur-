package com.ironlog.app.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Every runtime permission the app can ask for, with the copy that explains it.
 *
 * Permissions are modelled as data rather than scattered string literals for
 * three reasons: the OS-version splits (notifications from 33, activity
 * recognition from 29, media images from 33) are handled in exactly one place;
 * the Settings screen can enumerate every permission and show its live state;
 * and the rationale text sits next to the permission it justifies, which makes
 * it hard to ship a request with no explanation attached.
 */
enum class AppPermission(
    val title: String,
    /** Shown *before* the system dialog, so the user knows what they are granting. */
    val rationale: String,
    /** Shown after a permanent denial, explaining what stops working. */
    val deniedConsequence: String,
    val isOptional: Boolean = true
) {
    CAMERA(
        title = "Camera",
        rationale = "Liftosaur opens the camera to take progress photos and a profile picture, and to " +
            "scan food barcodes. Photos stay in the app's private storage on this phone; scanned " +
            "frames are read on the phone and never stored or sent.",
        deniedConsequence = "Without camera access you can still pick existing images and type a barcode by hand, but you can't shoot a photo or scan a label in the app."
    ),
    LOCATION(
        title = "Location",
        rationale = "Location is used only while an outdoor cardio session is recording, to draw your route " +
            "and work out distance and pace. Tracking stops the moment you finish the session.",
        deniedConsequence = "Without location access, cardio sessions record time only — no route, distance or pace."
    ),
    NOTIFICATIONS(
        title = "Notifications",
        rationale = "Notifications are how the rest timer reaches you when the screen is off, and how workout " +
            "reminders arrive on the days you planned to train.",
        deniedConsequence = "Without notifications the rest timer only counts down while the app is open on screen."
    ),
    ACTIVITY_RECOGNITION(
        title = "Physical activity",
        rationale = "The step counter reads the phone's built-in pedometer to show your daily steps and to count " +
            "steps during a cardio session. It reads the sensor only — no movement history is stored off-device.",
        deniedConsequence = "Without activity access, daily steps and in-session step counts are unavailable."
    ),
    MICROPHONE(
        title = "Microphone",
        rationale = "The microphone is used only while you hold the mic button on a set, to hear a weight " +
            "and a rep count. The phone's own recogniser does the listening; Liftosaur keeps only the words.",
        deniedConsequence = "Without microphone access you can still type every set; you just can't say it."
    ),
    BLUETOOTH_SCAN(
        title = "Nearby devices",
        rationale = "To find a heart-rate strap, Liftosaur scans for nearby Bluetooth devices that advertise " +
            "the standard heart-rate service. Nothing about them is stored except the one you connect.",
        deniedConsequence = "Without it, a strap cannot be found. Cardio still records distance, pace and steps."
    ),
    BLUETOOTH_CONNECT(
        title = "Connect to the strap",
        rationale = "Connecting reads beats per minute from the strap you chose, for this session only.",
        deniedConsequence = "Without it, the strap cannot be read. Cardio still records everything else."
    );
    // Choosing a gallery image goes through the system photo picker, which
    // needs no permission of its own, so there is no entry for photo access.

    /**
     * The concrete manifest permission for the running OS version, or null when
     * this version needs no runtime grant (so the app must treat it as granted).
     */
    val manifestPermission: String?
        get() = when (this) {
            CAMERA -> Manifest.permission.CAMERA
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS else null
            ACTIVITY_RECOGNITION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    Manifest.permission.ACTIVITY_RECOGNITION else null
            BLUETOOTH_SCAN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
                else Manifest.permission.ACCESS_FINE_LOCATION
            BLUETOOTH_CONNECT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
        }

    fun isGranted(context: Context): Boolean {
        val permission = manifestPermission ?: return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

/** Opens this app's page in system Settings, for permanently denied permissions. */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
}
