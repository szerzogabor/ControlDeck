package com.controlldeck.app

import android.app.Application
import com.controlldeck.app.di.ServiceLocator

class ControlDeckApplication : Application() {

    val serviceLocator: ServiceLocator by lazy { ServiceLocator.getOrCreate(this) }

    override fun onCreate() {
        super.onCreate()
        serviceLocator.start()
    }

    override fun onTerminate() {
        serviceLocator.shutdown()
        super.onTerminate()
    }
}
