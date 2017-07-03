package im.fsn.messenger.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import im.fsn.messenger.ThemeOptions;

import im.fsn.messenger.R;

public class SettingsActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {
	private int themeResId = -1;

	@Override
	protected void onApplyThemeResource(Resources.Theme theme, int resId,
			boolean first) {
		if (this.themeResId == -1)
			this.themeResId = ThemeOptions.getThemeResId(this);
		theme.applyStyle(this.themeResId, true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (this.themeResId == -1)
			this.themeResId = ThemeOptions.getThemeResId(this);

		this.setTheme(this.themeResId);
		super.onCreate(savedInstanceState);
		this.setTheme(this.themeResId);

		SharedPreferences mPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (mPrefs.getBoolean("pfUseHardwareAcceleration", true))
			getWindow().setFlags(0x01000000, 0x01000000); // WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
		// workaround for PreferenceScreen theming issues
		// this.setTheme(ThemeOptions.getThemeResId(this));

		/*
		 * ActionBar bar = getSupportActionBar();
		 * bar.setTitle(R.string.app_name); bar.setSubtitle(R.string.settings);
		 * bar.setDisplayHomeAsUpEnabled(true);
		 */
		this.addPreferencesFromResource(R.xml.preferences);
		// shoot me, this is easier
		AccountManager accountManager = AccountManager.get(this);
		Account[] localGoogleAccounts = accountManager
				.getAccountsByType("com.google");
		Preference pfSMSEnabled = this.findPreference("pfSMSEnabled");
		pfSMSEnabled.setEnabled(this.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_TELEPHONY));

		ListPreference p = (ListPreference) this
				.findPreference("pfGoogleVoiceAccount");
		int accountCount = localGoogleAccounts.length;
		CharSequence[] accountNames = new CharSequence[accountCount + 1];
		CharSequence[] accountValues = new CharSequence[accountCount + 1];
		accountNames[0] = "None"; // localize
		accountValues[0] = "";
		for (int i = 0; i < accountCount; i++) {
			accountNames[i + 1] = localGoogleAccounts[i].name;
			accountValues[i + 1] = localGoogleAccounts[i].name;
		}
		p.setEntries(accountNames);
		p.setEntryValues(accountValues);
		if (accountCount == 0)
			p.setEnabled(false);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			this.finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("pfThemeOption")) {
			this.themeResId = ThemeOptions.getThemeResId(this);

			// ThemeManager.restartWithTheme(SettingsActivity.this,
			// this.themeResId, true);
			Intent intent = getIntent();
			overridePendingTransition(0, 0);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			finish();
			overridePendingTransition(0, 0);
			startActivity(intent);

		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

}
