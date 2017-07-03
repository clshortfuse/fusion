package im.fsn.messenger.apisupport;

import im.fsn.messenger.R;

import java.io.InputStream;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

@TargetApi(14)
public class V14 {
	public static AccountManagerFuture<Bundle> getAuthToken(
			AccountManager accountManager, Account account,
			String authTokenType, Bundle options, boolean notifyAuthFailure,
			AccountManagerCallback<Bundle> callback, Handler handler) {
		return accountManager.getAuthToken(account, authTokenType, options,
				notifyAuthFailure, callback, handler);
	}

	public static int getDefaultDeviceListViewStyle(boolean light) {
		if (light)
			return android.R.style.Widget_DeviceDefault_Light_ListView;
		else
			return android.R.style.Widget_DeviceDefault_ListView;
	}

	

	public static Uri getProfileContentUri() {
		return Profile.CONTENT_URI;
	}

	public static InputStream openHiResContactPhotoInputStream(
			ContentResolver cr, Uri contactUri) {
		return ContactsContract.Contacts.openContactPhotoInputStream(cr,
				contactUri, true);
	}

	public static int getBackgroundStackedResId() {
		return android.R.attr.backgroundStacked;
	}

}
