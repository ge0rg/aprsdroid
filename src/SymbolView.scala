package org.aprsdroid.app

import _root_.android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import _root_.android.graphics.drawable.{Drawable, BitmapDrawable}
import _root_.android.graphics.{Canvas, Paint, Path, Point, Rect, Typeface}
import _root_.android.util.AttributeSet
import _root_.android.widget.ImageView

class SymbolView(context : Context, attrs : AttributeSet) extends ImageView(context, attrs) {

	var symbol : String = "/$"
	val iconbitmap = UnscaledBitmapLoader.loadFromResource(context.getResources(),
				R.drawable.allicons, null)
	val symbolSize = 16

	def setSymbol(new_sym : String) {
		symbol = new_sym
		invalidate()
	}

	def symbol2rect(symbol : String) : Rect = {
		val alt_offset = if (symbol(0) == '/') 0 else symbolSize*6
		val index = symbol(1) - 32
		val x = (index / 16) * symbolSize + alt_offset
		val y = (index % 16) * symbolSize
		new Rect(x, y, x+symbolSize, y+symbolSize)
	}

	def symbolIsOverlayed(symbol : String) = {
		(symbol(0) != '/' && symbol(0) != '\\')
	}


	override def onDraw(canvas : Canvas) {
		val srcRect = symbol2rect(symbol)
		//android.util.Log.d("SymbolView", "x * y = " +  getWidth() + "*" + getHeight())
		val destRect = new Rect(0, 0, getWidth(), getHeight())
		val fontSize = getHeight()*3/4 - 1
		val drawPaint = new Paint()
		drawPaint.setAntiAlias(true)
		drawPaint.setFilterBitmap(true)

		canvas.drawBitmap(iconbitmap, srcRect, destRect, drawPaint)

		val symbPaint = new Paint()
		symbPaint.setColor(0xffffffff)
		symbPaint.setTextAlign(Paint.Align.CENTER)
		symbPaint.setTypeface(Typeface.MONOSPACE)
		symbPaint.setTextSize(fontSize)
		symbPaint.setAntiAlias(true)

		val strokePaint = new Paint(symbPaint)
		strokePaint.setColor(0xff000000)
		strokePaint.setStyle(Paint.Style.STROKE)
		strokePaint.setStrokeWidth(2)

		if (symbolIsOverlayed(symbol)) {
			val x = getWidth()/2
			val y = getHeight()*3/4
			canvas.drawText(symbol(0).toString(), x, y, strokePaint)
			canvas.drawText(symbol(0).toString(), x, y, symbPaint)
		}
	}
}
