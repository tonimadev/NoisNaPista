package com.ipirangatech.fidd.feature.tracker.bridge

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Debug-only detection labeling list. Only ever pushed onto the back stack when
 * BuildConfig.DEBUG is true — see MainActivity. */
@Serializable
data object DebugNavKey : NavKey

/** Detail/labeling view for a single detection, reached from [DebugNavKey]'s list. */
@Serializable
data class DebugDetailNavKey(val potholeId: String) : NavKey
