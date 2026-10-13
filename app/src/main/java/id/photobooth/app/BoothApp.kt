package id.photobooth.app

import android.app.Application
import android.os.Handler
import android.os.Looper

class BoothApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var server: PhotoServer
        private set
    private val handler = Handler(Looper.getMainLooper())

    private val syncTick = object : Runnable {
        override fun run() {
            CloudSync.syncAll(this@BoothApp)
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        server = PhotoServer(Storage.sessionsRoot(this)) { prefs.eventTitle }
        applySharing()
        handler.postDelayed(syncTick, 5_000L)
    }

    /** Menyalakan / mematikan server lokal sesuai pengaturan. */
    fun applySharing() {
        if (prefs.localShare) server.start(prefs.serverPort) else server.stop()
    }

    companion object {
        lateinit var instance: BoothApp
            private set
    }
}
