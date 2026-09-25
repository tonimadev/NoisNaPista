package com.ipirangatech.fidd.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Compra única que remove os anúncios do app (produto in-app da Play Store). */
interface RemoveAdsRepository {
    /**
     * `true` se o usuário é dono do produto, `false` se não é, e `null` enquanto a Play ainda não
     * respondeu. O `null` existe para quem já comprou não ver um banner piscar a cada abertura
     * (e, sem loja, também não haveria anúncio para carregar).
     */
    val adsRemoved: StateFlow<Boolean?>

    /** Emite quando um toque em "Remover anúncios" não conseguiu abrir a loja a tempo. */
    val purchaseFailures: Flow<Unit>

    /** Conecta ao faturamento da Play. Pode ser chamado de novo; é no-op se já conectado. */
    fun connect()

    /** Abre a folha de compra da Play para [REMOVE_ADS_PRODUCT_ID]. */
    fun purchase(activity: Activity)

    companion object {
        /** Mesmo ID usado no Kairos; precisa existir como produto in-app no Play Console do FIDD. */
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads_premium"
    }
}
