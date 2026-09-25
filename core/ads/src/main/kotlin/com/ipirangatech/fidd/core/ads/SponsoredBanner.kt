package com.ipirangatech.fidd.core.ads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner do AdMob + convite para comprar "Remover anúncios". É o único formato de anúncio do app:
 * sem intersticial/recompensado, que tomariam a tela de quem pode estar dirigindo.
 *
 * @param ad o banner em si; trocável nos testes, onde o SDK do AdMob não carrega.
 */
@Composable
fun SponsoredBanner(
    adUnitId: String,
    onRemoveAds: () -> Unit,
    modifier: Modifier = Modifier,
    ad: @Composable (Modifier) -> Unit = { AdMobBanner(adUnitId, it) }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ad(Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ads_invite),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRemoveAds) {
                Text(stringResource(R.string.ads_remove_button))
            }
        }
    }
}

/** Banner adaptativo ancorado, com a largura que o slot tiver (lista com margem, painel dividido). */
@Composable
fun AdMobBanner(adUnitId: String, modifier: Modifier = Modifier) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier.wrapContentHeight()) {
        val widthDp = maxWidth.value.toInt()
        val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
        AndroidView(
            // Altura reservada antes do anúncio chegar: sem isso a lista pula quando ele carrega,
            // e conteúdo que se mexe sob o dedo gera clique acidental (política do AdMob).
            modifier = Modifier.fillMaxWidth().height(adSize.height.dp),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(adSize)
                    this.adUnitId = adUnitId
                    if (!isPreview) loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SponsoredBannerPreview() {
    SponsoredBanner(adUnitId = "ca-app-pub-3940256099942544/9214589741", onRemoveAds = {})
}
