package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ModuleSourceTimeRow(
    position: String,
    duration: String,
    foreground: Color,
    modifier: Modifier = Modifier,
    timeColor: Color = foreground,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    centerContent: @Composable () -> Unit = { ModuleSourcePill(foreground = foreground) },
) {
    val measurer = rememberTextMeasurer()
    val width = maxOf(measurer.measure(position, style).size.width, measurer.measure(duration, style).size.width)
    val sideWidth = with(LocalDensity.current) { width.toDp() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(position, Modifier.width(sideWidth), color = timeColor, style = style, maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            centerContent()
        }
        Text(duration, Modifier.width(sideWidth), color = timeColor, style = style, textAlign = TextAlign.End, maxLines = 1)
    }
}
