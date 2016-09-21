package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.graphics.drawable.Drawable
import _root_.android.graphics.{Bitmap, BitmapFactory, Canvas, Matrix, Paint, Path, Point, Rect, Typeface}
import _root_.android.util.AttributeSet
import _root_.android.widget.ImageView

object SymbolView {
	var iconbitmap : Bitmap = null

	def getSingleton(context : Context) = {
		if (iconbitmap == null) {
			iconbitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.allicons)
		}
		iconbitmap
	}
}

class SymbolView(context : Context, attrs : AttributeSet) extends ImageView(context, attrs) {

	var symbol : String = "/$"
	lazy val iconbitmap = SymbolView.getSingleton(context)
	lazy val symbolSize = iconbitmap.getWidth()/16


	def setSymbol(new_sym : String) {
		symbol = new_sym
		invalidate()
	}

	def symbol2rect(index : Int, page : Int) : Rect = {
		val alt_offset = page*symbolSize*6
		val y = (index / 16) * symbolSize + alt_offset
		val x = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}
	def symbol2rect(symbol : String) : Rect = {
		symbol2rect(symbol(1) - 33, if (symbol(0) == '/') 0 else 1)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}


	override def onDraw(canvas : Canvas) {
		val srcRect = symbol2rect(symbol)
		//android.util.Log.d("SymbolView", "x * y = " +  getWidth() + "*" + getHeight())
		val destRect = new Rect(0, 0, getWidth(), getHeight())
		val drawPaint = new Paint()
		drawPaint.setAntiAlias(true)
		drawPaint.setFilterBitmap(true)

		canvas.drawBitmap(iconbitmap, srcRect, destRect, drawPaint)

		if (symbolIsOverlayed(symbol)) {
			// use page 2, overlay letters
			canvas.drawBitmap(iconbitmap, symbol2rect(symbol(0)-33, 2), destRect, drawPaint)
		}
	}
}
