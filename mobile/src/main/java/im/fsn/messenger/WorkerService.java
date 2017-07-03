package im.fsn.messenger;

import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.GVoiceProvider;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.SMSProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;
import im.fsn.messenger.ui.LauncherActivity;
import im.fsn.messenger.ui.QuickReplyDialog;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import im.fsn.messenger.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.os.Build.VERSION;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

public class WorkerService extends Service {

	public class LocalBinder extends Binder {
		public WorkerService getService() {
			return WorkerService.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private List<Messenger> clientMessengers;
	private List<ContactItem> mDBContactList = null;
	private List<ContactItem> mPhoneContactList = null;

	private DBRequestHandlerThread mIMProvidersHandlerThread;
	private UIRequestHandlerThread mUIRequestHandlerThread;

	private Messenger mWorkerServiceMessenger = new Messenger(
			new WorkerServiceHandler(WorkerService.this));
	private boolean mUseNotificationsPreference = true;
	private Camera mCamera;

	public boolean startCameraPreview(SurfaceHolder holder, boolean useFront) {

		try {

			if (useFront)
				mCamera = FrontFacingCamera.getFrontFacingCamera();
			mCamera.setPreviewDisplay(holder);
			mCamera.unlock();
			return true;
		} catch (Exception e) {
			try {
				mCamera.lock();
			} catch (Exception e1) {
			}
			try {
				mCamera.release();
			} catch (Exception e2) {
			}
		}
		return false;
	}

	public void refreshLocalGoogleAccounts(Activity callBackActivity) {
		String gvAccountName = mPrefs.getString("pfGoogleVoiceAccount", null);
		AccountManager accountManager = AccountManager.get(this);

		Account[] accountList = accountManager.getAccounts();

		AccountManagerCallback<Bundle> gVoiceCallback = new AccountManagerCallback<Bundle>() {

			@Override
			public void run(AccountManagerFuture<Bundle> future) {

				Bundle bundle;
				try {
					bundle = future.getResult();

					if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
						String userAccount = bundle
								.getString(AccountManager.KEY_ACCOUNT_NAME);
						String userToken = bundle
								.getString(AccountManager.KEY_AUTHTOKEN);
						setCredentials(userAccount, userToken);
						return;
					}
				} catch (OperationCanceledException e) {
					e.printStackTrace();
				} catch (AuthenticatorException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		};

		if (gvAccountName != null)
			for (int i = 0; i < accountList.length; i++)
				if (accountList[i].name.equals(gvAccountName)
						&& accountList[i].type.equals("com.google")) {
					if (callBackActivity != null)
						accountManager.getAuthToken(accountList[i],
								"Google Voice", null, callBackActivity,
								gVoiceCallback, new Handler());
					else if (Build.VERSION.SDK_INT >= 14) {
						V14.getAuthToken(accountManager, accountList[i],
								"Google Voice", new Bundle(), true,
								gVoiceCallback, null);
					} else {
						accountManager.getAuthToken(accountList[i],
								"Google Voice", true, gVoiceCallback,
								new Handler() {
								});
					}

					break;
				}

	}

	public void setCredentials(String accountUser, String authToken) {
		boolean changed = false;
		for (IMProvider s : MemoryCache.IMProviders) {
			if (s.getIMProviderType() == IMProviderTypes.GVoice) {
				GVoiceProvider gs = (GVoiceProvider) s;
				if (gs.getAccountName() != null
						&& gs.getAccountName().compareToIgnoreCase(accountUser) == 0) {
					gs.setAuthToken(authToken);
					changed = true;
				}
			}
		}
		if (changed)
			this.mUIRequestHandlerThread.forceResume();
		if (isFirstRun) {
			AccountManager accountManager = AccountManager.get(this);
			accountManager.invalidateAuthToken(accountUser, authToken);
			isFirstRun = false;
		}
	}

	private boolean isFirstRun = true;

	private static class WorkerServiceHandler extends Handler {
		private final WeakReference<WorkerService> mService;

		public WorkerServiceHandler(WorkerService mService) {
			this.mService = new WeakReference<WorkerService>(mService);
		}

		@Override
		public void handleMessage(Message msg) {
			WorkerService wService = mService.get();
			if (wService == null)
				return;
			switch (msg.what) {
			case HandlerMessages.MSG_GETNEWCREDENTIALS:
				wService.refreshLocalGoogleAccounts(null);
				break;
			case HandlerMessages.MSG_PHONECONTACTSRECEIVED:
				List<ContactItem> phoneContacts = (List<ContactItem>) msg.obj;
				wService.mPhoneContactList = phoneContacts;
				wService.mergeContacts();
				break;
			case HandlerMessages.MSG_DBCONTACTSRECEIVED:
				List<ContactItem> dbContacts = (List<ContactItem>) msg.obj;
				wService.mDBContactList = dbContacts;
				wService.mergeContacts();
				break;
			case HandlerMessages.MSG_UNPROCESSEDMESSAGEBATCHRECEIVED:
				MessageItem[] rawMsgItems = (MessageItem[]) msg.obj;
				if (rawMsgItems.length == 0)
					break;
				wService.mIMProvidersHandlerThread
						.queueMessageBatch(rawMsgItems);
				break;
			case HandlerMessages.MSG_MESSAGEBATCHRECEIVED:
				MessageItem[] msgItems = (MessageItem[]) msg.obj;
				if (msgItems.length == 0)
					break;
				wService.signalMessageItem(msg);
				break;
			case HandlerMessages.MSG_MESSAGERECEIVED:
				MessageItem processedMsgItem = (MessageItem) msg.obj;
				wService.signalMessageItem(msg);
				if (!processedMsgItem.isRead())
					wService.notifyOnNewMessage(processedMsgItem);
				break;
			case HandlerMessages.MSG_UNPROCESSEDMESSAGERECEIVED:
			case HandlerMessages.MSG_UNPROCESSEDMESSAGESENT:
			case HandlerMessages.MSG_UNPROCESSEDMESSAGEFAILED:
				MessageItem newMsgItem = (MessageItem) msg.obj;
				wService.mIMProvidersHandlerThread.queueMessageItem(newMsgItem);
				break;
			case HandlerMessages.MSG_CONVERSATIONUPDATED:

				wService.signalMessageItem(msg);
				break;

			case HandlerMessages.MSG_MESSAGEDELETED:
				wService.queueContactsRefresh();
				// cheap, should build a get last message request
			case HandlerMessages.MSG_MESSAGESENT:
			case HandlerMessages.MSG_MESSAGEFAILED:
			case HandlerMessages.MSG_MESSAGEQUEUED:
			case HandlerMessages.MSG_MESSAGECHANGED:
			case HandlerMessages.MSG_MESSAGEONROUTE:

				wService.signalMessageItem(msg);
				break;
			case HandlerMessages.MSG_NOTIFYCONTACTASREAD:
				wService.signalMessageItem(msg);
				wService.requestConversation((ContactItem) msg.obj);
				break;
			}

		}
	};

	private long lastNotificationTime;

	private static boolean useNotifications = true;
	NotificationManager nm;
	private List<MessageItem> unreadMessageList = new ArrayList<MessageItem>();
	private List<FusionNotification> currentNotifications = new ArrayList<FusionNotification>();

	public class FusionNotification {
		public int notificationId = 0;
		public List<MessageItem> messages = new ArrayList<MessageItem>();
		public ContactItem contactItem;
	}

	public void notifyOnNewMessage(MessageItem msgItem) {
		if (!mUseNotificationsPreference)
			return;
		if (useNotifications) {
			boolean found = false;
			for (MessageItem m : unreadMessageList)
				if (m.getMessageId() == msgItem.getMessageId()) {
					found = true;
					break;
				}
			if (!found) {
				unreadMessageList.add(msgItem);
				if (useNotifications)
					rebuildNotifications(false);
			}
		} else {
			final AudioManager am = (AudioManager) this
					.getSystemService(Context.AUDIO_SERVICE);
			// Android 4.2.1+ workaround
			if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
				Log.d("WorkerService",
						"Vibrate with focus: " + msgItem.getText() + " from "
								+ msgItem.getExternalAddress());
				Vibrator v = (Vibrator) this
						.getSystemService(Context.VIBRATOR_SERVICE);
				try {
					v.vibrate(new long[] { 0, 500, 50, 200, 50, 200 }, -1);
				} catch (Exception e) {

				}
			}
		}

	}

	public void rebuildNotifications(boolean silent) {

		boolean splitByContact = mPrefs.getBoolean(
				"pfNotificationSplitByContact", false);
		boolean sortNewestFirst = mPrefs.getString(
				"pfNotificationMessageOrder", "0").equals("0");
		boolean upscaleContactPictures = mPrefs.getBoolean(
				"pfNotificationUpscaleContactPicture", true);
		boolean autoOpenQuickReply = mPrefs
				.getBoolean("pfQuickAutoOpen", false);
		boolean useVibration = mPrefs.getBoolean("pfNotificationVibrate", true);

		boolean wakeDevice = mPrefs.getBoolean("pfNotificationWakeUp", true);
		String strRingtonePreference = mPrefs.getString(
				"pfNotificationRingtone", "DEFAULT_SOUND");

		List<FusionNotification> fNotifications = new ArrayList<FusionNotification>();
		if (unreadMessageList.size() == 0) {
			nm.cancel(1001);
			nm.cancel(1002);
			return;
		}

		if (!splitByContact) {
			FusionNotification singleNotification = new FusionNotification();
			singleNotification.notificationId = 1001;
			singleNotification.messages.addAll(unreadMessageList);
			fNotifications.add(singleNotification);
		} else {
			for (MessageItem msgItem : unreadMessageList) {
				boolean found = false;
				int len = fNotifications.size();
				for (int i = 0; i < len; i++) {
					FusionNotification fnTemp = fNotifications.get(i);
					if (fnTemp.contactItem != null
							&& fnTemp.contactItem.isMessageItemIsValid(msgItem)) {
						fnTemp.messages.add(msgItem);
						fNotifications.set(i, fnTemp);
						found = true;
						break;
					}
				}
				if (found)
					continue;

				FusionNotification fn = new FusionNotification();
				ContactItem ci = null;
				synchronized (MemoryCache.ContactsList) {
					for (ContactItem c : MemoryCache.ContactsList) {
						if (c.isMessageItemIsValid(msgItem)) {
							ci = c;
							break;
						}
					}
				}
				if (ci == null) {
					ci = new ContactItem();
					ContactAddress ca = new ContactAddress();
					ca.setIMProviderType(msgItem.getIMProviderType());
					ca.setParsedAddress(msgItem.getExternalAddress());
					ci.setContactAddresses(new ContactAddress[] { ca });
					ci.setDisplayContactAddress(msgItem.getExternalAddress());
					ci.setDisplayName(msgItem.getExternalAddress());
				}
				fn.contactItem = ci;
				fn.messages.add(msgItem);
				fNotifications.add(fn);

			}
		}
		Log.d("WorkerService", "Notifications: " + fNotifications.size());

		for (FusionNotification fn : fNotifications) {

			Context context = getApplicationContext();

			Collections.sort(fn.messages);
			if (sortNewestFirst)
				Collections.reverse(fn.messages);
			MessageItem topMessage = fn.messages.get(0);
			List<ContactItem> contactList = new ArrayList<ContactItem>();

			if (fn.contactItem != null)
				contactList.add(fn.contactItem);
			else {
				for (MessageItem msgItem : fn.messages) {
					ContactItem ci = null;

					for (ContactItem c : contactList) {
						if (c.isMessageItemIsValid(msgItem)) {
							ci = c;
							break;
						}
					}

					synchronized (MemoryCache.ContactsList) {
						for (ContactItem c : MemoryCache.ContactsList) {
							if (c.isMessageItemIsValid(msgItem)) {
								ci = c;
								break;
							}
						}
					}

					if (ci == null) {
						ci = new ContactItem();
						ContactAddress ca = new ContactAddress();
						ca.setIMProviderType(topMessage.getIMProviderType());
						ca.setParsedAddress(topMessage.getExternalAddress());
						ci.setContactAddresses(new ContactAddress[] { ca });
					}

					if (!contactList.contains(ci))
						contactList.add(ci);
					if (fn.contactItem == null)
						fn.contactItem = ci;
				}

			}

			IMProvider p = IMProvider.findIMProvider(
					topMessage.getIMProviderType(), MemoryCache.IMProviders);

			Intent intentActivity = new Intent(Intent.ACTION_MAIN);
			intentActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
			intentActivity.setClass(context, LauncherActivity.class);

			intentActivity.putExtra("notificationContactItem", fn.contactItem);

			Intent intentQuickReplyActivity = new Intent(context,
					QuickReplyDialog.class);
			intentQuickReplyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			intentQuickReplyActivity.putExtra("notificationContactItems",
					contactList.toArray(new ContactItem[contactList.size()]));

			NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(
					this);

			RemoteViews contentView = new RemoteViews(getPackageName(),
					R.layout.legacy_notification);

			PendingIntent contentIntent = PendingIntent.getActivity(context,
					1003, intentActivity, PendingIntent.FLAG_CANCEL_CURRENT);

			PendingIntent markReadIntent = PendingIntent.getService(this, 1004,
					new Intent(this, WorkerService.class).putExtra(
							"notificationMarkReadMessageItems",
							fn.messages.toArray(new MessageItem[fn.messages
									.size()])),
					PendingIntent.FLAG_UPDATE_CURRENT);
			PendingIntent copyIntent = PendingIntent.getService(this, 1005,
					new Intent(this, WorkerService.class).putExtra(
							"notificationCopyMessageItem", topMessage),
					PendingIntent.FLAG_UPDATE_CURRENT);

			PendingIntent quickReplyIntent = PendingIntent.getActivity(context,
					1006, intentQuickReplyActivity,
					PendingIntent.FLAG_CANCEL_CURRENT);

			if (autoOpenQuickReply)
				nBuilder.setFullScreenIntent(quickReplyIntent, false);

			nBuilder.setContentIntent(contentIntent);
			nBuilder.setDeleteIntent(markReadIntent); // aka Dismiss
			nBuilder.addAction(R.drawable.content_copy_dark,
					getString(android.R.string.copy), copyIntent);
			nBuilder.addAction(R.drawable.ic_action_send, "Quick Reply",
					quickReplyIntent);
			contentView.setOnClickPendingIntent(R.id.legacy_quickreply,
					quickReplyIntent);

			nBuilder.setContentInfo(null);

			int messageCount = fn.messages.size();

			if (messageCount == 1) {
				contentView.setViewVisibility(R.id.legacy_info, View.GONE);

				nBuilder.setContentTitle(fn.contactItem.getDisplayName());

				contentView.setTextViewText(R.id.legacy_title,
						fn.contactItem.getDisplayName());

				nBuilder.setContentText(topMessage.getText());

				contentView.setTextViewText(R.id.legacy_text,
						topMessage.getText());

				NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
				bigTextStyle
						.setBigContentTitle(fn.contactItem.getDisplayName());
				bigTextStyle.bigText(topMessage.getText());
				nBuilder.setStyle(bigTextStyle);

			} else {

				nBuilder.setNumber(messageCount);
				contentView.setTextViewText(R.id.legacy_info, NumberFormat
						.getIntegerInstance().format(messageCount));
				nBuilder.setContentTitle(messageCount + " new messages");

				contentView.setTextViewText(R.id.legacy_title, messageCount
						+ " new messages");

				TextAppearanceSpan notificationSenderSpan = new TextAppearanceSpan(
						context, R.style.NotificationPrimaryText);
				TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
						context, R.style.NotificationSubjectText);
				SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
				String sender = fn.contactItem.getDisplayName() + ": ";
				String text = topMessage.getText();

				spannableStringBuilder.append(sender);
				spannableStringBuilder.setSpan(notificationSenderSpan, 0,
						sender.length(), 0);

				spannableStringBuilder.append(text);
				spannableStringBuilder.setSpan(notificationSubjectSpan,
						sender.length(), sender.length() + text.length(), 0);
				nBuilder.setContentText(spannableStringBuilder);
				contentView.setTextViewText(R.id.legacy_text,
						spannableStringBuilder);

				NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

				int len = fn.messages.size();
				for (int i = 0; i < len; i++) {
					MessageItem msgItem = fn.messages.get(i);
					ContactItem ci = null;
					for (ContactItem c : contactList) {
						if (c.isMessageItemIsValid(msgItem)) {
							ci = c;
							break;
						}
					}
					// Should be impossible to be null here;

					sender = ci.getDisplayName() + ": ";
					text = msgItem.getText();
					SpannableStringBuilder ssbLine = new SpannableStringBuilder();

					ssbLine.append(sender);
					ssbLine.setSpan(notificationSenderSpan, 0, sender.length(),
							0);

					ssbLine.append(text);
					ssbLine.setSpan(notificationSubjectSpan, sender.length(),
							sender.length() + text.length(), 0);
					inboxStyle.addLine(ssbLine);
				}
				nBuilder.setStyle(inboxStyle);
			}

			nBuilder.setOngoing(false);

			if (VERSION.SDK_INT > 16)
				nBuilder.setPriority(1); // Notification.PRIORITY_HIGH

			Bitmap b = null;

			int contactPixelSize = (int) TypedValue.applyDimension(
					TypedValue.COMPLEX_UNIT_DIP, 64, getResources()
							.getDisplayMetrics());
			if (mPrefs.getBoolean("pfUseNotificationContactPicture", true)) {
				if (fn.contactItem.getId() != 0L) {
					if (fn.contactItem.isPhotoChecked())
						b = fn.contactItem.getThumbnail64x64();
					else {
						Uri contactUri = Uri.withAppendedPath(
								ContactsContract.Contacts.CONTENT_URI,
								String.valueOf(fn.contactItem.getId()));
						InputStream input;
						if (VERSION.SDK_INT >= 14)
							input = V14.openHiResContactPhotoInputStream(
									getContentResolver(), contactUri);
						else
							input = ContactsContract.Contacts
									.openContactPhotoInputStream(
											getContentResolver(), contactUri);
						if (input != null) {
							b = BitmapFactory.decodeStream(input);
							if (b != null) {

								if (b.getHeight() > contactPixelSize) {
									b = Bitmap.createScaledBitmap(b,
											contactPixelSize, contactPixelSize,
											true);
								}
								fn.contactItem.setThumbnail64x64(b);
							}
						}
						fn.contactItem.setPhotoChecked(true);
					}
				}

				if (b == null)
					b = BitmapFactory.decodeResource(getResources(),
							R.drawable.ic_contact_picture_dark);
				else {
					if (upscaleContactPictures
							&& b.getHeight() < contactPixelSize)
						b = Bitmap.createScaledBitmap(b, contactPixelSize,
								contactPixelSize, true);

				}
				nBuilder.setLargeIcon(b);
				nBuilder.setSmallIcon(R.drawable.fusionnotif);
				contentView.setImageViewBitmap(R.id.legacy_icon, b);
				contentView.setImageViewResource(R.id.legacy_right_icon,
						R.drawable.fusionnotif);
				// alpha 153

			} else {
				contentView.setImageViewResource(R.id.legacy_icon,
						R.drawable.fusionnotif);
				contentView
						.setViewVisibility(R.id.legacy_right_icon, View.GONE);
			}

			if (p != null) {
				if (!silent)
					nBuilder.setSound(Uri.parse(strRingtonePreference));
				nBuilder.setLights(p.getLightColor(), 100, 5000);
			} else if (silent)
				nBuilder.setDefaults(Notification.DEFAULT_LIGHTS);
			else {
				nBuilder.setDefaults(Notification.DEFAULT_LIGHTS);
				nBuilder.setSound(Uri.parse(strRingtonePreference));
			}

			nBuilder.setWhen(topMessage.getCreationDateTime());
			contentView.setLong(R.id.legacy_time, "setTime",
					topMessage.getCreationDateTime());

			nBuilder.setOngoing(false);

			if (silent)
				nBuilder.setTicker(null);
			else
				nBuilder.setTicker(fn.contactItem.getDisplayName() + ": "
						+ topMessage.getText());

			final AudioManager am = (AudioManager) this
					.getSystemService(Context.AUDIO_SERVICE);
			// Android 4.2.1+ workaround
			if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
				if (!silent && useVibration)
					nBuilder.setVibrate(new long[] { 0, 500, 50, 200, 50, 200 });
				else
					nBuilder.setVibrate(new long[] { 0, 0 });
			} else
				nBuilder.setVibrate(new long[] { 0, 0 });

			nBuilder.setAutoCancel(true);
			if (VERSION.SDK_INT < 16 && VERSION.SDK_INT >= 11)
				nBuilder.setContent(contentView);
			Notification notif = nBuilder.build();

			if (VERSION.SDK_INT < 16)
				notif.flags |= NotificationCompat.FLAG_HIGH_PRIORITY;

		if (wakeDevice) {
			WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
					| PowerManager.ACQUIRE_CAUSES_WAKEUP, "Notification");
			wl.acquire();
			wl.release();
		}
			if (fn.notificationId == 0) {
				nm.notify(fn.contactItem.getIdentifierTag(), 1002, notif);
			} else {
				nm.notify(fn.notificationId, notif);
			}

		}

	}

	private PowerManager pm;
	private SharedPreferences mPrefs;
	private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key.equals("pfGoogleVoiceAccount")) {
				IMProvider imProvider = IMProvider.findIMProvider(
						IMProviderTypes.GVoice, MemoryCache.IMProviders);
				if (imProvider != null) {
					GVoiceProvider gvProvider = (GVoiceProvider) imProvider;
					gvProvider.setAccountName(sharedPreferences.getString(
							"pfGoogleVoiceAccount", ""));
					WorkerService.this.refreshLocalGoogleAccounts(null);
					gvProvider.setRequiresRestart(true);
					mIMProvidersHandlerThread.forceResume();
					mUIRequestHandlerThread.forceResume();
				}
			} else if (key.equals("pfUseNotifications")) {
				mUseNotificationsPreference = mPrefs.getBoolean(
						"pfUseNotifications", true);
			} else if (key.equals("pfSMSEnabled")) {
				IMProvider imProvider = IMProvider.findIMProvider(
						IMProviderTypes.SMS, MemoryCache.IMProviders);
				if (imProvider != null) {
					boolean enabled = (WorkerService.this.getPackageManager()
							.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) && mPrefs
							.getBoolean("pfSMSEnabled", true));
					if (imProvider.isAvailable() != enabled) {
						imProvider.setAvailable(enabled);
						mIMProvidersHandlerThread.forceResume();
						mUIRequestHandlerThread.forceResume();
					}
				}
			} else if (key.equals("pfSMSProcessIncomingMessages")) {
				boolean processIncomingMessages = mPrefs.getBoolean(
						"pfSMSProcessIncomingMessages", true);
				IMProvider imProvider = IMProvider.findIMProvider(
						IMProviderTypes.SMS, MemoryCache.IMProviders);
				((SMSProvider) imProvider)
						.setProcessIncomingMessages(processIncomingMessages);
				mIMProvidersHandlerThread.forceResume();
				mUIRequestHandlerThread.forceResume();
			}

		}
	};

	@Override
	public void onCreate() {

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mPrefs.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
		this.mUseNotificationsPreference = mPrefs.getBoolean(
				"pfUseNotifications", true);

		SMSProvider smsProvider = new SMSProvider(this, mWorkerServiceMessenger);
		boolean processIncomingMessages = mPrefs.getBoolean(
				"pfSMSProcessIncomingMessages", true);
		smsProvider.setProcessIncomingMessages(processIncomingMessages);
		if (this.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_TELEPHONY)
				&& mPrefs.getBoolean("pfSMSEnabled", true))
			smsProvider.setAvailable(true);

		String gvAccount = mPrefs.getString("pfGoogleVoiceAccount",
				new String());
		GVoiceProvider gvProvider = new GVoiceProvider(this,
				mWorkerServiceMessenger, gvAccount);

		MemoryCache.IMProviders = new IMProvider[] { smsProvider, gvProvider };

		mIMProvidersHandlerThread = new DBRequestHandlerThread(
				new DBMessagesAdapter(this), MemoryCache.IMProviders,
				mWorkerServiceMessenger);
		mIMProvidersHandlerThread
				.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
		mUIRequestHandlerThread = new UIRequestHandlerThread(
				this.getApplicationContext(), new DBMessagesAdapter(this),
				mWorkerServiceMessenger);
		mUIRequestHandlerThread
				.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		pm = (PowerManager) getSystemService(POWER_SERVICE);
		queueContactsRefresh();
		refreshLocalGoogleAccounts(null);

		this.getApplicationContext()
				.getContentResolver()
				.registerContentObserver(
						ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
						false, contentObserver);
	}

	private class MyContentObserver extends ContentObserver {

		public MyContentObserver() {
			super(null);
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			if (mUIRequestHandlerThread != null)
				mUIRequestHandlerThread.queuePhoneContactListRefresh();
		}
	}

	MyContentObserver contentObserver = new MyContentObserver();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(this.getClass().getSimpleName(), "Start Service");
		if (mIMProvidersHandlerThread != null
				&& !mIMProvidersHandlerThread.isAlive())
			mIMProvidersHandlerThread.start();

		if (mUIRequestHandlerThread != null
				&& !mUIRequestHandlerThread.isAlive())
			mUIRequestHandlerThread.start();

		if (intent != null) {
			Parcelable[] pItems = intent
					.getParcelableArrayExtra("notificationMarkReadMessageItems");

			intent.putExtra("notificationMarkReadMessageItems", (String) null);
			if (pItems != null) {

				String dismissValue = mPrefs.getString(
						"pfNotificationDismissAction", "2");
				int count = 0;
				if (dismissValue.equals("1"))
					count = 1;
				else if (dismissValue.equals("2"))
					count = pItems.length;

				for (int i = 0; i < count; i++) {
					mIMProvidersHandlerThread
							.queueMarkMessageAsRead((MessageItem) pItems[i]);
					int len = this.unreadMessageList.size();
					for (int j = len - 1; j >= 0; j--)
						if (((MessageItem) pItems[i]).getMessageId() == this.unreadMessageList
								.get(j).getMessageId()) {
							this.unreadMessageList.remove(j);
							break;
						}
				}

			}

			MessageItem notificationCopyMessageItem = intent
					.getParcelableExtra("notificationCopyMessageItem");
			intent.putExtra("notificationCopyMessageItem", (String) null);
			if (notificationCopyMessageItem != null) {

				if (Build.VERSION.SDK_INT >= 11) {
					V11.SetClipboard(this, "Fusion Message",
							notificationCopyMessageItem.getText());
				} else {
					android.text.ClipboardManager clipboard = (android.text.ClipboardManager) this
							.getSystemService(Context.CLIPBOARD_SERVICE);
					clipboard.setText(notificationCopyMessageItem.getText());
				}
				Toast.makeText(this, "Copied to Clipboard", Toast.LENGTH_SHORT)
						.show();
			}
		}
		return Service.START_STICKY;

	}

	public boolean queueContactsRefresh() {
		if (mIMProvidersHandlerThread == null)
			return false;
		mIMProvidersHandlerThread.queueDBContactListRefresh();
		return true;
	}

	public boolean queueMarkContactAsRead(ContactItem contactItem) {
		int len = this.unreadMessageList.size();
		boolean changed = false;
		for (int i = len - 1; i >= 0; i--) {
			if (contactItem.isMessageItemIsValid(this.unreadMessageList.get(i))) {
				changed = true;
				this.unreadMessageList.remove(i);
			}
		}
		if (changed)
			rebuildNotifications(true);
		if (mIMProvidersHandlerThread != null)
			return mIMProvidersHandlerThread
					.queueMarkContactAsRead(contactItem);

		return false;
	}

	public boolean deleteConversation(ContactItem contactItem) {
		if (mIMProvidersHandlerThread == null)
			return false;
		mIMProvidersHandlerThread.queueDeleteConversation(contactItem);
		return true;
	}

	public boolean queueMessageItem(MessageItem msgItem) {
		if (mIMProvidersHandlerThread == null)
			return false;
		mIMProvidersHandlerThread.queueMessageItem(msgItem);
		return true;
	}

	public boolean requestConversation(ContactItem contactItem) {
		if (mIMProvidersHandlerThread == null)
			return false;
		mIMProvidersHandlerThread.queueConversationRequestItem(contactItem);
		return true;
	}

	private void mergeContacts() {
		if (mPhoneContactList == null || mDBContactList == null)
			return;
		List<ContactItem> removeList = new ArrayList<ContactItem>();
		List<ContactItem> addList = new ArrayList<ContactItem>();
		List<ContactItem> newList = new ArrayList<ContactItem>();
		for (ContactItem c : mPhoneContactList)
			newList.add(c.clone());

		int iCount = mDBContactList.size();
		int jCount = newList.size();
		for (int i = 0; i < iCount; i++) {
			ContactItem dbContact = mDBContactList.get(i);
			ContactAddress dbContactAddress = dbContact.getContactAddresses()[0];

			IMProviderTypes dbProviderType = dbContactAddress
					.getIMProviderType();
			String dbAddress = dbContactAddress.getDisplayAddress();

			IMProvider imProvider = IMProvider.findIMProvider(dbProviderType,
					MemoryCache.IMProviders);

			String parsedAddress = dbContactAddress.getParsedAddress();

			boolean found = false;
			for (int j = 0; j < jCount; j++) {
				ContactItem crContact = newList.get(j);
				ContactAddress[] contactAddresses = crContact
						.getContactAddresses();
				int kCount = contactAddresses.length;
				for (int k = 0; k < kCount; k++) {
					ContactAddress ca = contactAddresses[k];
					if (ca.getIMProviderType() == dbProviderType
							&& ca.getParsedAddress().equals(parsedAddress)) {
						found = true;
						if (crContact.getLastMessageItem() == null
								|| dbContact.getLastMessageItem()
										.getCreationDateTime() > crContact
										.getLastMessageItem()
										.getCreationDateTime())
							crContact.setLastMessageItem(dbContact
									.getLastMessageItem());
						// removeList.add(dbContact);

						crContact.setUnreadCount(crContact.getUnreadCount()
								+ dbContact.getUnreadCount());
						break;
					}
				}
				if (found)
					break;
			}
			if (!found) {
				dbContactAddress.setParsedAddress(parsedAddress);
				dbContact
						.setContactAddresses(new ContactAddress[] { dbContactAddress });
				addList.add(dbContact);
			}
		}

		for (ContactItem addItem : addList)
			newList.add(addItem);

		Collections.sort(newList);
		MemoryCache.ContactsList = newList;
		Message msg = Message.obtain(null,
				HandlerMessages.MSG_CONTACTSRECEIVED, newList);
		this.signalMessageItem(msg);

	}

	public void addClientMessenger(Messenger clientMessenger) {
		if (clientMessengers == null)
			clientMessengers = new ArrayList<Messenger>();
		if (!clientMessengers.contains(clientMessenger))
			clientMessengers.add(clientMessenger);
	}

	public void removeClientMessenger(Messenger clientMessenger) {
		if (clientMessengers == null)
			return;
		if (clientMessengers.contains(clientMessenger))
			clientMessengers.remove(clientMessenger);
	}

	public void signalMessageItem(Message msg) {
		if (clientMessengers != null)
			for (Messenger m : clientMessengers)
				try {
					m.send(Message.obtain(msg));
				} catch (RemoteException e) {
					e.printStackTrace();
				}

	}

	@Override
	public void onDestroy() {

		Log.d(this.getClass().getSimpleName(), "Stop Service");

		for (int i = 0; i < MemoryCache.IMProviders.length; i++)
			MemoryCache.IMProviders[i].stop();
		mIMProvidersHandlerThread.interrupt();
		mUIRequestHandlerThread.interrupt();
		try {
			nm.cancelAll();
		} catch (Exception e) {
		}

		super.onDestroy();
	}

	private static long lastEnabledNotifications = 0L;

	public static void setUseNotifications(boolean useNotitifications) {
		if (!useNotitifications && WorkerService.useNotifications)
			lastEnabledNotifications = SystemClock.elapsedRealtime();
		WorkerService.useNotifications = useNotitifications;

		Log.d("WorkerService",
				"useNotifications: " + String.valueOf(useNotifications));
	}

	public void cancelAllNotifications() {
		if (nm != null)
			nm.cancelAll();
	}

}
