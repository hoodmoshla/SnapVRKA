package com.mvrk.vrka

import android.app.Application
import android.content.Context

class VrkaApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(SnapVrkaLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        SnapVrkaLocale.applyTo(this)
    }

    val downloads: VrkaDownloadManager by lazy {
        VrkaDownloadManager.create(this)
    }
}

val Context.vrkaApplication: VrkaApplication
    get() = applicationContext as VrkaApplication
