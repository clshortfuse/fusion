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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.internal.widget._AdapterViewICS;
import android.support.v7.internal.widget._SpinnerICS;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import im.fsn.messenger.ContactAddress;
import im.fsn.messenger.ContactItem;
import im.fsn.messenger.ConversationBannerSpinnerAdapter;
import im.fsn.messenger.ConversationListAdapter;
import im.fsn.messenger.ConversationListAdapter.MMSDownloader;
import im.fsn.messenger.ConversationListAdapter.OnDownloadMMSButtonClickListener;
import im.fsn.messenger.Emoji;
import im.fsn.messenger.EmojiImageAdapter;
import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.MMSObject;
import im.fsn.messenger.MemoryCache;
import im.fsn.messenger.MessageItem;
import im.fsn.messenger.MessageItem.MessageStatuses;
import im.fsn.messenger.R;
import im.fsn.messenger.ThemeOptions;
import im.fsn.messenger.Utils;
import im.fsn.messenger.WorkerService;
import im.fsn.messenger.apisupport.LinkSpec;
import im.fsn.messenger.apisupport.Linkify;
import im.fsn.messenger.apisupport.Patterns;
import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

public class ConversationFragment extends Fragment {

    private View completeView;
    private ImageButton ibSend;
    private ListView lvConversation;
    private LinearLayout llSend;
    private TextView tvCounter;
    private boolean isLightTheme;
    private ConversationListAdapter mListAdapter;
    private EditText etEntryText;
    private ContactItem mContactItem;
    private RelativeLayout rlContactBanner;
    private ImageView ivBannerBackground;
    private QuickContactBadge qcbBanner;
    private Spinner spBanner;
    private _SpinnerICS ispBanner;
    private ImageButton ibEditMore;
    private boolean isQuickReply;
    private LinearLayout llSlideOut;
    private ViewPager vpSlideOut;

    public static ConversationFragment newInstance(ContactItem contactItem,
                                                   int index) {
        return newInstance(contactItem, index, false);
    }

    public static ConversationFragment newInstance(ContactItem contactItem,
                                                   int index, boolean openEditTextImmediately) {
        ConversationFragment f = new ConversationFragment();
        Bundle b = new Bundle();
        b.putParcelable("contactItem", contactItem);
        b.putBoolean("openEditTextImmediately", openEditTextImmediately);
        b.putInt("index", index);
        b.putBoolean("isQuickReply", false);
        f.setArguments(b);
        return f;
    }

    public static ConversationFragment newInstance(ContactItem contactItem,
                                                   int index, boolean openEditTextImmediately, boolean isQuickReply) {
        ConversationFragment f = new ConversationFragment();
        Bundle b = new Bundle();
        b.putParcelable("contactItem", contactItem);
        b.putBoolean("openEditTextImmediately", openEditTextImmediately);
        b.putInt("index", index);
        b.putBoolean("isQuickReply", isQuickReply);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = this.getArguments();
        if (b != null) {
            this.mContactItem = b.getParcelable("contactItem");
            this.isQuickReply = b.getBoolean("isQuickReply");
            if (b.getBoolean("openEditTextImmediately"))
                this.forceEditTextFocus();
        }
        this.setHasOptionsMenu(true);
        this.setRetainInstance(true);
    }

    private String lastQuery;

    public void setSearchQuery(String query) {
        if (this.mListAdapter == null)
            return;
        if (query == null && lastQuery == null)
            return;
        if (query == null)
            this.mListAdapter.getFilter().filter(null);
        else
            this.mListAdapter.getFilter().filter(query.toLowerCase());
        this.lastQuery = query;
    }

    private SharedPreferences mPrefs;

    public static class ConfigurationData {

        public MessageItem[] messageList;

        public ConfigurationData(MessageItem[] messageList) {
            this.messageList = messageList;
        }

    }

    public boolean openSearch() {
        if (mnuConversationSearch != null && mnuConversationSearch.isVisible()) {
            MenuItemCompat.expandActionView(mnuConversationSearch);
            return true;
        }
        return false;
    }

    private MenuItem mnuConversationSearch;

