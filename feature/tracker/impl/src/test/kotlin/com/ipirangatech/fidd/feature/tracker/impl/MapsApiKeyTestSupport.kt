package com.ipirangatech.fidd.feature.tracker.impl

import android.app.Application
import android.os.Bundle
import org.robolectric.Shadows.shadowOf

/** Library modules have no Maps key in their manifest; tests that need the thumbnails inject one. */
fun setMapsApiKey(
    app: Application,
    key: String = "test-key",
) {
    shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).applicationInfo!!.metaData =
        Bundle().apply { putString("com.google.android.geo.API_KEY", key) }
}
