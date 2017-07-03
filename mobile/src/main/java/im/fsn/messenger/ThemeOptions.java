package im.fsn.messenger;

import im.fsn.messenger.R;
import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.apisupport.V14;

import android.R.anim;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.TypedValue;

public class ThemeOptions {

	public static int getThemeResId(Context mContext) {
		return getThemeResId(mContext, false);
	}

	public static boolean isInversedActionBar(Context mContext) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String sThemeOption = pref.getString("pfThemeOption", "0");
		int themeOption = 0;
		boolean rebuildPreference = false;
		try {
			themeOption = Integer.parseInt(sThemeOption);
		} catch (NumberFormatException e) {
			rebuildPreference = true;
		}
		if (rebuildPreference) {
			pref.edit().putString("pfThemeOption", "0").commit();
			themeOption = 0;
		}
		return (themeOption == 2 || themeOption == 5);
	}

	public static int getListViewStyleId(Context mContext) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String sThemeOption = pref.getString("pfThemeOption", "0");
		int themeOption = 0;
		boolean rebuildPreference = false;
		try {
			themeOption = Integer.parseInt(sThemeOption);
		} catch (NumberFormatException e) {
			rebuildPreference = true;
		}
		if (rebuildPreference) {
			pref.edit().putString("pfThemeOption", "0").commit();
			themeOption = 0;
		}
		switch (themeOption) {
		default:
		case 5:
		case 3:
			if (Build.VERSION.SDK_INT >= 14)
				return V14.getDefaultDeviceListViewStyle(false);
		case 0:
		case 2:
			if (Build.VERSION.SDK_INT >= 11)
				return V11.getHoloListViewStyle(false);
			return R.style.Widget_AppCompat_Base_ListView_Menu;
			
		case 4:
			if (Build.VERSION.SDK_INT >= 14)
				return V14.getDefaultDeviceListViewStyle(true);
		case 1:
			if (Build.VERSION.SDK_INT >= 11)
				return V11.getHoloListViewStyle(true);
			return R.style.Widget_AppCompat_Base_ListView_Menu;
		}

	}

	public static int getThemeResId(Context mContext, boolean useDialog) {
		return getThemeResId(mContext, useDialog, false);
	}

	public static int getThemeResId(Context mContext, boolean useDialog,
			boolean usePopUp) {

		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String sThemeOption = pref.getString("pfThemeOption", "0");
		int themeOption = 0;
		boolean rebuildPreference = false;
		try {
			themeOption = Integer.parseInt(sThemeOption);
		} catch (NumberFormatException e) {
			rebuildPreference = true;
		}
		if (rebuildPreference) {
			pref.edit().putString("pfThemeOption", "0").commit();
			themeOption = 0;
		}
		switch (themeOption) {
		default:
		case 0:
			if (usePopUp)
				return R.style.Theme_Holo_PopUp;
			if (useDialog)
				return R.style.Theme_Holo_Dialog;
			return R.style.Theme_Holo;
		case 1:
			if (usePopUp)
				return R.style.Theme_Holo_PopUp_Light;
			if (useDialog)
				return R.style.Theme_Holo_Dialog_Light;
			return R.style.Theme_Holo_Light;

		case 2:
			if (usePopUp)
				return R.style.Theme_Holo_PopUp_Light_DarkActionBar;
			if (useDialog)
				return R.style.Theme_Holo_Dialog_Light;
			return R.style.Theme_Holo_Light_DarkActionBar;
		case 3:
			if (usePopUp)
				return R.style.Theme_DeviceDefault_PopUp;
			if (useDialog)
				return R.style.Theme_DeviceDefault_Dialog;
			return R.style.Theme_DeviceDefault;
		case 4:
			if (usePopUp)
				return R.style.Theme_DeviceDefault_PopUp_Light;
			if (useDialog)
				return R.style.Theme_DeviceDefault_Dialog_Light;
			return R.style.Theme_DeviceDefault_Light;
		case 5:
			if (usePopUp)
				return R.style.Theme_DeviceDefault_PopUp_Light_DarkActionBar;
			if (useDialog)
				return R.style.Theme_DeviceDefault_Dialog_Light;
			return R.style.Theme_DeviceDefault_Light_DarkActionBar;
		}

	}

	public static boolean isLightBackground(Context mContext,
			boolean checkActionBar, boolean usePreference) {
		if (usePreference)
			return isLightBackground(mContext, checkActionBar);

		int backColor = 0;

		TypedValue tv = new TypedValue();
		if (mContext.getTheme().resolveAttribute(
				android.R.attr.colorBackground, tv, true))
			backColor = mContext.getResources().getColor(tv.resourceId);

		float[] hsvColor = new float[3];
		android.graphics.Color.colorToHSV(backColor, hsvColor);

		return (hsvColor[2] > 0.75);
	}

	public static boolean isLightBackground(Context mContext,
			boolean checkActionBar) {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String sThemeOption = pref.getString("pfThemeOption", "0");
		int themeOption = 0;
		boolean rebuildPreference = false;
		try {
			themeOption = Integer.parseInt(sThemeOption);
		} catch (NumberFormatException e) {
			rebuildPreference = true;
		}
		if (rebuildPreference) {
			pref.edit().putString("pfThemeOption", "0").commit();
			themeOption = 0;
		}
		switch (themeOption) {
		default:
		case 0:
		case 3:
			return false;
		case 1:
		case 4:
			return true;
		case 2:
		case 5:
			return !checkActionBar;
		}
	}
}
