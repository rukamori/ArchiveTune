/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 新UIのルートテーマ。
 *
 * `AppleTokens.kt` のトークンをコンポーネントローカルへ供給しつつ、
 * Material3 の [MaterialTheme] にも同じ値を流し込むことで、
 * Material3 の部品（リップル・スイッチ等）も同じ質感で動くようにしている。
 *
 * 本アプリはダーク中心だが、ユーザーが明るいテーマを選んだ場合は
 * [darkTheme] に `false` を渡す。
 */
@Composable
fun AppleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkApplePalette else LightApplePalette
    val type = appleTypeFor(dark = darkTheme)

    CompositionLocalProvider(
        LocalApplePalette provides palette,
        LocalAppleType provides type,
        LocalAppleMetrics provides AppleMetricsDefault,
        LocalAppleIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = appleColorScheme(palette, darkTheme),
            typography = appleTypography(type),
            shapes = appleShapes(),
            content = content,
        )
    }
}

/** [ApplePalette] を Material3 の [ColorScheme] へ写す。 */
internal fun appleColorScheme(p: ApplePalette, dark: Boolean): ColorScheme {
    val accent = p.accent
    val tint = accent.copy(alpha = 0.20f)
    return if (dark) {
        darkAppleScheme(p, accent, tint)
    } else {
        lightAppleScheme(p, accent, tint)
    }
}

private fun darkAppleScheme(p: ApplePalette, accent: Color, tint: Color): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = p.onAccent,
        primaryContainer = tint,
        onPrimaryContainer = p.onAccent,
        inversePrimary = accent,
        secondary = p.labelSecondary,
        onSecondary = p.background,
        secondaryContainer = p.surfaceRaised,
        onSecondaryContainer = p.onSurface,
        tertiary = p.labelSecondary,
        onTertiary = p.background,
        tertiaryContainer = p.surfaceRaised,
        onTertiaryContainer = p.onSurface,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.labelSecondary,
        surfaceTint = accent,
        inverseSurface = p.onBackground,
        inverseOnSurface = p.background,
        error = p.error,
        onError = Color.White,
        errorContainer = p.error.copy(alpha = 0.20f),
        onErrorContainer = p.error,
        outline = p.separator,
        outlineVariant = p.separator,
        scrim = p.shadow,
        surfaceBright = p.surfaceRaised,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerHighest = p.surfaceRaised,
        surfaceContainerLow = p.surfaceSunken,
        surfaceContainerLowest = p.background,
        surfaceDim = p.background,
        primaryFixed = accent,
        primaryFixedDim = accent.copy(alpha = 0.80f),
        onPrimaryFixed = p.onAccent,
        onPrimaryFixedVariant = p.onAccent,
        secondaryFixed = p.surfaceRaised,
        secondaryFixedDim = p.surface,
        onSecondaryFixed = p.onSurface,
        onSecondaryFixedVariant = p.labelSecondary,
        tertiaryFixed = p.surfaceRaised,
        tertiaryFixedDim = p.surface,
        onTertiaryFixed = p.onSurface,
        onTertiaryFixedVariant = p.labelSecondary,
    )

private fun lightAppleScheme(p: ApplePalette, accent: Color, tint: Color): ColorScheme =
    lightColorScheme(
        primary = accent,
        onPrimary = p.onAccent,
        primaryContainer = tint,
        onPrimaryContainer = p.onAccent,
        inversePrimary = accent,
        secondary = p.labelSecondary,
        onSecondary = p.background,
        secondaryContainer = p.surfaceRaised,
        onSecondaryContainer = p.onSurface,
        tertiary = p.labelSecondary,
        onTertiary = p.background,
        tertiaryContainer = p.surfaceRaised,
        onTertiaryContainer = p.onSurface,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.labelSecondary,
        surfaceTint = accent,
        inverseSurface = p.onBackground,
        inverseOnSurface = p.background,
        error = p.error,
        onError = Color.White,
        errorContainer = p.error.copy(alpha = 0.16f),
        onErrorContainer = p.error,
        outline = p.separator,
        outlineVariant = p.separator,
        scrim = p.shadow,
        surfaceBright = p.surfaceRaised,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerHighest = p.surfaceRaised,
        surfaceContainerLow = p.surfaceSunken,
        surfaceContainerLowest = p.background,
        surfaceDim = p.surfaceSunken,
        primaryFixed = accent,
        primaryFixedDim = accent,
        onPrimaryFixed = p.onAccent,
        onPrimaryFixedVariant = p.onAccent,
        secondaryFixed = p.surfaceRaised,
        secondaryFixedDim = p.surface,
        onSecondaryFixed = p.onSurface,
        onSecondaryFixedVariant = p.labelSecondary,
        tertiaryFixed = p.surfaceRaised,
        tertiaryFixedDim = p.surface,
        onTertiaryFixed = p.onSurface,
        onTertiaryFixedVariant = p.labelSecondary,
    )

/** [AppleType] を Material3 の [Typography] へ写す。 */
private fun appleTypography(t: AppleType): Typography =
    Typography(
        displayLarge = t.largeTitle,
        displayMedium = t.title1,
        displaySmall = t.title2,
        headlineLarge = t.title1,
        headlineMedium = t.title2,
        headlineSmall = t.title3,
        titleLarge = t.title3,
        titleMedium = t.headline,
        titleSmall = t.subhead,
        bodyLarge = t.body,
        bodyMedium = t.callout,
        bodySmall = t.footnote,
        labelLarge = t.headline,
        labelMedium = t.subhead,
        labelSmall = t.caption,
    )

/** [AppleMetrics] を Material3 の [Shapes] へ写す。 */
private fun appleShapes(): Shapes {
    val m = AppleMetricsDefault
    return Shapes(
        extraSmall = RoundedCornerShape((m.artworkCorner / 2).dp),
        small = RoundedCornerShape(m.artworkCorner.dp),
        medium = RoundedCornerShape(m.cardCorner.dp),
        large = RoundedCornerShape((m.cardCorner + 4).dp),
        extraLarge = RoundedCornerShape((m.cardCorner + 8).dp),
    )
}