    private MenuItem mnuConversationClose;
    private View searchView;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.conversationfragment, menu);
        mnuConversationSearch = menu.findItem(R.id.mnuConversationSearch);
        mnuConversationClose = menu.findItem(R.id.mnuConversationClose);

        if (ThemeOptions.isLightBackground(getActivity(), true)) {
            mnuConversationClose.setIcon(R.drawable.close_light);
        } else {
            mnuConversationClose.setIcon(R.drawable.close_dark);
        }

        mnuConversationClose
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {

                        FragmentActivity a = getActivity();
                        if (a.getClass() == LauncherActivity.class)
                            ((LauncherActivity) a).closeContact();
                        else if (a.getClass() == QuickReplyDialog.class)
                            ((QuickReplyDialog) a).closeContact();

                        return true;
                    }
                });

        if (Build.VERSION.SDK_INT >= 11) {
            searchView = V11
                    .buildSearchView(((ActionBarActivity) getActivity())
                            .getSupportActionBar().getThemedContext());
            V11.setOnQueryTextFocusChangeListener(searchView,
                    new OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            String query = null;
                            CharSequence c = V11.SearchViewGetQuery(searchView);
                            if (c == null || c.toString().equals("")) {
                                if (hasFocus) {
                                    query = new String();
                                    mnuConversationClose.setVisible(false);
                                } else {
                                    query = null;
                                    if (mnuConversationSearch != null)
                                        MenuItemCompat
                                                .collapseActionView(mnuConversationSearch);
                                    mnuConversationClose.setVisible(true);
                                }
                            } else {
                                query = c.toString();
                            }

                            setSearchQuery(query);

                        }
                    });
            V11.SetOnQueryTextListener(searchView,
                    new V11.SearchViewSetOnQueryTextListener() {

                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            return false;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {
                            setSearchQuery(newText);

                            return true;
                        }
                    });
            V11.SearchViewSetQueryHint(searchView, "Type to search");
            V11.SearchViewSetQuery(searchView, null, false);
            MenuItemCompat.setActionView(mnuConversationSearch, searchView);

        } else if (Build.VERSION.SDK_INT >= 8) {
            searchView = new SearchView(((ActionBarActivity) getActivity())
                    .getSupportActionBar().getThemedContext());
            ((SearchView) searchView)
                    .setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {

                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            String query = null;
                            CharSequence c = ((SearchView) searchView)
                                    .getQuery();
                            if (c == null || c.toString().equals("")) {
                                if (hasFocus)
                                    query = new String();

                                else {
                                    query = null;

                                    if (mnuConversationSearch != null)
                                        MenuItemCompat
                                                .collapseActionView(mnuConversationSearch);
                                }
                            } else {
                                query = c.toString();
                            }

                            setSearchQuery(query);

                        }
                    });
            ((SearchView) searchView)
                    .setOnQueryTextListener(new OnQueryTextListener() {

                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            return false;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {

                            setSearchQuery(newText);

                            return true;
                        }
                    });
            ((SearchView) searchView).setQueryHint("Type to search");
            MenuItemCompat.setActionView(mnuConversationSearch, searchView);

        }

        MenuItemCompat.setShowAsAction(mnuConversationSearch,
                MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
                        | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        if (ThemeOptions.isLightBackground(getActivity(), true))
            mnuConversationSearch.setIcon(R.drawable.action_search_light);
        else
            mnuConversationSearch.setIcon(R.drawable.action_search_dark);

        MenuItem mnuSettings = menu.add(R.string.settings);
        MenuItemCompat.setShowAsAction(mnuSettings,
                MenuItemCompat.SHOW_AS_ACTION_NEVER);
        mnuSettings.setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent i = new Intent(getActivity(), SettingsActivity.class);
                getActivity().startActivityForResult(i, 1012);
                return true;

            }
        });

        if (ThemeOptions.isLightBackground(getActivity(), true)) {
            mnuSettings.setIcon(R.drawable.action_settings_light);
        } else {
            mnuSettings.setIcon(R.drawable.action_settings_dark);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private ContactAddress getContactAddress() {
        try {
            if (VERSION.SDK_INT < 14)
                return (ContactAddress) this.ispBanner.getSelectedItem();
            else
                return (ContactAddress) this.spBanner.getSelectedItem();

        } catch (Exception e) {

        }
        Bundle args = ConversationFragment.this.getArguments();
        ContactAddress ca = null;
        if (args != null) {
            ca = args.getParcelable("selectedContactAddress");
        }
        if (ca == null) {
            ContactItem ci = getContactItem();
            if (ci != null)
                ca = ci.getContactAddresses()[0];
        }
        return ca;
    }

    public ContactItem getContactItem() {
        if (this.mContactItem != null)
            return this.mContactItem;
        Bundle args = ConversationFragment.this.getArguments();
        if (args == null)
            return null;
        this.mContactItem = args.getParcelable("contactItem");
        return this.mContactItem;
    }

    private static class ContactBannerImageBuilder extends
            AsyncTask<ContactItem, Void, Bitmap> {

        private Context mContext;
        private boolean isLightTheme;

        public ContactBannerImageBuilder(Context mContext, ImageView ivBanner,
                                         boolean isLightTheme) {
            this.mContext = mContext;
            this.ivBanner = ivBanner;
            this.isLightTheme = isLightTheme;
        }

        private ImageView ivBanner;

        @Override
        protected Bitmap doInBackground(ContactItem... params) {
            ContactItem c = params[0];
            long contactId = c.getId();
            if (contactId == 0)
                return null;

            Uri contactUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_URI,
                    String.valueOf(contactId));

            InputStream input;
            if (VERSION.SDK_INT >= 14)
                input = V14.openHiResContactPhotoInputStream(
                        mContext.getContentResolver(), contactUri);
            else
                input = ContactsContract.Contacts.openContactPhotoInputStream(
                        mContext.getContentResolver(), contactUri);

            Bitmap bFullScale = null;
            if (input != null) {
                bFullScale = BitmapFactory.decodeStream(input);

            }
            if (bFullScale == null) {
                bFullScale = BitmapFactory.decodeResource(mContext
                                .getResources(),
                        (isLightTheme ? R.drawable.ic_contact_picture_light
                                : R.drawable.ic_contact_picture_dark));

            }

            Bitmap blurred = Utils.fastblur(bFullScale, 10, 0, 0,
                    bFullScale.getWidth(), bFullScale.getHeight());

            int bannerHeight = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 72, mContext.getResources()
                            .getDisplayMetrics());

            float height = blurred.getHeight();
            float width = blurred.getWidth();
            float scale = 1.0f;

            if (height > bannerHeight)
                scale = height / bannerHeight;

            if (scale != 1.0f) {
                blurred = Bitmap.createBitmap(blurred, 0,
                        (int) (height / 2 - bannerHeight / 2), (int) width,
                        (int) (height / scale));
            }

            return blurred;
        }

        @Override
        protected void onPostExecute(Bitmap result) {

            super.onPostExecute(result);

            ivBanner.setImageBitmap(result);
            ivBanner.setColorFilter(0xffaaaaaa, PorterDuff.Mode.MULTIPLY);

        }
    }

    private void buildContactBanner(View v) {
        ivBannerBackground = (ImageView) v
                .findViewById(R.id.ivBannerBackground);
        qcbBanner = (QuickContactBadge) v.findViewById(R.id.qcbBanner);

        if (VERSION.SDK_INT < 14)
            ispBanner = (_SpinnerICS) v.findViewById(R.id.spConversationBanner);
        else
            spBanner = (Spinner) v.findViewById(R.id.spConversationBanner);

        ContactBannerImageBuilder builder = new ContactBannerImageBuilder(
                this.getActivity(), this.ivBannerBackground, isLightTheme);
        builder.execute(this.mContactItem);
        Bitmap bThumbnail = mContactItem.getThumbnail64x64();
        if (bThumbnail == null)
            bThumbnail = BitmapFactory.decodeResource(getActivity()
                            .getResources(),
                    (isLightTheme ? R.drawable.ic_contact_picture_light
                            : R.drawable.ic_contact_picture_dark));
        if (mContactItem.getId() == 0) {
            ContactAddress ca = mContactItem.getContactAddresses()[0];
            if (ca.getIMProviderType() == IMProviderTypes.SMS
                    || ca.getIMProviderType() == IMProviderTypes.GVoice)
                qcbBanner.assignContactFromPhone(ca.getParsedAddress(), true);
        } else {
            Uri contactUri = Uri.withAppendedPath(
                    ContactsContract.Contacts.CONTENT_URI,
                    String.valueOf(mContactItem.getId()));
            qcbBanner.assignContactUri(contactUri);
        }
        qcbBanner.setImageBitmap(bThumbnail);

        char navMode = mPrefs.getString("pfPageNavigationMode", "1").charAt(0);
        if (navMode != '0' && navMode != '1')
            navMode = '1';
        String sThemeOption = mPrefs.getString("pfThemeOption", "0");
        if (sThemeOption.equals("1") || sThemeOption.equals("2")
                || sThemeOption.equals("4") || sThemeOption.equals("5")) {
            if (VERSION.SDK_INT < 14)
                ispBanner.getBackground().setColorFilter(0xffdddddd,
                        PorterDuff.Mode.SRC_ATOP); // holo_grey_light
            else
                spBanner.getBackground().setColorFilter(0xffdddddd,
                        PorterDuff.Mode.SRC_ATOP); // holo_grey_light
        }

        buildSpinnerAdapters();
    }

    private CustomTabPagerAdapter mPagerAdapter;

    private class CustomTabPagerAdapter extends PagerAdapter implements
            OnTabChangeListener, OnPageChangeListener {

        private ViewPager mViewPager;
        private TabHost mTabHost;

        public CustomTabPagerAdapter(ViewPager mViewPager, TabHost mTabHost) {
            this.mTabHost = mTabHost;
            this.mViewPager = mViewPager;
            this.mViewPager.setOnPageChangeListener(this);
            this.mTabHost.setOnTabChangedListener(this);
            this.mViewPager.setOffscreenPageLimit(3);
            mTabHost.setup();
            if (!bannerOnTop)
                mTabHost.addTab(mTabHost.newTabSpec("Services")
                        .setIndicator("Services")
                        .setContent(R.id.vServicesFake));
            mTabHost.addTab(mTabHost.newTabSpec("Emoji").setIndicator("Emoji")
                    .setContent(R.id.vEmojiFake));
            mTabHost.addTab(mTabHost.newTabSpec("Camera")
                    .setIndicator("Camera").setContent(R.id.vCameraFake));

            mViewPager.setAdapter(this);

        }

        @Override
        public int getCount() {
            if (bannerOnTop)
                return 2;
            else
                return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (bannerOnTop) {
                switch (position) {
                    default:
                    case 0:
                        return "Emoticons";
                    case 1:
                        return "Camera";
                }
            } else {
                switch (position) {
                    default:
                    case 0:
                        return "Services";
                    case 1:
                        return "Emoticons";
                    case 2:
                        return "Camera";
                }
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return (view == object);
        }

        private View buildEmojiGridView(ViewGroup collection) {
            Context mContext = collection.getContext();
            LayoutInflater mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            GridView gv = new GridView(mContext);
            gv.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                    LayoutParams.FILL_PARENT));
            int columnWidth = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 48, getResources()
                            .getDisplayMetrics());

            gv.setColumnWidth(columnWidth);
            gv.setNumColumns(GridView.AUTO_FIT);
            gv.setVerticalSpacing(columnWidth / 12);
            gv.setHorizontalSpacing(columnWidth / 12);
            gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            gv.setGravity(Gravity.CENTER);
            gv.setAdapter(new EmojiImageAdapter(mContext));
            gv.setOnItemClickListener(new OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {

                    int value = (int) (Integer) parent
                            .getItemAtPosition(position);
                    String s = new String(java.lang.Character.toChars(value));
                    int start = etEntryText.getSelectionStart();
                    int end = etEntryText.getSelectionEnd();
                    if (start - end != 0) {
                        etEntryText.getText().replace(Math.min(start, end),
                                Math.max(start, end), s);
                    } else
                        etEntryText.getText().replace(start, end, s);

                }
            });

            collection.addView(gv);
            return gv;
        }

        private View buildServiceView(ViewGroup collection) {
            Context mContext = collection.getContext();
            LayoutInflater mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            RelativeLayout v = (RelativeLayout) mInflater.inflate(
                    R.layout.contact_banner, null);

            collection.addView(v);
            buildContactBanner(v);

            return v;

        }

        private View buildCameraView(ViewGroup collection) {
            Context mContext = collection.getContext();
            LayoutInflater mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            TextView tv = new TextView(mContext);
            tv.setText("Coming Soon!");
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            collection.addView(tv);
            return tv;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {

            if (bannerOnTop) {
                switch (position) {
                    case 0:
                        return buildEmojiGridView(collection);
                    case 1:
                        return buildCameraView(collection);
                }
            } else {
                switch (position) {
                    case 0:
                        return buildServiceView(collection);
                    case 1:
                        return buildEmojiGridView(collection);
                    case 2:
                        return buildCameraView(collection);
                }
            }
            return null;
        }

        @Override
        public void onPageSelected(int position) {
            mTabHost.setCurrentTab(position);
        }

        @Override
        public void onTabChanged(String tabId) {
            if (bannerOnTop) {
                if (tabId.equals("Emoji"))
                    mViewPager.setCurrentItem(0);
                else if (tabId.equals("Camera"))
                    mViewPager.setCurrentItem(1);
            } else {
                if (tabId.equals("Services"))
                    mViewPager.setCurrentItem(0);
                else if (tabId.equals("Emoji"))
                    mViewPager.setCurrentItem(1);
                else if (tabId.equals("Camera"))
                    mViewPager.setCurrentItem(2);
            }

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {
            // TODO Auto-generated method stub

        }
    }

    ;

    private TabHost mTabHost;
    private boolean bannerOnTop;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.conversation, container, false);
        this.completeView = v;

        this.ibSend = (ImageButton) v.findViewById(R.id.ibSend);
        this.etEntryText = (EditText) v.findViewById(R.id.etEntryText);
        this.llSend = (LinearLayout) v.findViewById(R.id.llSend);
        this.tvCounter = (TextView) v.findViewById(R.id.tvCounter);
        this.llSlideOut = (LinearLayout) v.findViewById(R.id.llSlideOut);
        this.mTabHost = (TabHost) v.findViewById(R.id.thConversations);
        this.vpSlideOut = (ViewPager) v.findViewById(R.id.vpSlideOut);

        this.lvConversation = (ListView) v.findViewById(R.id.lvConversation);

        this.ibEditMore = (ImageButton) v.findViewById(R.id.ibEditMore);

        this.isLightTheme = ThemeOptions
                .isLightBackground(getActivity(), false);
        if (isLightTheme) {
            ibSend.getDrawable().setColorFilter(0xff0099cc,
                    PorterDuff.Mode.MULTIPLY); // holo_blue_dark
            ibSend.setBackgroundResource(R.drawable.abc_list_selector_holo_dark);
            ibEditMore
                    .setBackgroundResource(R.drawable.abc_list_selector_holo_dark);
        } else {
            ibSend.getDrawable().setColorFilter(0xff33b5e5,
                    PorterDuff.Mode.MULTIPLY); // holo_blue_light
            ibSend.setBackgroundResource(R.drawable.abc_list_selector_holo_light);
            ibEditMore
                    .setBackgroundResource(R.drawable.abc_list_selector_holo_light);
        }

        ibEditMore.setEnabled(true);
        ibSend.setEnabled(false);
        ibEditMore.setOnClickListener(ibEditMoreOnClickListener);

        this.mListAdapter = new ConversationListAdapter(getActivity(),
                this.mContactItem);

        ibSend.setOnClickListener(ibSendOnClickListener);

        this.lvConversation.setAdapter(mListAdapter);

        this.lvConversation.setVerticalFadingEdgeEnabled(true);
        this.lvConversation.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        this.lvConversation.setStackFromBottom(true);
        this.lvConversation
                .setOnItemClickListener(lvConversationOnItemClickListener);
        this.lvConversation
                .setOnItemLongClickListener(lvConversationOnItemLongClickListener);
        this.mListAdapter
                .setOnDownloadMMSClickListener(mOnDownloadMMSButtonClickListener);

        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(inflater
                .getContext());
        this.mPrefs
                .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

        bannerOnTop = mPrefs.getBoolean("pfShowContactBanner", false);

        mPagerAdapter = new CustomTabPagerAdapter(vpSlideOut, mTabHost);

        this.slideOutIsShown = false;

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (!isLandscape) {
            if (bannerOnTop) {
                rlContactBanner = (RelativeLayout) inflater.inflate(
                        R.layout.contact_banner, null);
                ((ViewGroup) v).addView(rlContactBanner, 0);
                rlContactBanner.getLayoutParams().height = (int) TypedValue
                        .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72,
                                getActivity().getResources()
                                        .getDisplayMetrics());
                buildContactBanner(rlContactBanner);
            }
            if (!slideOutIsShown)
                hideSlideOut(true);
            ;// llSlideOut.setVisibility(View.GONE);
        }

        useColoredSendIcon = mPrefs.getBoolean("pfConversationColoredSendIcon",
                false);

        boolean imeEnterAsSend = mPrefs.getBoolean("pfConversationEnterAsSend",
                false);
        String capOptions = mPrefs.getString(
                "pfConversationTextCaptilizations", "1");
        boolean capSentences = capOptions.equals("1");
        boolean capWords = capOptions.equals("2");
        boolean capAll = capOptions.equals("3");
        boolean autoCorrect = mPrefs.getBoolean("pfConversationAutoCorrect",
                true);
        boolean autoComplete = mPrefs.getBoolean("pfConversationAutoComplete",
                false);
        boolean textSuggestions = mPrefs.getBoolean(
                "pfConversationTextSuggestions", true);

        boolean multiline = mPrefs.getBoolean("pfConversationMultiline", true);
        boolean textShortMessage = mPrefs.getBoolean(
                "pfConversationShortMessageFeatures", true);

        int imeOptions = EditorInfo.IME_ACTION_SEND;
        int inputType = InputType.TYPE_CLASS_TEXT;
        if (!imeEnterAsSend)
            imeOptions ^= EditorInfo.IME_FLAG_NO_ENTER_ACTION;

        if (capSentences)
            inputType ^= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;

        if (capWords)
            inputType ^= InputType.TYPE_TEXT_FLAG_CAP_WORDS;

        if (capAll)
            inputType ^= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;

        if (autoCorrect)
            inputType ^= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;

        if (autoComplete)
            inputType ^= InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;

        if (!textSuggestions)
            inputType &= ~InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

        if (multiline)
            inputType ^= InputType.TYPE_TEXT_FLAG_MULTI_LINE;

        if (textShortMessage)
            inputType ^= InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;

        this.etEntryText.setImeOptions(imeOptions);
        this.etEntryText.setInputType(inputType);
        this.etEntryText
                .setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            sendMessage();
                            return true;
                        }
                        return false;
                    }
                });
        this.etEntryText.addTextChangedListener(entryTextTextWatcher);
        this.dpPaddingMargin = mPrefs.getInt("pfContactsListRightMargin", 0);
        final View activityRootView = v;
        v.getViewTreeObserver().addOnGlobalLayoutListener(
                new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        Rect r = new Rect();
                        // r will be populated with the coordinates of your view
                        // that area still visible.
                        activityRootView.getWindowVisibleDisplayFrame(r);

                        int heightDiff = activityRootView.getRootView()
                                .getHeight() - r.height();

                        if (heightDiff > 100) {
                            isKeyboardOpen = true;
                            if (!queueSlideOutOnKeyboardClose)
                                if (slideOutIsShown) {
                                    hideSlideOut(false);
                                    buildMoreButton();
                                }
                        } else {
                            if (queueSlideOutOnKeyboardClose
                                    && isKeyboardOpen == true) {
                                queueSlideOutOnKeyboardClose = false;

                                llSlideOut.setVisibility(View.VISIBLE);
                                slideOutIsShown = true;
                                buildMoreButton();

                            }
                            isKeyboardOpen = false;

                        }

                    }
                });
        // buildSpinnerAdapters();
        buildMoreButton();
        this.setRetainInstance(true);
        return v;
    }

    private void buildMoreButton() {

        if (this.isLightTheme) {
            if (this.slideOutIsShown)
                ibEditMore.setImageResource(R.drawable.navigation_expand_light);
            else
                ibEditMore
                        .setImageResource(R.drawable.navigation_collapse_light);
        } else {
            if (this.slideOutIsShown)
                ibEditMore.setImageResource(R.drawable.navigation_expand_dark);
            else
                ibEditMore
                        .setImageResource(R.drawable.navigation_collapse_dark);
        }
    }

    private OnDownloadMMSButtonClickListener mOnDownloadMMSButtonClickListener = new OnDownloadMMSButtonClickListener() {

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onClick(View v, MMSObject mmsObject, MessageItem msgItem) {
            Context mContext = v.getContext();
            MMSDownloader mmsDownloader = new MMSDownloader(mContext,
                    mmsObject, mService, msgItem);
            mmsDownloader.execute();

        }
    };
    private ListView.OnItemLongClickListener lvConversationOnItemLongClickListener = new android.widget.ListView.OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(android.widget.AdapterView<?> parent,
                                       View view, int position, long id) {
            if (view.getClass() == ImageButton.class)
                return false;
            if (view.getClass() == android.widget.Button.class)
                return false;
            final MessageItem m = (MessageItem) parent
                    .getItemAtPosition(position);

            Context mContext = ConversationFragment.this.getActivity();

            Drawable dBackground = null;
            try {

                StateListDrawable d = (StateListDrawable) lvConversation
                        .getSelector();
                int stateCount = (Integer) d.getClass()
                        .getMethod("getStateCount", (Class[]) null).invoke(d);
                for (int i = 0; i < stateCount; i++) {
                    int[] state = (int[]) d
                            .getClass()
                            .getMethod("getStateSet", new Class[]{int.class})
                            .invoke(d, i);
                    if (state.length == 1
                            && state[0] == android.R.attr.state_focused) {
                        Method getDrawable = d.getClass().getMethod(
                                "getStateDrawable", new Class[]{int.class});
                        dBackground = (Drawable) getDrawable.invoke(d, i);
                    }
                }

            } catch (Exception e) {
            }

            view.setBackgroundDrawable(dBackground);
            // view.setBackgroundColor(Color.WHITE);
            ((ActionBarActivity) (ConversationFragment.this.getActivity()))
                    .startSupportActionMode(new MessageItemActionMode(m, view));
            return true;
        }

    };

    private final class MessageItemActionMode implements Callback {
        private MessageItem messageItem;

        private View mView;

        public MessageItemActionMode(MessageItem messageItem, View v) {
            super();
            this.messageItem = messageItem;
            this.mView = v;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Used to put dark icons on light action bar
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.conversation_item_context_menu, menu);


            boolean isLight = ThemeOptions.isLightBackground(getActivity(),
                    true);

            if (isLight)
                return true;

            menu.findItem(R.id.mnuInfo).setIcon(R.drawable.action_about_dark);
            menu.findItem(R.id.mnuCopy).setIcon(R.drawable.content_copy_dark);
            menu.findItem(R.id.mnuDelete).setIcon(R.drawable.content_discard_dark);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.mnuCopy:
                    if (Build.VERSION.SDK_INT >= 11) {
                        V11.SetClipboard(getActivity(), "Fusion Message",
                                messageItem.getText());
                    } else {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getActivity()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(messageItem.getText());
                    }
                    Toast.makeText(getActivity(), "Copied to Clipboard",
                            Toast.LENGTH_SHORT).show();
                    break;
                case R.id.mnuInfo:


                    StringBuilder s = new StringBuilder();
                    if (this.messageItem.isIncoming()) {
                        if (this.messageItem.getInternalAddress() != null)
                            s.append("Recipient: "
                                    + this.messageItem.getInternalAddress() + "\n");

                        s.append("Sender:");
                        s.append(this.messageItem.getExternalAddress());
                        s.append('\n');
                    } else {
                        s.append("Recipient: "
                                + this.messageItem.getExternalAddress() + "\n");
                        if (this.messageItem.getInternalAddress() != null) {
                            s.append("Sender: ");
                            s.append(this.messageItem.getInternalAddress());
                            s.append('\n');
                        }
                    }
                    s.append("Service: ");
                    s.append(this.messageItem.getIMProviderType().toString());
                    s.append('\n');
                    s.append("Status: ");
                    s.append(this.messageItem.getMessageStatus().toString());
                    s.append('\n');
                    s.append("MessageID: ");
                    s.append(String.valueOf(this.messageItem.getMessageId()));

                    if (VERSION.SDK_INT < 11) {
                        AlertDialog.Builder aBuilder = new AlertDialog.Builder(
                                getActivity());
                        aBuilder.setTitle("Message Info");
                        aBuilder.setNeutralButton(android.R.string.ok, null);
                        aBuilder.setMessage(s.toString());
                        aBuilder.show();
                    } else {
                        android.app.AlertDialog.Builder aBuilder = new android.app.AlertDialog.Builder(
                                getActivity());
                        aBuilder.setTitle("Message Info");
                        aBuilder.setNeutralButton(android.R.string.ok, null);
                        aBuilder.setMessage(s.toString());
                        aBuilder.show();
                    }
                    break;
                case R.id.mnuDelete:
                    if (mService != null) {
                        DialogInterface.OnClickListener adConfirmYes = new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                messageItem
                                        .setMessageStatus(MessageStatuses.Deleted);
                                mService.queueMessageItem(messageItem);

                            }
                        };
                        android.app.AlertDialog.Builder adConfirmDelete = new android.app.AlertDialog.Builder(
                                getActivity());
                        adConfirmDelete.setTitle("Delete Message?");
                        adConfirmDelete
                                .setMessage("Are you sure you would like to delete this message?");
                        adConfirmDelete.setPositiveButton("Yes", adConfirmYes);

                        adConfirmDelete.setNegativeButton("No", null);

                        // So... apparently Google glitched their YES/NO strings
                        // android.R.string.yes = "Ok"
                        // android.R.string.no = "Cancel"
                        adConfirmDelete.show();

                    }

            }

            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mView.setBackgroundDrawable(null);
        }
    }

    private android.widget.ListView.OnItemClickListener lvConversationOnItemClickListener = new android.widget.ListView.OnItemClickListener() {

        @Override
        public void onItemClick(android.widget.AdapterView<?> a, View v,
                                int position, long id) {
            final MessageItem c = (MessageItem) a.getItemAtPosition(position);
            SpannableString text = new SpannableString(c.getText());

            final ArrayList<LinkSpec> links = new ArrayList<LinkSpec>();

            Linkify.gatherLinks(links, text, Patterns.WEB_URL, new String[]{
                            "http://", "https://", "rtsp://"},
                    Linkify.sUrlMatchFilter, null);

            Linkify.gatherLinks(links, text, Patterns.EMAIL_ADDRESS,
                    new String[]{"mailto:"}, null, null);

            Linkify.gatherLinks(links, text, Patterns.PHONE,
                    new String[]{"tel:"}, Linkify.sPhoneNumberMatchFilter,
                    Linkify.sPhoneNumberTransformFilter);

            Linkify.gatherMapLinks(links, text);

            Linkify.pruneOverlaps(links);

            int length = links.size();
            if (length == 0) {
                return;
            }

            CharSequence[] l = new CharSequence[length];
            for (int i = 0; i < length; i++) {
                LinkSpec spec = links.get(i);
                CharSequence cs = text.subSequence(spec.start, spec.end);
                if (spec.url.startsWith("tel:"))
                    cs = "Call " + cs;
                else if (spec.url.startsWith("mailto:"))
                    cs = "Email " + cs;
                else if (spec.url.startsWith("geo:"))
                    cs = "Map of " + cs;
                else
                    cs = spec.url;
                l[i] = cs;
            }
            AlertDialog.Builder aBuilder = new AlertDialog.Builder(
                    getActivity());
            aBuilder.setTitle("Select an action...");
            aBuilder.setNegativeButton(android.R.string.cancel, null);
            aBuilder.setItems(l, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(links
                            .get(which).url));
                    startActivity(i);
                }
            });
            aBuilder.show();
        }
    };

    private int dpPaddingMargin = 0;
    private boolean useColoredSendIcon = false;
    private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (key.equals("pfConversationColoredSendIcon")) {
                useColoredSendIcon = sharedPreferences.getBoolean(key, false);
                refreshSendIconColor(null);
            } else if (key.equals("pfContactsListRightMargin")) {
                dpPaddingMargin = mPrefs.getInt(key, 0);

            }
        }

    };

    public void refreshSendIconColor(ContactAddress ca) {
        if (etEntryText == null)
            return;
        if (ibSend == null)
            return;
        int inactiveColor;
        if (isLightTheme)
            inactiveColor = 0xffdddddd; // lighter_gray
        else
            inactiveColor = 0xffaaaaaa; // darker_gray

        int serviceColor;
        if (isLightTheme)
            serviceColor = 0xff0099cc; // holo_blue_dark
        else
            serviceColor = 0xff33b5e5; // holo_blue_light

        if (ca == null)
            ca = this.getContactAddress();
        if (ca != null) {
            IMProvider p = IMProvider.findIMProvider(ca.getIMProviderType(),
                    MemoryCache.IMProviders);
            if (p != null) {
                if (isLightTheme)
                    serviceColor = p.getLightColor();
                else
                    serviceColor = p.getDarkColor();
            }
        }
        if (useColoredSendIcon && etEntryText.getText() == null
                || etEntryText.getText().length() != 0) {
            ibSend.setEnabled(true);
            ibSend.getDrawable().setColorFilter(serviceColor,
                    PorterDuff.Mode.MULTIPLY);
        } else {
            ibSend.setEnabled(false);
            ibSend.getDrawable().setColorFilter(inactiveColor,
                    PorterDuff.Mode.MULTIPLY);
        }
        if (useColoredSendIcon) {
            this.etEntryText.getBackground().setColorFilter(serviceColor,
                    PorterDuff.Mode.SRC_ATOP);

        }

    }

    private String lastEmojiModifiedString = null;
    private TextWatcher entryTextTextWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {

            int length = s.length();

            if (length > 100
                    || ConversationFragment.this.etEntryText.getLineCount() > 1) {
                tvCounter.setVisibility(View.VISIBLE);
                int splits = length / 160;
                boolean isLight = ThemeOptions.isLightBackground(getActivity(),
                        false);
                if (splits > 0) {
                    tvCounter.setText(length + "/" + ((splits + 1) * 160)
                            + " (" + (splits + 1) + ")");
                    /*
                     * if (isLight) tvCounter.setTextColor(0xffff8800); //
					 * holo_orange_dark else tvCounter.setTextColor(0xffffbb33);
					 * // holo_orange_light
					 */
                } else {
                    tvCounter.setText(length + "/160");
					/*
					 * if (isLightTheme) tvCounter.setTextColor(0xffdddddd); //
					 * lighter_gray else tvCounter.setTextColor(0xffaaaaaa); //
					 * darker_gray
					 */
                }
            } else {
                tvCounter.setVisibility(View.GONE);
            }

            refreshSendIconColor(null);
        }

        @Override
        public void afterTextChanged(Editable s) {

            String str = s.toString();
            if (!str.equals(lastEmojiModifiedString)) {
                float size = etEntryText.getTextSize();
                Emoji.addSmiles(etEntryText.getContext(), s, false, (int) size);
                lastEmojiModifiedString = str;
            }
        }
    };

    private String[] splitTextMessage(String input) {
        List<String> sentenceSplits = new ArrayList<String>();
        List<String> wordSplits = new ArrayList<String>();
        List<String> characterSplits = new ArrayList<String>();

        int targetSplit = ((input.length() - 1) / 160) + 1;

        BreakIterator sentenceIterator = BreakIterator
                .getSentenceInstance(Locale.getDefault());
        sentenceIterator.setText(input);
        int sentenceStart = sentenceIterator.first();
        for (int sentenceEnd = sentenceIterator.next(); sentenceEnd != BreakIterator.DONE; sentenceStart = sentenceEnd, sentenceEnd = sentenceIterator
                .next())
            sentenceSplits.add(input.substring(sentenceStart, sentenceEnd));
        return null;
    }

    public class ShrinkAnimation extends Animation {

        int mFromHeight;
        View mView;

        public ShrinkAnimation(View view) {
            this.mView = view;
            this.mFromHeight = view.getMeasuredHeight();
        }

        @Override
        protected void applyTransformation(float interpolatedTime,
                                           Transformation t) {

            int newHeight;
            newHeight = (int) (mFromHeight * (1.0f - interpolatedTime));
            mView.getLayoutParams().height = newHeight;
            // mView.setAlpha(1.0f - interpolatedTime);
            mView.requestLayout();

        }

        @Override
        public void initialize(int width, int height, int parentWidth,
                               int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }

    public class GrowAnimation extends Animation {

        int mToHeight;
        View mView;

        public GrowAnimation(View view, int toHeight) {
            this.mView = view;
            this.mToHeight = toHeight;
        }

        @Override
        protected void applyTransformation(float interpolatedTime,
                                           Transformation t) {

            int newHeight = (int) (mToHeight * interpolatedTime);
            mView.getLayoutParams().height = newHeight;
            // mView.setAlpha(1.0f - interpolatedTime);
            mView.requestLayout();

        }

        @Override
        public void initialize(int width, int height, int parentWidth,
                               int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }

    TranslateAnimation shrinkAnim;
    TranslateAnimation growAnim;

    private void hideSlideOut(boolean animate) {
        if (growAnim != null)
            growAnim.cancel();

		/*
		 * shrinkAnim = new TranslateAnimation(0, 0, 1.0f, -1.0f);
		 * 
		 * shrinkAnim.setDuration(2000); shrinkAnim.setFillAfter(true);
		 * llSlideOut.startAnimation(shrinkAnim);
		 */
        llSlideOut.setVisibility(View.GONE);
        slideOutIsShown = false;
    }

    private InputMethodManager imm;
    private boolean slideOutIsShown = false;
    private boolean isKeyboardOpen = false;
    private boolean queueSlideOutOnKeyboardClose = false;
    private View.OnClickListener ibEditMoreOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (slideOutIsShown) {
                hideSlideOut(false);
            } else {
                if (isKeyboardOpen)
                    queueSlideOutOnKeyboardClose = true;
                if (imm == null)
                    imm = (InputMethodManager) getActivity().getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(
                        etEntryText.getApplicationWindowToken(), 0);

                int height = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 277, getActivity()
                                .getResources().getDisplayMetrics());

                if (shrinkAnim != null)
                    shrinkAnim.cancel();

				/*
				 * growAnim = new TranslateAnimation(0, 0, 0f, 1.0f);
				 * 
				 * growAnim.setInterpolator(new DecelerateInterpolator());
				 * growAnim.setDuration(2000); growAnim.setFillAfter(true);
				 * llSlideOut.startAnimation(growAnim);
				 */
                if (!queueSlideOutOnKeyboardClose)
                    llSlideOut.setVisibility(View.VISIBLE);

                slideOutIsShown = true;

            }
            buildMoreButton();

        }
    };

    private void sendMessage() {
        MessageItem msgItem = new MessageItem();
        String message = etEntryText.getText().toString();
        if (TextUtils.isEmpty(message))
            return;
        msgItem.setText(etEntryText.getText().toString());
        msgItem.setIncoming(false);
        msgItem.setMessageStatus(MessageItem.MessageStatuses.Queued);

        ContactAddress ca = getContactAddress();
        if (ca != null) {
            // blank!?
            msgItem.setIMProviderType(ca.getIMProviderType());
            msgItem.setExternalAddress(ca.getParsedAddress());
            msgItem.setCreationDateTime(System.currentTimeMillis());
            ConversationFragment.this.mService.queueMessageItem(msgItem);
            etEntryText.setText(null);
        }

    }

    private View.OnClickListener ibSendOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            sendMessage();
        }
    };

    private void refreshFilter() {
        mListAdapter.refreshFilter();
    }

    private static class mHandler extends Handler {
        private ConversationFragment f;

        mHandler(ConversationFragment contactsFragment) {
            this.f = contactsFragment;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerMessages.MSG_CONTACTSRECEIVED:

                    break;
                case HandlerMessages.MSG_CONVERSATIONUPDATED:
                    if (f.mContactItem != null
                            && f.mContactItem.equals(((ContactItem) msg.obj)))
                        f.refreshFilter();
                    break;
                case HandlerMessages.MSG_MESSAGEBATCHRECEIVED:
                    MessageItem[] msgItems = (MessageItem[]) msg.obj;
                    if (msgItems.length == 0)
                        return;
                    if (f.mContactItem != null
                            && f.mContactItem.isMessageItemIsValid(msgItems[0]))
                        f.refreshFilter();
                    break;
                case HandlerMessages.MSG_MESSAGEDELETED:
                case HandlerMessages.MSG_MESSAGEQUEUED:
                case HandlerMessages.MSG_MESSAGERECEIVED:
                case HandlerMessages.MSG_MESSAGEFAILED:
                case HandlerMessages.MSG_MESSAGEONROUTE:
                case HandlerMessages.MSG_MESSAGESENT:
                case HandlerMessages.MSG_MESSAGECHANGED:
                    if (f.mContactItem != null
                            && f.mContactItem
                            .isMessageItemIsValid((MessageItem) msg.obj))
                        f.refreshFilter();
                    break;
            }

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        buildSpinnerAdapters();
        buildMoreButton();
    }

    private _AdapterViewICS.OnItemSelectedListener icsContactSpinnerItemSelected = new _AdapterViewICS.OnItemSelectedListener() {

        @Override
        public void onItemSelected(_AdapterViewICS<?> parent, View view,
                                   int position, long id) {
            final ContactAddress ca = (ContactAddress) parent
                    .getItemAtPosition(position);
            refreshSendIconColor(ca);

        }

        @Override
        public void onNothingSelected(_AdapterViewICS<?> parent) {
            // TODO Auto-generated method stub

        }

    };

    private OnItemSelectedListener contactSpinnerItemSelected = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view,
                                   int position, long id) {
            final ContactAddress ca = (ContactAddress) parent
                    .getItemAtPosition(position);

            refreshSendIconColor(ca);
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {

        }

    };

    private boolean spinnersBuilt = false;

    private void buildSpinnerAdapters() {

        Log.w("ConversationFragment", "buildSpinnerAdapters(): Start");
        ContactItem ci = getContactItem();
        Log.w("ConversationFragment",
                "buildSpinnerAdapters(): " + ci.getDisplayName());
        ConversationBannerSpinnerAdapter cbsa = new ConversationBannerSpinnerAdapter(
                ConversationFragment.this.getActivity(), ci);

        if (VERSION.SDK_INT < 14) {
            if (ispBanner == null)
                return;
            ispBanner.setAdapter(cbsa);
            ispBanner.setOnItemSelectedListener(null);
            ispBanner.setOnItemSelectedListener(icsContactSpinnerItemSelected);
        } else {
            if (spBanner == null)
                return;
            spBanner.setAdapter(cbsa);
            spBanner.setOnItemSelectedListener(contactSpinnerItemSelected);
        }
        int index = -1;
        ContactAddress selectedContactAddress = ci.getLastContactAddress();
        if (selectedContactAddress != null)
            index = cbsa.getIndex(selectedContactAddress);
        if (index == -1)
            index = 0;
        if (VERSION.SDK_INT < 14) {
            if (ispBanner.getSelectedItemPosition() != index) {
                ispBanner.setSelection(index, true);
            }
        } else {
            if (spBanner.getSelectedItemPosition() != index) {
                spBanner.setSelection(index, true);
            }
        }
    }

    private WorkerService mService;
    private Messenger mMessenger = new Messenger(new mHandler(
            ConversationFragment.this));
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((WorkerService.LocalBinder) service).getService();

            buildSpinnerAdapters();

            mService.addClientMessenger(mMessenger);
            mService.requestConversation(getContactItem());
            if (queueEditTextFocus) {
                if (etEntryText != null) {
                    etEntryText.requestFocus();
                    etEntryText.post(new Runnable() {
                        @Override
                        public void run() {
                            FragmentActivity a = getActivity();
                            if (a == null)
                                return;
                            InputMethodManager imm = (InputMethodManager) getActivity()
                                    .getSystemService(
                                            Context.INPUT_METHOD_SERVICE);
                            imm.showSoftInput(etEntryText,
                                    InputMethodManager.SHOW_IMPLICIT);
                        }
                    });

                }
                queueEditTextFocus = false;
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

    };

    @Override
    public void onStart() {
        super.onStart();
        Context c = getActivity().getApplicationContext();
        c.bindService(new Intent(c, WorkerService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mService != null)
            mService.removeClientMessenger(mMessenger);
        getActivity().getApplicationContext().unbindService(mConnection);

    }

    private boolean queueEditTextFocus = false;

    public void forceEditTextFocus() {
        if (etEntryText != null) {
            etEntryText.requestFocus();
            etEntryText.post(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getActivity()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(etEntryText,
                            InputMethodManager.SHOW_IMPLICIT);
                }
            });

        } else {

            queueEditTextFocus = true;
        }

    }

    public void setEnabled(boolean state) {
        if (etEntryText != null && etEntryText.isEnabled() != state)
            etEntryText.setEnabled(state);
        if (lvConversation != null && lvConversation.isEnabled() != state)
            lvConversation.setEnabled(state);
    }

    public void cancelSearch() {
        if (mnuConversationSearch != null)
            MenuItemCompat.collapseActionView(mnuConversationSearch);
        if (searchView != null) {
            if (VERSION.SDK_INT >= 11)
                V11.SearchViewSetQuery(searchView, null, false);
            else
                ((SearchView) searchView).setQuery(null, false);
        }
        this.setSearchQuery(null);
        this.setSearchQuery(null);

    }

}
