package com.ironlog.app.ui.components

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ironlog.app.core.location.RoutePoint
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.Slate900
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * The route on a real map.
 *
 * OpenStreetMap tiles through osmdroid: no key, no account, and the only
 * thing that leaves the phone is the tile coordinates of the area on screen,
 * fetched while the map is visible and cached afterwards. The tiles are
 * tinted dark to sit with the rest of the app, and the route is drawn in
 * chalk on top. Without a connection the grid stays blank and the route is
 * still there, which is the honest state rather than an error.
 *
 * Live mode follows the latest fix. A finished session is framed once.
 */
@Composable
fun RouteMap(
    points: List<RoutePoint>,
    live: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routeColour = Chalk.toArgb()
    val haloColour = Slate900.toArgb()
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            configureOsmdroid(ctx)
            MapView(ctx).also { mapRef = it }.apply {
                // Compose detaches and re-attaches the view during layout; by
                // default osmdroid would tear the overlays down on the first
                // detach and crash on the next update. Teardown is ours, below.
                setDestroyMode(false)
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                overlayManager.tilesOverlay.setColorFilter(darkTiles())
                overlays.add(CopyrightOverlay(ctx))
                // A fresh map opens somewhere sensible until the first fix lands.
                controller.setZoom(15.0)
                tag = RouteOverlays(
                    line = Polyline(this).apply {
                        outlinePaint.color = routeColour
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        setOnClickListener { _, _, _ -> true }
                    },
                    here = Marker(this).apply {
                        icon = dot(routeColour, haloColour)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                ).also { overlays.add(it.line); overlays.add(it.here) }
            }
        },
        update = { map ->
            val overlays = map.tag as RouteOverlays
            val geo = points.map { GeoPoint(it.lat, it.lon, it.altitude) }
            overlays.line.setPoints(geo)
            val last = geo.lastOrNull()
            overlays.here.isEnabled = last != null
            last?.let { overlays.here.position = it }
            when {
                geo.isEmpty() -> Unit
                geo.size == 1 -> {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(last)
                }
                live -> {
                    // Following the runner: the whole route stays in view, and the
                    // map only moves when the newest fix has left the frame, so a
                    // pinch or a drag is not undone on the very next fix.
                    val box = BoundingBox.fromGeoPointsSafe(geo).padded()
                    if (map.width == 0 || map.height == 0) {
                        // Returning to a session already under way: the update runs
                        // while the view is being attached, before it has a size, and
                        // osmdroid's zoomToBoundingBox never converges on a zero-sized
                        // map (it loops on the main thread until the app is killed).
                        // Frame the route once the first layout has given it a size.
                        map.post { map.zoomToBoundingBox(box, false, FRAME_PADDING_PX) }
                    } else {
                        val visible = map.boundingBox
                        val inFrame = last != null && visible.latitudeSpan > 0 && visible.contains(last)
                        if (!inFrame || map.zoomLevelDouble > 18.0) {
                            map.zoomToBoundingBox(box, true, FRAME_PADDING_PX)
                        }
                    }
                }
                else -> map.post {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(geo).padded(), false, FRAME_PADDING_PX)
                }
            }
            map.invalidate()
        },
        // The composable leaving is the one true end of the view: tiles, overlays
        // and listeners are released here and nowhere else.
        onRelease = { map -> map.onDetach() }
    )

    // The view has its own lifecycle for tile loading and must hear about ours.
    DisposableEffect(lifecycleOwner, mapRef) {
        val map = mapRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private class RouteOverlays(val line: Polyline, val here: Marker)

private const val FRAME_PADDING_PX = 64

/** A little air around the route, and a floor so a short walk is not zoomed to the pavement. */
private fun BoundingBox.padded(): BoundingBox {
    val latSpan = maxOf(latitudeSpan, 0.004)
    val lonSpan = maxOf(longitudeSpanWithDateLine, 0.004)
    return BoundingBox(
        centerLatitude + latSpan * 0.6, centerLongitude + lonSpan * 0.6,
        centerLatitude - latSpan * 0.6, centerLongitude - lonSpan * 0.6
    )
}

/**
 * Tiles are inverted and drained of most colour, which turns the standard
 * light map into a slate one that the chalk route reads clearly against.
 */
private fun darkTiles(): ColorMatrixColorFilter {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val muted = ColorMatrix().apply { setSaturation(0.25f) }
    val dimmed = ColorMatrix().apply { setScale(0.82f, 0.85f, 0.9f, 1f) }
    invert.postConcat(muted)
    invert.postConcat(dimmed)
    return ColorMatrixColorFilter(invert)
}

/** The current position: a chalk dot with a dark ring so it stands off the route line. */
private fun dot(fill: Int, ring: Int): android.graphics.drawable.Drawable {
    val size = 36
    return android.graphics.drawable.LayerDrawable(
        arrayOf(
            ShapeDrawable(OvalShape()).apply { paint.color = ring; intrinsicWidth = size; intrinsicHeight = size },
            ShapeDrawable(OvalShape()).apply { paint.color = fill; intrinsicWidth = size - 12; intrinsicHeight = size - 12 }
        )
    ).apply { setLayerInset(1, 6, 6, 6, 6) }
}

/**
 * osmdroid's global settings, set once. The tile cache lives in the app's
 * cache directory, never on shared storage, and the user agent names the
 * app as the tile server's usage policy asks.
 */
private fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.filesDir, "osmdroid").apply { mkdirs() }
        osmdroidTileCache = File(context.cacheDir, "osmdroid-tiles").apply { mkdirs() }
        isMapViewHardwareAccelerated = true
    }
}
