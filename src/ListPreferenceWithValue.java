package de.duenndns;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;

public class ListPreferenceWithValue extends ListPreference {
	CharSequence mSummary;

	public ListPreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ListPreferenceWithValue(Context context) {
		super(context);
	}

	private void setSummaryToText(CharSequence text) {
		if (mSummary == null)
			mSummary = getSummary();
		if (text == null || text.length() == 0)
			setSummary(mSummary);
		else
			setSummary(text);
	}
	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		setSummaryToText(getEntry());
	}

	@Override
	public void setValue(String text) {
		super.setValue(text);
		setSummaryToText(getEntry());
	}

}
