package org.aprsdroid.app

import _root_.android.util.Log

object Benchmark {
	def apply[T](tag: String)(block: => T) {
		val start = System.currentTimeMillis
		try {
			block
		} finally {
			val exectime = System.currentTimeMillis - start
			Log.d(tag, "exectuion time: %.3f s".format(exectime / 1000.0))
		}
	}
}


