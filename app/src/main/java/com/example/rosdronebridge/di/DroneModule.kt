package com.example.rosdronebridge.di

import com.example.rosdronebridge.util.DroneStateTracker
import com.example.rosdronebridge.util.ROSMessageParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DroneModule {

    @Provides
    @Singleton
    fun provideROSMessageParser(): ROSMessageParser = ROSMessageParser()

//    @Provides
//    @Singleton
//    fun provideDroneStateTracker(): DroneStateTracker = DroneStateTracker()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Note: We don't need to provide DroneController here if we add 
    // @Inject constructor to the DroneController class itself.
}
