/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.AodThumbnailShape

fun AodThumbnailShape.supportsArtworkGlowShadow(): Boolean =
    when (this) {
        AodThumbnailShape.FLOWER,
        AodThumbnailShape.CLOVER_4,
        AodThumbnailShape.COOKIE_6,
        AodThumbnailShape.COOKIE_9,
        AodThumbnailShape.SUNNY,
        AodThumbnailShape.SOFT_BURST,
        -> false

        else -> true
    }

@Composable
fun AodThumbnailShape.toComposeShape(
    cornerRadius: Float,
    startAngle: Int,
): Shape =
    when (this) {
        AodThumbnailShape.ROUNDED -> {
            remember(cornerRadius) {
                RoundedCornerShape(cornerRadius.coerceIn(0f, 128f).dp)
            }
        }

        AodThumbnailShape.SQUARE -> {
            MaterialShapes.Square.toShape(startAngle)
        }

        AodThumbnailShape.CIRCLE -> {
            MaterialShapes.Circle.toShape(startAngle)
        }

        AodThumbnailShape.PILL -> {
            MaterialShapes.Pill.toShape(startAngle)
        }

        AodThumbnailShape.ARCH -> {
            MaterialShapes.Arch.toShape(startAngle)
        }

        AodThumbnailShape.SLANTED -> {
            MaterialShapes.Slanted.toShape(startAngle)
        }

        AodThumbnailShape.DIAMOND -> {
            MaterialShapes.Diamond.toShape(startAngle)
        }

        AodThumbnailShape.PENTAGON -> {
            MaterialShapes.Pentagon.toShape(startAngle)
        }

        AodThumbnailShape.TRIANGLE -> {
            MaterialShapes.Triangle.toShape(startAngle)
        }

        AodThumbnailShape.HEART -> {
            MaterialShapes.Heart.toShape(startAngle)
        }

        AodThumbnailShape.FLOWER -> {
            MaterialShapes.Flower.toShape(startAngle)
        }

        AodThumbnailShape.CLOVER_4 -> {
            MaterialShapes.Clover4Leaf.toShape(startAngle)
        }

        AodThumbnailShape.COOKIE_6 -> {
            MaterialShapes.Cookie6Sided.toShape(startAngle)
        }

        AodThumbnailShape.COOKIE_9 -> {
            MaterialShapes.Cookie9Sided.toShape(startAngle)
        }

        AodThumbnailShape.SUNNY -> {
            MaterialShapes.Sunny.toShape(startAngle)
        }

        AodThumbnailShape.SOFT_BURST -> {
            MaterialShapes.SoftBurst.toShape(startAngle)
        }

        AodThumbnailShape.GHOSTISH -> {
            MaterialShapes.Ghostish.toShape(startAngle)
        }

        AodThumbnailShape.PIXEL_CIRCLE -> {
            MaterialShapes.PixelCircle.toShape(startAngle)
        }
    }
