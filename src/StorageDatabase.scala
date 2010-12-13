package de.duenndns.aprsdroid

import _root_.android.content.Context
import _root_.android.content.ContentValues
import _root_.android.database.sqlite.SQLiteOpenHelper
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.database.Cursor
import _root_.android.util.Log
import _root_.android.widget.FilterQueryProvider

object StorageDatabase {
	val TAG = "StorageDatabase"
	val DB_VERSION = 4
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
		val TYPE_INCMG	= 3
		val COLUMN_TYPE		= 3
		val COLUMN_MESSAGE	= 5

		var trimCounter	= 0
	}

	object Position {
		val TABLE = "position"
		val _ID = "_id"
		val TS = "ts"
		val CALL = "call"
		val LAT = "lat"
		val LON = "lon"
		val SYMBOL = "symbol"
		val COMMENT = "comment"
		lazy val TABLE_CREATE = "CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT, %s LONG, %s TEXT, %s INTEGER, %s INTEGER, %s TEXT, %s TEXT)"
					.format(TABLE, _ID, TS, CALL, LAT, LON, SYMBOL, COMMENT)
		lazy val COLUMNS = Array(_ID, TS, CALL, LAT, LON, SYMBOL, COMMENT)
		lazy val TABLE_INDEX = "CREATE INDEX idx_position_%s ON position (%s)"
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
		db.execSQL(Position.TABLE_CREATE)
	}
	override def onUpgrade(db: SQLiteDatabase, from : Int, to : Int) {
		if (from == 1 && to >= 2) {
			db.execSQL("ALTER TABLE %s ADD COLUMN %s".format(Post.TABLE, "TYPE INTEGER DEFAULT 0"))
		}
		if (from <= 2 && to >= 3) {
			db.execSQL(Position.TABLE_CREATE)
			// we can not call getPosts() here due to recursion issues
			val c = db.query(Post.TABLE, Post.COLUMNS, "TYPE = 0 OR TYPE = 3",
						null, null, null, "_ID DESC", null)
			c.moveToFirst()
			while (!c.isAfterLast()) {
				val message = c.getString(c.getColumnIndexOrThrow(Post.MESSAGE))
				val ts = c.getLong(c.getColumnIndexOrThrow(Post.TS))
				addPosition(ts, message)
				c.moveToNext()
			}
			c.close()
		}
		if (from <= 3 && to >= 4) {
			Array("call", "lat", "lon").map(col => db.execSQL(Position.TABLE_INDEX.format(col, col)))
		}
	}

	def trimPosts(ts : Long) {
		Log.d(TAG, "StorageDatabase.trimPosts")
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Post.TABLE, Post.TS),
			Array(long2Long(ts)))
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Position.TABLE, Position.TS),
			Array(long2Long(ts)))
	}

	// default trim filter: 31 days in [ms]
	def trimPosts() : Unit = trimPosts(System.currentTimeMillis - 31L * 24 * 3600 * 1000)

	def addPosition(ts : Long, message : String) {
		try {
			val (call, lat, lon, sym, comment) = AprsPacket.parseReport(message)
			val cv = new ContentValues()
			cv.put(Position.TS, ts.asInstanceOf[java.lang.Long])
			cv.put(Position.CALL, call)
			cv.put(Position.LAT, lat.asInstanceOf[java.lang.Integer])
			cv.put(Position.LON, lon.asInstanceOf[java.lang.Integer])
			cv.put(Position.SYMBOL, sym)
			cv.put(Position.COMMENT, comment)
			Log.d(TAG, "got %s(%d, %d)%s -> %s".format(call, lat, lon, sym, comment))
			getWritableDatabase().insertOrThrow(Position.TABLE, Position.CALL, cv)
		} catch {
		case e : Exception =>
		}
	}

	def getPositions(sel : String, selArgs : Array[String], limit : String) : Cursor = {
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS,
			sel, selArgs,
			Position.CALL, null, "_ID DESC", limit)
	}

	def getStaPositions(call : String, limit : String) : Cursor = {
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS,
			"call LIKE ? AND TS > ?", Array(call, limit),
			null, null, "_ID DESC", null)
	}

	def addPost(ts : Long, posttype : Int, status : String, message : String) {
		val cv = new ContentValues()
		cv.put(Post.TS, ts.asInstanceOf[java.lang.Long])
		cv.put(Post.TYPE, posttype.asInstanceOf[java.lang.Integer])
		cv.put(Post.STATUS, status)
		cv.put(Post.MESSAGE, message)
		Log.d(TAG, "StorageDatabase.addPost: " + status + " - " + message)
		getWritableDatabase().insertOrThrow(Post.TABLE, Post.MESSAGE, cv)
		if (posttype == Post.TYPE_POST || posttype == Post.TYPE_INCMG) {
			addPosition(ts, message)
		}
		if (Post.trimCounter == 0) {
			trimPosts()
			Post.trimCounter = 100
		} else Post.trimCounter -= 1
	}

	def getPosts(sel : String, selArgs : Array[String], limit : String) : Cursor = {
		getWritableDatabase().query(Post.TABLE, Post.COLUMNS,
			sel, selArgs,
			null, null, "_ID DESC", limit)
	}

	def getPosts(limit : String) : Cursor = getPosts(null, null, limit)

	def getPosts() : Cursor = getPosts(null)

	def getSinglePost(sel : String, selArgs : Array[String]) : (Long, String, String) = {
		val c = getPosts(sel, selArgs, "1")
		c.moveToFirst()
		if (c.isAfterLast()) {
			c.close()
			return (0, "", "")
		} else {
			val tsidx = c.getColumnIndexOrThrow(Post.TS)
			val statidx = c.getColumnIndexOrThrow(Post.STATUS)
			val msgidx = c.getColumnIndexOrThrow(Post.MESSAGE)
			val (ts, status, message) = (c.getLong(tsidx), c.getString(statidx), c.getString(msgidx))
			c.close()
			return (ts, status, message)
		}
	}
	def getLastPost() = getSinglePost(null, null)

	def getPostFilter(limit : String) : FilterQueryProvider = {
		new FilterQueryProvider() {
			def runQuery(constraint : CharSequence) : Cursor = {
				getPosts("MESSAGE LIKE ?", Array("%%%s%%".format(constraint)),
					limit)
			}

		}
	}
}
