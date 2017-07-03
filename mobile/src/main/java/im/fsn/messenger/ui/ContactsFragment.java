package im.fsn.messenger.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;

import java.lang.reflect.Method;

import im.fsn.messenger.ContactItem;
import im.fsn.messenger.ContactsListAdapter;
import im.fsn.messenger.HandlerMessages;
import im.fsn.messenger.R;
import im.fsn.messenger.ThemeOptions;
import im.fsn.messenger.WorkerService;
import im.fsn.messenger.apisupport.V11;

public class ContactsFragment extends Fragment {

    private ListView lvContacts;
    private ContactsListAdapter cListAdapter;
    private View vContactsMarginDivider;

    public static ContactsFragment newInstance() {
        ContactsFragment f = new ContactsFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    private SharedPreferences mPrefs;
    private View completeView;
    private String lastQuery;

    public void setSearchQuery(String query) {
        if (this.cListAdapter == null)
            return;
        if (query == null && lastQuery == null)
            return;
        if (query == null)
            this.cListAdapter.getFilter().filter(null);
        else
            this.cListAdapter.getFilter().filter(query.toLowerCase());
        this.lastQuery = query;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.contacts, container, false);
        this.lvContacts = (ListView) v.findViewById(R.id.lvContacts);
        cListAdapter = new ContactsListAdapter(inflater.getContext());

        this.lvContacts.setAdapter(cListAdapter);
        this.setSearchQuery(null);
        this.vContactsMarginDivider = v
                .findViewById(R.id.vContactsMarginDivider);
        this.lvContacts.setOnItemClickListener(lvContactsOnItemClickListener);
        this.lvContacts
                .setOnItemLongClickListener(lvContactsOnItemLongClickListener);
        this.completeView = v;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(inflater
                .getContext());
        this.mPrefs
                .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
        this.dpPaddingMargin = mPrefs.getInt("pfContactsListRightMargin", 0);
        // this.applyPadding();
        this.setHasOptionsMenu(true);
        this.setRetainInstance(true);
        return v;
    }

    private View searchView;
    private MenuItem mnuContactSearch;

    public boolean openSearch() {
        if (mnuContactSearch != null && mnuContactSearch.isVisible()) {
            MenuItemCompat.expandActionView(mnuContactSearch);
            return true;
        }
        return false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.contactsfragment, menu);
        mnuContactSearch = menu.findItem(R.id.mnuContactSearch);

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
                                if (hasFocus)
                                    query = new String();

