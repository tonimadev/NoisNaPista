package com.ipirangatech.fidd.core.sensor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {
    @Binds
    abstract fun bindMotionSensor(impl: AndroidMotionSensor): MotionSensor
}
