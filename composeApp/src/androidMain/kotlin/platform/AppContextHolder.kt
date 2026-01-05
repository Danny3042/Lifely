package platform

import android.app.Application

object AppContextHolder {
    lateinit var context: Application

    fun init(app: Application) {
        context = app
    }
}

