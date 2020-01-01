package org.aprsdroid.app

import android.app.Activity
import android.os.Bundle
import android.view.View

import com.google.android.gms.maps.MapView

class GoogleMapAct extends Activity with UIHelper {
        lazy val loading = findViewById(R.id.loading).asInstanceOf[View]
        lazy val mapview = findViewById(R.id.mapview).asInstanceOf[MapView]

        override def onCreate(savedInstanceState: Bundle) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.googlemapview)

                mapview.onCreate(savedInstanceState)
        }

        override def onLowMemory(): Unit = {
                super.onLowMemory()
                mapview.onLowMemory()
        }

        override def onStart(): Unit = {
                super.onStart()
                mapview.onStart()
        }

        override def onResume(): Unit = {
                super.onResume()
                mapview.onResume()
        }

        override def onSaveInstanceState(outState: Bundle): Unit = {
                super.onSaveInstanceState(outState)
                mapview.onSaveInstanceState(outState)
        }

        override def onPause(): Unit = {
                super.onPause()
                mapview.onPause()
        }

        override def onStop(): Unit = {
                super.onStop()
                mapview.onStop()
        }

        override def onDestroy(): Unit = {
                super.onDestroy()
                mapview.onDestroy()
        }

        override def onStartLoading() {
                loading.setVisibility(View.VISIBLE)
        }

        override def onStopLoading() {
                loading.setVisibility(View.GONE)
        }
}

