/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 新UIのデザイントークン。
 *
 * Apple Music (iOS) の**質感**を参考にしつつ、Apple の公式アセット
 * （ロゴ・アイコン・SF Pro フォント等）は一切使用しない。
 * アイデンティティは [ApplePalette.accent] を含む本アプリ独自のものである。
 *
 * 数値は iOS の Dynamic Type（既定サイズ）とレイアウトガターを手本に割り付け、
 * フォントはシステムフォントで字送り・行送りを調整して近づけている。
 *
 * 画面は必ずこのトークン経由で色・余白・角丸を参照すること。
 * ハードコードされた色値を Composable 内に書かないこと。
 *
 * 過去にリポジトリへ同梱されていた `res/font/sfprodisplaybold.ttf` は
 * Apple の資産であるため、新UIでは参照しない。
 */

/* -------------------------------------------------------------------------- */
/*  Color                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * 前面・背景のカラーパレット。
 *
 * [dark] は本アプリの主軸であるダークテーマ。明るいテーマは識別用に最小限だけ持つ。
 */
@Immutable
data class ApplePalette(
    /** ブランドのアクセント（柔らかいピンクレッド）。 */
    val accent: Color,
    /** アクセントの上に載せる文字色。 */
    val onAccent: Color,
    /** 画面の地色。 */
    val background: Color,
    /** 地色の上の文字色。 */
    val onBackground: Color,
    /** 地色の上の二次文字色（説明・メタ情報）。 */
    val labelSecondary: Color,
    /** 地色の上の三次文字色（もっとも控えめ）。 */
    val labelTertiary: Color,
    /** リスト行・カードの面。 */
    val surface: Color,
    /** 面の上の文字色。 */
    val onSurface: Color,
    /** 面より一段明るい（浮いた）面。 */
    val surfaceRaised: Color,
    /** 面より一段暗い（くぼんだ）面。 */
    val surfaceSunken: Color,
    /** 区切り線。 */
    val separator: Color,
    /** 影を落とすための純黒。 */
    val shadow: Color,
    /** 読み込み中など、未確定の面を表す色。 */
    val skeleton: Color,
    /** エラー色。 */
    val error: Color,
    /** 各種フィル（「すべて」チップなど）の下地。 */
    val fill: Color,
    /** フィルの下地の上に載せる文字色。 */
    val onFill: Color,
)

internal val DarkApplePalette = ApplePalette(
    accent = Color(0xFFFA2D48),
    onAccent = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5),
    labelTertiary = Color(0x4DEBEBF5),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFF2C2C2E),
    surfaceSunken = Color(0xFF111113),
    separator = Color(0x24FFFFFF),
    shadow = Color(0xFF000000),
    skeleton = Color(0xFF1C1C1E),
    error = Color(0xFFFF453A),
    fill = Color(0x14FFFFFF),
    onFill = Color(0xE5FFFFFF),
)

internal val LightApplePalette = ApplePalette(
    accent = Color(0xFFE01E3C),
    onAccent = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43),
    labelTertiary = Color(0x4D3C3C43),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE5E5EA),
    separator = Color(0x1F000000),
    shadow = Color(0xFF000000),
    skeleton = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    fill = Color(0x14000000),
    onFill = Color(0xE5000000),
)

/* -------------------------------------------------------------------------- */
/*  Typography                                                                */
/* -------------------------------------------------------------------------- */

/**
 * iOS の Dynamic Type（既定サイズ）を手本にしたタイポグラフィ。
 *
 * 行送りは SF Pro の比（約 1.21〜1.24）に合わせ、字送りはシステムフォントの
 * わずかに広い字面を詰めて Apple Music に近い密度にしている。
 */
@Immutable
data class AppleType(
    /** 画面いちばん大きな見出し（Home の "ホーム" 等）。34sp */
    val largeTitle: TextStyle,
    /** セクションを跨ぐ大見出し。28sp */
    val title1: TextStyle,
    /** セクション見出し。22sp */
    val title2: TextStyle,
    /** カードのタイトル・カード内の小見出し。20sp */
    val title3: TextStyle,
    /** 強調された本文（曲名・見出し級）。17sp semibold */
    val headline: TextStyle,
    /** 標準本文。17sp */
    val body: TextStyle,
    /** 控えめな本文。16sp */
    val callout: TextStyle,
    /** 補足（アーティスト名など）。15sp */
    val subhead: TextStyle,
    /** 注釈。13sp */
    val footnote: TextStyle,
    /** キャプション。12sp */
    val caption: TextStyle,
    /** 最小のキャプション（タイムスタンプ等）。11sp */
    val micro: TextStyle,
)

private val SystemFont = FontFamily.Default

