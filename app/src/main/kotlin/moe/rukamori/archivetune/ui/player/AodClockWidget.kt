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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodClockStyle

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
    var formattedHours by remember { mutableStateOf("") }
    var formattedMinutes by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }

    fun updateClock() {
        val now = Date()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val timePattern = when (clockStyle) {
            AodClockStyle.BOLD_DIGITAL -> if (is24Hour) "HH:mm" else "h:mm a"
            AodClockStyle.MINIMAL -> if (is24Hour) "HH:mm" else "h:mm a"
            AodClockStyle.ELEGANT_THIN -> if (is24Hour) "HH:mm" else "h:mm a"
            AodClockStyle.PIXEL_STACKED -> if (is24Hour) "HH:mm" else "h:mm"
        }
        formattedTime = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        formattedDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
        formattedHours = SimpleDateFormat(if (is24Hour) "HH" else "hh", Locale.getDefault()).format(now)
        formattedMinutes = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    }

    LaunchedEffect(clockStyle) {
        while (true) {
            updateClock()
            delay(1000L)
        }
    }

    DisposableEffect(context, clockStyle) {
        updateClock()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        updateClock()
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
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
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val registeredIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
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
        if (showClock) {
            if (clockStyle == AodClockStyle.PIXEL_STACKED) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formattedHours,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 60.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formattedMinutes,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 60.sp,
                        color = Color.White.copy(alpha = 0.80f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (formattedTime.isNotBlank()) {
                val (fontSize, fontWeight, fontFamily) = when (clockStyle) {
                    AodClockStyle.BOLD_DIGITAL -> Triple(44.sp, FontWeight.Black, FontFamily.Monospace)
                    AodClockStyle.MINIMAL -> Triple(38.sp, FontWeight.Medium, FontFamily.Default)
                    AodClockStyle.ELEGANT_THIN -> Triple(48.sp, FontWeight.ExtraLight, FontFamily.SansSerif)
                    AodClockStyle.PIXEL_STACKED -> Triple(44.sp, FontWeight.Bold, FontFamily.Default)
                }

                Text(
                    text = formattedTime,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

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
                    text = stringResource(R.string.aod_battery_percent, batteryLevel),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}
