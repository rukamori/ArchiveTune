/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private const val LYRICS_MORPH_DAMPING_RATIO = 0.86f
private const val LYRICS_MORPH_STIFFNESS = 380f
private val LyricsMorphLargeShadow = 18.dp
private val LyricsMorphSmallShadow = 4.dp

internal val LocalLyricsArtworkMorph = staticCompositionLocalOf<LyricsArtworkMorphState?> { null }

@Stable
internal class LyricsArtworkMorphState(
    initiallyVisible: Boolean,
) {
    val progress = Animatable(if (initiallyVisible) 1f else 0f)

    private var visible by mutableStateOf(initiallyVisible)
    private var settled by mutableStateOf(true)

    private var sourceOwner: Any? = null
    var sourceBounds by mutableStateOf<Rect?>(null)
        private set
    var sourceCornerRadius by mutableStateOf(0.dp)
        private set
    var sourceImageUrl by mutableStateOf<String?>(null)
        private set

    private var targetOwner: Any? = null
    var targetBounds by mutableStateOf<Rect?>(null)
        private set
    var targetCornerRadius by mutableStateOf(0.dp)
        private set

    val isEngaged: Boolean by derivedStateOf { visible || progress.value > 0f }

    val isFlightActive: Boolean by derivedStateOf {
        isEngaged && !(visible && settled) && sourceBounds != null && targetBounds != null
    }

    suspend fun animateVisibility(
        visible: Boolean,
        animationsDisabled: Boolean,
    ) {
        this.visible = visible
        val target = if (visible) 1f else 0f
        if (animationsDisabled) {
            progress.snapTo(target)
            settled = true
            return
        }
        settled = false
        progress.animateTo(
            targetValue = target,
            animationSpec =
                spring(
                    dampingRatio = LYRICS_MORPH_DAMPING_RATIO,
                    stiffness = LYRICS_MORPH_STIFFNESS,
                ),
        )
        settled = true
    }

    internal fun report(
        owner: Any,
        role: LyricsArtworkMorphRole,
        bounds: Rect,
        cornerRadius: Dp,
        imageUrl: String?,
    ) {
        when (role) {
            LyricsArtworkMorphRole.Source -> {
                sourceOwner = owner
                sourceBounds = bounds
                sourceCornerRadius = cornerRadius
                sourceImageUrl = imageUrl
            }

            LyricsArtworkMorphRole.Target -> {
                targetOwner = owner
                targetBounds = bounds
                targetCornerRadius = cornerRadius
            }
        }
    }

    internal fun release(
        owner: Any,
        role: LyricsArtworkMorphRole,
    ) {
        when (role) {
            LyricsArtworkMorphRole.Source -> {
                if (sourceOwner !== owner) return
                sourceOwner = null
                sourceBounds = null
                sourceImageUrl = null
            }

            LyricsArtworkMorphRole.Target -> {
                if (targetOwner !== owner) return
                targetOwner = null
                targetBounds = null
            }
        }
    }
}

internal fun lyricsStagedProgress(
    progress: Float,
    start: Float,
    end: Float,
): Float {
    val linear = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(linear)
}

internal enum class LyricsArtworkMorphRole { Source, Target }

internal fun Modifier.lyricsArtworkMorphSource(
    morph: LyricsArtworkMorphState?,
    cornerRadius: Dp,
    imageUrl: String?,
    enabled: Boolean = true,
): Modifier =
    if (morph == null || !enabled) {
        this
    } else {
        this then LyricsArtworkMorphElement(morph, LyricsArtworkMorphRole.Source, cornerRadius, imageUrl)
    }

internal fun Modifier.lyricsArtworkMorphTarget(
    morph: LyricsArtworkMorphState?,
    cornerRadius: Dp,
): Modifier =
    if (morph == null) {
        this
    } else {
        this then LyricsArtworkMorphElement(morph, LyricsArtworkMorphRole.Target, cornerRadius, null)
    }

private data class LyricsArtworkMorphElement(
    val morph: LyricsArtworkMorphState,
    val role: LyricsArtworkMorphRole,
    val cornerRadius: Dp,
    val imageUrl: String?,
) : ModifierNodeElement<LyricsArtworkMorphNode>() {
    override fun create(): LyricsArtworkMorphNode = LyricsArtworkMorphNode(morph, role, cornerRadius, imageUrl)

    override fun update(node: LyricsArtworkMorphNode) {
        node.update(morph, role, cornerRadius, imageUrl)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "lyricsArtworkMorph"
        properties["role"] = role
        properties["cornerRadius"] = cornerRadius
    }
}

private class LyricsArtworkMorphNode(
    private var morph: LyricsArtworkMorphState,
    private var role: LyricsArtworkMorphRole,
    private var cornerRadius: Dp,
    private var imageUrl: String?,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode,
    DrawModifierNode {
    private var lastBounds: Rect? = null

    fun update(
        morph: LyricsArtworkMorphState,
        role: LyricsArtworkMorphRole,
        cornerRadius: Dp,
        imageUrl: String?,
    ) {
        if (morph !== this.morph || role != this.role) {
            this.morph.release(this, this.role)
        }
        this.morph = morph
        this.role = role
        this.cornerRadius = cornerRadius
        this.imageUrl = imageUrl
        lastBounds?.let { morph.report(this, role, it, cornerRadius, imageUrl) }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val bounds = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        lastBounds = bounds
        morph.report(this, role, bounds, cornerRadius, imageUrl)
    }

    override fun ContentDrawScope.draw() {
        if (!morph.isFlightActive) drawContent()
    }

    override fun onDetach() {
        lastBounds = null
        morph.release(this, role)
    }
}

@Composable
internal fun LyricsArtworkFlight(
    morph: LyricsArtworkMorphState,
    fallbackImageUrl: String?,
    originProvider: () -> Offset,
    modifier: Modifier = Modifier,
) {
    if (!morph.isFlightActive) return
    val source = morph.sourceBounds ?: return
    val target = morph.targetBounds ?: return
    val fraction = morph.progress.value.coerceIn(0f, 1f)
    val bounds = lerp(source, target, fraction).translate(-originProvider())
    if (bounds.width <= 0f || bounds.height <= 0f) return

    val density = LocalDensity.current
    val imageRequest = rememberOfflineArtworkImageRequest(morph.sourceImageUrl ?: fallbackImageUrl)
    val shape = RoundedCornerShape(lerp(morph.sourceCornerRadius, morph.targetCornerRadius, fraction))
    val shadowElevation = lerp(LyricsMorphLargeShadow, LyricsMorphSmallShadow, fraction)
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }

    Box(
        modifier =
            modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(width = width, height = height)
                .shadow(elevation = shadowElevation, shape = shape, clip = false)
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.24f)),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
