package com.fpmislata.prestecs.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.data.api.dto.Estat

@Composable
fun Estat.label(): String = stringResource(
    when (this) {
        Estat.PRESTAT -> R.string.estat_prestat
        Estat.NO_RETORNAT -> R.string.estat_no_retornat
        Estat.RETORNAT -> R.string.estat_retornat
    },
)

private data class BadgeColors(val container: Color, val content: Color)

// Same meaning as the web badges: green lent today, red not returned, grey
// returned. Tones from Bootstrap's alert palette, which the web uses.
private val LightBadges = mapOf(
    Estat.PRESTAT to BadgeColors(container = Color(0xFFD1E7DD), content = Color(0xFF0F5132)),
    Estat.NO_RETORNAT to BadgeColors(container = Color(0xFFF8D7DA), content = Color(0xFF842029)),
    Estat.RETORNAT to BadgeColors(container = Color(0xFFE2E3E5), content = Color(0xFF41464B)),
)

private val DarkBadges = mapOf(
    Estat.PRESTAT to BadgeColors(container = Color(0xFF1E3A2B), content = Color(0xFFA3CFBB)),
    Estat.NO_RETORNAT to BadgeColors(container = Color(0xFF4A1F24), content = Color(0xFFF1AEB5)),
    Estat.RETORNAT to BadgeColors(container = Color(0xFF2B2F33), content = Color(0xFFC4C8CB)),
)

@Composable
fun EstatBadge(estat: Estat, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = (if (dark) DarkBadges else LightBadges).getValue(estat)
    Surface(
        modifier = modifier,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = estat.label(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