                                else {
                                    query = null;
                                    if (mnuContactSearch != null)
                                        MenuItemCompat
                                                .collapseActionView(mnuContactSearch);
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
                            if (!MenuItemCompat
                                    .isActionViewExpanded(mnuContactSearch))
                                newText = null;
                            setSearchQuery(newText);
                            return true;
                        }
                    });
            V11.SearchViewSetQueryHint(searchView, "Contact Name or Number");
            MenuItemCompat.setActionView(mnuContactSearch, searchView);

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
                                    if (mnuContactSearch != null)
                                        MenuItemCompat
                                                .collapseActionView(mnuContactSearch);
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
            ((SearchView) searchView).setQueryHint("Contact Name or Number");
            MenuItemCompat.setActionView(mnuContactSearch, searchView);

        }

        MenuItemCompat.setShowAsAction(mnuContactSearch,
                MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
                        | MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        if (ThemeOptions.isLightBackground(getActivity(), true))
            mnuContactSearch.setIcon(R.drawable.ic_menu_msg_compose_holo_light);
        else
            mnuContactSearch.setIcon(R.drawable.ic_menu_msg_compose_holo_dark);

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
        MenuItem mnuQRT = menu.add("QuickReplyTest");
        mnuQRT.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent i = new Intent(getActivity(), QuickReplyDialog.class);
                i.setAction("android.intent.action.SENDTO");
                i.setData(Uri.parse("smsto:5514820127"));
                getActivity().startActivity(i);

                return true;
            }
        });
        super.onCreateOptionsMenu(menu, inflater);
    }

    private OnItemClickListener lvContactsOnItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> a, View v, int position, long id) {
            final ContactItem c = (ContactItem) a.getItemAtPosition(position);
            if (mLauncherActivity != null) {
                ContactsFragment.this.cancelSearch();
                mLauncherActivity.selectContact(c);
            }
        }
    };

    private int dpPaddingMargin = 0;
    private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {

        }

    };

    private ListView.OnItemLongClickListener lvContactsOnItemLongClickListener = new android.widget.ListView.OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(android.widget.AdapterView<?> parent,
                                       View view, int position, long id) {
            if (view.getClass() == ImageButton.class)
                return false;
            if (view.getClass() == android.widget.Button.class)
                return false;
            final ContactItem c = (ContactItem) parent
                    .getItemAtPosition(position);

            Context mContext = ContactsFragment.this.getActivity();

            Drawable dBackground = null;
            try {

                StateListDrawable d = (StateListDrawable) lvContacts
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

            ((ActionBarActivity) (ContactsFragment.this.getActivity()))
                    .startSupportActionMode(new ContactItemActionMode(c, view));

            return true;
        }

    };

    private final class ContactItemActionMode implements
            android.support.v7.view.ActionMode.Callback {
        private ContactItem contactItem;

        private MenuItem mnuDelete;
        private View mView;

        public ContactItemActionMode(ContactItem contactItem, View v) {
            super();
            this.contactItem = contactItem;
            this.mView = v;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Used to put dark icons on light action bar
            boolean isLight = ThemeOptions.isLightBackground(getActivity(),
                    true);

            this.mnuDelete = menu.add("Delete");
            this.mnuDelete.setIcon(isLight ? R.drawable.content_discard_light
                    : R.drawable.content_discard_dark);
            MenuItemCompat.setShowAsAction(mnuDelete,
                    MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            if (item == mnuDelete) {
                if (mService != null) {
                    DialogInterface.OnClickListener adConfirmYes = new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            mService.deleteConversation(contactItem);

                        }
                    };
                    android.app.AlertDialog.Builder adConfirmDelete = new android.app.AlertDialog.Builder(
                            getActivity());
                    adConfirmDelete.setTitle("Delete Conversation?");
                    adConfirmDelete
                            .setMessage("Are you sure you would like to delete this conversation?");
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

    private LauncherActivity mLauncherActivity;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mLauncherActivity = LauncherActivity.class.cast(activity);
        } catch (ClassCastException e) {
            e.printStackTrace();
        }

		/*
         * You're supposed to make a special activity class and extend it but
		 * this is so much easier. It breaks the ability to plug and drop this
		 * existing fragment into another activity but I don't honestly plan on
		 * doing any of that now
		 */
    }

    private static class mHandler extends Handler {
        private ContactsFragment f;

        mHandler(ContactsFragment contactsFragment) {
            this.f = contactsFragment;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HandlerMessages.MSG_NOTIFYCONTACTASREAD:
                    // ContactItem readContact = (ContactItem) msg.obj;
                    f.cListAdapter.refreshFilter();
                    break;
                case HandlerMessages.MSG_CONVERSATIONUPDATED:
                    f.cListAdapter.refreshFilter();
                    break;
                case HandlerMessages.MSG_CONTACTSRECEIVED:
                    // ContactItem[] contacts = (ContactItem[]) msg.obj;
                    // f.refillContacts(contacts);
                    f.cListAdapter.refreshFilter();
                    break;
                case HandlerMessages.MSG_MESSAGEBATCHRECEIVED:
                    f.cListAdapter.refreshFilter();
                    break;
                case HandlerMessages.MSG_MESSAGEFAILED:
                case HandlerMessages.MSG_MESSAGESENT:
                case HandlerMessages.MSG_MESSAGERECEIVED:
                case HandlerMessages.MSG_MESSAGECHANGED:
                case HandlerMessages.MSG_MESSAGEQUEUED:
                    // f.updateLastMessage((MessageItem) msg.obj);
                    f.cListAdapter.refreshFilter();
                    break;
                case HandlerMessages.MSG_MESSAGEDELETED:
                    f.cListAdapter.refreshFilter();
                    break;
            }

        }
    }

    private boolean mIsBound;
    private WorkerService mService;
    private Messenger mMessenger = new Messenger(new mHandler(
            ContactsFragment.this));
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((WorkerService.LocalBinder) service).getService();
            mService.addClientMessenger(mMessenger);
            ;// refillContacts(mService.getContactList());
            // get cached Contacts
            mService.queueContactsRefresh();
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
        this.mIsBound = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIsBound) {
            if (mService != null)
                mService.removeClientMessenger(mMessenger);
            getActivity().getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void cancelSearch() {
        if (mnuContactSearch != null)
            MenuItemCompat.collapseActionView(mnuContactSearch);
        if (searchView != null) {
            if (VERSION.SDK_INT >= 11)
                V11.SearchViewSetQuery(searchView, null, false);
            else
                ((SearchView) searchView).setQuery(null, false);
        }
        this.setSearchQuery(null);

    }
}
