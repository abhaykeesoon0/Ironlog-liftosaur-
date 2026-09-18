package com.ironlog.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ironlog.app.core.permissions.AppPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS sampling for outdoor cardio.
 *
 * Two pieces of filtering matter more than the plumbing:
 *
 *  - **Accuracy gate.** The first fixes after a cold start are often 50-100 m
 *    out, which would add hundreds of spurious metres to a route. Fixes worse
 *    than [MAX_ACCURACY_METERS] are discarded rather than smoothed.
 *  - **Jitter gate.** A stationary phone still reports slightly different
 *    coordinates every second. Points closer than [MIN_DISPLACEMENT_METERS] to
 *    the previous accepted fix are dropped, so standing at a traffic light does
 *    not slowly inflate the distance.
 *
 * Sampling runs at one fix per two seconds. Faster gains nothing at running pace
 * and costs battery, which matters on a device that must survive the whole
 * session.
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean = AppPermission.LOCATION.isGranted(context)

    /** True when the user has location switched on at the OS level. */
    fun isLocationEnabled(): Boolean {
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        return manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * Caller must hold ACCESS_FINE_LOCATION; [hasPermission] is checked first and
     * the flow closes immediately if it is missing.
     */
    @SuppressLint("MissingPermission")
    fun trackRoute(): Flow<RoutePoint> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISPLACEMENT_METERS)
            .setWaitForAccurateLocation(true)
            .build()

        var lastAccepted: RoutePoint? = null

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return

                val point = RoutePoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    timestamp = location.time,
                    altitude = if (location.hasAltitude()) location.altitude else 0.0,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                    speedMps = if (location.hasSpeed()) location.speed else 0f
                )

                val previous = lastAccepted
                if (previous != null &&
                    GeoMath.distanceMeters(previous, point) < MIN_DISPLACEMENT_METERS
                ) return

                lastAccepted = point
                trySend(point)
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val INTERVAL_MS = 2_000L
        const val MIN_INTERVAL_MS = 1_000L
        const val MIN_DISPLACEMENT_METERS = 5f
        const val MAX_ACCURACY_METERS = 25f
    }
}
