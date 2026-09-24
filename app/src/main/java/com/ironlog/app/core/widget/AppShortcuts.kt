package com.ironlog.app.core.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ironlog.app.MainActivity
import com.ironlog.app.R

/**
 * The launcher shortcuts: long-press the icon and go straight to training,
 * the food log or the coach.
 *
 * Published from code rather than a static XML because the debug and
 * release builds carry different application ids and a static shortcut has
 * to name one. Each shortcut opens the app on a route through the same
 * deep-link extra a notification uses, so the app lock still comes first.
 */
object AppShortcuts {

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "train", "Train", "Start a workout", R.drawable.ic_shortcut_train, "routines"),
            shortcut(context, "food", "Log food", "Add to today's food log", R.drawable.ic_shortcut_food, "nutrition"),
            shortcut(context, "coach", "Coach", "Ask about training", R.drawable.ic_shortcut_coach, "coach")
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, short: String, long: String, icon: Int, route: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(MainActivity.EXTRA_DEEP_LINK_ROUTE, route)
            )
            .build()
}
