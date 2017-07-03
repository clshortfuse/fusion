package im.fsn.messenger.providers;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.MessageItem;
import im.fsn.messenger.PhoneUtilsLite;
import im.fsn.messenger.Utils;
import im.fsn.messenger.MessageItem.ExtraDataTypes;
import im.fsn.messenger.MessageItem.MessageStatuses;
import im.fsn.messenger.mms.DeliveryInd;
import im.fsn.messenger.mms.EncodedStringValue;
import im.fsn.messenger.mms.GenericPdu;
import im.fsn.messenger.mms.MmsException;
import im.fsn.messenger.mms.NotificationInd;
import im.fsn.messenger.mms.PduHeaders;
import im.fsn.messenger.mms.PduParser;
import im.fsn.messenger.mms.PduPersister;
import im.fsn.messenger.mms.ReadOrigInd;
import im.fsn.messenger.mms.SqliteWrapper;
import im.fsn.messenger.mms.Telephony.Mms;
import im.fsn.messenger.mms.Telephony.Mms.Inbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class SMSProvider extends IMProvider {

	private SmsManager smsManager;
	private ContentResolver mContentResolver;
	private final Object syncObject = new Object();
	private SharedPreferences mPrefs;
	private TelephonyManager telManager;
	private final static String TAG = "SMSProvider";

	public SMSProvider(Context mContext, Messenger mMessenger) {
		super(mContext, mMessenger);
		this.smsManager = SmsManager.getDefault();

		mContentResolver = mContext.getContentResolver();
		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		this.telManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);

	}

	@Override
	public boolean usesSMSAddresses() {
		return true;
	}

	private boolean receiversRegistered = false;
	private boolean processIncomingMessages = false;

	@Override
	public void start() {
		running = true;
		this.lastSMSSyncID = mPrefs.getLong("lastSMSSyncID", 0);
		this.lastSMSSyncTime = mPrefs.getLong("lastSMSSyncTime", 0);
		this.lastMMSSyncID = mPrefs.getLong("lastMMSSyncID", 0);
		this.lastMMSSyncTime = mPrefs.getLong("lastMMSSyncTime", 0);
		if (processIncomingMessages) {
			mContext.registerReceiver(SMSReceiver, new IntentFilter(
					SMS_RECEIVED));
			try {
				IntentFilter ifMMS = new IntentFilter(WAP_PUSH_RECEIVED);
				ifMMS.addDataType("application/vnd.wap.mms-message");
				mContext.registerReceiver(SMSReceiver, ifMMS);
			} catch (MalformedMimeTypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		mContext.registerReceiver(SMSReceiver, new IntentFilter(SMS_SENT));
		mContext.registerReceiver(SMSReceiver, new IntentFilter(SMS_DELIVERED));
		mContentResolver.registerContentObserver(
				Uri.parse("content://mms-sms"), true, mContentObserver);
		mContentResolver.registerContentObserver(
				Uri.parse("content://sms"), true, mContentObserver);
		mContentResolver.registerContentObserver(
				Uri.parse("content://mms"), true, mContentObserver);

		receiversRegistered = true;
		test();
	}

	private ContentObserver mContentObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			getSms();
			getMms();
		}
	};

	@Override
	public void stop() {
		if (receiversRegistered) {
			mContentResolver.unregisterContentObserver(mContentObserver);
			mContext.unregisterReceiver(SMSReceiver);
			receiversRegistered = false;
		}
		mPrefs.edit().putLong("lastSMSSyncID", this.lastSMSSyncID).commit();
		running = false;
	}

	public static String replaceFormFeeds(String s) {
		// Some providers send formfeeds in their messages. Convert those
		// formfeeds to newlines.
		return s == null ? "" : s.replace('\f', '\n');
	}

	private void parseSMS(SmsMessage[] msgs) {
		SmsMessage sms = msgs[0];
		MessageItem msgItem = new MessageItem();
		msgItem.setIncoming(true);
		msgItem.setIMProviderType(IMProviderTypes.SMS);
		msgItem.setMessageStatus(MessageStatuses.Delivered);
		msgItem.setExternalAddress(this.parseAddress(sms
				.getOriginatingAddress()));
		// long ts = msg.getTimestampMillis();
		msgItem.setCompletionDateTime(System.currentTimeMillis());
		msgItem.setCreationDateTime(System.currentTimeMillis());
		msgItem.setRead(false);
		int pduCount = msgs.length;
		if (pduCount == 1) {
			// There is only one part, so grab the body directly.
			msgItem.setText(replaceFormFeeds(sms.getDisplayMessageBody()));
		} else {
			// Build up the body from the parts.
			StringBuilder body = new StringBuilder();
			for (int i = 0; i < pduCount; i++) {
				sms = msgs[i];
				body.append(sms.getDisplayMessageBody());
			}
			msgItem.setText(replaceFormFeeds(body.toString()));
		}
		if (TextUtils.isEmpty(msgItem.getText()))
			return;

		syncLocked = true;
		try {
			ContentValues cv = new ContentValues();
			cv.put("address", msgItem.getExternalAddress());
			cv.put("date", msgItem.getCreationDateTime());
			cv.put("read", msgItem.isRead());
			cv.put("type", 1);
			cv.put("protocol", 0);
			cv.put("body", msgItem.getText());
			cv.put("service_center", sms.getServiceCenterAddress());

			Uri insertedUri = mContentResolver.insert(
					Uri.parse("content://sms"), cv);
			msgItem.setImportMessageId(insertedUri.getLastPathSegment());
		} catch (Exception e) {

		}

		Message m = Message.obtain(null,
				HandlerMessages.MSG_UNPROCESSEDMESSAGERECEIVED, msgItem);
		try {
			mMessenger.send(m);
		} catch (RemoteException e) {

		}

	}

	private static long findThreadId(Context context, GenericPdu pdu, int type) {
		String messageId;

		if (type == PduHeaders.MESSAGE_TYPE_DELIVERY_IND) {
			messageId = new String(((DeliveryInd) pdu).getMessageId());
		} else {
			messageId = new String(((ReadOrigInd) pdu).getMessageId());
		}

		StringBuilder sb = new StringBuilder('(');
		sb.append(Mms.MESSAGE_ID);
		sb.append('=');
		sb.append('?');
		sb.append(" AND ");
		sb.append(Mms.MESSAGE_TYPE);
		sb.append('=');
		sb.append(PduHeaders.MESSAGE_TYPE_SEND_REQ);
		// TODO ContentResolver.query() appends closing ')' to the selection
		// argument
		// sb.append(')');

		Cursor cursor = SqliteWrapper.query(context,
				context.getContentResolver(), Mms.CONTENT_URI,
				new String[] { Mms.THREAD_ID }, sb.toString(),
				new String[] { messageId }, null);
		if (cursor != null) {
			try {
				if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
					return cursor.getLong(0);
				}
			} finally {
				cursor.close();
			}
		}

		return -1;
	}

	private static boolean isDuplicateNotification(Context context,
			NotificationInd nInd) {
		byte[] rawLocation = nInd.getContentLocation();
		if (rawLocation != null) {
			String location = new String(rawLocation);
			String selection = Mms.CONTENT_LOCATION + " = ?";
			String[] selectionArgs = new String[] { location };
			Cursor cursor = SqliteWrapper.query(context,
					context.getContentResolver(), Mms.CONTENT_URI,
					new String[] { Mms._ID }, selection, selectionArgs, null);
			if (cursor != null) {
				try {
					if (cursor.getCount() > 0) {
						// We already received the same notification before.
						return true;
					}
				} finally {
					cursor.close();
				}
			}
		}
		return false;
	}

	private void parseMMS(byte[] pushData) {
		PduParser parser = new PduParser(pushData);
		GenericPdu pdu = parser.parse();

		PduPersister p = PduPersister.getPduPersister(mContext);

		ContentResolver cr = mContext.getContentResolver();
		int type = pdu.getMessageType();
		long threadId = -1;
		EncodedStringValue pFrom = pdu.getFrom();
		boolean useGroupMMS = true;
		boolean isTransIdEnabled = true;
		boolean allowAutoDownload = true;
		try {
			switch (type) {
			case PduHeaders.MESSAGE_TYPE_DELIVERY_IND:
				threadId = findThreadId(mContext, pdu, type);
				if (threadId == -1) {
					// The associated SendReq isn't found, therefore
					// skip
					// processing this PDU.
					break;
				}

				Uri delUri = p.persist(pdu, Inbox.CONTENT_URI, true,
						useGroupMMS, null);
				// Update thread ID for ReadOrigInd & DeliveryInd.
				ContentValues delValues = new ContentValues(1);
				delValues.put(Mms.THREAD_ID, threadId);
				SqliteWrapper.update(mContext, cr, delUri, delValues, null,
						null);
				break;
			case PduHeaders.MESSAGE_TYPE_READ_ORIG_IND: {

				threadId = findThreadId(mContext, pdu, type);
				if (threadId == -1) {
					// The associated SendReq isn't found, therefore
					// skip
					// processing this PDU.
					break;
				}

				Uri readUri = p.persist(pdu, Inbox.CONTENT_URI, true,
						useGroupMMS, null);
				// Update thread ID for ReadOrigInd & DeliveryInd.
				ContentValues readvalues = new ContentValues(1);
				readvalues.put(Mms.THREAD_ID, threadId);
				SqliteWrapper.update(mContext, cr, readUri, readvalues, null,
						null);
				break;
			}
			case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND: {
				NotificationInd nInd = (NotificationInd) pdu;

				if (isTransIdEnabled) {
					byte[] contentLocation = nInd.getContentLocation();
					if ('=' == contentLocation[contentLocation.length - 1]) {
						byte[] transactionId = nInd.getTransactionId();
						byte[] contentLocationWithId = new byte[contentLocation.length
								+ transactionId.length];
						System.arraycopy(contentLocation, 0,
								contentLocationWithId, 0,
								contentLocation.length);
						System.arraycopy(transactionId, 0,
								contentLocationWithId, contentLocation.length,
								transactionId.length);
						nInd.setContentLocation(contentLocationWithId);
					}
				}

				if (!isDuplicateNotification(mContext, nInd)) {
					// Save the pdu. If we can start downloading the
					// real pdu immediately,
					// don't allow persist() to create a thread for the
					// notificationInd
					// because it causes UI jank.
					Uri uri = p.persist(pdu, Inbox.CONTENT_URI, true,
							useGroupMMS, null);

				} else
					Log.v(TAG, "Skip downloading duplicate message: "
							+ new String(nInd.getContentLocation()));

				break;
			}
			default:
				Log.e(TAG, "Received unrecognized PDU.");
			}
		} catch (MmsException e) {
			Log.e(TAG, "Failed to save the data from PUSH: type=" + type, e);
		} catch (RuntimeException e) {
			Log.e(TAG, "Unexpected RuntimeException.", e);
		}

		this.getMms();

	}


	private static final String SMS_DELIVERED = "im.fsn.messenger.SMS_DELIVERED";
	private static final String SMS_SENT = "im.fsn.messenger.SMS_SENT";
	private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
	private static final String WAP_PUSH_RECEIVED = "android.provider.Telephony.WAP_PUSH_RECEIVED";

	public static final SmsMessage[] getMessagesFromIntent(Intent intent) {
		Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
		byte[][] pduObjs = new byte[messages.length][];

		for (int i = 0; i < messages.length; i++) {
			pduObjs[i] = (byte[]) messages[i];
		}
		byte[][] pdus = new byte[pduObjs.length][];
		int pduCount = pdus.length;
		SmsMessage[] msgs = new SmsMessage[pduCount];
		for (int i = 0; i < pduCount; i++) {
			pdus[i] = pduObjs[i];
			msgs[i] = SmsMessage.createFromPdu(pdus[i]);
		}
		return msgs;
	}

	private BroadcastReceiver SMSReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String intentAction = intent.getAction();
			if (intentAction == null)
				return;
			if (intentAction.equals(SMS_RECEIVED)) {

				Bundle bundle = intent.getExtras();
				if (bundle == null)
					return;
				Object[] messages = (Object[]) bundle.get("pdus");
				SmsMessage[] smsMessages = new SmsMessage[messages.length];
				for (int i = 0; i < messages.length; i++)
					smsMessages[i] = SmsMessage
							.createFromPdu((byte[]) messages[i]);
				parseSMS(smsMessages);
			} else if (intentAction.equals(SMS_SENT)) {
				mBusy = false;
				MessageItem msgItem = intent.getParcelableExtra("messageItem");
				int resultCode = this.getResultCode();
				int handleMessage = 0;
				msgItem.setCompletionDateTime(System.currentTimeMillis());
				int messageIndex = intent.getIntExtra("MessageIndex", 0);
				int messageCount = intent.getIntExtra("MessageCount", 1);

				if (resultCode == Activity.RESULT_OK) {
					handleMessage = HandlerMessages.MSG_UNPROCESSEDMESSAGESENT;
					msgItem.setMessageStatus(MessageStatuses.Sent);
					if (messageIndex == messageCount - 1) {
						syncLocked = true;
						try {
							ContentValues cv = new ContentValues();
							cv.put("address", msgItem.getExternalAddress());
							cv.put("date", msgItem.getCreationDateTime());
							cv.put("read", true);
							cv.put("body", msgItem.getText());
							Uri insertedUri = mContentResolver.insert(
									Uri.parse("content://sms/sent"), cv);
							msgItem.setImportMessageId(insertedUri
									.getLastPathSegment());
						} catch (Exception e) {

						}
					}
				} else {
					handleMessage = HandlerMessages.MSG_UNPROCESSEDMESSAGEFAILED;
					msgItem.setMessageStatus(MessageStatuses.Failed);
				}
				if (handleMessage != 0) {
					Message m = Message.obtain(null, handleMessage, msgItem);
					try {
						mMessenger.send(m);
					} catch (RemoteException e) {

					}
				}

			} else if (intentAction.equals(SMS_DELIVERED)) {
				Bundle bundle = intent.getExtras();
				if (bundle == null)
					return;
				MessageItem msgItem = intent.getParcelableExtra("messageItem");
				// todo
			} else if (intentAction.equals(WAP_PUSH_RECEIVED)) {
				byte[] pushData = intent.getByteArrayExtra("data");
				parseMMS(pushData);
			}
		}

	};

	private ArrayList<String> splitMessages(String input) {
		int len = input.length();
		int count = len / 160;
		ArrayList<String> messages = new ArrayList<String>();
		for (int i = 0; i < count; i++) {
			int start = i - 1 * 160;
			int end = start + 160;
			if (end > len)
				end = len;
			messages.add(input.substring(start, end));
		}
		return messages;
	}

	public MessageItem sendSingleMessage(MessageItem msgItem) {
		PendingIntent sentIntent = PendingIntent.getBroadcast(mContext, 0,
				new Intent(SMS_SENT).putExtra("messageItem", msgItem),
				PendingIntent.FLAG_UPDATE_CURRENT);

		PendingIntent deliveryIntent = PendingIntent.getBroadcast(mContext, 0,
				new Intent(SMS_DELIVERED).putExtra("messageItem", msgItem), 0);

		try {
			mBusy = true;
			smsManager.sendTextMessage(msgItem.getExternalAddress(), null,
					msgItem.getText(), sentIntent, null);
			msgItem.setMessageStatus(MessageItem.MessageStatuses.OnRoute);
		} catch (Exception e) {
			e.printStackTrace();
			mBusy = false;
			msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
		}
		return msgItem;
	}

	@Override
	public MessageItem sendMessage(MessageItem msgItem) {
		String text = msgItem.getText();
		if (TextUtils.isEmpty(text)) {
			mBusy = false;
			msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
			return msgItem;
		}

		int[] params = SmsMessage.calculateLength(text, false);
		int nSmsPages = params[0];
		if (nSmsPages <= 0) {
			mBusy = false;
			msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
			return msgItem;
		}
		if (nSmsPages == 1) {
			return sendSingleMessage(msgItem);
		} else
			return sendMultipleMessages(msgItem);

	}

	public MessageItem sendMultipleMessages(MessageItem msgItem) {
		// check cell signal here

		String text = msgItem.getText();
		ArrayList<String> messages = smsManager.divideMessage(text);
		String splitPreference = mPrefs.getString("pfSMSSplitMessages", "0");
		boolean useSplit;
		if (splitPreference.equals("0")) {
			if (telManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA)
				useSplit = true;
			else
				useSplit = false;
		} else if (splitPreference.equals("2"))
			useSplit = false;
		else
			useSplit = true;

		int messageCount = messages.size();
		if (messageCount == 0) {
			msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
			return msgItem;
		}

		try {
			mBusy = true;
			ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(
					messageCount);
			for (int i = 0; i < messageCount; i++) {
				Intent intent = new Intent(SMS_SENT).putExtra("messageItem",
						msgItem);
				int requestCode = (int) ((msgItem.getMessageId() % 1000L) + (i + 1));
				intent.putExtra("MessageIndex", i);
				intent.putExtra("MessageCount", messageCount);
				PendingIntent pIntent = PendingIntent.getBroadcast(mContext,
						requestCode, intent, 0);
				if (useSplit) {
					smsManager.sendTextMessage(msgItem.getExternalAddress(),
							null, messages.get(i), pIntent, null);
				} else {
					sentIntents.add(pIntent);
				}

			}
			if (!useSplit)
				smsManager.sendMultipartTextMessage(
						msgItem.getExternalAddress(), null, messages,
						sentIntents, null);
			msgItem.setMessageStatus(MessageItem.MessageStatuses.OnRoute);
		} catch (Exception e) {
			e.printStackTrace();
			mBusy = false;
			msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
		}

		return msgItem;

	}

	@Override
	public IMProviderTypes getIMProviderType() {
		return IMProviderTypes.SMS;
	}

	@Override
	public String parseAddress(String address) {
		return PhoneUtilsLite.parseAddress(address, null, telManager
				.getNetworkCountryIso().toUpperCase(Locale.US));
	}

	private boolean mBusy = false;

	@Override
	public boolean isBusy() {
		return mBusy;
	}

	@Override
	public String getSender() {

		return null;
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

	private int mDarkColor = 0xff669900; // holo_green_dark
	private int mLightColor = 0xff99cc00; // holo_green_light

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
	public boolean markAsRead(MessageItem msgItem) {
		if (msgItem.getIMProviderType() != this.getIMProviderType())
			return false;
		ContentValues cv = new ContentValues();
		cv.put("read", 1);
		if (msgItem.getExtraDataType() == ExtraDataTypes.None) {
			return mContentResolver
					.update(Uri.parse("content://sms"), cv, "_id=?",
							new String[] { String.valueOf(msgItem
									.getImportMessageId()) }) != 0;
		} else {
			return mContentResolver
					.update(Uri.parse("content://mms"), cv, "_id=?",
							new String[] { String.valueOf(msgItem
									.getImportMessageId()) }) != 0;
		}

	}

	@Override
	public boolean markAsRead(MessageItem[] items) {
		for (MessageItem m : items)
			markAsRead(m);
		return true;
	}

	@Override
	public boolean delete(MessageItem msgItem) {
		if (msgItem.getExtraDataType() == ExtraDataTypes.None) {
			mContentResolver
					.delete(Uri.parse("content://sms"), "_id=?",
							new String[] { String.valueOf(msgItem
									.getImportMessageId()) });

		} else {
			mContentResolver
					.delete(Uri.parse("content://mms"), "_id=?",
							new String[] { String.valueOf(msgItem
									.getImportMessageId()) });
		}
		return true;

	}

	@Override
	public boolean delete(MessageItem[] items) {
		for (MessageItem m : items)
			delete(m);
		return true;
	}

	@SuppressLint("NewApi")
	private void test() {
		if (true)
			return;
		Uri mUri = Uri.parse("content://mms");
		List<MessageItem[]> messages = new ArrayList<MessageItem[]>();
		Cursor cursor = null;
		cursor = mContext.getContentResolver().query(mUri, null, null, null,
				null);

		while (cursor.moveToNext()) {
			int columnCount = cursor.getColumnCount();
			Object[] dataArray = new Object[columnCount];
			for (int i = 0; i < columnCount; i++) {
				int type = cursor.getType(i);
				switch (type) {
				default:
				case Cursor.FIELD_TYPE_NULL:
					dataArray[i] = null;
					break;
				case Cursor.FIELD_TYPE_BLOB:
					dataArray[i] = cursor.getBlob(i);
					break;
				case Cursor.FIELD_TYPE_STRING:
					dataArray[i] = cursor.getString(i);
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					dataArray[i] = cursor.getLong(i);
					break;
				case Cursor.FIELD_TYPE_FLOAT:
					dataArray[i] = cursor.getFloat(i);
				}
			}
			dataArray.toString();
		}
		cursor.close();
	}

	private long lastSMSSyncID = 0L;
	private long lastMMSSyncID = 0L;

	public boolean getSms() {
		if (syncLocked)
			return false;

		Uri mUri = Uri.parse("content://sms");
		List<MessageItem[]> messages = new ArrayList<MessageItem[]>();
		Cursor cursor = null;
		try {
			String[] selectColumns = new String[] { "_id", "thread_id",
					"address", "date", "read", "type", "body" };
			cursor = mContext.getContentResolver().query(mUri, selectColumns,
					"_id > ? AND address IS NOT NULL",
					new String[] { String.valueOf(lastSMSSyncID) }, "address ASC, _id ASC" );
			if (cursor == null) {
				return false;
			}

			List<MessageItem> currentMessageList = new ArrayList<MessageItem>();
			String currentDBAddress = null;
			String currentParsedAddress = null;
			for (boolean hasData = cursor.moveToFirst(); hasData; hasData = cursor
					.moveToNext()) {
				MessageItem m = new MessageItem();

				long importMessageId = cursor.getLong(0);
				m.setImportMessageId(String.valueOf(importMessageId));
				m.setImportConversationId(String.valueOf(cursor.getLong(1)));

				String address = cursor.getString(2);
				if (TextUtils.isEmpty(address))
					continue;
				if (currentDBAddress == null
						|| !address.equals(currentDBAddress)) {
					// new contact
					int arrayLength = currentMessageList.size();
					if (arrayLength != 0) {
						MessageItem[] messageArray = new MessageItem[arrayLength];

						currentMessageList.toArray(messageArray);
						currentMessageList = new ArrayList<MessageItem>();
						Message osMessage = Message
								.obtain(null,
										HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED,
										messageArray);
						try {
							mMessenger.send(osMessage);
						} catch (RemoteException e) {
						}
					}

					// prepareParsing
					currentParsedAddress = this.parseAddress(address);
					if (currentParsedAddress == null)
						currentParsedAddress = address;
					currentDBAddress = address;
				}
				m.setExternalAddress(currentParsedAddress);
				m.setCreationDateTime(cursor.getLong(3));

				m.setCompletionDateTime(m.getCreationDateTime());

				int messageType = cursor.getInt(5);
				MessageStatuses status = MessageStatuses.Deleted;
				boolean isIncoming = false;
				switch (messageType) {
				case 1:
					status = MessageStatuses.Delivered;
					isIncoming = true;
					if (importMessageId > this.lastSMSSyncID) {
						this.lastSMSSyncID = importMessageId;
						mPrefs.edit()
								.putLong("lastSMSSyncID",
										this.lastSMSSyncID - 10).commit();
					}

					break;
				default:
				case 3:
					continue;
				case 2:
					status = MessageStatuses.Sent;
					isIncoming = false;
					if (importMessageId > this.lastSMSSyncID) {
						this.lastSMSSyncID = importMessageId;
						mPrefs.edit()
								.putLong("lastSMSSyncID",
										this.lastSMSSyncID - 10).commit();
					}
					break;
				case 4:
					status = MessageStatuses.OnRoute;
					isIncoming = false;
					break;
				case 5:
					status = MessageStatuses.Failed;
					isIncoming = false;
					break;
				case 6:
					status = MessageStatuses.Queued;
					isIncoming = false;
				}

				if (isIncoming)
					m.setRead(cursor.getInt(4) == 1);
				else
					m.setRead(true);
				m.setMessageStatus(status);
				m.setIncoming(isIncoming);
				m.setText(cursor.getString(6));
				m.setIMProviderType(IMProviderTypes.SMS);

				currentMessageList.add(m);

			}
			int arrayLength = currentMessageList.size();
			if (arrayLength != 0) {
				MessageItem[] messageArray = new MessageItem[arrayLength];

				currentMessageList.toArray(messageArray);
				currentMessageList = new ArrayList<MessageItem>();
				Message osMessage = Message.obtain(null,
						HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED,
						messageArray);
				try {
					mMessenger.send(osMessage);
				} catch (RemoteException e) {
				}
			}
		} catch (Exception e) {

		} finally {
			if (cursor != null)
				cursor.close();
		}
		return true;
	}

	private String[] getMMSAddresses(long importMessageId, boolean isIncoming) {
		String selectionAdd = new String("msg_id=? and type=?");
		String uriStr = MessageFormat.format("content://mms/{0}/addr",
				importMessageId);
		Uri uriAddress = Uri.parse(uriStr);
		Cursor cAdd = mContext.getContentResolver().query(
				uriAddress,
				null,
				selectionAdd,
				new String[] { String.valueOf(importMessageId),
						String.valueOf(isIncoming ? 137 : 151) }, null);
		List<String> names = new ArrayList<String>();
		if (cAdd.moveToFirst()) {
			do {
				String number = cAdd.getString(cAdd.getColumnIndex("address"));
				if (number != null) {
					names.add(number);
				}
			} while (cAdd.moveToNext());
		}
		if (cAdd != null) {
			cAdd.close();
		}
		return names.toArray(new String[names.size()]);
	}

	public boolean getMms() {
		if (syncLocked)
			return false;

		Uri mUri = Uri.parse("content://mms");
		List<MessageItem[]> messages = new ArrayList<MessageItem[]>();
		Cursor cursor = null;
		try {
			String[] selectColumns = new String[] { "_id", "thread_id", "date",
					"msg_box", "read", "m_type" };
			cursor = mContext.getContentResolver().query(mUri, selectColumns,
					"_id > ?", new String[] { String.valueOf(lastMMSSyncID) },
					"thread_id ASC, _id ASC");
			if (cursor == null) {
				return false;
			}

			List<MessageItem> currentMessageList = new ArrayList<MessageItem>();
			String currentDBAddress = null;
			String currentParsedAddress = null;
			for (boolean hasData = cursor.moveToFirst(); hasData; hasData = cursor
					.moveToNext()) {
				MessageItem m = new MessageItem();

				long importMessageId = cursor.getLong(0);
				m.setImportMessageId(String.valueOf(importMessageId));
				m.setImportConversationId(String.valueOf(cursor.getLong(1)));
				m.setText(new String());
				int messageBox = cursor.getInt(3);
				MessageStatuses status = MessageStatuses.Deleted;
				boolean isIncoming = false;
				switch (messageBox) {
				case 1:
					status = MessageStatuses.Delivered;
					isIncoming = true;
					if (importMessageId > this.lastMMSSyncID) {
						this.lastMMSSyncID = importMessageId;
						mPrefs.edit()
								.putLong("lastMMSSyncID", this.lastMMSSyncID - 10)
								.commit();
					}

					break;
				default:
				case 3:
					continue;
				case 2:
					status = MessageStatuses.Sent;
					isIncoming = false;
					if (importMessageId > this.lastMMSSyncID) {
						this.lastMMSSyncID = importMessageId;
						mPrefs.edit()
								.putLong("lastMMSSyncID", this.lastMMSSyncID - 10)
								.commit();
					}
					break;
				case 4:
					status = MessageStatuses.OnRoute;
					isIncoming = false;
					break;
				}

				String[] addresses = getMMSAddresses(importMessageId,
						isIncoming);
				if (addresses.length != 1) {
					Log.d("SMSProvider", "Group MMS: PANIC!");
					continue;
				}
				String address = addresses[0];
				if (TextUtils.isEmpty(address))
					continue;
				if (currentDBAddress == null
						|| !address.equals(currentDBAddress)) {
					// new contact
					int arrayLength = currentMessageList.size();
					if (arrayLength != 0) {
						MessageItem[] messageArray = new MessageItem[arrayLength];

						currentMessageList.toArray(messageArray);
						currentMessageList = new ArrayList<MessageItem>();
						Message osMessage = Message
								.obtain(null,
										HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED,
										messageArray);
						try {
							mMessenger.send(osMessage);
						} catch (RemoteException e) {
						}
					}

					// prepareParsing
					currentParsedAddress = this.parseAddress(address);
					if (currentParsedAddress == null)
						currentParsedAddress = address;
					currentDBAddress = address;
				}
				m.setExternalAddress(currentParsedAddress);
				m.setCreationDateTime(cursor.getLong(2) * 1000);

				m.setCompletionDateTime(m.getCreationDateTime());

				if (isIncoming)
					m.setRead(cursor.getInt(4) == 1);
				else
					m.setRead(true);
				m.setMessageStatus(status);
				m.setIncoming(isIncoming);
				int messageType = cursor.getInt(5);
				m.setExtraDataType(ExtraDataTypes.Unknown);

				m.setIMProviderType(IMProviderTypes.SMS);

				currentMessageList.add(m);

			}
			int arrayLength = currentMessageList.size();
			if (arrayLength != 0) {
				MessageItem[] messageArray = new MessageItem[arrayLength];

				currentMessageList.toArray(messageArray);
				currentMessageList = new ArrayList<MessageItem>();
				Message osMessage = Message.obtain(null,
						HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED,
						messageArray);
				try {
					mMessenger.send(osMessage);
				} catch (RemoteException e) {
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return true;
	}

	private long lastSMSSyncTime = 0L;
	private long lastMMSSyncTime = 0L;
	private long syncRepeatTime = 5L * 60 * 1000L;

	@Override
	public boolean syncMessages(boolean force) {
		long now = SystemClock.elapsedRealtime();
		boolean returnValue = false;
		if (force || now - syncRepeatTime >= lastSMSSyncTime) {
			getSms();
			this.lastSMSSyncTime = now;
			Log.d("SMSProvider", "Sync Started");
			mPrefs.edit().putLong("lastSMSSyncTime", this.lastSMSSyncTime)
					.commit();
			returnValue = true;
		}
		if (force || now - syncRepeatTime >= lastMMSSyncTime) {
			getMms();
			this.lastMMSSyncTime = now;
			Log.d("SMSProvider", "Sync Started");
			mPrefs.edit().putLong("lastMMSSyncTime", this.lastMMSSyncTime)
					.commit();
			returnValue = true;
		}

		return returnValue;
	}

	public boolean isProcessIncomingMessages() {
		return processIncomingMessages;
	}

	public void setProcessIncomingMessages(boolean processIncomingMessages) {
		if (this.processIncomingMessages != processIncomingMessages) {
			this.processIncomingMessages = processIncomingMessages;
			this.mRequiresRestart = true;
		}
	}

	private boolean syncLocked = false;

	@Override
	public void disableSyncLock() {
		this.syncLocked = false;
		this.syncMessages(true);
	}

}
