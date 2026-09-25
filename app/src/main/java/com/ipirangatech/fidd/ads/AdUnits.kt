package com.ipirangatech.fidd.ads

import com.ipirangatech.fidd.BuildConfig

/**
 * Cada ponto de inserção tem o próprio bloco de anúncios no AdMob, para a receita de cada tela
 * aparecer separada. Os IDs vêm do admob.properties (ver app/build.gradle.kts).
 */
internal enum class AdPlacement(val releaseUnitId: String) {
    HOME(BuildConfig.ADMOB_BANNER_HOME),
    MAP(BuildConfig.ADMOB_BANNER_MAP),
    HISTORY(BuildConfig.ADMOB_BANNER_HISTORY),
    RANKING(BuildConfig.ADMOB_BANNER_RANKING),
}

internal object AdUnits {
    /** Banner de teste oficial do Google (anúncio de exemplo, sem receita). */
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"

    /**
     * Em debug é sempre o de teste: ver/clicar anúncio real do próprio app conta como tráfego
     * inválido e pode suspender a conta AdMob. Em release vem do admob.properties.
     */
    fun banner(
        placement: AdPlacement,
        debug: Boolean = BuildConfig.DEBUG,
    ): String = if (debug) TEST_BANNER else placement.releaseUnitId
}
