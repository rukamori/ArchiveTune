/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarDialog(
    onDismissRequest: () -> Unit,
    onSupport: () -> Unit,
    onLater: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            contentColor = AlertDialogDefaults.textContentColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.star),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .padding(12.dp)
                                    .size(AlertDialogDefaults.IconSize),
                        )
                    }

                    Text(
                        text = stringResource(R.string.support_development_title),
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = AlertDialogDefaults.titleContentColor,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.support_development_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    val supportItemColors =
                        ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                            colors = supportItemColors,
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                            leadingContent = {
                                Icon(painterResource(R.drawable.coffee), contentDescription = null)
                            },
                            supportingContent = {
                                Text(stringResource(R.string.onboarding_community_donate_desc))
                            },
                        ) {
                            Text(stringResource(R.string.about_content_desc_donate))
                        }

                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                            colors = supportItemColors,
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                            leadingContent = {
                                Icon(painterResource(R.drawable.github), contentDescription = null)
                            },
                            supportingContent = {
                                Text(stringResource(R.string.onboarding_community_github_desc))
                            },
                        ) {
                            Text(stringResource(R.string.about_content_desc_github))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            uriHandler.openUri("https://miuka.moe/donate")
                            onSupport()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.coffee),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.support_development_donate),
                            textAlign = TextAlign.Center,
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onLater,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.later), textAlign = TextAlign.Center)
                        }

                        FilledTonalButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/rukamori/ArchiveTune")
                                onSupport()
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.github),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.support_development_star),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
