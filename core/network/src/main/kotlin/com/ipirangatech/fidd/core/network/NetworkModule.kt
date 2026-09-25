package com.ipirangatech.fidd.core.network

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Deployed NoisNaPistaBackend. To test against a local `./gradlew bootRun` instead, switch to
    // "http://localhost:8080/" and run `adb reverse tcp:8080 tcp:8080` (cleartext to localhost is
    // already allowed by network_security_config.xml).
    private const val BASE_URL = "https://fidd.com.br/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // HttpLoggingInterceptor's default Logger prints via System.out, which isn't reliably
        // visible in logcat — route it through android.util.Log instead.
        val logging = HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }
            .apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePotholeService(retrofit: Retrofit): PotholeService = retrofit.create(PotholeService::class.java)

    @Provides
    @Singleton
    fun provideCityService(retrofit: Retrofit): CityService = retrofit.create(CityService::class.java)
}
