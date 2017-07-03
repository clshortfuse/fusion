package im.fsn.messenger;

import im.fsn.messenger.ui.ContactsFragment;
import im.fsn.messenger.ui.ConversationFragment;

import java.util.ArrayList;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class CustomViewPagerAdapterV4 extends FragmentStatePagerAdapter {

	private static final String TAG = "CustomViewPagerAdapter";
	private final ArrayList<ContactItem> mContactItems = new ArrayList<ContactItem>();
	private long[] mItemIds = new long[] {};
	private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<Fragment.SavedState>();
	private ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
	private Fragment mCurrentPrimaryItem = null;

	private final FragmentManager mFragmentManager;

	public CustomViewPagerAdapterV4(FragmentManager fm) {
		super(fm);
		this.mFragmentManager = fm;
		mFragments.add(null);
	}

	public void addConversation(ContactItem c) {
		mContactItems.add(c);
		mFragments.add(null);
	}

	@Override
	public int getItemPosition(Object object) {
		if (object == null)
			return POSITION_NONE;
		int index = mFragments.indexOf(object);
		if (index == -1)
			return POSITION_NONE;
		return index;
	}

	public Fragment getCurrentFragment(int position) {
		if (position >= mFragments.size())
			return null;
		return mFragments.get(position);
	}

	@Override
	public Fragment getItem(int position) {

		Fragment f = mFragments.get(position);
		if (f != null)
			return f;
		if (position == 0) {
			f = ContactsFragment.newInstance();
		} else {
			f = ConversationFragment.newInstance(
					mContactItems.get(position - 1), position - 1);
		}
		mFragments.set(position, f);
		return f;
	}

	@Override
	public int getCount() {
		return mFragments.size();
	}

}
