package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.{Context, DialogInterface, Intent}
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

class PasscodeDialog(act : Activity, firstrun : Boolean) extends AlertDialog(act)
		with DialogInterface.OnClickListener 
		with DialogInterface.OnCancelListener
		with TextWatcher
		with View.OnFocusChangeListener
		{
	lazy val prefs = new PrefsWrapper(act)

	val inflater = act.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
			.asInstanceOf[LayoutInflater]
	val fr_view = inflater.inflate(R.layout.firstrunview, null, false)
	val inputCall = fr_view.findViewById(R.id.callsign).asInstanceOf[EditText]
	val inputPass = fr_view.findViewById(R.id.passcode).asInstanceOf[EditText]
	lazy val okButton = getButton(DialogInterface.BUTTON_POSITIVE)
	var movedAwayFromCallsign = false

	setTitle(act.getString(if (firstrun) R.string.fr_title else R.string.p_passcode_entry))
	if (!firstrun) {
		fr_view.findViewById(R.id.fr_text).asInstanceOf[View].setVisibility(View.GONE)
		fr_view.findViewById(R.id.fr_text2).asInstanceOf[View].setVisibility(View.GONE)
	}
	setView(fr_view)
	setIcon(android.R.drawable.ic_dialog_info)

	inputCall.setText(prefs.getCallsign())
	inputCall.addTextChangedListener(this)
	inputCall.setFilters(Array(new InputFilter.AllCaps()))
	inputCall.setOnFocusChangeListener(this)
	inputPass.setText(prefs.getString("passcode", ""))
	inputPass.addTextChangedListener(this)

	setButton(DialogInterface.BUTTON_POSITIVE, act.getString(android.R.string.ok), this)
	setButton(DialogInterface.BUTTON_NEUTRAL, act.getString(R.string.p_passreq), this)
	setOnCancelListener(this)

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		if (inputCall.getText().toString() == "")
			okButton.setEnabled(false)
		if (!firstrun)
			inputPass.requestFocus()
	}

	// DialogInterface.OnClickListener
	override def onClick(d : DialogInterface, which : Int) {
		which match {
		case DialogInterface.BUTTON_POSITIVE =>
			saveFirstRun(true)
			//TODO checkConfig()
		case DialogInterface.BUTTON_NEUTRAL =>
			saveFirstRun(false)
			act.startActivity(new Intent(Intent.ACTION_VIEW,
				Uri.parse(act.getString(R.string.passcode_url))))
		case _ =>
			cancelled()
		}
	}

	// DialogInterface.OnCancelListener
	override def onCancel(d : DialogInterface) {
		cancelled()
	}

	// TextWatcher
	override def afterTextChanged(s : Editable) {
		verifyInput()
	}
	override def beforeTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	override def onTextChanged(s : CharSequence, start : Int, before : Int, count : Int) {
	}
	
	// OnFocusChangeListener
	override def onFocusChange(v: View, hasFocus : Boolean) {
		// only relevant for inputCall
		if (!hasFocus) {
			movedAwayFromCallsign = true
			verifyInput()
		}
	}

	def passOK(call : String, pass : String) = {
		if (pass != "") AprsPacket.passcodeAllowed(call, pass, true) else true
	}

	def verifyInput() {
		val call = inputCall.getText().toString()
		val pass = inputPass.getText().toString()
		val callError = if (call != "" || !movedAwayFromCallsign) null else act.getString(R.string.p_callsign_entry)
		val passError = if (passOK(call, pass)) null else act.getString(R.string.wrongpasscode)
		inputCall.setError(callError)
		inputPass.setError(passError)
		okButton.setEnabled(call != "" && callError == null && passError == null)
	}
	def saveFirstRun(completed : Boolean) {
		val call = inputCall.getText().toString()
		val passcode = inputPass.getText().toString()
		val pe = prefs.prefs.edit()
		call.split("-") match {
		case Array(callsign) =>
			pe.putString("callsign", callsign)
		case Array(callsign, ssid) =>
			pe.putString("callsign", callsign)
			pe.putString("ssid", ssid)
		case _ =>
			Log.d("PasscodeDialog", "could not split callsign")
			act.finish()
			return
		}
		if (passOK(call, passcode))
			pe.putString("passcode", passcode)
		pe.putBoolean("firstrun", !completed)
		pe.commit()
	}

	def cancelled() {
		if (firstrun) {
			Log.d("PasscodeDialog", "closing parent activity")
			act.finish()
		}
	}
}
