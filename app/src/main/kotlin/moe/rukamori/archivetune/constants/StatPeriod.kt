/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import moe.rukamori.archivetune.ui.screens.OptionStats
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class StatPeriod {
    HOURS_48,
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            HOURS_48 -> {
                LocalDateTime
                    .now()
                    .minusDays(2)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            WEEK_1 -> {
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_1 -> {
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_3 -> {
                LocalDateTime
                    .now()
                    .minusMonths(3)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            MONTH_6 -> {
                LocalDateTime
                    .now()
                    .minusMonths(6)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            YEAR_1 -> {
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }

            ALL -> {
                0
            }
        }
}

fun statToPeriod(
    selection: OptionStats,
    test: Int,
): Long =
    when (selection) {
        OptionStats.WEEKS -> {
            LocalDateTime
                .now()
                .minusWeeks(test.toLong())
                .minusDays(1)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.MONTHS -> {
            LocalDateTime
                .now()
                .withDayOfMonth(1)
                .minusMonths(test.toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.YEARS -> {
            LocalDateTime
                .now()
                .withDayOfMonth(1)
                .withMonth(1)
                .minusYears(test.toLong())
                .toInstant(
                    ZoneOffset.UTC,
                ).toEpochMilli()
        }

        OptionStats.CONTINUOUS -> {
            val index = if (test > StatPeriod.entries.size) 0 else test
            StatPeriod.entries[index].toTimeMillis()
        }
    }
