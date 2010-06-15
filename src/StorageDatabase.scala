package de.duenndns.aprsdroid

import _root_.android.content.Context
import _root_.android.content.ContentValues
import _root_.android.database.sqlite.SQLiteOpenHelper
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.database.Cursor
import _root_.android.util.Log

object StorageDatabase {
	val TAG = "StorageDatabase"
	val DB_VERSION = 2
	val DB_NAME = "storage.db"
	object Post {
		val TABLE = "posts"
		val _ID = "_id"
		val TS = "ts"
		val TYPE = "type"
		val STATUS = "status"
		val MESSAGE = "message"
		lazy val TABLE_CREATE = "CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT, %s LONG, %s INTEGER, %s TEXT, %s TEXT)"
					.format(TABLE, _ID, TS, TYPE, STATUS, MESSAGE);
		lazy val COLUMNS = Array(_ID, TS, "DATETIME(TS/1000, 'unixepoch', 'localtime') as TSS", TYPE, STATUS, MESSAGE);

		val TYPE_POST	= 0
		val TYPE_INFO	= 1
		val TYPE_ERROR	= 2
	}

	var singleton : StorageDatabase = null
	def open(context : Context) : StorageDatabase = {
		if (singleton == null) {
			Log.d(TAG, "open(): instanciating StorageDatabase")
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
		Log.d(TAG, "onCreate(): creating new database " + DB_NAME);
		db.execSQL(Post.TABLE_CREATE);
	}
	override def onUpgrade(db: SQLiteDatabase, from : Int, to : Int) {
		if (from == 1 && to == 2) {
			db.execSQL("ALTER TABLE %s ADD COLUMN %s".format(Post.TABLE, "TYPE INTEGER DEFAULT 0"))
		} else
			throw new IllegalStateException("StorageDatabase.onUpgrade(%d, %d)".format(from, to))
	}

	def trimPosts(ts : Long) {
		Log.d(TAG, "StorageDatabase.trimPosts")
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Post.TABLE, Post.TS),
			Array(long2Long(ts)))
	}

	def addPost(ts : Long, posttype : Int, status : String, message : String) {
		val cv = new ContentValues()
		cv.put(Post.TS, ts.asInstanceOf[java.lang.Long])
		cv.put(Post.TYPE, posttype.asInstanceOf[java.lang.Integer])
		cv.put(Post.STATUS, status)
		cv.put(Post.MESSAGE, message)
		Log.d(TAG, "StorageDatabase.addPost: " + status + " - " + message)
		getWritableDatabase().insertOrThrow(Post.TABLE, Post.MESSAGE, cv)
		// filter against db bloat: 31 days in [ms]
		trimPosts(ts - 31L * 24 * 3600 * 1000)
	}

	def getPosts(limit : String) : Cursor = {
		getWritableDatabase().query(Post.TABLE, Post.COLUMNS, null, null, null, null, "TS DESC", limit)
	}

	def getPosts() : Cursor = getPosts(null)

	def getLastPost() : (Long, String, String) = {
		val c = getPosts("1")
		c.moveToFirst()
		if (c.isAfterLast()) {
			return (0, "", "")
		} else {
			val tsidx = c.getColumnIndexOrThrow(Post.TS)
			val statidx = c.getColumnIndexOrThrow(Post.STATUS)
			val msgidx = c.getColumnIndexOrThrow(Post.MESSAGE)
			return (c.getLong(tsidx), c.getString(statidx), c.getString(msgidx))
		}
	}
}
