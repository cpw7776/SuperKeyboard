package io.superkeyboard

import android.app.Application
import io.superkeyboard.clipboard.ClipboardDatabase

class SuperKeyboardApp : Application() {

    lateinit var clipboardDatabase: ClipboardDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        clipboardDatabase = ClipboardDatabase.create(this)
    }

    companion object {
        lateinit var instance: SuperKeyboardApp
            private set
    }
}
