/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled

@Composable
internal fun LibraryEmptyState(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    modifier: Modifier = Modifier,
    @StringRes actionLabelRes: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motion = MaterialTheme.motionScheme
    val badgeShape = MaterialShapes.Cookie9Sided.toShape()
    val badgeScale = remember { Animatable(if (animationsDisabled) 1f else 0.6f) }
    val badgeRotation = remember { Animatable(if (animationsDisabled) 0f else -40f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            badgeScale.snapTo(1f)
            badgeRotation.snapTo(0f)
        } else {
            launch { badgeScale.animateTo(1f, motion.slowSpatialSpec()) }
            launch { badgeRotation.animateTo(0f, motion.slowSpatialSpec()) }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .size(112.dp)
                        .graphicsLayer { rotationZ = badgeRotation.value }
                        .background(MaterialTheme.colorScheme.primaryContainer, badgeShape),
            )
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionLabelRes != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(actionLabelRes))
            }
        }
    }
}
