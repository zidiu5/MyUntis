package com.myuntis.app.di

import com.google.gson.GsonBuilder
import com.myuntis.app.data.network.UntisApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://webuntis.com/"

    // =============================================================
    // HTML GUARD INTERCEPTOR
    // =============================================================
    // WebUntis sometimes returns an HTML login page (200 OK) instead
    // of JSON when the session is expired or the wrong auth method
    // is used. Gson then crashes with "Expected BEGIN_OBJECT but was
    // STRING" because HTML is not valid JSON.
    //
    // This interceptor detects HTML responses and replaces them with
    // an empty JSON object {} so Gson can parse it without crashing.
    // Our data classes map {} to null data fields, which we handle.
    // =============================================================
    private val htmlGuardInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())

        val contentType = response.header("Content-Type") ?: ""
        val isHtml = contentType.contains("text/html", ignoreCase = true)

        if (isHtml) {
            // Consume the HTML body and discard it
            response.body?.close()

            // Replace with empty JSON so Gson doesn't crash
            val emptyJson = "{}".toResponseBody(
                "application/json; charset=utf-8".toMediaType()
            )
            response.newBuilder()
                .code(200)
                .body(emptyJson)
                .build()
        } else {
            response
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.myuntis.app.BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.HEADERS  // BODY logs too much, HEADERS enough
            else
                HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            // HTML guard MUST be first so it runs before logging
            .addInterceptor(htmlGuardInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideUntisApiService(retrofit: Retrofit): UntisApiService {
        return retrofit.create(UntisApiService::class.java)
    }
}