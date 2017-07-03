package im.fsn.messenger.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import im.fsn.messenger.ContactAddress;
import im.fsn.messenger.ContactItem;
import im.fsn.messenger.Conversation;
import im.fsn.messenger.CustomViewPagerAdapterV8;
import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.MemoryCache;
import im.fsn.messenger.MessageItem;
import im.fsn.messenger.PhoneUtilsLite;
import im.fsn.messenger.R;
import im.fsn.messenger.ThemeOptions;
import im.fsn.messenger.WorkerService;
import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

public class QuickReplyDialog extends ActionBarActivity {

	private int themeResId = -1;
	private boolean mSoftKeyboardVisible;

	protected View getRootView(int resourceId, int j) {
		// mEmojiContainerId = j;
		final FrameLayout framelayout = new FrameLayout(this) {

			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				Display display = getWindowManager().getDefaultDisplay();
				Point size = new Point();
				size.x = display.getWidth();
				size.y = display.getHeight();

				float heightPercentage = mPrefs
						.getInt("pfQuickReplyHeight", 65) / 100f;
				int layoutHeight = (int) (size.y * heightPercentage);

				int actualHeight = android.view.View.MeasureSpec
						.getSize(heightMeasureSpec);
				int desiredHeight = android.view.View.MeasureSpec
						.makeMeasureSpec(layoutHeight,
								android.view.View.MeasureSpec.AT_MOST);
				if (actualHeight > layoutHeight)
					super.onMeasure(widthMeasureSpec, desiredHeight);
				else
					super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			}

		};

		framelayout.setLayoutParams(new android.view.ViewGroup.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		getLayoutInflater().inflate(resourceId, framelayout, true);
		return framelayout;
	}

