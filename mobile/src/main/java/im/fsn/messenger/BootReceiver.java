package im.fsn.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences mPrefs = PreferenceManager
				.getDefaultSharedPreferences(context);

		mPrefs.edit().putBoolean("showBetaWarning", true).commit();
		context.startService(new Intent(context, WorkerService.class));
	}

}
