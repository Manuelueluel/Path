package it.traccia.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import javax.inject.Inject

@HiltAndroidApp
class TracciaApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // MapLibre shares the app client: same disk cache and same offline tile router.
        HttpRequestUtil.setOkHttpClient(okHttpClient)
        // Without network MapLibre would stop issuing requests altogether; the tile router must
        // always see them, since it serves offline regions with no network at all.
        MapLibre.setConnected(true)
    }
}
