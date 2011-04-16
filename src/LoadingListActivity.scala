package org.aprsdroid.app

import _root_.android.app.ListActivity
import _root_.android.os.Bundle
import _root_.android.view.Window

class LoadingListActivity extends ListActivity with LoadingIndicator {

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
	}

	override def onStartLoading() {
		setProgressBarIndeterminateVisibility(true)
	}

	override def onStopLoading() {
		setProgressBarIndeterminateVisibility(false)
	}
}
