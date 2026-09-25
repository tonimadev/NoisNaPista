package com.ipirangatech.fidd.core.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Inicializa o AdMob fora da main thread (recomendação do SDK: a inicialização faz I/O).
 *
 * O MobileAds lê o user agent do WebView ao iniciar; sem um provedor de WebView (ex.: pacote do
 * WebView sendo atualizado) isso lança — bug real no Kairos, onde derrubava o app a cada abertura.
 * Aqui a falha só desliga os anúncios da sessão.
 */
fun initializeMobileAds(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    initialize: (Context) -> Unit = { MobileAds.initialize(it) }
) {
    scope.launch {
        try {
            initialize(context)
        } catch (e: RuntimeException) {
            Log.w("FIDD", "MobileAds initialization failed; ads disabled for this session", e)
        }
    }
}
