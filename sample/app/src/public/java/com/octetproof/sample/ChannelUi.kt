package com.octetproof.sample

import android.content.Context
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.octetproof.sdk.logging.LogLevel

// This app has the Generate and Verify tabs only, so these hooks do nothing.

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun NavGraphBuilder.channelRoutes(vm: SampleViewModel, nav: NavController) {}

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
@Composable
fun RowScope.ChannelTabs(vm: SampleViewModel, route: String?, nav: NavController) {}

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun LazyListScope.channelGenerateExtras(vm: SampleViewModel) {}

@Suppress("UNUSED_PARAMETER")
fun Modifier.channelTitleTap(nav: NavController): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun channelLogLevel(context: Context): LogLevel = LogLevel.INFO

val channelGlossary: Map<String, String> = emptyMap()

/** Usage reporting goes to the SDK's default Credit endpoint. */
@Suppress("UNUSED_PARAMETER")
fun channelCreditServiceUrl(activationServerUrl: String): String? = null
