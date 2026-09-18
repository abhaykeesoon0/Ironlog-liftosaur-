package com.ironlog.app.ui.screens.exercises

import com.ironlog.app.data.entity.RecordType
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.ui.format.formatVolume
import com.ironlog.app.ui.format.formatWeight

/**
 * A record's headline value plus the set that produced it, e.g. "112.5 kg"
 * with context "from 100 kg × 4". Shared by Home, Progress and exercise detail.
 */
fun formatRecordValue(type: RecordType, value: Double, unit: UnitSystem): String = when (type) {
    RecordType.HEAVIEST_WEIGHT, RecordType.BEST_ESTIMATED_1RM -> unit.formatWeight(value)
    RecordType.BEST_SET_VOLUME, RecordType.BEST_SESSION_VOLUME -> unit.formatVolume(value)
    RecordType.MOST_REPS -> "${value.toInt()} reps"
}

fun formatRecordContext(type: RecordType, weightKg: Double?, reps: Int?, unit: UnitSystem): String? {
    if (type == RecordType.BEST_SESSION_VOLUME) return null
    return when {
        weightKg != null && weightKg > 0 && reps != null -> "${unit.formatWeight(weightKg)} × $reps"
        reps != null -> "$reps reps"
        else -> null
    }
}
