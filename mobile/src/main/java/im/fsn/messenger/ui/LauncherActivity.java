package im.fsn.messenger.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;

import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;

import im.fsn.messenger.ContactItem;

import im.fsn.messenger.CustomViewPagerAdapterV8;
import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.MemoryCache;
import im.fsn.messenger.MessageItem;
import im.fsn.messenger.R;
import im.fsn.messenger.ThemeOptions;
import im.fsn.messenger.WorkerService;
import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.apisupport.V14;

public class LauncherActivity extends ActionBarActivity {

	private final String[] skippableSMSApps = new String[] {
			"com.google.android.apps.googlevoice", "com.motorola.notification",
			"com.samsung.map", "com.sec.android.app.videoplayer", "com.smlds",
			"com.vlingo.midas", "com.wsomacp", "com.wssnps", "com.wssyncmldm",
			"com.broadcom.bt.app.system" };
	public OnPageChangeListener onPageChangeListener = new OnPageChangeListener() {

		@Override
		public void onPageScrollStateChanged(int arg0) {

		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {

		}

		@Override
		public void onPageSelected(int position) {

			Log.d("LauncherActivity", "OnPageSelectedStart: " + position);
			buildSubtitle();
			ActionBar mActionBar = getSupportActionBar();
			if (mActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST
					|| mActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_TABS)
				mActionBar.setSelectedNavigationItem(position);

			Log.d("LauncherActivity", "OnPageSelectedStartMarkAsRead: "
					+ position);
			mSelectedContactIndex = position - 1;
			if (position > 0
					&& mService != null
					&& mSelectedContactIndex < MemoryCache.MainUIContactsList
							.size()) {
				ContactItem c = MemoryCache.MainUIContactsList
						.get(mSelectedContactIndex);
				if (c.isUnread())
					mService.queueMarkContactAsRead(c);
			}

			Log.d("LauncherActivity", "OnPageSelectedStartHomeState: "
					+ position);
			int currentDisplayOptions = mActionBar.getDisplayOptions();
			boolean currentHomeAsUpState = ((currentDisplayOptions & ActionBar.DISPLAY_HOME_AS_UP) == ActionBar.DISPLAY_HOME_AS_UP);
			boolean preferredHomeAsUpState = (position != 0);
			if (currentHomeAsUpState != preferredHomeAsUpState)
				mActionBar.setDisplayOptions(
						(preferredHomeAsUpState ? ActionBar.DISPLAY_HOME_AS_UP
								: 0), ActionBar.DISPLAY_HOME_AS_UP);

			Log.d("LauncherActivity", "OnPageSelectedStartHideIMM: " + position);
			if (imm == null)
				imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm.isActive())
				imm.hideSoftInputFromWindow(
						mViewPager.getApplicationWindowToken(), 0);

			Log.d("LauncherActivity", "OnPageSelectedStartCancelSearchView: "
					+ position);
			if (position != previousViewPagerIndex) {
				cancelSearchView(previousViewPagerIndex);
				cancelSearchView(position);

			}

			previousViewPagerIndex = position;
			// refreshMenuOptions();
			Log.d("LauncherActivity", "OnPageSelectedStop: " + position);

		}
	};
	InputMethodManager imm;
	Thread.UncaughtExceptionHandler mUEHandler;
	int maxConversations = 3;
	private SharedPreferences mPrefs;
	private int dpPaddingMargin = 0;
	private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {

		}

	};
	private int previousViewPagerIndex = 0;
	private ViewPager mViewPager;
	private CustomViewPagerAdapterV8 mViewPagerAdapter;
	private PagerTabStrip ptsMain;
	private int themeResId = -1;
	private int mSelectedContactIndex = -1;

	private boolean mIsBound;
	private WorkerService mService;
	private Messenger mMessenger = new Messenger(new LauncherActivityHandler(
			LauncherActivity.this));

	private DrawerLayout mDrawerLayout;
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = ((WorkerService.LocalBinder) service).getService();
			mService.addClientMessenger(mMessenger);
			mService.refreshLocalGoogleAccounts(LauncherActivity.this);
			// when returning to activity
			// refreshActionbar(mViewPager.getCurrentItem());
			WorkerService.setUseNotifications(false);
			mService.cancelAllNotifications();
			if (mPrefs.getBoolean("showBetaWarning", true)) {
				showWarnings();
				mPrefs.edit().putBoolean("showBetaWarning", false).commit();
			}

		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;

		}

	};
	private ContactItem notificationContactItem = null;

	public static float dpToPixels(Context context, int dpPaddingMargin2) {

		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				dpPaddingMargin2, context.getResources().getDisplayMetrics());

	}

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

		// Thread.setDefaultUncaughtExceptionHandler(mUEHandler);
		onNewIntent(getIntent());

	}

	public void closeContact() {
		int index = mViewPager.getCurrentItem();
		closeContactAt(index - 1);
	}

	public void closeContactAt(int index) {

		MemoryCache.MainUIContactsList.remove(index);
		// mViewPager.setOffscreenPageLimit(0);

		mViewPagerAdapter.removePageAt(index + 1);
		mViewPagerAdapter.notifyDataSetChanged();
		// mViewPager.setOffscreenPageLimit(1);

		if (mViewPager.getCurrentItem() != index + 1)
			mViewPager.setCurrentItem(index + 1, false);
		buildSubtitle();
		return;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// Checks whether a hardware keyboard is available
		if (newConfig.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {

		}
	}

	private void buildSubtitle() {
		ActionBar bar = this.getSupportActionBar();
		int index = 0;
		if (this.mViewPager != null)
			index = this.mViewPager.getCurrentItem();
		if (index != 0) {
			bar.setTitle(this.mViewPagerAdapter.getPageTitle(index));
			bar.setSubtitle(null);
		} else {
			bar.setTitle(R.string.app_name);
			bar.setSubtitle(R.string.subtitle);
		}
	}

	private void buildUI() {
		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (mPrefs.getBoolean("pfUseHardwareAcceleration", true))
			getWindow().setFlags(0x01000000, 0x01000000); // WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
		setContentView(R.layout.main);

		this.mViewPager = (ViewPager) this.findViewById(R.id.vpMain);

		this.mDrawerLayout = (DrawerLayout) this
				.findViewById(R.id.drawer_layout);
		this.mDrawerLayout.setDrawerLockMode(
				DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
		this.ptsMain = (PagerTabStrip) this.findViewById(R.id.ptsMain);

		this.mViewPager.setOffscreenPageLimit(this.maxConversations + 1);

		ActionBar bar = this.getSupportActionBar();
		bar.setTitle(R.string.app_name);
		buildSubtitle();

		char navMode = mPrefs.getString("pfPageNavigationMode", "1").charAt(0);
		if (navMode != '0' && navMode != '1')
			navMode = '1';
		switch (navMode) {
		default:
		case '0':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			ptsMain.setVisibility(View.GONE);
			break;
		case '1':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			ptsMain.setVisibility(View.VISIBLE);

			TypedValue tvBarTabStyle = new TypedValue();
			int actionBarStyleResId;
			int backgroundResId;
			int actionBarTabTextStyleResId;
			int actionBarTabStyleResId;

			if (VERSION.SDK_INT >= 14) {
				backgroundResId = V14.getBackgroundStackedResId();
				actionBarStyleResId = V11.getActionBarStyleResId();
				actionBarTabTextStyleResId = V11
						.getActionBarTabTextStyleResId();
				actionBarTabStyleResId = V11.getActionBarTabStyleResId();
			} else if (VERSION.SDK_INT >= 11) {
				actionBarStyleResId = V11.getActionBarStyleResId();
				backgroundResId = android.R.attr.background;
				actionBarTabTextStyleResId = V11
						.getActionBarTabTextStyleResId();
				actionBarTabStyleResId = V11.getActionBarTabStyleResId();
			} else {

				backgroundResId = R.attr.backgroundStacked;
				actionBarStyleResId = R.attr.actionBarStyle;
				actionBarTabTextStyleResId = R.attr.actionBarTabTextStyle;
				actionBarTabStyleResId = R.attr.actionBarTabStyle;
			}

			getTheme().resolveAttribute(actionBarStyleResId, tvBarTabStyle,
					true);
			TypedArray taBackground = this.obtainStyledAttributes(
					tvBarTabStyle.resourceId, new int[] { backgroundResId });
			ptsMain.setBackgroundResource(taBackground.getResourceId(0, 0));
			taBackground.recycle();

			TypedValue tvBarTabTextStyle = new TypedValue();
			getTheme().resolveAttribute(actionBarTabTextStyleResId,
					tvBarTabTextStyle, true);

			TypedArray taTitleTextColor = this.obtainStyledAttributes(
					tvBarTabTextStyle.resourceId,
					new int[] { android.R.attr.textColor });
			// ptsMain.setSelectedColor(taTitleTextColor.getColor(0, 0));
			ptsMain.setTextColor(taTitleTextColor.getColor(0, 0));
			taTitleTextColor.recycle();

			TypedValue tvBarTabBarStyle = new TypedValue();
			getTheme().resolveAttribute(actionBarTabStyleResId,
					tvBarTabBarStyle, true);
			TypedArray taIndicator = this.obtainStyledAttributes(
					tvBarTabBarStyle.resourceId,
					new int[] { android.R.attr.background });
			TypedValue tvDivider = new TypedValue();
			taIndicator.getValue(0, tvDivider);
			int color = -1;
			StateListDrawable sldIndicator = (StateListDrawable) getResources()
					.getDrawable(tvDivider.resourceId);
			sldIndicator.setState(new int[] { android.R.attr.state_selected });
			Drawable drawable = sldIndicator.getCurrent();
			Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
					drawable.getIntrinsicHeight(), Config.ARGB_8888);
			Canvas canvas = new Canvas(bmp);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
			int height = bmp.getHeight();
			int width = bmp.getWidth();
			color = bmp.getPixel(width / 2, height / 2);
			ptsMain.setTabIndicatorColor(color);

			taIndicator.recycle();
			break;
		case '2':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			ptsMain.setVisibility(View.GONE);
			break;
		case '3':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			ptsMain.setVisibility(View.GONE);
			break;
		}

		this.mViewPagerAdapter = new CustomViewPagerAdapterV8(this,
				this.getSupportFragmentManager());
		this.mViewPager.setAdapter(mViewPagerAdapter);
		this.mViewPager.setOnPageChangeListener(onPageChangeListener);

		rebuildViewPager();

		this.dpPaddingMargin = mPrefs.getInt("pfContactsListRightMargin", 0);
		this.mPrefs
				.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

		// API11+

		LauncherActivity.this.selectContact(notificationContactItem);

	}

	public void selectContact(ContactItem c) {
		if (c == null)
			return;

		mSelectedContactIndex = 0;
		int index = -1;
		for (int i = 0; i < MemoryCache.MainUIContactsList.size(); i++) {
			if (MemoryCache.MainUIContactsList.get(i).equals(c)) {
				index = i;
				break;
			}
		}
		if (index == 0) {
			mViewPager.setCurrentItem(1, true);
			return;
		} else if (index != -1) {
			ContactItem contact = MemoryCache.MainUIContactsList.get(index);
			MemoryCache.MainUIContactsList.remove(index);
			MemoryCache.MainUIContactsList.add(0, contact);

			// mViewPager.setOffscreenPageLimit(0);
			mViewPagerAdapter.removePageAt(index + 1);
			Bundle b = new Bundle();
			b.putParcelable("contactItem", contact);
			mViewPagerAdapter.insertPage(ConversationFragment.class, b, 1);
			mViewPagerAdapter.notifyDataSetChanged();
			// mViewPager.setOffscreenPageLimit(1);

			mViewPager.setCurrentItem(1, true);
		} else {
			int count = MemoryCache.MainUIContactsList.size();

			if (count == maxConversations) {
				// mViewPager.setOffscreenPageLimit(0);
				mViewPagerAdapter.removePageAt(maxConversations);
				MemoryCache.MainUIContactsList.remove(maxConversations - 1);
			}

			MemoryCache.MainUIContactsList.add(0, c);
			Bundle b = new Bundle();
			b.putParcelable("contactItem", c);
			// mViewPager.setOffscreenPageLimit(0);
			mViewPagerAdapter.insertPage(ConversationFragment.class, b, 1);

			// mViewPager.setOffscreenPageLimit(1);
			mViewPager.setCurrentItem(1, true);
			// rebuildViewPager();

		}

		// mViewPager.setCurrentItem(1, true);

	}

	private void rebuildViewPager() {

		mViewPagerAdapter.addPage(ContactsFragment.class, null);
		for (ContactItem c : MemoryCache.MainUIContactsList) {
			Bundle b = new Bundle();
			b.putParcelable("contactItem", c);
			mViewPagerAdapter.addPage(ConversationFragment.class, b);
		}
		mViewPagerAdapter.notifyDataSetChanged();

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			mViewPager.setCurrentItem(0, true);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mViewPager.getCurrentItem() != 0) {
				mViewPager.setCurrentItem(0, true);
				return true;
			} else {
				;// this.moveTaskToBack(true);
				;// return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onSearchRequested() {
		if (mViewPager == null)
			return false;

		int index = mViewPager.getCurrentItem();
		Fragment f = this.getSupportFragmentManager().findFragmentByTag(
				"android:switcher:" + mViewPager.getId() + ":" + index);
		if (f == null)
			return false;
		if (index == 0) {
			return ((ContactsFragment) f).openSearch();
		} else {
			return ((ConversationFragment) f).openSearch();

		}

	}

	private void cancelSearchView(int index) {

		if (mViewPagerAdapter.getCount() >= index)
			return;
		Fragment f = mViewPagerAdapter.getItem(index);

		if (index == 0) {
			ContactsFragment cf = (ContactsFragment) f;
			if (cf != null)
				cf.cancelSearch();

		} else {
			ConversationFragment convF = (ConversationFragment) f;
			if (convF != null)
				convF.cancelSearch();
		}

	}

	private MenuItem mnuSettings;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = this.getMenuInflater();
		inflater.inflate(R.menu.global, menu);

		mnuSettings = menu.findItem(R.id.mnuSettings);

		mnuSettings.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				Intent i = new Intent(LauncherActivity.this,
						SettingsActivity.class);
				LauncherActivity.this.startActivityForResult(i, 1012);
				return true;

			}
		});

		if (ThemeOptions.isLightBackground(this, true)) {
			mnuSettings.setIcon(R.drawable.action_settings_light);
		} else {
			mnuSettings.setIcon(R.drawable.action_settings_dark);
		}

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1012) {
			this.finish();
			this.startActivity(new Intent(this, this.getClass()));
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		notificationContactItem = intent
				.getParcelableExtra("notificationContactItem");
		buildUI();

		startService(new Intent(LauncherActivity.this, WorkerService.class));
	}

	private void showWarnings() {

		BetaWarningDialogFragment f = new BetaWarningDialogFragment();
		f.show(getSupportFragmentManager(),
				BetaWarningDialogFragment.class.getSimpleName());

		PackageManager pm = LauncherActivity.this.getPackageManager();

		List<PackageInfo> pkInfos = pm
				.getInstalledPackages(PackageManager.GET_PERMISSIONS);
		boolean hasGVPermission = (pm
				.checkPermission(
						"com.google.android.apps.googlevoice.INBOX_NOTIFICATION.permission.C2D_MESSAGE",
						"im.fsn.messenger") == PackageManager.PERMISSION_GRANTED);
		boolean isGVAppsInstalled = false;
		List<PackageInfo> smsPackages = new ArrayList<PackageInfo>();
		for (PackageInfo pkInfo : pkInfos) {
			if (pkInfo.applicationInfo == null)
				continue;
			if (!pkInfo.applicationInfo.enabled)
				continue;
			if (pkInfo.requestedPermissions == null)
				continue;
			if (pkInfo.packageName
					.equals("com.google.android.apps.googlevoice"))
				isGVAppsInstalled = true;
			boolean skipPackage = false;
			for (int i = 0; i < skippableSMSApps.length; i++) {
				if (pkInfo.packageName.equals(skippableSMSApps[i])) {
					skipPackage = true;
					break;
				}
			}
			if (skipPackage)
				continue;
			boolean isPackageSelf = pkInfo.packageName
					.equals("im.fsn.messenger");

			boolean hasWriteSMS = false;
			boolean hasReceiveSMS = false;
			for (int i = 0; i < pkInfo.requestedPermissions.length; i++) {
				if (isPackageSelf) {
					break;
				}
				if (pkInfo.requestedPermissions[i]
						.equals("android.permission.WRITE_SMS")) {
					hasWriteSMS = true;

				} else if (pkInfo.requestedPermissions[i]
						.equals("android.permission.RECEIVE_SMS")) {
					hasReceiveSMS = true;
				}
			}
			if (hasWriteSMS && hasReceiveSMS)
				smsPackages.add(pkInfo);
		}

		if (!hasGVPermission && !isGVAppsInstalled) {
			String messageText = LauncherActivity.this
					.getString(R.string.GVoicePermissionWarning);
			AlertDialog.Builder b = new AlertDialog.Builder(
					LauncherActivity.this);
			b.setTitle(R.string.Warning);
			b.setMessage(messageText);
			b.setNeutralButton("OK", null);
			b.show();
		}

		boolean processingSMSIn = mPrefs.getBoolean(
				"pfSMSProcessIncomingMessages", true);
		if (smsPackages.size() == 0 && !processingSMSIn) {
			String messageText = LauncherActivity.this
					.getString(R.string.SMSProcessingMissing);
			AlertDialog.Builder b = new AlertDialog.Builder(
					LauncherActivity.this);
			b.setTitle(R.string.Warning);
			b.setMessage(messageText);
			b.setPositiveButton("Yes", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mPrefs.edit()
							.putBoolean("pfSMSProcessIncomingMessages", true)
							.commit();

				}
			});
			b.setNegativeButton("No", null);
			b.show();
		} else if (smsPackages.size() != 0 && processingSMSIn) {
			String messageText = LauncherActivity.this
					.getString(R.string.SMSProcessingConflictHeader);
			messageText += "\n\n";
			for (PackageInfo p : smsPackages) {
				messageText += p.applicationInfo.loadLabel(pm).toString();
				messageText += "\n\n";
			}
			messageText += LauncherActivity.this
					.getString(R.string.SMSProcessingConflictQuestion);
			AlertDialog.Builder b = new AlertDialog.Builder(
					LauncherActivity.this);
			b.setMessage(messageText);
			b.setPositiveButton("Yes", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mPrefs.edit()
							.putBoolean("pfSMSProcessIncomingMessages", false)
							.commit();
				}
			});
			b.setNegativeButton("No", null);
			b.show();
		}

	}

	@Override
	public void onStart() {
		super.onStart();
		Context c = getApplicationContext();
		c.bindService(new Intent(c, WorkerService.class), mConnection,
				Context.BIND_AUTO_CREATE);
		this.mIsBound = true;

	}

	@Override
	public void onStop() {
		super.onStop();
		if (mIsBound) {
			if (mService != null) {
				mService.removeClientMessenger(mMessenger);
			}
			getApplicationContext().unbindService(mConnection);
			mIsBound = false;
		}
		WorkerService.setUseNotifications(true);
	}

	private void checkMessageIsRead(MessageItem m) {
		if (mService == null)
			return;

		if (!m.isIncoming() || m.isRead())
			return;

		int currentIndex = mViewPager.getCurrentItem();
		if (currentIndex <= 0)
			return;

		int contactIndex = currentIndex - 1;
		if (contactIndex > MemoryCache.MainUIContactsList.size())
			return;

		ContactItem currentContactItem = MemoryCache.MainUIContactsList
				.get(contactIndex);
		if (!currentContactItem.isMessageItemIsValid(m))
			return;

		mService.queueMarkContactAsRead(currentContactItem);

		Log.d("LauncherActivity",
				"MarkContactAsRead: " + currentContactItem.getDisplayName());

	}

	private static class LauncherActivityHandler extends Handler {
		private LauncherActivity l;

		LauncherActivityHandler(LauncherActivity launcherActivity) {
			this.l = launcherActivity;
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HandlerMessages.MSG_NOTIFYCONTACTASREAD:
				if (msg.obj == null)
					break;
				break;
			case HandlerMessages.MSG_CONTACTSRECEIVED:
				if (msg.obj == null)
					break;

				break;
			case HandlerMessages.MSG_MESSAGEBATCHRECEIVED:
				if (msg.obj == null)
					break;
				MessageItem[] msgItems = (MessageItem[]) msg.obj;
				if (msgItems.length == 0)
					break;
				MessageItem lastMessage = msgItems[msgItems.length - 1];
				l.checkMessageIsRead(lastMessage);
				break;
			case HandlerMessages.MSG_MESSAGERECEIVED:
			case HandlerMessages.MSG_MESSAGECHANGED:
				if (msg.obj == null)
					break;

				MessageItem m = (MessageItem) msg.obj;
				l.checkMessageIsRead(m);
				break;
			}

		}
	}

}