	private void resizeWindow() {
		android.view.Window w = this.getWindow();
		Display display = this.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		size.x = display.getWidth();
		size.y = display.getHeight();
		// display.getSize(size);

		float widthPercentage = mPrefs.getInt("pfQuickReplyWidth", 95) / 100f;
		float heightPercentage = mPrefs.getInt("pfQuickReplyHeight", 65) / 100f;

		int layoutWidth = (int) (size.x * widthPercentage);
		int layoutHeight = (int) (size.y * heightPercentage);

		android.view.WindowManager.LayoutParams params = w.getAttributes();

		params.width = layoutWidth;
		params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
		params.alpha = 1.0f - (mPrefs.getInt("pfQuickReplyTransparency", 5) / 100f);
		params.dimAmount = mPrefs.getInt("pfQuickReplyBackgroundDimming", 50) / 100f;

		w.setAttributes((android.view.WindowManager.LayoutParams) params);
		// This sets the window size, while working afround the
		// IllegalStateException thrown by ActionBarView

		w.setLayout(layoutWidth,
				android.view.WindowManager.LayoutParams.WRAP_CONTENT);
		w.setGravity(Gravity.CENTER);

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(0x00000008); // Window.FEATURE_ACTION_BAR);

		if (this.themeResId == -1)
			this.themeResId = ThemeOptions.getThemeResId(this, false, true);

		this.setTheme(this.themeResId);

		super.onCreate(savedInstanceState);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		android.view.Window w = this.getWindow();
		if (mPrefs.getBoolean("pfUseHardwareAcceleration", true))
			w.setFlags(0x01000000, 0x01000000); // WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
		w.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
				WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		w.setFlags(0, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
		w.setFlags(0, WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);

		setContentView(getRootView(R.layout.quickreply, 0));

		resizeWindow();

		ActionBar bar = this.getSupportActionBar();
		bar.setTitle(R.string.app_name);
		bar.setSubtitle(R.string.subtitle);

		boolean showActionBar = mPrefs.getBoolean("pfQuickReplyShowActionBar",
				true);
		if (!showActionBar)
			bar.hide();

		this.mViewPager = (ViewPager) this.findViewById(R.id.vpMain);
		this.ptsQuickReply = (PagerTabStrip) this.findViewById(R.id.ptsMain);

		char navMode = mPrefs.getString("pfPageNavigationMode", "1").charAt(0);
		if (navMode != '0' && navMode != '1')
			navMode = '1';
		switch (navMode) {
		default:
		case '0':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			ptsQuickReply.setVisibility(View.GONE);
			break;
		case '1':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			ptsQuickReply.setVisibility(View.VISIBLE);

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
			ptsQuickReply.setBackgroundResource(taBackground
					.getResourceId(0, 0));
			taBackground.recycle();

			TypedValue tvBarTabTextStyle = new TypedValue();
			getTheme().resolveAttribute(actionBarTabTextStyleResId,
					tvBarTabTextStyle, true);

			TypedArray taTitleTextColor = this.obtainStyledAttributes(
					tvBarTabTextStyle.resourceId,
					new int[] { android.R.attr.textColor });
			// ptsQuickReply.setSelectedColor(taTitleTextColor.getColor(0, 0));
			ptsQuickReply.setTextColor(taTitleTextColor.getColor(0, 0));
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
			ptsQuickReply.setTabIndicatorColor(color);
			taIndicator.recycle();
			break;
		case '2':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			ptsQuickReply.setVisibility(View.GONE);
			break;
		case '3':
			bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			ptsQuickReply.setVisibility(View.GONE);
			break;
		}
		onNewIntent(getIntent());
	}

	@Override
	public void onDestroy() {
		MemoryCache.QuickReplyContactsList.clear();
		super.onDestroy();

	}

	private ViewPager mViewPager;
	private CustomViewPagerAdapterV8 mViewPagerAdapter;
	private PagerTabStrip ptsQuickReply;
	private ContactItem startUpContactItem = null;
	private SharedPreferences mPrefs;

	@Override
	protected void onNewIntent(Intent intent) {

		this.mViewPagerAdapter = new CustomViewPagerAdapterV8(this,
				this.getSupportFragmentManager());
		this.mViewPager.setAdapter(mViewPagerAdapter);
		this.mViewPager.setOnPageChangeListener(onPageChangeListener);

		Parcelable[] pItems = intent
				.getParcelableArrayExtra("notificationContactItems");
		if (pItems != null) {
			for (int i = 0; i < pItems.length; i++) {

				ContactItem c = (ContactItem) pItems[i];
				if (i == 0)
					this.startUpContactItem = c;
				boolean found = false;
				for (ContactItem mc : MemoryCache.QuickReplyContactsList) {
					if (mc.equals(c)) {
						found = true;
						break;
					}
				}
				if (!found)
					MemoryCache.QuickReplyContactsList.add(c);
			}

		}
		String intentAction = intent.getAction();
		if (intentAction != null
				&& intent.getAction().equals("android.intent.action.SENDTO")) {
			Uri data = intent.getData();
			if (data.equals(null)) {
			} else {
				if (data.getScheme().equals("smsto")) {
					String externalAddress = PhoneUtilsLite.parseAddress(
							data.getSchemeSpecificPart(), null, null);

					if (externalAddress != null) {
						ContactItem c = MemoryCache.getContact(
								IMProviderTypes.SMS, externalAddress);
						boolean found = false;
						for (ContactItem mc : MemoryCache.QuickReplyContactsList) {
							if (mc.equals(c)) {
								found = true;
								break;
							}
						}
						if (!found)
							MemoryCache.QuickReplyContactsList.add(c);
					}
				}
			}
		}
		rebuildViewPager();

		startService(new Intent(this, WorkerService.class));
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
	public boolean onSearchRequested() {
		if (mnuSearch != null) {
			MenuItemCompat.expandActionView(mnuSearch);
			return true;
		}
		return false;
	}

	private void cancelSearchView(int index) {

		Fragment f = this.getSupportFragmentManager().findFragmentByTag(
				"android:switcher:" + mViewPager.getId() + ":" + index);

		ConversationFragment convF = (ConversationFragment) f;
		if (convF != null)
			convF.cancelSearch();

	}

	private View searchView;
	private MenuItem mnuSearch;

	private void rebuildViewPager() {

		boolean pfQuickAutoKeyboard = mPrefs.getBoolean("pfQuickAutoKeyboard",
				true);
		for (ContactItem c : MemoryCache.QuickReplyContactsList) {
			Bundle b = new Bundle();
			b.putParcelable("contactItem", c);
			b.putBoolean("openEditTextImmediately", pfQuickAutoKeyboard);
			b.putBoolean("isQuickReply", true);
			mViewPagerAdapter.addPage(ConversationFragment.class, b);
		}

		this.mViewPager
				.setOffscreenPageLimit(MemoryCache.QuickReplyContactsList
						.size());

		mViewPagerAdapter.notifyDataSetChanged();

	}

	@Override
	public void onStart() {
		super.onStart();
		Context c = getApplicationContext();
		c.bindService(new Intent(c, WorkerService.class), mConnection,
				Context.BIND_AUTO_CREATE);
		this.mIsBound = true;

	}

	private boolean mIsBound = false;
	private WorkerService mService;
	private Messenger mMessenger = new Messenger(new QuickReplyDialogHandler(
			this));

	@Override
	public void onStop() {
		super.onStop();
		WorkerService.setUseNotifications(true);
		if (mIsBound) {
			if (mService != null) {
				mService.removeClientMessenger(mMessenger);
			}
			getApplicationContext().unbindService(mConnection);
			mIsBound = false;
		}
		WorkerService.setUseNotifications(true);
	}

	public void closeContact() {
		int index = mViewPager.getCurrentItem();
		closeContactAt(index);
	}

	public void closeContactAt(int index) {

		MemoryCache.QuickReplyContactsList.remove(index);
		if (MemoryCache.QuickReplyContactsList.size() == 0) {
			this.finish();
			return;
		}
		mViewPagerAdapter.removePageAt(index);
		mViewPagerAdapter.notifyDataSetChanged();

		if (mViewPager.getCurrentItem() != index)
			mViewPager.setCurrentItem(index, false);

		return;
	}

	private static class QuickReplyDialogHandler extends Handler {
		private QuickReplyDialog q;

		QuickReplyDialogHandler(QuickReplyDialog quickReplyDialog) {
			this.q = quickReplyDialog;
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
				q.checkMessageIsRead(lastMessage);
				break;
			case HandlerMessages.MSG_MESSAGESENT:
				if (msg.obj == null)
					break;
				MessageItem sentMessage = (MessageItem) msg.obj;
				q.onMessageSent(sentMessage);

				break;
			case HandlerMessages.MSG_MESSAGERECEIVED:
			case HandlerMessages.MSG_MESSAGECHANGED:
				if (msg.obj == null)
					break;

				MessageItem m = (MessageItem) msg.obj;
				q.checkMessageIsRead(m);
				break;
			}

		}
	}

	private void onMessageSent(MessageItem m) {
		if (mService == null)
			return;

		int currentIndex = mViewPager.getCurrentItem();
		if (currentIndex < 0)
			return;

		int contactIndex = currentIndex;
		if (contactIndex >= MemoryCache.QuickReplyContactsList.size())
			return;

		ContactItem currentContactItem = MemoryCache.QuickReplyContactsList
				.get(contactIndex);
		if (!currentContactItem.isMessageItemIsValid(m))
			return;

		if (currentIndex != mViewPagerAdapter.getCount() - 1) {
			if (mPrefs.getBoolean("pfQuickReplyAutoAdvance", true))
				mViewPager.setCurrentItem(currentIndex + 1, true);
			else if (mPrefs.getBoolean("pfQuickAutoClose", true))
				this.finish();
		} else if (mPrefs.getBoolean("pfQuickAutoClose", true))
			this.finish();

	}

	private void checkMessageIsRead(MessageItem m) {
		if (mService == null)
			return;

		if (!m.isIncoming() || m.isRead())
			return;

		int currentIndex = mViewPager.getCurrentItem();
		if (currentIndex < 0)
			return;

		int contactIndex = currentIndex;
		if (contactIndex > MemoryCache.QuickReplyContactsList.size())
			return;

		ContactItem currentContactItem = MemoryCache.QuickReplyContactsList
				.get(contactIndex);
		if (currentContactItem.isMessageItemIsValid(m)) {
			mService.queueMarkContactAsRead(currentContactItem);
			Log.d("LauncherActivity", "MarkContactAsRead: "
					+ currentContactItem.getDisplayName());
		} else {
			boolean foundContact = false;
			for (int i = 0; i < MemoryCache.QuickReplyContactsList.size(); i++) {
				if (MemoryCache.QuickReplyContactsList.get(i)
						.isMessageItemIsValid(m)) {
					foundContact = true;
					break;
				}
			}
			if (!foundContact) {
				ContactItem ci = null;
				synchronized (MemoryCache.ContactsList) {
					for (ContactItem c : MemoryCache.ContactsList) {
						if (c.isMessageItemIsValid(m)) {
							ci = c;
							break;
						}
					}
				}
				if (ci == null) {
					ci = new ContactItem();
					ContactAddress ca = new ContactAddress();
					ca.setIMProviderType(m.getIMProviderType());
					ca.setParsedAddress(m.getExternalAddress());
					ci.setContactAddresses(new ContactAddress[] { ca });
					ci.setDisplayContactAddress(m.getExternalAddress());
					ci.setDisplayName(m.getExternalAddress());
				}
				MemoryCache.QuickReplyContactsList.add(ci);
				Bundle b = new Bundle();
				b.putParcelable("contactItem", ci);
				boolean pfQuickAutoKeyboard = mPrefs.getBoolean(
						"pfQuickAutoKeyboard", true);
				b.putBoolean("openEditTextImmediately", pfQuickAutoKeyboard);
				b.putBoolean("isQuickReply", true);
				mViewPagerAdapter.addPage(ConversationFragment.class, b);
				mViewPagerAdapter.notifyDataSetChanged();
			}
		}

	}

	private void buildSubtitle() {
		ActionBar bar = this.getSupportActionBar();
		int index = 0;
		if (this.mViewPager != null)
			index = this.mViewPager.getCurrentItem();

		bar.setTitle(this.mViewPagerAdapter.getPageTitle(index));
		bar.setSubtitle(null);

	}

	public OnPageChangeListener onPageChangeListener = new OnPageChangeListener() {

		@Override
		public void onPageScrollStateChanged(int arg0) {

		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {

		}

		@Override
		public void onPageSelected(int position) {
			Log.d("QuickReplyDialog", "OnPageSelectedStart: " + position);

			buildSubtitle();
			ActionBar mActionBar = getSupportActionBar();
			if (mActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST
					|| mActionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_TABS)
				mActionBar.setSelectedNavigationItem(position);

			Log.d("QuickReplyDialog", "OnPageSelectedStartMarkAsRead: "
					+ position);

			if (mService != null
					&& position < MemoryCache.QuickReplyContactsList.size()) {
				ContactItem c = MemoryCache.QuickReplyContactsList
						.get(position);
				mService.queueMarkContactAsRead(c);
			}

			if (mPrefs.getBoolean("pfQuickAutoKeyboard", true)) {
				Fragment f = getSupportFragmentManager().findFragmentByTag(
						"android:switcher:" + mViewPager.getId() + ":"
								+ position);
				ConversationFragment cf = (ConversationFragment) f;
				if (cf != null)
					cf.forceEditTextFocus();
			}

			if (position != previousViewPagerIndex)
				cancelSearchView(previousViewPagerIndex);

			previousViewPagerIndex = position;
			Log.d("LauncherActivity", "OnPageSelectedStop: " + position);

		}
	};
	int previousViewPagerIndex;
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = ((WorkerService.LocalBinder) service).getService();
			mService.addClientMessenger(mMessenger);
			if (startUpContactItem != null) {
				mService.queueMarkContactAsRead(startUpContactItem);

				startUpContactItem = null;
			}
			WorkerService.setUseNotifications(false);
			// when returning to activity
			// refreshActionbar(mViewPager.getCurrentItem());

		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}

	};

	public void selectContact(ContactItem c) {
		if (c == null)
			return;

		int index = -1;
		for (int i = 0; i < MemoryCache.QuickReplyContactsList.size(); i++) {
			if (MemoryCache.QuickReplyContactsList.get(i).equals(c)) {
				index = i;
				break;
			}
		}
		if (index == 0) {
			;// rebuildViewPager();
			mViewPager.setCurrentItem(1, true);
			return;
		} else if (index != -1) {
			ContactItem contact = MemoryCache.QuickReplyContactsList.get(index);
			MemoryCache.QuickReplyContactsList.remove(index);
			MemoryCache.QuickReplyContactsList.add(0, contact);
			// rebuildViewPager();
		} else {
			MemoryCache.QuickReplyContactsList.add(0, c);
		}
	}

}
