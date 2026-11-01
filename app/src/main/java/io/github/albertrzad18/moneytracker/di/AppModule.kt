package io.github.albertrzad18.moneytracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.data.remote.CoinGeckoApi
import io.github.albertrzad18.moneytracker.data.remote.NbpApi
import io.github.albertrzad18.moneytracker.data.remote.YahooApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Yahoo rejects requests without a browser-like User-Agent.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideYahooApi(client: OkHttpClient, json: Json): YahooApi =
        retrofit("https://query1.finance.yahoo.com/", client, json).create()

    @Provides
    @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient, json: Json): CoinGeckoApi =
        retrofit("https://api.coingecko.com/api/v3/", client, json).create()

    @Provides
    @Singleton
    fun provideNbpApi(client: OkHttpClient, json: Json): NbpApi =
        retrofit("https://api.nbp.pl/api/", client, json).create()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "money-tracker.db").build()

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Scope for work that must outlive a screen, such as a refresh started after saving a lot. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
