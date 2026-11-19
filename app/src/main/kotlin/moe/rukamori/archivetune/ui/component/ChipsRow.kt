/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    icons: Map<E, Int> = emptyMap(),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            val isSelected = currentValue == value
            val iconRes = icons[value]

            FilterChip(
                selected = isSelected,
                onClick = { onValueUpdate(value) },
                label = { Text(label) },
                leadingIcon = {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.done),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    } else if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = containerColor,
                    ),
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}
