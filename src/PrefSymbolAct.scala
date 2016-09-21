package org.aprsdroid.app

import _root_.android.app.Activity
import _root_.android.content.Context
import _root_.android.os.Bundle
import _root_.android.text.{Editable, TextWatcher}
import _root_.android.view.{View, ViewGroup}
import _root_.android.util.TypedValue
import _root_.android.widget.{AbsListView, AdapterView, BaseAdapter, EditText, ImageView, GridView}
import _root_.android.widget.AdapterView.OnItemClickListener

class PrefSymbolAct extends Activity with TextWatcher {
	lazy val overlayedit = findViewById(R.id.overlay).asInstanceOf[EditText]
	lazy val symbolview = findViewById(R.id.symbol).asInstanceOf[SymbolView]
	lazy val prefs = new PrefsWrapper(this)
	var chosen_sym : String = ""

	val OVERLAYABLE = "#&0>A^_acnsuvz"

	def overlayAllowed(symbol : String) = {
		symbol(0) != '/' && OVERLAYABLE.contains(symbol(1))
	}

	def setSymbol(symbol : String) {
		val ov_en = overlayAllowed(symbol)
		overlayedit.setEnabled(ov_en)

		val ov = overlayedit.getText().toString()
		if (ov_en && (ov.length == 1)) {
			chosen_sym = "%c%c".format(ov(0), symbol(1))
		} else {
			chosen_sym = symbol
		}
		if (chosen_sym.length == 2)
			symbolview.setSymbol(chosen_sym)
		else symbolview.setSymbol("/$")
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.prefsymbol)
		val gv = findViewById(R.id.gridview).asInstanceOf[GridView]
		gv.setAdapter(new SymbolAdapter(this))
		gv.setOnItemClickListener(new OnItemClickListener() {
				override def onItemClick(av : AdapterView[_], v : View, position : Int, id : Long) {
					android.util.Log.d("PrefSymbolAct", "tapped " + v.asInstanceOf[SymbolView].symbol)
					setSymbol(v.asInstanceOf[SymbolView].symbol)
				}})
		chosen_sym = prefs.getString("symbol", "/$")
		if (chosen_sym.length != 2)
			chosen_sym = "/$"
		val ov = chosen_sym(0)
		if (ov != '/' && ov != '\\')
			overlayedit.setText("" + ov)
		overlayedit.addTextChangedListener(this)
		setSymbol(chosen_sym)
	}

	// OK button XML
	def onOkClicked(view : View) {
		prefs.prefs.edit().putString("symbol", chosen_sym).commit()
		finish()
	}

	// TextWatcher for edit
	override def afterTextChanged(s : Editable) {
		setSymbol("%c%c".format('\\', chosen_sym(1)))
	}
	override def beforeTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	override def onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}


	class SymbolAdapter(context : Context) extends BaseAdapter {
		override def getCount() = 16*12 - 2

		override def getItem(position : Int) : Object = {
			val primary = position / 95
			val secondary = position%95
			return "/\\"(primary) + ('!' + secondary).asInstanceOf[Char].toString
		}

		override def getItemId(position : Int) = position.asInstanceOf[Long]

		override def getView(position : Int, convertView : View, parent : ViewGroup) : View = {
			val v = if (convertView == null) {
					val vt = new SymbolView(context, null)

					val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
						getResources().getDisplayMetrics()).asInstanceOf[Int]

					vt.setLayoutParams(new AbsListView.LayoutParams(px, px))
					vt.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
					//vt.setPadding(8, 8, 8, 8)
					vt
				} else convertView.asInstanceOf[SymbolView]
			v.setSymbol(getItem(position).asInstanceOf[String])
			return v
		}
	}
}
