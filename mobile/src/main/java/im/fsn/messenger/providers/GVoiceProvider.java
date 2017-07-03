package im.fsn.messenger.providers;

import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.MessageItem;
import im.fsn.messenger.PhoneUtilsLite;
import im.fsn.messenger.Utils;
import im.fsn.messenger.MessageItem.MessageStatuses;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class GVoiceProvider extends IMProvider {

	private final Object syncObject = new Object();
	private TelephonyManager telManager;
	private SharedPreferences mPrefs;

	public GVoiceProvider(Context mContext, Messenger mMessenger,
			String accountName) {
		super(mContext, mMessenger);
		this.setAccountName(accountName);
		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		this.lastInboxRefresh = System.currentTimeMillis()
				- (1000L * 60L * 60L * 24L * 7L);
		this.telManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);

	}

	private String accountName;
	private String authToken;

	private long lastRegisterAttempt = 0L;

	@Override
	public IMProviderTypes getIMProviderType() {
		return IMProviderTypes.GVoice;
	}

	@Override
	public boolean usesSMSAddresses() {
		return true;
	}

	@Override
	public void start() {
		this.running = true;
		this.lastSyncTime = mPrefs.getLong("lastGVSyncTime", 0);
		if (!intentRegistered)
			notificationRegistration(true);
		this.mRequiresRestart = false;
	}

	private static String GVNotifificationCategory = "com.google.android.apps.googlevoice.INBOX_NOTIFICATION";
	private static String C2DMIntent = "com.google.android.c2dm.intent.RECEIVE";
	private static String GVAppMessageReceived = "com.google.android.apps.googlevoice.SMS_RECEIVED";

	private boolean intentRegistered;
	private static final Uri URI = Uri
			.parse("content://com.google.android.gsf.gservices");
	private static final String ID_KEY = "android_id";

	private String getAndroidId(Context ctx) {

		String[] params = { ID_KEY };
		Cursor c = ctx.getContentResolver()
				.query(URI, null, null, params, null);
		if (c == null)
			return null;
		if (!c.moveToFirst() || c.getColumnCount() < 2) {
			c.close();
			return null;
		}

		try {
			return Long.toHexString(Long.parseLong(c.getString(1)));
		} catch (NumberFormatException e) {
			return null;
		} finally {
			c.close();
		}
	}

	private long lastNotificationCheckInRequest = 0L;
	private long lastNotificationCheckInResponse = 0L;

	private void notificationCheckIn() {
		if (SystemClock.elapsedRealtime() - lastNotificationCheckInRequest < 1000 * 60 * 5) {
			Log.d("GVoiceProvider", "Check in skipped");
			return;
		}
		notificationRegistration(false);
		notificationRegistration(true);
		String androidId = getAndroidId(mContext);
		String routingInfo = new String();
		if (android.os.Build.VERSION.SDK_INT < 8)
			routingInfo = String.format("gtalk://%s#android-%s", new Object[] {
					this.accountName, androidId });
		else
			routingInfo = String.format("android://%s", androidId);

		if (this.authToken != null) {

			StringBuilder jsonBuilder = new StringBuilder();
			jsonBuilder.append('{');
			// open1
			jsonBuilder.append(JSONObject.quote("request")).append(':');
			jsonBuilder.append('{');
			// open2
			jsonBuilder.append(JSONObject.quote("destination")).append(':');
			jsonBuilder.append('{');
			// open3
			jsonBuilder.append(JSONObject.quote("androidPrimaryId"))
					.append(':');
			jsonBuilder.append(JSONObject.quote(this.accountName));
			jsonBuilder.append(',');

			jsonBuilder.append(JSONObject.quote("routingInfo")).append(':');
			jsonBuilder.append(JSONObject.quote(routingInfo).replace("\\", ""));
			jsonBuilder.append(',');

			jsonBuilder.append(JSONObject.quote("type")).append(':');
			jsonBuilder.append(4);

			jsonBuilder.append('}');
			// close3
			jsonBuilder.append(',');
			jsonBuilder.append(JSONObject.quote("reason")).append(':');
			jsonBuilder.append(2); // MANUAL_REFRESH
			jsonBuilder.append(',');

			jsonBuilder.append(JSONObject.quote("token")).append(':');
			jsonBuilder.append(JSONObject.quote("").replace("\\", ""));

			jsonBuilder.append('}');
			// close2
			jsonBuilder.append('}');
			// close1
			//
			// format
			// {
			// "request": {
			// "destination": {
			// "androidPrimaryId": "ACCOUNT_NAME",
			// "routingInfo": "ROUTING_INFO",
			// "type": INT_TYPE
			// },
			// "reason": INT_TYPE,
			// "token": "token"
			// }
			// }

			String json = jsonBuilder.toString();
			lastNotificationCheckInRequest = SystemClock.elapsedRealtime();
			Log.d("GVoiceProvider", "Trying check in...");
			CheckInThread t = new CheckInThread(json);
			t.start();

		}
	}

	private void notificationRegistration(boolean register) {
		String androidId = getAndroidId(mContext);
		String routingInfo = new String();
		if (android.os.Build.VERSION.SDK_INT < 8)
			routingInfo = String.format("gtalk://%s#android-%s", new Object[] {
					this.accountName, androidId });
		else
			routingInfo = String.format("android://%s", androidId);

		if (this.authToken != null) {

			StringBuilder jsonBuilder = new StringBuilder();
			jsonBuilder.append('{');
			// open1
			jsonBuilder.append(JSONObject.quote("request")).append(':');
			jsonBuilder.append('{');
			// open2
			jsonBuilder.append(JSONObject.quote("destination")).append(':');
			jsonBuilder.append('{');
			// open3
			jsonBuilder.append(JSONObject.quote("androidPrimaryId"))
					.append(':');
			jsonBuilder.append(JSONObject.quote(this.accountName));
			jsonBuilder.append(',');

			// item1end
			jsonBuilder.append(JSONObject.quote("eventPayload")).append(':');
			jsonBuilder.append('[');
			// openarray1

			jsonBuilder.append('{');
			// open5
			jsonBuilder.append(JSONObject.quote("2147483647")).append(':');
			jsonBuilder.append(JSONObject.quote(GVNotifificationCategory));
			jsonBuilder.append('}');
			// close5
			jsonBuilder.append(']');
			// closearray1
			jsonBuilder.append(',');
			// item2end
			jsonBuilder.append(JSONObject.quote("routingInfo")).append(':');
			jsonBuilder.append(JSONObject.quote(routingInfo).replace("\\", ""));
			jsonBuilder.append(',');
			// item3end
			jsonBuilder.append(JSONObject.quote("type")).append(':');
			jsonBuilder.append(4);
			// item4end
			jsonBuilder.append('}');
			// close3
			jsonBuilder.append('}');
			// close2
			jsonBuilder.append('}');
			// close1
			//
			// format
			// {
			// "request": {
			// "destination": {
			// "androidPrimaryId": "ACCOUNT_NAME",
			// "eventPayload": [
			// {
			// "2147483647": "NOTIFICATION_INTENT"
			// }
			// ],
			// "routingInfo": "ROUTING_INFO",
			// "type": INT_TYPE
			// }
			// }

			String json = jsonBuilder.toString();
			if (register) {
				AsyncGetResponseThread t = new AsyncGetResponseThread("ud",
						json);
				t.start();
				IntentFilter c2dmIntentFilter = new IntentFilter(C2DMIntent);
				c2dmIntentFilter.addCategory(GVNotifificationCategory);
				IntentFilter gvAppIntentFilter = new IntentFilter(
						GVAppMessageReceived);

				mContext.registerReceiver(InboxNotificationReceiver,
						c2dmIntentFilter);
				mContext.registerReceiver(InboxNotificationReceiver,
						gvAppIntentFilter);
				Log.d("fusion", "Google Voice notifications registered");
				this.intentRegistered = true;
			} else {
				AsyncGetResponseThread t = new AsyncGetResponseThread("rd",
						json);
				t.start();
				mContext.unregisterReceiver(InboxNotificationReceiver);
				Log.d("fusion", "Google Voice notifications unregistered");
				this.intentRegistered = false;
			}
			// Yes, 'rd' and 'ud' are backwards.
			// In fact, I think they both do the same thing. (Register)

		}

	}

	private long lastInboxRefresh = 0L;

	@Override
	public boolean markAsRead(MessageItem[] items) {
		List<String> conversationIds = new ArrayList<String>();
		for (MessageItem m : items) {
			if (m.getIMProviderType() != this.getIMProviderType())
				continue;
			String conversationId = m.getImportConversationId();
			if (!conversationIds.contains(conversationId)) {
				conversationIds.add(conversationId);
			}
		}
		String[] idArray = new String[conversationIds.size()];
		conversationIds.toArray(idArray);
		return markAsRead(idArray);
	}

	@Override
	public boolean markAsRead(MessageItem msgItem) {
		if (msgItem.getIMProviderType() != this.getIMProviderType())
			return false;
		return markAsRead(new String[] { msgItem.getImportConversationId() });
	}

	private boolean markAsRead(String[] conversationIds) {
		StringBuilder jsonBuilder = new StringBuilder();
		jsonBuilder.append('{');
		// open1
		jsonBuilder.append(JSONObject.quote("request")).append(':');
		jsonBuilder.append('{');
		// open2
		jsonBuilder.append(JSONObject.quote("conversationId")).append(':');
		jsonBuilder.append('[');
		// openarray1
		for (int i = 0; i < conversationIds.length; i++) {
			jsonBuilder.append(JSONObject.quote(conversationIds[i]));
			if (i != conversationIds.length - 1)
				jsonBuilder.append(',');
		}
		jsonBuilder.append(']');
		// closearray1
		jsonBuilder.append(',');
		jsonBuilder.append(JSONObject.quote("removeLabel")).append(':');
		jsonBuilder.append('[');
		// openarray2
		jsonBuilder.append(JSONObject.quote("unread"));
		jsonBuilder.append(']');
		// closearray2
		jsonBuilder.append('}');
		// open2
		jsonBuilder.append('}');
		// open1
		String json = jsonBuilder.toString();
		AsyncGetResponseThread t = new AsyncGetResponseThread("ucl", json);
		t.start();
		return true;
	}

	private class AsyncGetResponseThread extends Thread {
		private String mQuery;
		private String mApi;

		public AsyncGetResponseThread(String api, String query) {
			this.mQuery = query;
			this.mApi = api;
		}

		@Override
		public void run() {
			String result = getResponse(mApi, mQuery, authToken);
			if (result == null)
				return;
			JSONObject jHttpResponse;
			try {
				jHttpResponse = new JSONObject(result);
			} catch (JSONException e) {
				return;
			}
			JSONObject jResponse;
			try {
				jResponse = jHttpResponse.getJSONObject("response");
			} catch (JSONException e1) {
				try {
					JSONObject jError = jHttpResponse.getJSONObject("error");
					int responseCode = jError.getInt("code");
					if (responseCode == 401)
						getNewCredentials();
				} catch (JSONException e2) {

				}
				return;
			}
		}
	}

	private class CheckInThread extends Thread {

		private String mQuery;

		public CheckInThread(String query) {
			this.mQuery = query;
		}

		@Override
		public void run() {
			String result = null;

			result = getResponse("in", mQuery, authToken);

			if (result == null)
				return;
			// parse
			JSONObject jHttpResponse;
			try {
				jHttpResponse = new JSONObject(result);
			} catch (JSONException e) {
				return;
			}

			JSONObject jResponse;
			try {
				jResponse = jHttpResponse.getJSONObject("response");
			} catch (JSONException e1) {
				try {
					Log.d("GVoiceProvider", "Check in failed");
					JSONObject jError = jHttpResponse.getJSONObject("error");
					int responseCode = jError.getInt("code");
					if (responseCode == 401)
						getNewCredentials();
				} catch (JSONException e2) {

				}
				return;
			}

			GVoiceProvider.this.lastNotificationCheckInResponse = SystemClock
					.elapsedRealtime();
			Log.d("GVoiceProvider", "Checked in");
		}

	}

	private class ListConversationsThread extends Thread {

		private String mQuery;

		public ListConversationsThread(String query) {
			this.mQuery = query;
		}

		@Override
		public void run() {
			long startTime = SystemClock.elapsedRealtime();
			this.setName("ListConversationsThread-" + startTime);
			// get
			String result = null;

			result = getResponse("lc", mQuery, authToken);

			if (result == null)
				return;
			// parse
			JSONObject jHttpResponse;
			try {
				jHttpResponse = new JSONObject(result);
			} catch (JSONException e) {
				return;
			}

			JSONObject jResponse;
			try {
				jResponse = jHttpResponse.getJSONObject("response");
			} catch (JSONException e1) {
				try {
					JSONObject jError = jHttpResponse.getJSONObject("error");
					int responseCode = jError.getInt("code");
					if (responseCode == 401)
						getNewCredentials();
				} catch (JSONException e2) {

				}
				return;
			}

			JSONArray conversations;
			try {
				conversations = jResponse.getJSONArray("conversation");
			} catch (JSONException e) {
				// Response with no conversations???
				return;
			}

			int conversationCount = conversations.length();
			for (int i = 0; i < conversationCount; i++) {
				try {
					JSONObject conversation = conversations.getJSONObject(i);

					boolean isRead = conversation.getBoolean("read");
					String conversationId = conversation.getString("id");
					JSONArray messages = conversation.getJSONArray("phoneCall");
					int messageCount = messages.length();
					MessageItem[] messageArray = new MessageItem[messageCount];
					for (int j = 0; j < messageCount; j++) {
						JSONObject message = messages.getJSONObject(j);
						MessageItem m = new MessageItem();

						m.setImportConversationId(conversationId);
						long time = decodeRfc3339(message
								.getString("startTime"));
						m.setCompletionDateTime(time);
						m.setCreationDateTime(time);
						m.setExternalAddress(parseAddress(message
								.getJSONObject("contact").getString(
										"phoneNumber")));

						m.setImportMessageId(message.getString("id"));
						m.setIMProviderType(IMProviderTypes.GVoice);
						boolean isIncoming = message.getString("type").equals(
								"SMS_IN");
						m.setRead(!isIncoming ? true : isRead);
						m.setIncoming(isIncoming);
						m.setInternalAddress(GVoiceProvider.this.accountName);
						m.setMessageStatus(MessageStatuses.Sent);
						m.setText(message.getString("messageText"));
						messageArray[j] = m;
					}

					Message osMessage = Message
							.obtain(null,
									HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED,
									messageArray);
					try {
						synchronized (syncObject) {
							mMessenger.send(osMessage);
						}
					} catch (RemoteException e) {

					}

				} catch (JSONException e1) {
					e1.printStackTrace();
				}
			}

		}

	}

	private void listConversations(int count) {
		if (this.syncLocked)
			return;
		StringBuilder jsonBuilder = new StringBuilder();
		jsonBuilder.append('{');
		// open1
		jsonBuilder.append(JSONObject.quote("request")).append(':');
		jsonBuilder.append('{');
		// open2
		jsonBuilder.append(JSONObject.quote("label")).append(':');
		jsonBuilder.append('[');
		// openarray1
		jsonBuilder.append(JSONObject.quote("sms"));
		jsonBuilder.append(']');
		// closearray1
		jsonBuilder.append(',');
		jsonBuilder.append(JSONObject.quote("offset")).append(':');
		jsonBuilder.append("0");
		jsonBuilder.append(',');
		jsonBuilder.append(JSONObject.quote("limit")).append(':');
		jsonBuilder.append(String.valueOf(count));
		jsonBuilder.append(',');
		jsonBuilder.append(JSONObject.quote("wantTranscript")).append(':');
		jsonBuilder.append("false");
		jsonBuilder.append('}');
		// close2
		jsonBuilder.append('}');
		// close1

		String json = jsonBuilder.toString();
		ListConversationsThread t = new ListConversationsThread(json);
		t.start();

		this.lastInboxRefresh = SystemClock.elapsedRealtime();
	}

	private static void appendInt(StringBuilder paramStringBuilder,
			int paramInt1, int paramInt2) {
		if (paramInt1 < 0) {
			paramStringBuilder.append('-');
			paramInt1 = -paramInt1;
		}
		int i = paramInt1;
		while (i > 0) {
			i /= 10;
			paramInt2--;
		}
		for (int j = 0; j < paramInt2; j++)
			paramStringBuilder.append('0');
		if (paramInt1 != 0)
			paramStringBuilder.append(paramInt1);
	}

	private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	public static long decodeRfc3339(String str) throws NumberFormatException {
		try {
			Calendar dateTime = new GregorianCalendar(GMT);
			int year = Integer.parseInt(str.substring(0, 4));
			int month = Integer.parseInt(str.substring(5, 7)) - 1;
			int day = Integer.parseInt(str.substring(8, 10));
			int tzIndex;
			int length = str.length();
			boolean dateOnly = length <= 10
					|| Character.toUpperCase(str.charAt(10)) != 'T';
			if (dateOnly) {
				dateTime.set(year, month, day);
				tzIndex = 10;
			} else {
				int hourOfDay = Integer.parseInt(str.substring(11, 13));
				int minute = Integer.parseInt(str.substring(14, 16));
				int second = Integer.parseInt(str.substring(17, 19));
				dateTime.set(year, month, day, hourOfDay, minute, second);
				if (str.charAt(19) == '.') {
					int milliseconds = Integer.parseInt(str.substring(20, 23));
					dateTime.set(Calendar.MILLISECOND, milliseconds);
					tzIndex = 23;
				} else {
					tzIndex = 19;
				}
			}
			Integer tzShiftInteger = null;
			long value = dateTime.getTimeInMillis();
			if (length > tzIndex) {
				int tzShift;
				if (Character.toUpperCase(str.charAt(tzIndex)) == 'Z') {
					tzShift = 0;
				} else {
					tzShift = Integer.parseInt(str.substring(tzIndex + 1,
							tzIndex + 3))
							* 60
							+ Integer.parseInt(str.substring(tzIndex + 4,
									tzIndex + 6));
					if (str.charAt(tzIndex) == '-') {
						tzShift = -tzShift;
					}
					value -= tzShift * 60000;
				}
				tzShiftInteger = tzShift;
			}
			return value;
		} catch (StringIndexOutOfBoundsException e) {
			throw new NumberFormatException("Invalid date/time format.");
		}
	}

	public static String encodeRfc3339(long value) {
		StringBuilder sb = new StringBuilder();

		Calendar dateTime = new GregorianCalendar(GMT);
		long localTime = value;
		Integer tzShift = 0;
		if (tzShift != null) {
			localTime += tzShift.longValue() * 60000;
		}
		dateTime.setTimeInMillis(localTime);

		appendInt(sb, dateTime.get(Calendar.YEAR), 4);
		sb.append('-');
		appendInt(sb, dateTime.get(Calendar.MONTH) + 1, 2);
		sb.append('-');
		appendInt(sb, dateTime.get(Calendar.DAY_OF_MONTH), 2);

		sb.append('T');
		appendInt(sb, dateTime.get(Calendar.HOUR_OF_DAY), 2);
		sb.append(':');
		appendInt(sb, dateTime.get(Calendar.MINUTE), 2);
		sb.append(':');
		appendInt(sb, dateTime.get(Calendar.SECOND), 2);

		if (dateTime.isSet(Calendar.MILLISECOND)) {
			sb.append('.');
			appendInt(sb, dateTime.get(Calendar.MILLISECOND), 3);
		}

		if (tzShift != null) {

			if (tzShift.intValue() == 0) {

				sb.append('Z');

			} else {

				int absTzShift = tzShift.intValue();
				if (tzShift > 0) {
					sb.append('+');
				} else {
					sb.append('-');
					absTzShift = -absTzShift;
				}

				int tzHours = absTzShift / 60;
				int tzMinutes = absTzShift % 60;
				appendInt(sb, tzHours, 2);
				sb.append(':');
				appendInt(sb, tzMinutes, 2);
			}
		}
		return sb.toString();
	}

	private long lastNotificationId = 0L;
	private BroadcastReceiver InboxNotificationReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(C2DMIntent)) {
				if (intent.hasCategory(GVNotifificationCategory)) {
					Bundle bundle = intent.getExtras();
					if (bundle == null)
						return;
					if (!bundle.getString("call_type").equals("10"))
						return;
					Long currentNotificationId = Long.parseLong(bundle
							.getString("notification_id"));
					if (currentNotificationId <= lastNotificationId)
						return;
					String text = bundle.getString("call_content");
					if (text == null)
						return;

					MessageItem i = new MessageItem();
					i.setText(text);
					i.setIncoming(true);
					i.setIMProviderType(IMProviderTypes.GVoice);
					i.setMessageStatus(MessageStatuses.Delivered);
					i.setExternalAddress(parseAddress(bundle
							.getString("sender_address")));
					long ts = Long.parseLong(bundle.getString("call_time"));
					i.setCompletionDateTime(ts);
					i.setCreationDateTime(ts);
					i.setRead(false);
					i.setImportConversationId(bundle
							.getString("conversation_id"));
					i.setImportMessageId(bundle.getString("call_id"));
					i.setInternalAddress(accountName);
					Message m = Message.obtain(null,
							HandlerMessages.MSG_UNPROCESSEDMESSAGERECEIVED, i);
					try {
						mMessenger.send(m);
					} catch (RemoteException e) {

					}
					lastNotificationId = currentNotificationId;

				}
			} else if (intent.getAction().equals(GVAppMessageReceived))
				listConversations(2);
		}

	};

	public static String getResponse(String api, String postData,
			String authToken) {
		try {

			URL loginURL = new URL(
					"https://www.googleapis.com/voice/v0.1internal/" + api
							+ "?key=AIzaSyAgMHwukoX_J2jsWbKTDRfrCOG4s27MWWY");
			HttpURLConnection httpConnection = (HttpURLConnection) loginURL
					.openConnection();
			httpConnection.setUseCaches(false);

			httpConnection.setRequestMethod("POST");
			httpConnection.setRequestProperty("authorization", "OAuth "
					+ authToken);
			httpConnection.setRequestProperty("user-agent", "fusion (gzip)");
			httpConnection.setRequestProperty("content-encoding", "gzip");
			httpConnection.setRequestProperty("accept-encoding", "gzip");
			httpConnection.setRequestProperty("content-type",
					"application/json; charset=UTF-8");
			httpConnection.setConnectTimeout(15 * 1000);
			httpConnection.setReadTimeout(15 * 1000);

			httpConnection.setDoOutput(true);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			GZIPOutputStream gOut = new GZIPOutputStream(baos);
			byte[] b = postData.getBytes("UTF-8");
			gOut.write(b);
			gOut.flush();
			gOut.close();
			byte[] bPostData = baos.toByteArray();
			httpConnection.setRequestProperty("content-length",
					String.valueOf(bPostData.length));

			OutputStream os = httpConnection.getOutputStream();
			os.write(bPostData);
			os.flush();
			os.close();

			httpConnection.connect();
			int responseCode = httpConnection.getResponseCode();
			InputStream gzippedResponse = null;
			switch (responseCode) {

			default:
				gzippedResponse = httpConnection.getErrorStream();
			case HttpURLConnection.HTTP_OK:
				if (gzippedResponse == null)
					gzippedResponse = httpConnection.getInputStream();
				InputStream ungzippedResponse = new GZIPInputStream(
						gzippedResponse);
				InputStreamReader reader = new InputStreamReader(
						ungzippedResponse, "UTF-8");
				StringWriter writer = new StringWriter();

				char[] buffer = new char[10240];
				for (int length = 0; (length = reader.read(buffer)) > 0;) {
					writer.write(buffer, 0, length);
				}

				String response = writer.toString();

				return response;
			}

		} catch (UnsupportedEncodingException e) {

			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void test() {

	}

	@Override
	public void stop() {
		if (intentRegistered)
			notificationRegistration(false);
		this.running = false;

	}

	private boolean mBusy = false;

	private class SendMessageThread extends Thread {
		private MessageItem msgItem;

		public SendMessageThread(MessageItem msgItem) {
			this.msgItem = msgItem;
		}

		private boolean parseResponse(String response) {
			if (response == null) {
				return false;
			}
			JSONObject jHttpResponse;
			try {
				jHttpResponse = new JSONObject(response);
			} catch (JSONException e) {
				return false;
			}

			JSONObject jResponse;
			try {
				jResponse = jHttpResponse.getJSONObject("response");
			} catch (JSONException e1) {
				try {
					JSONObject jError = jHttpResponse.getJSONObject("error");
					int responseCode = jError.getInt("code");
					if (responseCode == 401) {
						getNewCredentials();
						return false;
					}
				} catch (JSONException e2) {

				}
				return false;
			}

			String conversationId;
			try {
				conversationId = jResponse.getString("conversationId");
				if (conversationId != null) {
					msgItem.setImportConversationId(conversationId);
					syncLocked = true;
					// NO MESSAGEID!!! Refresh (sigh) the entire conversation
					// even though this is asynchronous, out-of-order execution
					// wouldn't happen with the queue system.
					// ...made changes, could happen
					return true;
				}
			} catch (JSONException e) {
				// Response with no conversationid???
				e.printStackTrace();
			}
			return false;

		}

		@Override
		public void run() {

			StringBuilder jsonRequest = new StringBuilder();
			jsonRequest
					.append("{\"request\":{\"createPhonebookIfNotExist\":false,\"outgoingDestination\":[\"");
			jsonRequest.append(msgItem.getExternalAddress());
			jsonRequest.append("\"],\"smsMessage\":");
			String escapedText = JSONObject.quote(msgItem.getText());

			jsonRequest.append(escapedText);
			jsonRequest.append("}}");

			String response = getResponse("sms", jsonRequest.toString(),
					authToken);

			synchronized (syncObject) {
				msgItem.setInternalAddress(accountName);
				int handleMessage;
				if (this.parseResponse(response)) {
					handleMessage = HandlerMessages.MSG_UNPROCESSEDMESSAGESENT;
					msgItem.setMessageStatus(MessageStatuses.Sent);
				} else {
					handleMessage = HandlerMessages.MSG_UNPROCESSEDMESSAGEFAILED;
					msgItem.setMessageStatus(MessageStatuses.Failed);
				}
				msgItem.setCompletionDateTime(System.currentTimeMillis());
				mBusy = false;
				Message m = Message.obtain(null, handleMessage, msgItem);
				try {
					mMessenger.send(m);
				} catch (RemoteException e) {
				}
			}

		}
	}

	@Override
	public MessageItem sendMessage(MessageItem msgItem) {

		mBusy = true;
		SendMessageThread t = new SendMessageThread(msgItem);
		t.start();
		msgItem.setMessageStatus(MessageItem.MessageStatuses.OnRoute);
		return msgItem;

	}

	private void getNewCredentials() {
		AccountManager accountManager = AccountManager.get(mContext);
		accountManager.invalidateAuthToken(this.accountName, this.authToken);
		lastSyncTime = 0L;
		this.setAuthToken(null);
		Message m = Message.obtain(null, HandlerMessages.MSG_GETNEWCREDENTIALS);
		try {
			mMessenger.send(m);
		} catch (RemoteException e) {
		}
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
		this.setAvailable(!TextUtils.isEmpty(this.accountName)
				&& !TextUtils.isEmpty(this.authToken));

	}

	@Override
	public boolean isBusy() {
		return mBusy;
	}

	public void setAuthToken(String authToken) {
		if (TextUtils.isEmpty(authToken))
			return;

		if (this.authToken != null && authToken != null
				&& this.authToken.equals(authToken))
			return;

		this.authToken = authToken;

		this.setAvailable(!TextUtils.isEmpty(this.accountName)
				&& !TextUtils.isEmpty(this.authToken));
		mRequiresRestart = true;
	}

	@Override
	public String getSender() {
		return this.accountName;
	}

	private boolean available;

	@Override
	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	private boolean running;

	private int mLightColor = 0xff33b5e5; // holo_blue_light
	private int mDarkColor = 0xff0099cc; // holo_blue_dark

	@Override
	public int getDarkColor() {
		return mDarkColor;
	}

	@Override
	public void setDarkColor(int color) {
		this.mDarkColor = color;
	}

	@Override
	public int getLightColor() {
		return mLightColor;
	}

	@Override
	public void setLightColor(int color) {
		this.mLightColor = color;
	}

	@Override
	public boolean delete(MessageItem item) {
		return false;
	}

	@Override
	public boolean delete(MessageItem[] items) {
		return false;
	}

	private long lastSyncTime = 0L;
	private long syncRepeatTime = 5L * 60 * 1000L;

	@Override
	public boolean syncMessages(boolean force) {
		long now = SystemClock.elapsedRealtime();
		if (now - lastNotificationCheckInResponse > 1000 * 60 * 30)
			this.notificationCheckIn();

		if (!force && now - syncRepeatTime < lastSyncTime) {
			Log.d("GVoiceProvider", "Sync Skipped");
			return true;
		}
		listConversations(20);
		this.lastSyncTime = now;
		Log.d("GVoiceProvider", "Sync Started");
		mPrefs.edit().putLong("lastGVSyncTime", this.lastSyncTime).commit();
		return true;
	}

	private boolean syncLocked = false;

	@Override
	public void disableSyncLock() {
		this.syncLocked = false;
		listConversations(2);

	}

	@Override
	public String parseAddress(String address) {
		return PhoneUtilsLite.parseAddress(address, null, telManager
				.getNetworkCountryIso().toUpperCase(Locale.US));
	}

}
