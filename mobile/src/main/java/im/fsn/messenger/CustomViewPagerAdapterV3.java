package im.fsn.messenger;

import im.fsn.messenger.ui.ContactsFragment;
import im.fsn.messenger.ui.ConversationFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

public class CustomViewPagerAdapterV3 extends FragmentStatePagerAdapter {

	private static final String TAG = "CustomViewPagerAdapter";
	private final ArrayList<ContactItem> mContactItems = new ArrayList<ContactItem>();
	private long[] mItemIds = new long[] {};
	private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<Fragment.SavedState>();
	private ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
	private Fragment mCurrentPrimaryItem = null;

	private final FragmentManager mFragmentManager;

	public CustomViewPagerAdapterV3(FragmentManager fm) {
		super(fm);
		this.mFragmentManager = fm;
	}

	public void addConversation(ContactItem c) {
		mContactItems.add(c);
	}

	public void insertConversation(ContactItem c, int index) {
		if (this.getCount() == index) {
			addConversation(c);
			return;
		}
		this.mContactItems.add(index - 1, c);
	}

	public void replacePage(ContactItem c, int index) {
		mContactItems.set(index - 1, c);
	}

	public void removePage(int index) {
		mContactItems.remove(index - 1);
	}

	@Override
	public int getItemPosition(Object object) {

		return POSITION_NONE;

	}

	@Override
	public Fragment getItem(int position) {

		return ConversationFragment.newInstance(mContactItems.get(position),
				position);
	}

	@Override
	public int getCount() {
		return mContactItems.size();
	}

}
