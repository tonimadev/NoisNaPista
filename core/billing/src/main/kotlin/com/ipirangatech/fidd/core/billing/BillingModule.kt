package com.ipirangatech.fidd.core.billing

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import digital.tonima.paywall.core.PayWallConfig
import digital.tonima.paywall.core.PayWallManager
import digital.tonima.paywall.play.PayWallManagerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BillingModule {
    @Binds
    fun bindRemoveAdsRepository(impl: PlayRemoveAdsRepository): RemoveAdsRepository

    companion object {
        @Provides
        @Singleton
        fun providePayWallManager(
            @ApplicationContext context: Context,
        ): PayWallManager =
            PayWallManagerImpl(
                context,
                PayWallConfig(
                    inAppProductIds = setOf(RemoveAdsRepository.REMOVE_ADS_PRODUCT_ID),
                    debugMode = BuildConfig.DEBUG,
                ),
            )
    }
}
