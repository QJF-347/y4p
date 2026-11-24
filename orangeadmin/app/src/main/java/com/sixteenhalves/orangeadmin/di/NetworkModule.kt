package com.sixteenhalves.orangeadmin.di

import com.sixteenhalves.orangeadmin.ntwk.AuthAuthenticator
import com.sixteenhalves.orangeadmin.ntwk.AuthInterceptor
import com.sixteenhalves.orangeadmin.ntwk.NetworkService
import com.sixteenhalves.orangeadmin.repository.AdminRepository
import com.sixteenhalves.orangeadmin.repository.AdminRepositoryImpl
import com.sixteenhalves.orangeadmin.repository.UserRepository
import com.sixteenhalves.orangeadmin.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator
    ): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                setLevel(HttpLoggingInterceptor.Level.BODY)
            }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(authAuthenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit
            .Builder()
            .baseUrl("https://y4p.onrender.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providesNetworkService(retrofit: Retrofit): NetworkService = retrofit.create(NetworkService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUserRepository(userRepositoryImpl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(adminRepositoryImpl: AdminRepositoryImpl): AdminRepository
}
