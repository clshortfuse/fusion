package im.fsn.messenger;

import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import im.fsn.messenger.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

public class ConversationBannerSpinnerAdapter implements SpinnerAdapter {

	private ContactItem mContactItem;
	private Context mContext;
	private LayoutInflater mInflater;
	private SharedPreferences mPrefs;

	public ConversationBannerSpinnerAdapter(Context context,
			ContactItem contactItem) {
		super();
		this.mContext = context;
		this.mContactItem = contactItem;
		this.mInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

	}

	public ContactItem getContactItem() {
		return mContactItem;
	}

	public int getIndex(ContactAddress ca) {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		if (cas == null || MemoryCache.IMProviders == null)
			return -1;
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					MemoryCache.IMProviders).isAvailable()) {
				if (cas[i].equals(ca))
					return count;
				count++;
			}
		return -1;
	}

	@Override
	public int getCount() {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		if (cas == null || MemoryCache.IMProviders == null)
			return 0;
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					MemoryCache.IMProviders).isAvailable())
				count++;
		return count;

	}

	@Override
	public Object getItem(int position) {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					MemoryCache.IMProviders).isAvailable()) {
				if (position == count)
					return mContactItem.getContactAddresses()[i];
				count++;
			}

		return null;
	}

	@Override
	public long getItemId(int arg0) {

		return 0;
	}

	@Override
	public int getItemViewType(int arg0) {

		return 0;
	}

	private class ViewHolder {
		public ImageView ivIcon;
		public TextView tvLine1;
		public TextView tvLine2;
	}

	private class ViewHolderDropDown {
		public ImageView ivIcon;
		public TextView tvLine1;
		public TextView tvLine2;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			convertView = mInflater.inflate(
					R.layout.conversation_banner_spinner_item, null);
			holder = new ViewHolder();
			holder.tvLine1 = (TextView) convertView
					.findViewById(R.id.tvBannerSpinnerLine1);
			holder.tvLine2 = (TextView) convertView
					.findViewById(R.id.tvBannerSpinnerLine2);
			holder.ivIcon = (ImageView) convertView
					.findViewById(R.id.ivBannerSpinnerIcon);
			convertView.setTag(holder);
		} else
			holder = (ViewHolder) convertView.getTag();

		ContactAddress ca = (ContactAddress) this.getItem(position);

		IMProviderTypes pType = ca.getIMProviderType();
		IMProvider provider = IMProvider.findIMProvider(pType,
				MemoryCache.IMProviders);

		int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				10, mContext.getResources().getDisplayMetrics());

		holder.tvLine1.setText(ca.getDisplayAddress());
		holder.tvLine1.setTextColor(0xffffffff); // white
		String altText = null;
		switch (ca.getAddressType()) {
		case android.provider.ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM:
			altText = ca.getAddressLabel();
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
			altText = "Home";
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
			altText = "Mobile";
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
			altText = "Other";
			break;
		}
		if (TextUtils.isEmpty(altText))
			holder.tvLine2.setText(IMProvider.IMServiceNames[pType.ordinal()]);
		else {
			holder.tvLine2.setText(IMProvider.IMServiceNames[pType.ordinal()]
					+ " - " + altText);
		}
		holder.tvLine2.setTextColor(0xffdddddd); // holo_grey_light

		Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint p = new Paint();
		p.setColor(provider.getDarkColor());
		c.drawCircle(size / 2, size / 2, size / 2, p);
		holder.ivIcon.setImageBitmap(b);

		return convertView;

	}

	@Override
	public int getViewTypeCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean hasStableIds() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void registerDataSetObserver(DataSetObserver arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		ViewHolderDropDown holder;
		if (convertView == null) {
			convertView = mInflater.inflate(
					R.layout.conversation_banner_spinner_dropdown_item, null);
			holder = new ViewHolderDropDown();
			holder.tvLine1 = (TextView) convertView.findViewById(R.id.tvLine1);
			holder.tvLine2 = (TextView) convertView.findViewById(R.id.tvLine2);
			holder.ivIcon = (ImageView) convertView.findViewById(R.id.ivIcon);
			convertView.setTag(holder);
		} else
			holder = (ViewHolderDropDown) convertView.getTag();

		ContactAddress ca = (ContactAddress) this.getItem(position);

		IMProviderTypes pType = ca.getIMProviderType();
		IMProvider provider = IMProvider.findIMProvider(pType,
				MemoryCache.IMProviders);

		int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				10, mContext.getResources().getDisplayMetrics());

		// drop down uses theme colors
		holder.tvLine1.setText(ca.getDisplayAddress());

		String altText = null;
		switch (ca.getAddressType()) {
		case android.provider.ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM:
			altText = ca.getAddressLabel();
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
			altText = "Home";
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
			altText = "Mobile";
			break;
		case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
			altText = "Other";
			break;
		}
		if (TextUtils.isEmpty(altText))
			holder.tvLine2.setText(IMProvider.IMServiceNames[pType.ordinal()]);
		else {
			holder.tvLine2.setText(IMProvider.IMServiceNames[pType.ordinal()]
					+ " - " + altText);
		}

		Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint p = new Paint();
		p.setColor(provider.getDarkColor());
		c.drawCircle(size / 2, size / 2, size / 2, p);
		holder.ivIcon.setImageBitmap(b);

		return convertView;

	}

}
