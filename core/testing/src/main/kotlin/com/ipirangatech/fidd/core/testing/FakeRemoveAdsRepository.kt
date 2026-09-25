package com.ipirangatech.fidd.core.testing

import android.app.Activity
import com.ipirangatech.fidd.core.billing.RemoveAdsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRemoveAdsRepository(adsRemoved: Boolean? = false) : RemoveAdsRepository {
    override val adsRemoved = MutableStateFlow<Boolean?>(adsRemoved)
    override val purchaseFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var connectCalls = 0
    var purchaseCalls = 0

    override fun connect() {
        connectCalls++
    }

    override fun purchase(activity: Activity) {
        purchaseCalls++
    }
}
