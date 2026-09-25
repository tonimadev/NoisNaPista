package com.ipirangatech.fidd.ads

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipirangatech.fidd.R
import com.ipirangatech.fidd.core.billing.RemoveAdsRepository
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdsUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : AdsUiEffect
}

/** Decide se o banner aparece e dispara a compra de "Remover anúncios". Vive no escopo da Activity. */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val removeAdsRepository: RemoveAdsRepository,
    potholeDetector: PotholeDetector
) : ViewModel() {

    /**
     * Banner só quando a loja confirmou que o usuário NÃO comprou (null = ainda não sabemos) e a
     * detecção está desligada: com ela ligada a pessoa está dirigindo, e anúncio ali é distração
     * (e toque acidental perto do botão de parar).
     */
    val showAds: StateFlow<Boolean> =
        combine(removeAdsRepository.adsRemoved, potholeDetector.isTracking) { removed, tracking ->
            removed == false && !tracking
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiEffect = Channel<AdsUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<AdsUiEffect> = _uiEffect.receiveAsFlow()

    // Só agradece a compra feita agora, não a restaurada em cada abertura do app.
    private var purchaseRequested = false

    init {
        removeAdsRepository.connect()
        viewModelScope.launch {
            removeAdsRepository.purchaseFailures.collect {
                _uiEffect.send(AdsUiEffect.ShowMessage(R.string.ads_purchase_failed))
            }
        }
        viewModelScope.launch {
            removeAdsRepository.adsRemoved.collect { removed ->
                if (removed == true && purchaseRequested) {
                    purchaseRequested = false
                    _uiEffect.send(AdsUiEffect.ShowMessage(R.string.ads_purchase_thanks))
                }
            }
        }
    }

    fun onRemoveAdsClick(activity: Activity) {
        purchaseRequested = true
        removeAdsRepository.purchase(activity)
    }
}
