package com.axvyn.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** Application entry point for lightweight process-wide initialization. */
class AxvynApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
