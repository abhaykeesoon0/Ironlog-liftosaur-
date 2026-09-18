package com.ironlog.app.core.location

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val altitude: Double = 0.0,
    /** Reported horizontal accuracy in metres; used to reject bad fixes. */
    val accuracy: Float = 0f,
    val speedMps: Float = 0f
)

object GeoMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Great-circle distance via the haversine formula.
     *
     * The flat-earth approximation is tempting at these distances but breaks down
     * badly at high latitudes, and a run is a few thousand consecutive segments —
     * small per-segment errors accumulate into a visibly wrong total.
     */
    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)

        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    fun totalDistanceMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(points[i - 1], points[i])
        }
        return total
    }

    fun elevationGainMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until points.size) {
            val delta = points[i].altitude - points[i - 1].altitude
            // Ignore sub-metre changes: GPS altitude is noisy enough that summing
            // every tiny rise would invent hundreds of metres of climb on flat ground.
            if (delta > 1.0) gain += delta
        }
        return gain
    }

    /** Seconds per kilometre. Returns 0 when there is not enough data to be meaningful. */
    fun avgPaceSecondsPerKm(distanceMeters: Double, durationSeconds: Int): Int {
        if (distanceMeters < 10.0 || durationSeconds <= 0) return 0
        return (durationSeconds / (distanceMeters / 1000.0)).toInt()
    }

    fun formatPace(secondsPerKm: Int): String =
        if (secondsPerKm <= 0) "—" else "%d:%02d /km".format(secondsPerKm / 60, secondsPerKm % 60)

    /**
     * Rough energy estimate using MET values. Deliberately labelled "estimated"
     * everywhere in the UI: without a heart-rate strap this cannot be accurate,
     * and presenting it as a measurement would be misleading.
     */
    fun estimateCalories(
        distanceMeters: Double,
        durationSeconds: Int,
        bodyweightKg: Double,
        met: Double
    ): Int {
        if (durationSeconds <= 0 || bodyweightKg <= 0) return 0
        val hours = durationSeconds / 3600.0
        return (met * bodyweightKg * hours).toInt()
    }

    /** Normalises a route into 0..1 screen space for the Canvas map renderer. */
    fun normalise(points: List<RoutePoint>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }

        val latSpan = (maxLat - minLat).takeIf { it > 1e-9 } ?: 1e-9
        val lonSpan = (maxLon - minLon).takeIf { it > 1e-9 } ?: 1e-9
        // Keep the aspect ratio honest: one degree of longitude is shorter than
        // one of latitude away from the equator, so a raw stretch to fill the box
        // would distort the route's shape.
        val span = maxOf(latSpan, lonSpan * cos(Math.toRadians((minLat + maxLat) / 2)))

        return points.map { point ->
            val x = ((point.lon - minLon) * cos(Math.toRadians((minLat + maxLat) / 2)) / span).toFloat()
            val y = (1.0 - (point.lat - minLat) / span).toFloat()
            x to y
        }
    }
}
