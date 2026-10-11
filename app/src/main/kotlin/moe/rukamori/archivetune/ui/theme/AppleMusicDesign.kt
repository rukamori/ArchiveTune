/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple Music (iOS) 由来のデザイントークン。
 *
 * これは [ArchiveTuneTheme] を置き換えるものではなく、その上に重ねる補助レイヤー
 * である。既存のテーマはユーザーが選んだ色・フォント・動的カラーを尊重する必要が
 * あるため、こちらは「既定の見た目の方向づけ」にとどめ、実際の色面は
 * [AppleMusicSurface] 経由で解決する。
 *
 * 画面は [rememberAppleMusicDesign] でトークンを取得して使う。参照しない画面は
 * 従来どおり MaterialTheme を見るので、移行は画面単位で進められる。
 */

/** Apple Music のアクセント（柔らかいピンクレッド）。 */
val AppleMusicAccent = Color(0xFFFA2D48)

/** 深い黒からグレーへわたる Apple Music の面（サーフェス）階調。 */
@Immutable
data class AppleMusicSurface(
    /** ページの地。Apple Music は真っ黒に近い。 */
    val background: Color,
    /** フローティングバー / ミニプレイヤーなど、地の上に浮く面。 */
    val elevated: Color,
    /** elevated の一段上。カード・シートのコントラスト用。 */
    val elevatedHigh: Color,
    /** 区切り線。枠線を減らす方針のため低不透明度で使う。 */
    val hairline: Color,
    /** プライマリテキスト。 */
    val onBackground: Color,
    /** セカンダリテキスト。Apple は白を 60% で落とす。 */
    val secondary: Color,
    /** ターシャリテキスト。 */
    val tertiary: Color,
    /** フローティングバーのガラス面（半透明）。 */
    val glass: Color,
    /** ガラス面の縁に立てるハイライト。 */
    val glassEdge: Color,
)

private val DarkSurface =
    AppleMusicSurface(
        background = Color.Black,
        elevated = Color(0xFF1C1C1E),
        elevatedHigh = Color(0xFF2C2C2E),
        hairline = Color(0xFF38383A),
        onBackground = Color.White,
        secondary = Color(0xB3EBEBF5), // white 70%
        tertiary = Color(0x4DEBEBF5), // white 30%
        glass = Color(0x33FFFFFF), // white 20%
        glassEdge = Color(0x1FFFFFFF), // white 12%
    )

private val LightSurface =
    AppleMusicSurface(
        background = Color(0xFFF9F9F9),
        elevated = Color(0xFFFFFFFF),
        elevatedHigh = Color(0xFFF2F2F7),
        hairline = Color(0x333C3C43),
        onBackground = Color(0xFF000000),
        secondary = Color(0x993C3C43),
        tertiary = Color(0x4D3C3C43),
        glass = Color(0x59FFFFFF),
        glassEdge = Color(0x2EFFFFFF),
    )

/** Apple Music の余白スケール。4 の倍数で、画面全体で同じ値を使う。 */
@Immutable
data class AppleMusicSpacing(
    /** 極小。アイコンとテキストの詰め。 */
    val tiny: androidx.compose.ui.unit.Dp = 4.dp,
    /** 小。行内の要素間。 */
    val small: androidx.compose.ui.unit.Dp = 8.dp,
    /** 中。カード内のパディング。 */
    val medium: androidx.compose.ui.unit.Dp = 12.dp,
    /** 標準。画面の左右余白。Apple Music は 20 前後。 */
    val standard: androidx.compose.ui.unit.Dp = 20.dp,
    /** 大。セクション間の縦の間。 */
    val large: androidx.compose.ui.unit.Dp = 24.dp,
    /** 特大。見出しとコンテンツの間。 */
    val extraLarge: androidx.compose.ui.unit.Dp = 32.dp,
)

/** Apple Music の文字スケール。見出しは大きく、行間は詰めない。 */
@Immutable
data class AppleMusicTypography(
    /** Home / Library の巨大見出し。 */
    val largeTitle: TextStyle =
        TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    /** セクション見出し（Recently Played など）。 */
    val title: TextStyle =
        TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    /** カードや行の主テキスト。 */
    val headline: TextStyle =
        TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    /** 通常の本文。 */
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    /** アーティスト名などの副次テキスト。 */
    val subheadline: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    /** キャプション・タイム。 */
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** フローティングバー / ミニプレイヤー共通の形状。 */
@Immutable
data class AppleMusicShape(
    /** 浮遊バー・ミニプレイヤーの角。 */
    val floating: androidx.compose.ui.unit.Dp = 16.dp,
    /** アルバムアートの角。Apple Music はやや小さめ。 */
    val artwork: androidx.compose.ui.unit.Dp = 8.dp,
    /** シート・ダイアログ。 */
    val sheet: androidx.compose.ui.unit.Dp = 24.dp,
    /** ボタン。 */
    val button: androidx.compose.ui.unit.Dp = 12.dp,
)

/**
 * フローティングバーとミニプレイヤーの「ガラス面」の共通定数。
 *
 * ここを変えると両方同時に変わる。素の透明度を下げただけのバーにしないため、
 * 呼び出し側は必ず 地色 → ブラー → ティント の順で重ねること。
 */
@Immutable
data class AppleMusicGlass(
    /** 浮遊ナビのブラー半径。 */
    val navigationBlurRadius: androidx.compose.ui.unit.Dp = 26.dp,
    /**
     * ブラー非対応時の地色の不透明度。
     * ここが低いとラベルが読めないので、下限を 0.8 付近に置く。
     */
    val fallbackAlpha: Float = 0.86f,
    /** ミニプレイヤーのブラー半径。 */
    val miniPlayerBlurRadius: androidx.compose.ui.unit.Dp = 22.dp,
    /** ミニプレイヤーのブラー強度。地色を少し透かす程度に抑える。 */
    val miniPlayerAlpha: Float = 0.5f,
    /** 上端ハイライトと縁の太さ。Apple Music は極細のまま。 */
    val hairline: androidx.compose.ui.unit.Dp = 0.5.dp,
)

@Immutable
data class AppleMusicDesign(
    val surface: AppleMusicSurface,
    val accent: Color,
    val spacing: AppleMusicSpacing,
    val typography: AppleMusicTypography,
    val shape: AppleMusicShape,
    /** 浮遊ナビ / ミニプレイヤーのガラス面。 */
    val glass: AppleMusicGlass = AppleMusicGlass(),
)

val DarkAppleMusicDesign =
    AppleMusicDesign(
        surface = DarkSurface,
        accent = AppleMusicAccent,
        spacing = AppleMusicSpacing(),
        typography = AppleMusicTypography(),
        shape = AppleMusicShape(),
    )

val LightAppleMusicDesign =
    AppleMusicDesign(
        surface = LightSurface,
        accent = AppleMusicAccent,
        spacing = AppleMusicSpacing(),
        typography = AppleMusicTypography(),
        shape = AppleMusicShape(),
    )

/**
 * 現在のテーマに合わせたデザイントークンを返す。
 *
 * 暗いテーマなら [DarkAppleMusicDesign]、明るいテーマなら [LightAppleMusicDesign]。
 * 既存の [ArchiveTuneTheme] はユーザーが選んだ色を尊重する必要があるため、
 * トークン側はそちらに合わせて暗/明を解決する。
 */
@Composable
@ReadOnlyComposable
fun rememberAppleMusicDesign(): AppleMusicDesign =
    if (isSystemInDarkTheme()) DarkAppleMusicDesign else LightAppleMusicDesign

