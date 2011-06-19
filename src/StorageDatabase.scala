package org.aprsdroid.app

import _root_.android.content.Context
import _root_.android.content.ContentValues
import _root_.android.database.sqlite.SQLiteOpenHelper
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.database.Cursor
import _root_.android.util.Log
import _root_.android.widget.FilterQueryProvider

import _root_.net.ab0oo.aprs.parser._

import _root_.scala.math.{cos, Pi}

object StorageDatabase {
	val TAG = "StorageDatabase"
	val DB_VERSION = 1
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
		val SPEED = "speed"
		val COURSE = "course"
		val ALT = "alt"
		val SYMBOL = "symbol"
		val COMMENT = "comment"
		val ORIGIN = "origin"	// originator call for object/item
		val QRG = "qrg"		// voice frequency
		lazy val TABLE_CREATE = """CREATE TABLE %s (%s INTEGER PRIMARY KEY AUTOINCREMENT, %s LONG,
			%s TEXT, %s INTEGER, %s INTEGER,
			%s INTEGER, %s INTEGER, %s INTEGER,
			%s TEXT, %s TEXT, %s TEXT, %s TEXT)"""
			.format(TABLE, _ID, TS,
				CALL, LAT, LON,
				SPEED, COURSE, ALT,
				SYMBOL, COMMENT, ORIGIN, QRG)
		lazy val TABLE_DROP = "DROP TABLE %s".format(TABLE)
		lazy val COLUMNS = Array(_ID, TS, CALL, LAT, LON, SYMBOL, COMMENT, SPEED, COURSE, ALT, ORIGIN, QRG)
		lazy val COL_DIST = "((lat - %d)*(lat - %d) + (lon - %d)*(lon - %d)*%d/100) as dist"

		val COLUMN_TS		= 1
		val COLUMN_CALL		= 2
		val COLUMN_LAT		= 3
		val COLUMN_LON		= 4
		val COLUMN_SYMBOL	= 5
		val COLUMN_COMMENT	= 6
		val COLUMN_SPEED	= 7
		val COLUMN_COURSE	= 8
		val COLUMN_ALT		= 9
		val COLUMN_ORIGIN	= 10
		val COLUMN_QRG		= 11

		lazy val COLUMNS_MAP = Array(_ID, CALL, LAT, LON, SYMBOL)
		val COLUMN_MAP_CALL	= 1
		val COLUMN_MAP_LAT	= 2
		val COLUMN_MAP_LON	= 3
		val COLUMN_MAP_SYMBOL	= 4

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

	def cursor2call(c : Cursor) : String = {
		val msgidx = c.getColumnIndex(Post.MESSAGE)
		val callidx = c.getColumnIndex(Position.CALL)
		if (msgidx != -1 && callidx == -1) { // Post table
			val t = c.getInt(Post.COLUMN_TYPE)
			if (t == Post.TYPE_POST || t == Post.TYPE_INCMG)
				c.getString(msgidx).split(">")(0)
			else
				null
		} else
			c.getString(Position.COLUMN_CALL)
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
		Array("call", "lat", "lon").map(col => db.execSQL(Position.TABLE_INDEX.format(col, col)))
	}
	def resetPositionsTable(db : SQLiteDatabase) {
		db.execSQL(Position.TABLE_DROP)
		db.execSQL(Position.TABLE_CREATE)
		Array("call", "lat", "lon").map(col => db.execSQL(Position.TABLE_INDEX.format(col, col)))
		return; // this code causes a too long wait in onUpgrade...
		// we can not call getPosts() here due to recursion issues
		val c = db.query(Post.TABLE, Post.COLUMNS, "TYPE = 0 OR TYPE = 3",
					null, null, null, "_ID DESC", null)
		c.moveToFirst()
		while (!c.isAfterLast()) {
			val message = c.getString(c.getColumnIndexOrThrow(Post.MESSAGE))
			val ts = c.getLong(c.getColumnIndexOrThrow(Post.TS))
			parsePacket(ts, message)
			c.moveToNext()
		}
		c.close()
	}
	def resetPositionsTable() : Unit = resetPositionsTable(getWritableDatabase())

	override def onUpgrade(db: SQLiteDatabase, from : Int, to : Int) {
	}

