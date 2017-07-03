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

public class CustomViewPagerAdapter extends PagerAdapter {

	private static final String TAG = "CustomViewPagerAdapter";
	private final ArrayList<ContactItem> mContactItems = new ArrayList<ContactItem>();
	private long[] mItemIds = new long[] {};
	private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<Fragment.SavedState>();
	private ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
	private Fragment mCurrentPrimaryItem = null;

	private final FragmentManager mFragmentManager;

	public CustomViewPagerAdapter(FragmentManager fm) {
		this.mFragmentManager = fm;
	}

	public void addContactsPage() {
		this.mFragments.add(null);
	}

	public void addConversation(ContactItem c) {
		mContactItems.add(c);
		mFragments.add(null);

	}

	public void moveFragment(int previousIndex, int newIndex) {
		ContactItem c = this.mContactItems.get(previousIndex - 1);
		Fragment f = this.mFragments.get(previousIndex);

		this.mContactItems.add(newIndex - 1, c);
		this.mFragments.add(newIndex, f);

		this.mContactItems.remove(previousIndex - 1);
		this.mFragments.remove(previousIndex);
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
		int previousFragmentId = this.mFragments.get(index).getId();
		Fragment newFragment = ConversationFragment.newInstance(c, index - 1);
		newFragment.setRetainInstance(true);
		mFragmentManager.beginTransaction()
				.replace(previousFragmentId, newFragment)
				.commitAllowingStateLoss();
		this.mFragments.set(index, newFragment);
	}

	public void removePage(int index) {

		FragmentTransaction ft = mFragmentManager.beginTransaction();
		Fragment f = mFragments.get(index);
		mFragments.remove(index);
		if (f != null) {
			f.setRetainInstance(false);
			ft.remove(f);
		}
		ft.commitAllowingStateLoss();

		mContactItems.remove(index - 1);

	}

	@Override
	public int getCount() {
		return mFragments.size();
	}

	@Override
	public int getItemPosition(Object object) {

		Fragment f = (Fragment) object;
		int index = this.mFragments.indexOf(f);
		if (index == -1)
			return POSITION_NONE;
		return index;
	}

	private FragmentTransaction mCurTransaction = null;

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		// If we already have this item instantiated, there is nothing
		// to do. This can happen when we are restoring the entire pager
		// from its saved state, where the fragment manager has already
		// taken care of restoring the fragments we previously had instantiated.
		Fragment f = mFragments.get(position);
		if (f != null)
			return f;

		if (mCurTransaction == null) {
			mCurTransaction = mFragmentManager.beginTransaction();
		}

		Fragment fragment = null;
		if (position == 0) {
			fragment = ContactsFragment.newInstance();
		} else {
			fragment = ConversationFragment.newInstance(
					this.mContactItems.get(position - 1), position - 1);
		}

		while (mFragments.size() <= position) {
			mFragments.add(null);
		}
		fragment.setMenuVisibility(false);
		mFragments.set(position, fragment);
		mCurTransaction.add(container.getId(), fragment);

		return fragment;

	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		if (position >= getCount()) {
			FragmentManager manager = ((Fragment) object).getFragmentManager();
			FragmentTransaction trans = manager.beginTransaction();
			trans.remove((Fragment) object);
			trans.commit();
		}
	}

	@Override
	public void finishUpdate(ViewGroup container) {
		if (mCurTransaction != null) {
			mCurTransaction.commitAllowingStateLoss();
			mCurTransaction = null;
			mFragmentManager.executePendingTransactions();
		}
	}

	@Override
	public void setPrimaryItem(ViewGroup container, int position, Object object) {
		Fragment fragment = (Fragment) object;
		if (fragment != mCurrentPrimaryItem) {
			if (mCurrentPrimaryItem != null) {
				mCurrentPrimaryItem.setMenuVisibility(false);
			}
			if (fragment != null) {
				fragment.setMenuVisibility(true);
			}
			mCurrentPrimaryItem = fragment;
		}
	}

	public Fragment getItem(int position) {
		return mFragments.get(position);
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return ((Fragment) object).getView() == view;
	}

	@Override
	public Parcelable saveState() {
		Bundle state = new Bundle();
		if (mItemIds.length > 0) {
			state.putLongArray("itemids", mItemIds);
		}
		if (mSavedState.size() > 0) {
			Fragment.SavedState[] fss = new Fragment.SavedState[mSavedState
					.size()];
			mSavedState.toArray(fss);
			state.putParcelableArray("states", fss);
		}
		for (int i = 0; i < mFragments.size(); i++) {
			Fragment f = mFragments.get(i);
			if (f != null) {
				String key = "f" + i;

				mFragmentManager.putFragment(state, key, f);
			}
		}
		return state;
	}

	@Override
	public void restoreState(Parcelable state, ClassLoader loader) {
		if (state != null) {
			Bundle bundle = (Bundle) state;
			bundle.setClassLoader(loader);
			mItemIds = bundle.getLongArray("itemids");
			if (mItemIds == null) {
				mItemIds = new long[] {};
			}
			Parcelable[] fss = bundle.getParcelableArray("states");
			mSavedState.clear();
			mFragments.clear();
			if (fss != null) {
				for (int i = 0; i < fss.length; i++) {
					mSavedState.add((Fragment.SavedState) fss[i]);
				}
			}
			Iterable<String> keys = bundle.keySet();
			for (String key : keys) {
				if (key.startsWith("f")) {
					int index = Integer.parseInt(key.substring(1));
					Fragment f = mFragmentManager.getFragment(bundle, key);
					if (f != null) {
						while (mFragments.size() <= index) {
							mFragments.add(null);
						}
						f.setMenuVisibility(false);
						mFragments.set(index, f);
					} else {
						Log.w(TAG, "Bad fragment at key " + key);
					}
				}
			}
		}
	}

	public long getItemId(int position) {
		return position;
	}

}
