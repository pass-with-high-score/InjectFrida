package app.pwhs.inject.frida

import android.app.Application
import app.pwhs.inject.frida.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class BaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@BaseApplication)
            workManagerFactory()
            modules(appModules)
        }
    }
}
