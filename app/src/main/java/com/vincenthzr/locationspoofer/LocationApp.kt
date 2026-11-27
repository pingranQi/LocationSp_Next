package com.vincenthzr.locationspoofer

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.google.android.libraries.places.api.Places
import com.vincenthzr.locationspoofer.di.appModules
import com.vincenthzr.locationspoofer.utils.XposedModuleStatus
import com.vincenthzr.locationspoofer.utils.SettingsManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class LocationApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()

        // Migrate legacy API keys before initializing map SDKs.
        val settings = SettingsManager(this)
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        val customApiKey = settings.amapApiKey
        if (!customApiKey.isNullOrEmpty()) {
            MapsInitializer.setApiKey(customApiKey)
            AMapLocationClient.setApiKey(customApiKey)
            ServiceSettings.getInstance().setApiKey(customApiKey)
        }

        val baiduApiKey = settings.baiduApiKey
        if (!baiduApiKey.isNullOrEmpty()) {
            com.baidu.mapapi.SDKInitializer.setApiKey(baiduApiKey)
        }

        try {
            com.baidu.mapapi.SDKInitializer.setAgreePrivacy(this, true)
            com.baidu.mapapi.SDKInitializer.setCoordType(com.baidu.mapapi.CoordType.GCJ02)
            com.baidu.mapapi.SDKInitializer.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val googleApiKey = settings.googleApiKey
        if (!Places.isInitialized() && !googleApiKey.isNullOrBlank()) {
            try {
                Places.initialize(this, googleApiKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startKoin {
            androidLogger()
            androidContext(this@LocationApp)
            modules(appModules)
        }

        // Create the config publisher before registering; it observes the current service and rebinds.
        org.koin.java.KoinJavaComponent.get<com.vincenthzr.locationspoofer.utils.ConfigManager>(
            com.vincenthzr.locationspoofer.utils.ConfigManager::class.java
        )
        Thread {
            try {
                XposedServiceHelper.registerListener(this@LocationApp)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onServiceBind(service: XposedService) {
        XposedModuleStatus.update(service)
    }

    override fun onServiceDied(service: XposedService) {
        XposedModuleStatus.clear(service)
    }
}
