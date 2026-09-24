package com.ironlog.app.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ironlog.app.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home-screen widget: this week's sessions, the streak, and today's food
 * against its target, with a tap that opens the app.
 *
 * A widget runs outside the app's process and without its view models, so it
 * cannot read the database the way a screen does. Instead the app publishes a
 * handful of numbers to the preference store whenever they change, and the
 * widget reads those. That keeps the widget cheap to draw and means it never
 * holds a database open on a launcher's schedule.
 */
class IronLogWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun preferences(): DataStore<Preferences>
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = EntryPointAccessors.fromApplication(context, Deps::class.java).preferences().data.first()
        val workouts = prefs[WidgetSummary.KEY_WORKOUTS] ?: 0
        val goal = prefs[WidgetSummary.KEY_GOAL] ?: 0
        val streak = prefs[WidgetSummary.KEY_STREAK] ?: 0
        val kcal = prefs[WidgetSummary.KEY_KCAL] ?: 0
        val target = prefs[WidgetSummary.KEY_KCAL_TARGET] ?: 0

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(SLATE))
                    .cornerRadius(20.dp)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("IRONLOG", style = TextStyle(color = ColorProvider(FAINT), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stat(if (goal > 0) "$workouts/$goal" else "$workouts", "this week")
                    Spacer(GlanceModifier.width(18.dp))
                    Stat(if (streak > 0) "$streak" else "—", if (streak == 1) "week streak" else "weeks in a row")
                    Spacer(GlanceModifier.width(18.dp))
                    Stat(if (target > 0) "$kcal/$target" else "$kcal", "kcal today")
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Stat(value: String, label: String) {
        Column {
            Text(value, style = TextStyle(color = ColorProvider(CHALK), fontSize = 20.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = ColorProvider(DIM), fontSize = 11.sp))
        }
    }

    private companion object {
        val SLATE = Color(0xFF151A20)
        val CHALK = Color(0xFFF2F5F7)
        val DIM = Color(0xFFA7B4C0)
        val FAINT = Color(0xFF6B7987)
    }
}

class IronLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IronLogWidget()
}

/**
 * The numbers the widget shows, written by the app and read by the widget.
 *
 * Published rather than computed by the widget, so the widget never opens the
 * database and the numbers are exactly the ones the Home screen shows.
 */
@Singleton
class WidgetSummary @Inject constructor(
    private val preferences: DataStore<Preferences>
) {
    suspend fun publishTraining(context: Context, workoutsThisWeek: Int, weeklyGoal: Int, streakWeeks: Int) {
        preferences.edit {
            it[KEY_WORKOUTS] = workoutsThisWeek
            it[KEY_GOAL] = weeklyGoal
            it[KEY_STREAK] = streakWeeks
        }
        refresh(context)
    }

    suspend fun publishFood(context: Context, kcalToday: Int, kcalTarget: Int?) {
        preferences.edit {
            it[KEY_KCAL] = kcalToday
            it[KEY_KCAL_TARGET] = kcalTarget ?: 0
        }
        refresh(context)
    }

    /** Asks the launcher to place the widget, the way a user would from the widget picker. */
    fun requestPin(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, IronLogWidgetReceiver::class.java), null, null)
    }

    private suspend fun refresh(context: Context) {
        runCatching { IronLogWidget().updateAll(context) }
    }

    companion object {
        val KEY_WORKOUTS = intPreferencesKey("widget_workouts")
        val KEY_GOAL = intPreferencesKey("widget_goal")
        val KEY_STREAK = intPreferencesKey("widget_streak")
        val KEY_KCAL = intPreferencesKey("widget_kcal")
        val KEY_KCAL_TARGET = intPreferencesKey("widget_kcal_target")
    }
}
