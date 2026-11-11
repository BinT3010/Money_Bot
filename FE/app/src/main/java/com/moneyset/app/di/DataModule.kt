package com.moneyset.app.di

import com.moneyset.app.BuildConfig
import com.moneyset.app.data.auth.AuthDataSource
import com.moneyset.app.data.auth.FakeAuthDataSource
import com.moneyset.app.data.auth.RemoteAuthDataSource
import com.moneyset.app.data.source.FinanceDataSource
import com.moneyset.app.data.source.fake.FakeFinanceDataSource
import com.moneyset.app.data.source.remote.RemoteFinanceDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideFinanceDataSource(
        fake: Provider<FakeFinanceDataSource>,
        remote: Provider<RemoteFinanceDataSource>,
    ): FinanceDataSource = if (BuildConfig.USE_MOCK_API) fake.get() else remote.get()

    @Provides
    @Singleton
    fun provideAuthDataSource(
        fake: Provider<FakeAuthDataSource>,
        remote: Provider<RemoteAuthDataSource>,
    ): AuthDataSource = if (BuildConfig.USE_MOCK_API) fake.get() else remote.get()
}
