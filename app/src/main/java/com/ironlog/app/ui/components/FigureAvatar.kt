package com.ironlog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.ironlog.app.data.repository.FIGURE_AVATAR_PREFIX
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.Slate700

/**
 * The built-in avatars, for anyone who would rather not put their face in the
 * app -- which, in a gym, is most people.
 *
 * Stored in the same field as a photo, distinguished by a prefix rather than a
 * second column: a user has one avatar, and modelling it as two nullable fields
 * invites the state where both are set and neither wins. The prefix also means
 * no schema migration for what is a presentation choice.
 */
object FigureAvatars {

    /** Marks an avatar value as a built-in figure rather than a file path. */
    const val PREFIX = FIGURE_AVATAR_PREFIX

    data class Figure(val id: String, val label: String, val icon: ImageVector)

    val ALL = listOf(
        Figure("lifter", "Lifter", Icons.Default.FitnessCenter),
        Figure("runner", "Runner", Icons.AutoMirrored.Filled.DirectionsRun),
        Figure("fighter", "Fighter", Icons.Default.SportsMartialArts),
        Figure("flame", "Streak", Icons.Default.LocalFireDepartment),
        Figure("bolt", "Bolt", Icons.Default.Bolt),
        Figure("crown", "Champion", Icons.Default.WorkspacePremium),
        Figure("calm", "Recovery", Icons.Default.SelfImprovement)
    )

    fun valueFor(figure: Figure) = PREFIX + figure.id

    fun isFigure(value: String?) = value != null && value.startsWith(PREFIX)

    fun iconFor(value: String?): ImageVector? {
        if (!isFigure(value)) return null
        val id = value!!.removePrefix(PREFIX)
        return ALL.firstOrNull { it.id == id }?.icon
    }
}

/**
 * Draws whichever kind of avatar the value names: a built-in figure, a photo
 * the user took, or nothing.
 */
@Composable
fun AvatarContent(
    value: String?,
    modifier: Modifier = Modifier,
    tint: Color = Chalk,
    background: Color = Slate700
) {
    val figure = FigureAvatars.iconFor(value)
    when {
        figure != null -> Box(
            modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                figure,
                contentDescription = "Your avatar",
                tint = tint,
                modifier = Modifier.fillMaxSize(FIGURE_INSET)
            )
        }

        value != null -> coil.compose.AsyncImage(
            model = java.io.File(value),
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}

/** Leaves a ring of background around the emblem rather than filling the circle. */
private const val FIGURE_INSET = 0.55f
