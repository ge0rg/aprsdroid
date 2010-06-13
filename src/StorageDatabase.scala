package de.duenndns.aprsdroid

import _root_.android.content.Context
import _root_.android.content.ContentValues
import _root_.android.database.sqlite.SQLiteOpenHelper
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.database.Cursor
import _root_.android.util.Log

object StorageDatabase {
	val TAG = "StorageDatabase"
	val DB_VERSION = 1
	val DB_NAME = "storage.db"
	object Post {
		val TABLE = "posts"
		val _ID = "_id"
		val TS = "ts"
		val MESSAGE = "message"
		lazy val TABLE_CREATE = "CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT, %s LONG, %s TEXT)"
					.format(TABLE, _ID, TS, MESSAGE);
		lazy val COLUMNS = Array(_ID, TS, "DATETIME(TS/1000, 'unixepoch', 'localtime') as TSS", MESSAGE);
	}

	var singleton : StorageDatabase = null
	def open(context : Context) : StorageDatabase = {
		if (singleton == null) {
			Log.d(TAG, "open(): creating new StorageDatabase")
			singleton = new StorageDatabase(context.getApplicationContext())
		}
		singleton
	}
}

class StorageDatabase(context : Context) extends
		SQLiteOpenHelper(context, StorageDatabase.DB_NAME,
			null, StorageDatabase.DB_VERSION) {
	import StorageDatabase._

	override def onCreate(db: SQLiteDatabase) {
		Log.d(TAG, "onCreate()");
		db.execSQL(Post.TABLE_CREATE);
	}
	override def onUpgrade(db: SQLiteDatabase, from : Int, to : Int) {
		throw new IllegalStateException("StorageDatabase.onUpgrade(%d, %d)".format(from, to))
	}

	def trimPosts(ts : Long) {
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Post.TABLE, Post.TS),
			Array(long2Long(ts)))
	}

	def addPost(ts : Long, message : String) {
		val cv = new ContentValues()
		cv.put(Post.TS, ts.asInstanceOf[java.lang.Long])
		cv.put(Post.MESSAGE, message)
		getWritableDatabase().insertOrThrow(Post.TABLE, null, cv)
		// filter against db bloat: 31 days in [ms]
		trimPosts(ts - 31 * 24 * 3600 * 1000)
	}

	def getPosts(limit : String) : Cursor = {
		getWritableDatabase().query(Post.TABLE, Post.COLUMNS, null, null, null, null, "TS DESC", limit)
	}

	def getPosts() : Cursor = getPosts(null)

	def getLastPost() : (Long, String) = {
		val c = getPosts("1")
		c.moveToFirst()
		if (c.isAfterLast()) {
			return (0, "")
		} else {
			val tsidx = c.getColumnIndexOrThrow(Post.TS)
			val msgidx = c.getColumnIndexOrThrow(Post.MESSAGE)
			return (c.getLong(tsidx), c.getString(msgidx))
		}
	}
}
