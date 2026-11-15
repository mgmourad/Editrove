package news.openwire

import android.app.Application
import news.openwire.di.AppContainer

class OpenWireApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