internal fun appleTypeFor(dark: Boolean): AppleType {
    val base = if (dark) Color.White else Color.Black
    fun style(
        size: Int,
        weight: FontWeight,
        line: Int,
        tracking: Double,
    ) = TextStyle(
        fontFamily = SystemFont,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
        color = base,
    )
    return AppleType(
        largeTitle = style(34, FontWeight.Bold, 41, -0.40),
        title1 = style(28, FontWeight.Bold, 34, -0.32),
        title2 = style(22, FontWeight.Bold, 28, -0.24),
        title3 = style(20, FontWeight.SemiBold, 25, -0.20),
        headline = style(17, FontWeight.SemiBold, 22, -0.14),
        body = style(17, FontWeight.Normal, 22, -0.10),
        callout = style(16, FontWeight.Normal, 21, -0.08),
        subhead = style(15, FontWeight.Normal, 20, -0.06),
        footnote = style(13, FontWeight.Normal, 18, -0.04),
        caption = style(12, FontWeight.Normal, 16, -0.02),
        micro = style(11, FontWeight.Normal, 13, 0.0),
    )
}

/* -------------------------------------------------------------------------- */
/*  Metrics                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * 余白・角丸・ガラス表現の寸法。
 *
 * iOS の画面ガター（16pt）を基準に、Apple Music の緩やかな余白取りに合わせている。
 */
@Immutable
data class AppleMetrics(
    /** 極小の間隔（アイコンとラベル等）。4dp */
    val xs: Int,
    /** 小さな間隔。8dp */
    val sm: Int,
    /** 中の間隔（行の内側）。12dp */
    val md: Int,
    /** 画面ガター（左右の基本余白）。16dp */
    val gutter: Int,
    /** セクション間。24dp */
    val section: Int,
    /** セクションを大きく跨ぐ間隔。32dp */
    val block: Int,
    /** アルバムアートの角丸。8dp */
    val artworkCorner: Int,
    /** カードの角丸。12dp */
    val cardCorner: Int,
    /** 丸ボタン（再生ボタン等）の角丸。 */
    val roundCorner: Int,
    /** 一覧行の高さ。56dp */
    val rowHeight: Int,
    /** ボトムナビ（インセット除く）の高さ。56dp */
    val navBarHeight: Int,
    /** ミニプレイヤーの高さ。64dp */
    val miniPlayerHeight: Int,
    /** ガラスのブラー半径。24dp */
    val glassBlur: Int,
    /** ガラスの下地に重ねるティントの不透明度。 */
    val glassTintAlpha: Float,
    /** ガラス上端のハイライトの不透明度。 */
    val glassHighlightAlpha: Float,
    /** ガラス縁のヘアラインの太さ。0.5dp */
    val hairline: Float,
)

internal val AppleMetricsDefault = AppleMetrics(
    xs = 4,
    sm = 8,
    md = 12,
    gutter = 16,
    section = 24,
    block = 32,
    artworkCorner = 8,
    cardCorner = 12,
    roundCorner = 999,
    rowHeight = 56,
    navBarHeight = 56,
    miniPlayerHeight = 64,
    glassBlur = 30,
    glassTintAlpha = 0.07f,
    glassHighlightAlpha = 0.10f,
    hairline = 0.5f,
)

/* -------------------------------------------------------------------------- */
/*  CompositionLocals                                                         */
/* -------------------------------------------------------------------------- */

/** 現在の [ApplePalette]。[AppleTheme] で供給される。 */
val LocalApplePalette = staticCompositionLocalOf { DarkApplePalette }

/** 現在の [AppleType]。[AppleTheme] で供給される。 */
val LocalAppleType = staticCompositionLocalOf { appleTypeFor(dark = true) }

/** 現在の [AppleMetrics]。[AppleTheme] で供給される。 */
val LocalAppleMetrics = staticCompositionLocalOf { AppleMetricsDefault }

/** 暗いテーマかどうか。 */
val LocalAppleIsDark = staticCompositionLocalOf { true }

/** 現在のパレット。 */
@Composable
@ReadOnlyComposable
fun applePalette(): ApplePalette = LocalApplePalette.current

/** 現在のタイポグラフィ。 */
@Composable
@ReadOnlyComposable
fun appleType(): AppleType = LocalAppleType.current

/** 現在のメトリクス。 */
@Composable
@ReadOnlyComposable
fun appleMetrics(): AppleMetrics = LocalAppleMetrics.current

/** ダークテーマなら true。 */
@Composable
@ReadOnlyComposable
fun appleIsDark(): Boolean = LocalAppleIsDark.current

/**
 * システムのダーク設定と [forceDark] を合わせた最終的なダーク判定。
 * 本アプリはダーク中心なので、未指定時は常にダークとして扱う。
 */
@Composable
@ReadOnlyComposable
fun resolveAppleDark(forceDark: Boolean? = null): Boolean =
    forceDark ?: isSystemInDarkTheme()
