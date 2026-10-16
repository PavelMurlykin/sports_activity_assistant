package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.ui.theme.Pine
import com.pamurlykin.sportsactivityassistant.ui.theme.Sky

data class SportVisual(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
    @DrawableRes val artwork: Int? = null,
)

fun sportVisual(slug: String): SportVisual =
    SportEditors.visual(slug) ?: SportVisual(Icons.Rounded.FitnessCenter, Pine, Sky)

@Composable
fun SportBadge(
    slug: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val visual = sportVisual(slug)
    val artwork = visual.artwork
    if (artwork != null) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            modifier = modifier.size(size),
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .background(visual.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.tint,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}
