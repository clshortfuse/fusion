package im.fsn.messenger.providers;

import im.fsn.messenger.MessageItem;

import android.app.Activity;
import android.content.Context;
import android.os.Messenger;

public abstract class IMProvider {

	protected Context mContext;
	protected Messenger mMessenger;

	public static int getDarkColor(IMProviderTypes type) {
		switch (type) {
		case SMS:
			return 0xff669900;
		case GVoice:
			return 0xff0099cc;
		}
		return mDarkColor;
	}

	public static int getLightColor(IMProviderTypes type) {
		switch (type) {
		case SMS:
			return 0xff99cc00;
		case GVoice:
			return 0xff33b5e5;
		}
		return mLightColor;
	}

	public IMProvider(Context mContext, Messenger mMessenger) {
		this.mContext = mContext;
		this.mMessenger = mMessenger;
	}

	public static String[] IMServiceShortNames = new String[] { "SMS", "GV" };
	public static String[] IMServiceNames = new String[] { "Text Messaging",
			"Google Voice" };

	public static enum IMProviderTypes {
		SMS, GVoice
	}

	public abstract IMProviderTypes getIMProviderType();

	public void start() {

	}

	public void stop() {
	}

	public abstract boolean syncMessages(boolean force);

	public abstract MessageItem sendMessage(MessageItem item);

	public abstract String parseAddress(String address);

	public abstract boolean isBusy();

	public abstract boolean isAvailable();

	public abstract void setAvailable(boolean isAvailable);

	public abstract String getSender();

	public abstract boolean isRunning();

	public abstract boolean markAsRead(MessageItem[] items);

	public abstract boolean markAsRead(MessageItem item);

	public abstract boolean delete(MessageItem item);

	public abstract boolean delete(MessageItem[] items);

	public abstract void disableSyncLock();

	public boolean usesSMSAddresses() {
		return false;
	}

	private int mNextSyncTime = -1;

	public void performSync() {

	}

	public static IMProvider findIMProvider(IMProviderTypes providerType,
			IMProvider[] providers) {
		if (providers == null)
			return null;
		for (int i = 0; i < providers.length; i++)
			if (providers[i].getIMProviderType() == providerType)
				return providers[i];
		return null;
	}

	protected Activity mActivity;

	public Activity getActivity() {
		return mActivity;
	}

	public void setActivity(Activity activity) {
		this.mActivity = activity;
	}

	protected boolean mRequiresRestart = false;

	public boolean getRequiresRestart() {
		return this.mRequiresRestart;
	}

	public void setRequiresRestart(boolean requiresRestart) {
		this.mRequiresRestart = requiresRestart;
	}

	protected static int mLightColor = 0xffffffff; // white
	protected static int mDarkColor = 0xff000000; // black

	public int getDarkColor() {
		return mDarkColor;
	}

	public void setDarkColor(int color) {
		this.mDarkColor = color;
	}

	public int getLightColor() {
		return mLightColor;
	}

	public void setLightColor(int color) {
		this.mLightColor = color;
	}

	public int getNextSyncTime() {
		return mNextSyncTime;
	}

	public void setNextSyncTime(int nextSyncTime) {
		this.mNextSyncTime = nextSyncTime;
	}

}
