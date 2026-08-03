/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.constants.AodClockStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AodClockWidget(
    showClock: Boolean,
    clockStyle: AodClockStyle,
    showBattery: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!showClock && !showBattery) return

    val context = LocalContext.current
    var formattedTime by remember { mutableStateOf("") }
    var formattedDate by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(clockStyle) {
        val timeFormatPattern = when (clockStyle) {
            AodClockStyle.BOLD_DIGITAL -> "HH:mm"
            AodClockStyle.MINIMAL -> "h:mm a"
            AodClockStyle.ELEGANT_THIN -> "HH:mm"
        }
        val timeFormatter = SimpleDateFormat(timeFormatPattern, Locale.getDefault())
        val dateFormatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        while (true) {
            val now = Date()
            formattedTime = timeFormatter.format(now)
            formattedDate = dateFormatter.format(now)
            delay(1000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                    }
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val registeredIntent = context.registerReceiver(receiver, filter)
        if (registeredIntent != null) {
            val level = registeredIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = registeredIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
            val status = registeredIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        if (showClock && formattedTime.isNotBlank()) {
            val (fontSize, fontWeight, fontFamily) = when (clockStyle) {
                AodClockStyle.BOLD_DIGITAL -> Triple(44.sp, FontWeight.Black, FontFamily.Monospace)
                AodClockStyle.MINIMAL -> Triple(38.sp, FontWeight.Medium, FontFamily.Default)
                AodClockStyle.ELEGANT_THIN -> Triple(48.sp, FontWeight.ExtraLight, FontFamily.SansSerif)
            }

            Text(
                text = formattedTime,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            if (formattedDate.isNotBlank()) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (showBattery && batteryLevel >= 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = when {
                        isCharging -> Icons.Default.BatteryChargingFull
                        batteryLevel >= 90 -> Icons.Default.BatteryFull
                        else -> Icons.Default.BatteryStd
                    },
                    contentDescription = null,
                    tint = if (isCharging) accentColor else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$batteryLevel%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}
