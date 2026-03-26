package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.ui.theme.Clay
import com.pamurlykin.sportsactivityassistant.ui.theme.Mist
import com.pamurlykin.sportsactivityassistant.ui.theme.Pine
import com.pamurlykin.sportsactivityassistant.ui.theme.Sky

data class SportVisual(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
)

fun sportVisual(slug: String): SportVisual {
    return when (slug) {
        "football" -> SportVisual(Icons.Rounded.SportsSoccer, Pine, Sky)
        "climbing" -> SportVisual(Icons.Rounded.Terrain, Mist, Clay)
        else -> SportVisual(Icons.Rounded.SportsSoccer, Pine, Sky)
    }
}

@Composable
fun SportBadge(
    slug: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val visual = sportVisual(slug)
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
