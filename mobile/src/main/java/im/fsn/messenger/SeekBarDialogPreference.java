package im.fsn.messenger;

import android.R.anim;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarDialogPreference extends DialogPreference implements
		SeekBar.OnSeekBarChangeListener {

	private static final String fusionns = "http://schemas.android.com/apk/res/im.fsn.messenger";
	private static final String androidns = "http://schemas.android.com/apk/res/android";

	private SeekBar mSeekBar;
	private TextView mSplashText, mValueText;
	private Context mContext;

	private String mDialogMessage, mSuffix, mSingleSuffix;
	private int mDefault, mMin, mMax, mValue, mPreviousValue, mIncrement = 0;


	public SeekBarDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		int resId = 0;

		resId = attrs.getAttributeResourceValue(androidns, "dialogMessage", 0);
		if (resId != 0)
			mDialogMessage = context.getString(resId);
		else
			mDialogMessage = attrs
					.getAttributeValue(androidns, "dialogMessage");

		resId = attrs.getAttributeResourceValue(fusionns, "pluralItemText", 0);
		if (resId != 0)
			mSuffix = context.getString(resId);
		else
			mSuffix = attrs.getAttributeValue(fusionns, "pluralItemText");

		resId = attrs.getAttributeResourceValue(fusionns, "singleItemText", 0);
		if (resId != 0)
			mSingleSuffix = context.getString(resId);
		else
			mSingleSuffix = attrs.getAttributeValue(fusionns, "singleItemText");

		mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
		mMax = attrs.getAttributeIntValue(androidns, "max", 100);
		mMin = attrs.getAttributeIntValue(fusionns, "min", 0);
		mIncrement = attrs.getAttributeIntValue(fusionns, "increment", 1);

	}

	@Override
	protected View onCreateDialogView() {
		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(mContext);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(6, 6, 6, 6);

		mSplashText = new TextView(mContext);
		if (mDialogMessage != null)
			mSplashText.setText(mDialogMessage);
		layout.addView(mSplashText);

		mValueText = new TextView(mContext);
		mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
		mValueText.setTextSize(32);
		params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(mValueText, params);

		mSeekBar = new SeekBar(mContext);
		mSeekBar.setOnSeekBarChangeListener(this);
		layout.addView(mSeekBar, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		if (shouldPersist())
			mValue = getPersistedInt(mDefault);
		mPreviousValue = mValue;
		mSeekBar.setMax((mMax - mMin) / mIncrement);
		mSeekBar.setProgress((mValue - mMin) / mIncrement);
		return layout;
	}

	@Override
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		mSeekBar.setMax((mMax - mMin) / mIncrement);
		mSeekBar.setProgress((mValue - mMin) / mIncrement);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			if (shouldPersist())
				persistInt(mValue);
			callChangeListener(Integer.valueOf(mValue));
		} else if (shouldPersist()) {
			persistInt(mPreviousValue);
			callChangeListener(Integer.valueOf(mPreviousValue));
		}
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue) {
		super.onSetInitialValue(restore, defaultValue);
		if (restore)
			mValue = shouldPersist() ? getPersistedInt(mDefault) : 0;
		else
			mValue = (Integer) defaultValue;
	}

	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
		int newValue = (value * mIncrement) + mMin;
		String t = String.valueOf(newValue);
		if (newValue == 1 && mSingleSuffix != null)
			mValueText.setText(mSingleSuffix == null ? t : t
					.concat(mSingleSuffix));
		else
			mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
		if (fromTouch)
			this.mValue = newValue;
	}

	public void onStartTrackingTouch(SeekBar seek) {
	}

	public void onStopTrackingTouch(SeekBar seek) {
	}

	public void setMax(int max) {
		mMax = max;
	}

	public int getMax() {
		return mMax;
	}

	public void setProgress(int progress) {
		mValue = (progress * mIncrement) + mMin;
		if (mSeekBar != null)
			mSeekBar.setProgress(progress);
	}

	public int getProgress() {
		return (mValue - mMin) / mIncrement;
	}
}