package de.duenndns;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

public class PreferenceWithValue extends Preference {
	CharSequence mSummary;

	public PreferenceWithValue(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PreferenceWithValue(Context context) {
		super(context);
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
		setSummaryToText(getSharedPreferences().getString(getKey(), ""));
	}

}