	def trimPosts(ts : Long) = Benchmark("trimPosts") {
		//Log.d(TAG, "StorageDatabase.trimPosts")
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Post.TABLE, Post.TS),
			Array(long2Long(ts)))
		getWritableDatabase().execSQL("DELETE FROM %s WHERE %s < ?".format(Position.TABLE, Position.TS),
			Array(long2Long(ts)))
	}

	// default trim filter: 31 days in [ms]
	def trimPosts() : Unit = trimPosts(System.currentTimeMillis - 2L * 24 * 3600 * 1000)

	def addPosition(ts : Long, ap : APRSPacket, pos : Position, objectname : String) {
		import Position._
		val cv = new ContentValues()
		val call = ap.getSourceCall()
		val lat = (pos.getLatitude()*1000000).asInstanceOf[Int]
		val lon = (pos.getLongitude()*1000000).asInstanceOf[Int]
		val sym = "%s%s".format(pos.getSymbolTable(), pos.getSymbolCode())
		val comment = ap.getAprsInformation().getComment()
		val qrg = AprsPacket.parseQrg(comment)
		cv.put(TS, ts.asInstanceOf[java.lang.Long])
		if (objectname != null) {
			cv.put(CALL, objectname)
			cv.put(ORIGIN, call)
		} else
			cv.put(CALL, call)
		cv.put(LAT, lat.asInstanceOf[java.lang.Integer])
		cv.put(LON, lon.asInstanceOf[java.lang.Integer])
		cv.put(SYMBOL, sym)
		cv.put(COMMENT, comment)
		cv.put(QRG, qrg)
		Log.d(TAG, "got %s(%d, %d)%s -> %s".format(call, lat, lon, sym, comment))
		getWritableDatabase().insertOrThrow(TABLE, CALL, cv)
	}

	def parsePacket(ts : Long, message : String) {
		try {
			val fap = new Parser().parse(message)
			if (fap.getAprsInformation() == null) {
				Log.d(TAG, "parsePacket() misses payload: " + message)
				return
			}
			if (fap.hasFault())
				throw new Exception("FAP fault")
			fap.getAprsInformation() match {
				case pp : PositionPacket => addPosition(ts, fap, pp.getPosition(), null)
				case op : ObjectPacket => addPosition(ts, fap, op.getPosition(), op.getObjectName())
			}
		} catch {
		case e : Exception =>
			Log.d(TAG, "parsePacket() unsupported packet: " + message)
			e.printStackTrace()
		}
	}

	def getPositions(sel : String, selArgs : Array[String], limit : String) : Cursor = Benchmark("getPositions") {
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS_MAP,
			sel, selArgs,
			null, null, "CALL, _ID", limit)
	}

	def getRectPositions(lat1 : Int, lon1 : Int, lat2 : Int, lon2 : Int, limit : String) : Cursor = {
		Log.d(TAG, "StorageDatabase.getRectPositions: %d,%d - %d,%d".format(lat1, lon1, lat2, lon2))
		getPositions("LAT >= ? AND LAT <= ? AND LON >= ? AND LON <= ?",
			Array(lat1, lat2, lon1, lon2).map(_.toString), limit)
	}

	def getStaPosition(call : String) : Cursor = Benchmark("getStaPosition") {
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS,
			"call LIKE ?", Array(call),
			null, null, "_ID DESC", "1")
	}
	def getStaPositions(call : String, limit : String) : Cursor = Benchmark("getStaPositions") {
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS,
			"call LIKE ? AND TS > ?", Array(call, limit),
			null, null, "_ID DESC", null)
	}
	def getAllSsids(call : String) : Cursor = Benchmark("getAllSsids") {
		val querycall = call.split("[- _]+")(0) + "%"
		getReadableDatabase().query(Position.TABLE, Position.COLUMNS,
			"call LIKE ? or origin LIKE ?", Array(querycall, querycall),
			"call", null, null, null)
	}
	def getNeighbors(mycall : String, lat : Int, lon : Int, ts : Long, limit : String) : Cursor = Benchmark("getNeighbors") {
		// calculate latitude correction
		val corr = (cos(Pi*lat/180000000.)*cos(Pi*lat/180000000.)*100).toInt
		Log.d(TAG, "getNeighbors: correcting by %d".format(corr))
		// add a distance column to the query
		val newcols = Position.COLUMNS :+ Position.COL_DIST.format(lat, lat, lon, lon, corr)
		getReadableDatabase().query(Position.TABLE, newcols,
			"ts > ? or call = ?", Array(ts.toString, mycall),
			"call", null, "dist", limit)
	}

	def getNeighborsLike(call : String, lat : Int, lon : Int, ts : Long, limit : String) : Cursor = Benchmark("getNeighborsLike") {
		// calculate latitude correction
		val corr = (cos(Pi*lat/180000000.)*cos(Pi*lat/180000000.)*100).toInt
		Log.d(TAG, "getNeighborsLike: correcting by %d".format(corr))
		// add a distance column to the query
		val newcols = Position.COLUMNS :+ Position.COL_DIST.format(lat, lat, lon, lon, corr)
		getReadableDatabase().query(Position.TABLE, newcols,
			"call like ?", Array(call),
			"call", null, "dist", limit)
	}

	def addPost(ts : Long, posttype : Int, status : String, message : String) {
		val cv = new ContentValues()
		cv.put(Post.TS, ts.asInstanceOf[java.lang.Long])
		cv.put(Post.TYPE, posttype.asInstanceOf[java.lang.Integer])
		cv.put(Post.STATUS, status)
		cv.put(Post.MESSAGE, message)
		getWritableDatabase().insertOrThrow(Post.TABLE, Post.MESSAGE, cv)
		if (posttype == Post.TYPE_POST || posttype == Post.TYPE_INCMG) {
			parsePacket(ts, message)
		} else {
			// only log status messages
			Log.d(TAG, "StorageDatabase.addPost: " + status + " - " + message)
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

	def getStaPosts(call : String, limit : String) : Cursor = {
		val start = "%s%%".format(call)		// match for call-originated messages
		val obj1 = "%%;%s%%".format(call)	// ;call - object
		val obj2 = "%%)%s%%".format(call)	// )call - item
		getPosts("message LIKE ? OR message LIKE ? OR message LIKE ?",
			Array(start, obj1, obj2), "100")
	}

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
