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

public class CustomViewPagerAdapterV2 extends FragmentStatePagerAdapter {

	private static final String TAG = "CustomViewPagerAdapter";
	private final ArrayList<ContactItem> mContactItems = new ArrayList<ContactItem>();
	private long[] mItemIds = new long[] {};
	private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<Fragment.SavedState>();
	private ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
	private Fragment mCurrentPrimaryItem = null;

	private final FragmentManager mFragmentManager;

	public CustomViewPagerAdapterV2(FragmentManager fm) {
		super(fm);
		this.mFragmentManager = fm;
		mFragments.add(null);
	}

	public void addConversation(ContactItem c) {
		mContactItems.add(c);
		mFragments.add(null);
	}

	public void insertConversation(ContactItem c, int index) {
		if (this.getCount() == index) {
			addConversation(c);
			return;
		}
		this.mContactItems.add(index - 1, c);
		this.mFragments.add(index, null);
	}

	public void replacePage(ContactItem c, int index) {
		mContactItems.set(index - 1, c);
	}

	public void removePage(int index) {
		mContactItems.remove(index - 1);
		mFragments.remove(index);
	}

	@Override
	public int getItemPosition(Object object) {
		return POSITION_NONE;

	}

	public Fragment getCurrentFragment(int position) {
		if (position >= mFragments.size())
			return null;
		return mFragments.get(position);
	}

	@Override
	public Fragment getItem(int position) {

		if (position == 0) {
			Fragment f = ContactsFragment.newInstance();
			mFragments.set(0, f);
			return f;
		} else {
			Fragment f = ConversationFragment.newInstance(
					mContactItems.get(position - 1), position - 1);
			mFragments.set(position, f);
			return f;
		}
	}

	@Override
	public int getCount() {
		return mContactItems.size() + 1;
	}

}
