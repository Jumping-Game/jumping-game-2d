package com.bene.jump.di

import android.content.Context
import com.bene.jump.data.NetPrefsStore
import com.bene.jump.data.SettingsStore
import com.bene.jump.input.TiltInput
import com.bene.jump.input.TouchInput
import com.bene.jump.net.api.RoomsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRoomsApi(client: OkHttpClient): RoomsApi = RoomsApi(client)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
    ): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun provideNetPrefsStore(
        @ApplicationContext context: Context,
    ): NetPrefsStore = NetPrefsStore(context)

    @Provides
    @Singleton
    fun provideTiltInput(
        @ApplicationContext context: Context,
    ): TiltInput = TiltInput(context)

    @Provides
    @Singleton
    fun provideTouchInput(): TouchInput = TouchInput()
}
