package app.pwhs.inject.frida.di

import app.pwhs.inject.frida.data.api.GithubApiService
import app.pwhs.inject.frida.data.local.SettingsManager
import app.pwhs.inject.frida.data.repository.GithubRepository
import app.pwhs.inject.frida.root.FridaRootManager
import app.pwhs.inject.frida.ui.viewmodel.MainViewModel
import app.pwhs.inject.frida.worker.DownloadAndInjectWorker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val networkModule = module {
    single {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }
}

val dataModule = module {
    singleOf(::GithubRepository)
    single { SettingsManager(androidContext()) }
}

val rootModule = module {
    singleOf(::FridaRootManager)
}

val workerModule = module {
    workerOf(::DownloadAndInjectWorker)
}

val uiModule = module {
    viewModelOf(::MainViewModel)
}

val appModules = listOf(networkModule, dataModule, rootModule, workerModule, uiModule)
