package com.ipirangatech.fidd.core.billing

import android.app.Activity
import com.ipirangatech.fidd.core.billing.RemoveAdsRepository.Companion.REMOVE_ADS_PRODUCT_ID
import digital.tonima.paywall.core.PayWallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** [RemoveAdsRepository] sobre a lib PayWall, que cuida do BillingClient (conexão, consulta, acknowledge). */
@Singleton
class PlayRemoveAdsRepository @Inject constructor(
    private val payWallManager: PayWallManager
) : RemoveAdsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _purchaseFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val purchaseFailures: Flow<Unit> = _purchaseFailures.asSharedFlow()

    // Se a loja não responde (Play sem conta, billing indisponível — erro que a PayWall nem tenta
    // de novo), o "ainda não sei" viraria "nunca mostra anúncio". Depois da carência, assume que
    // não comprou; se a posse chegar depois, o banner some.
    private val storeGaveUp = flow {
        emit(false)
        delay(UNKNOWN_GRACE_MS)
        emit(true)
    }

    override val adsRemoved: StateFlow<Boolean?> =
        combine(payWallManager.ownedProductIds, payWallManager.isReady, storeGaveUp, ::removedOrUnknown)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                removedOrUnknown(payWallManager.ownedProductIds.value, payWallManager.isReady.value, false)
            )

    override fun connect() = payWallManager.connect()

    override fun purchase(activity: Activity) {
        if (payWallManager.isReady.value) {
            payWallManager.launchPurchase(activity, REMOVE_ADS_PRODUCT_ID)
            return
        }
        // A PayWall ignora launchPurchase antes de isReady. Em vez de engolir o toque (conexão
        // ainda subindo ou caiu e está em backoff), reconecta e compra assim que ficar pronta;
        // se não ficar, avisa a UI em vez de o botão parecer quebrado.
        payWallManager.connect()
        scope.launch {
            val ready = withTimeoutOrNull(READY_TIMEOUT_MS) { payWallManager.isReady.first { it } } ?: false
            if (ready) {
                payWallManager.launchPurchase(activity, REMOVE_ADS_PRODUCT_ID)
            } else {
                _purchaseFailures.tryEmit(Unit)
            }
        }
    }

    private companion object {
        const val READY_TIMEOUT_MS = 15_000L
        const val UNKNOWN_GRACE_MS = 5_000L

        // Posse confirmada vale mesmo com a conexão caída; "não é dono" só depois que a loja
        // respondeu (isReady) ou da carência, para não confundir "ainda não sei" com "não comprou".
        fun removedOrUnknown(owned: Set<String>, ready: Boolean, gaveUp: Boolean): Boolean? = when {
            REMOVE_ADS_PRODUCT_ID in owned -> true
            ready || gaveUp -> false
            else -> null
        }
    }
}
