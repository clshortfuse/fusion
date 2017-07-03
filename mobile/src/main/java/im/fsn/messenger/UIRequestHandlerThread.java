package im.fsn.messenger;

import im.fsn.messenger.DBMessagesAdapter.InsertOrUpdateResult;
import im.fsn.messenger.MessageItem.MessageStatuses;
import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.message.BasicNameValuePair;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;

public class UIRequestHandlerThread extends Thread {

	private final Object syncObject = new Object();
	private boolean isBusy = false;
	private DBMessagesAdapter dbMessages;
	private Messenger callbackMessenger;
	private boolean phoneContactListRefreshRequested = true;
	private Context mContext;
	private TelephonyManager telManager;
	private PhoneNumberUtil phoneUtil = null;

	private CacheAdapter dbCache;

	public UIRequestHandlerThread(Context context,
			DBMessagesAdapter dbMessages, Messenger callbackMessenger) {
		this.mContext = context;
		this.dbMessages = dbMessages;
		this.dbCache = new CacheAdapter(context);
		this.callbackMessenger = callbackMessenger;
		this.telManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);
		this.phoneUtil = PhoneNumberUtil.getInstance();
		this.setPriority(MIN_PRIORITY);
	}

	public void forceResume() {
		synchronized (syncObject) {
			syncObject.notifyAll();
		}
	}

	public void setCallbackMessenger(Messenger callbackMessenger) {
		this.callbackMessenger = callbackMessenger;
	}

	public boolean queuePhoneContactListRefresh() {

		synchronized (syncObject) {
			this.phoneContactListRefreshRequested = true;
			syncObject.notifyAll();
		}
		return true;
	}

	@Override
	public void run() {
		long startTime = SystemClock.elapsedRealtime();
		this.setName("UIRequestHandlerThread-" + startTime);
		while (!Thread.interrupted()) {

			while (phoneContactListRefreshRequested) {
				phoneContactListRefreshRequested = false;
				getPhoneContactsList();
			}

			for (int i = 0; i < MemoryCache.IMProviders.length; i++) {
				boolean forceSync = false;
				if (MemoryCache.IMProviders[i].isRunning()
						&& (MemoryCache.IMProviders[i].getRequiresRestart() || !MemoryCache.IMProviders[i]
								.isAvailable()))
					MemoryCache.IMProviders[i].stop();
				if (MemoryCache.IMProviders[i].isAvailable()
						&& !MemoryCache.IMProviders[i].isRunning()) {
					MemoryCache.IMProviders[i].start();
					forceSync = true;
				}
				if (MemoryCache.IMProviders[i].isAvailable()
						&& MemoryCache.IMProviders[i].isRunning())
					MemoryCache.IMProviders[i].syncMessages(forceSync);
			}
			synchronized (syncObject) {
				if (phoneContactListRefreshRequested == false)
					try {

						syncObject.wait(60000);
					} catch (InterruptedException e) {
						break;
					}
			}
		}

	}

	public void getPhoneContactsList() {

		ContentResolver cr = mContext.getContentResolver();
		Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;

		String colId = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;

		String colDisplayName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;

		String colNumber = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String colType = ContactsContract.CommonDataKinds.Phone.TYPE;
		String colLabel = ContactsContract.CommonDataKinds.Phone.LABEL;
		String[] projection = new String[] { colId, colDisplayName, colNumber,
				colType, colLabel };

		Cursor cur = cr.query(uri, projection, null, null, colId);
		if (cur == null)
			return;
		List<ContactItem> contacts = new ArrayList<ContactItem>();
		long lastId = -1;
		ContactItem lastContactItem = null;
		long tStart = SystemClock.elapsedRealtime();
		long t1 = 0;
		long t2 = 0;
		long t3 = 0;

		Map<String, String> parsedSMS = dbCache.getList();
		long t4 = SystemClock.elapsedRealtime();
		while (cur.moveToNext()) {
			ContactItem contactItem = new ContactItem();

			long id = cur.getLong(0);
			String contactAddress = cur.getString(2);
			int addressType = -1;
			if (!cur.isNull(3))
				addressType = cur.getInt(3);
			String addressLabel = null;
			if (!cur.isNull(4))
				addressLabel = cur.getString(4);
			if (TextUtils.isEmpty(contactAddress))
				continue;

			if (id == lastId) {
				contacts.remove(lastContactItem);
				contactItem = lastContactItem;
			} else {
				contactItem.setId(id);
				String name = cur.getString(1);
				contactItem.setDisplayName(name);
				contactItem.setContactAddresses(new ContactAddress[0]);
			}
			for (int i = 0; i < MemoryCache.IMProviders.length; i++) {
				IMProvider s = MemoryCache.IMProviders[i];
				ContactAddress ca = new ContactAddress();
				ca.setDisplayAddress(contactAddress);
				ca.setAddressType(addressType);
				ca.setAddressLabel(addressLabel);

				String parsed = null;
				if (s.usesSMSAddresses()) {
					t1 = SystemClock.elapsedRealtime();

					parsed = parsedSMS.get(contactAddress);
					if (parsed == null) {
						parsed = PhoneUtilsLite.parseAddress(contactAddress,
								phoneUtil, telManager.getNetworkCountryIso()
										.toUpperCase(Locale.US));
						if (parsed != null)
							parsedSMS.put(contactAddress, parsed);
					}
					t2 = SystemClock.elapsedRealtime();
					t3 += t2 - t1;

				} else
					parsed = s.parseAddress(contactAddress);
				if (TextUtils.isEmpty(parsed))
					continue;
				ca.setParsedAddress(parsed);
				ca.setIMProviderType(s.getIMProviderType());

				ContactAddress[] oldArray = contactItem.getContactAddresses();
				boolean isDupe = false; // (ie: +18005551234 vs (800)
										// 555-1234)

				ContactAddress[] newArray = new ContactAddress[oldArray.length + 1];
				for (int j = 0; j < oldArray.length; j++) {
					if (oldArray[j].equals(ca)) {
						isDupe = true;
						break;
					}
					newArray[j] = oldArray[j];
				}
				if (isDupe)
					continue;

				newArray[newArray.length - 1] = ca;
				contactItem.setContactAddresses(newArray);
				if (contactItem.getDisplayContactAddress() == null)
					contactItem.setDisplayContactAddress(contactAddress);

			}

			if (contactItem.getContactAddresses() != null
					&& contactItem.getContactAddresses().length != 0) {
				contacts.add(contactItem);
				lastId = id;
				lastContactItem = contactItem;
			} else {
				Log.w("UIRequestHandlerThread", "Unreadable Contact: "
						+ contactItem.getDisplayName());
			}
		}
		cur.close();
		Message msg = Message.obtain(null,
				HandlerMessages.MSG_PHONECONTACTSRECEIVED, contacts);
		try {
			this.callbackMessenger.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		long t5 = SystemClock.elapsedRealtime();
		dbCache.putList(parsedSMS);
		long t6 = SystemClock.elapsedRealtime();
		long tEnd = SystemClock.elapsedRealtime();

		Log.w("UIRequestHandlerThread", "Phone Contacts Parse Time: " + t3
				+ "ms");
		Log.w("UIRequestHandlerThread", "Phone Contacts Read Cache: "
				+ (t4 - tStart) + "ms");
		Log.w("UIRequestHandlerThread", "Phone Contacts Write Cache: "
				+ (t6 - t5) + "ms");
		Log.w("UIRequestHandlerThread", "Phone Contacts Total time: "
				+ (tEnd - tStart) + "ms");
		return;

	}

	public static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			// Calculate ratios of height and width to requested height and
			// width
			final int heightRatio = Math.round((float) height
					/ (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);

			// Choose the smallest ratio as inSampleSize value, this will
			// guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
		}

		return inSampleSize;
	}

	public static Bitmap decodeSampledBitmapFromStream(InputStream in,
			int reqWidth, int reqHeight) {

		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;

		BitmapFactory.decodeStream(in, null, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth,
				reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeStream(in, null, options);
	}
}
