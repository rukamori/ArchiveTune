/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeBackground(navController: NavController) {
    val context = LocalContext.current

    val (imageUri, onImageUriChange) = rememberPreference(PlayerCustomImageUriKey, "")
    val (blur, onBlurChange) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (contrast, onContrastChange) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (brightness, onBrightnessChange) = rememberPreference(PlayerCustomBrightnessKey, 1f)

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                onImageUriChange(uri.toString())
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.customize_background_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()

        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = SettingsDimensions.ScreenBottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val heightScale = 1.4f
            val playerPreviewHeight = (screenHeightDp * (1518f / 2400f) * heightScale).dp
            val lyricsPreviewHeight = (screenHeightDp * (1386f / 2400f) * heightScale).dp

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(playerPreviewHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUri.isNotBlank()) {
                    val t = (1f - contrast) * 128f + (brightness - 1f) * 255f
                    val cm =
                        ColorMatrix(
                            floatArrayOf(
                                contrast,
                                0f,
                                0f,
                                0f,
                                t,
                                0f,
                                contrast,
                                0f,
                                0f,
                                t,
                                0f,
                                0f,
                                contrast,
                                0f,
                                t,
                                0f,
                                0f,
                                0f,
                                1f,
                                0f,
                            ),
                        )
                    AsyncImage(
                        model = Uri.parse(imageUri),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .blur(blur.dp),
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(cm),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                    )
                    Image(
                        painter = painterResource(R.drawable.player_preview),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.image), contentDescription = null)
                        Text(stringResource(R.string.add_image))
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(lyricsPreviewHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUri.isNotBlank()) {
                    val t2 = (1f - contrast) * 128f + (brightness - 1f) * 255f
                    val cm2 =
                        ColorMatrix(
                            floatArrayOf(
                                contrast,
                                0f,
                                0f,
                                0f,
                                t2,
                                0f,
                                contrast,
                                0f,
                                0f,
                                t2,
                                0f,
                                0f,
                                contrast,
                                0f,
                                t2,
                                0f,
                                0f,
                                0f,
                                1f,
                                0f,
                            ),
                        )
                    AsyncImage(
                        model = Uri.parse(imageUri),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .blur(blur.dp),
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(cm2),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                    )
                    Image(
                        painter = painterResource(R.drawable.lyrics_preview),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.image), contentDescription = null)
                        Text(stringResource(R.string.add_image))
                    }
                }
            }
            Button(
                onClick = { launcher.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.add_image))
            }

            Text(stringResource(R.string.blur))
            Slider(
                value = blur,
                onValueChange = onBlurChange,
                valueRange = 0f..50f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.contrast))
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..2f,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.brightness))
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.5f..2f,
                modifier = Modifier.fillMaxWidth(),
            )

            FilledTonalButton(
                onClick = {
                    Toast.makeText(context, context.getString(R.string.save), Toast.LENGTH_SHORT).show()
                    navController.navigateUp()
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
