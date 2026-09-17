package digital.tonima.noisnapista.core.network

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

    // "localhost" here means the device itself — it only reaches the dev machine's
    // NoisNaPistaBackend (`./gradlew bootRun`) once you run `adb reverse tcp:8080 tcp:8080` (works
    // for both the emulator and a USB-connected physical device). Point this at a real deployed
    // URL before shipping.
    private const val BASE_URL = "http://localhost:8080/"

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
