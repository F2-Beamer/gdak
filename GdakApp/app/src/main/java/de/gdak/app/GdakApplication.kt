package de.gdak.app

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class GdakApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Mapsforge braucht die Grafik-Initialisierung einmal pro Prozess, bevor eine Karte gezeichnet wird.
        AndroidGraphicFactory.createInstance(this)
    }
}
