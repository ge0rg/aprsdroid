package de.duenndns.aprsdroid

import _root_.android.app.Activity
import _root_.android.os.Bundle

class APRSdroid extends Activity {
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
			setContentView(R.layout.main)
	}
	override def $tag() : Int = {
		try {
			return super.$tag();
		} catch {
			case e: Exception => throw new RuntimeException(e);
		}
	}
}

