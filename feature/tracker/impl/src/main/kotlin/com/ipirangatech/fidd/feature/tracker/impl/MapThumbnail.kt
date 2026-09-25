package com.ipirangatech.fidd.feature.tracker.impl

import android.content.Context
import android.content.pm.PackageManager

/** Reads the same key already configured for the Maps SDK meta-data — see AndroidManifest.xml —
 * so a static-map thumbnail URL can be built without duplicating the key anywhere. Shared by
 * HistoryScreen and the debug labeling screen. */
internal fun getMapsApiKey(context: Context): String? = try {
    val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    appInfo.metaData?.getString("com.google.android.geo.API_KEY")
} catch (e: Exception) {
    null
}

internal fun staticMapUrl(apiKey: String, latitude: Double, longitude: Double, zoom: Int = 17): String =
    "https://maps.googleapis.com/maps/api/staticmap" +
        "?center=$latitude,$longitude&zoom=$zoom&size=400x160&scale=2" +
        "&markers=color:red%7C$latitude,$longitude&key=$apiKey"
