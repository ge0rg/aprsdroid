package de.duenndns;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

public class EditTextPreferenceWithValue extends EditTextPreference {
	CharSequence mSummary;

	public EditTextPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditTextPreferenceWithValue(Context context) {
		super(context);
	}

	private void fixupCaps() {
		EditText et = getEditText();
		if ((et.getInputType() & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
			// append AllCaps filter
			InputFilter[] filters = et.getFilters();
			InputFilter[] newFilters = new InputFilter[filters.length + 1];
			System.arraycopy(filters, 0, newFilters, 0, filters.length);
			newFilters[filters.length] = new InputFilter.AllCaps();
			et.setFilters(newFilters);
		}
	}
	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		fixupCaps();
	}

	private void setSummaryToText(String text) {
		if (mSummary == null)
			mSummary = getSummary();
		if (text == null || text.length() == 0)
			setSummary(mSummary);
		else
			setSummary(mSummary + ": " + text);
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		setSummaryToText(getText());
	}

	@Override
	public void setText(String text) {
		super.setText(text);
		setSummaryToText(text);
	}

}
