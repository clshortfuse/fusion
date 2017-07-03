package im.fsn.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.DataSetObserver;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import im.fsn.messenger.providers.IMProvider;

import im.fsn.messenger.R;

public class ConversationSpinnerAdapter implements SpinnerAdapter {

	private ContactItem mContactItem;
	private Context mContext;
	private LayoutInflater mInflater;
	private SharedPreferences mPrefs;

	public ConversationSpinnerAdapter(Context context, ContactItem contactItem,
			IMProvider[] imProviders) {
		super();
		this.mContext = context;
		this.mContactItem = contactItem;
		this.mInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.mPrefs
				.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
		this.mIMProviders = imProviders;
		refreshPreferences();
	}

	public ContactItem getContactItem() {
		return mContactItem;
	}

	private boolean useIMServiceIndicator;
	private boolean useIMServiceText;
	private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key.equals("pfConversationSelectorIMService")) {
				refreshPreferences();
				// notifyDataSetChanged();
			}
		}
	};
	private IMProvider[] mIMProviders = null;

	private void refreshPreferences() {
		int value = Integer.parseInt(mPrefs.getString(
				"pfConversationSelectorIMService", "0"));
		this.useIMServiceIndicator = (value == 0 || value == 2);
		this.useIMServiceText = (value == 1 || value == 2);
	}

	public int getIndex(ContactAddress ca) {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		if (cas == null || this.mIMProviders == null)
			return -1;
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					this.mIMProviders).isAvailable()) {
				if (cas[i].equals(ca))
					return count;
				count++;
			}
		return -1;
	}

	@Override
	public int getCount() {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		if (cas == null || this.mIMProviders == null)
			return 0;
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					this.mIMProviders).isAvailable())
				count++;
		return count;

	}

	@Override
	public Object getItem(int position) {
		ContactAddress[] cas = mContactItem.getContactAddresses();
		int count = 0;
		for (int i = 0; i < cas.length; i++)
			if (IMProvider.findIMProvider(cas[i].getIMProviderType(),
					this.mIMProviders).isAvailable()) {
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
		public View vIndicator;
		public TextView textView1;
		public TextView textView2;

	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.conversation_spinner_item,
					null);
			holder = new ViewHolder();
			holder.textView1 = (TextView) convertView
					.findViewById(R.id.tvLine1);
			holder.textView2 = (TextView) convertView
					.findViewById(R.id.textView2);
			convertView.setTag(holder);
		} else
			holder = (ViewHolder) convertView.getTag();

		ContactAddress ca = (ContactAddress) this.getItem(position);
		String subText = ca.getDisplayAddress()
				+ " ("
				+ IMProvider.IMServiceShortNames[ca.getIMProviderType()
						.ordinal()] + ")";
		holder.textView1.setText(this.mContactItem.getDisplayName());
		holder.textView2.setText(subText);

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
		ViewHolder holder;
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.conversation_spinner_item,
					null);
			holder = new ViewHolder();
			holder.vIndicator = convertView
					.findViewById(R.id.vServiceIndicator);
			holder.textView1 = (TextView) convertView
					.findViewById(R.id.tvLine1);
			holder.textView2 = (TextView) convertView
					.findViewById(R.id.textView2);
			convertView.setTag(holder);
		} else
			holder = (ViewHolder) convertView.getTag();

		ContactAddress ca = (ContactAddress) this.getItem(position);

		holder.textView1.setText(ca.getDisplayAddress());

		if (this.useIMServiceText) {
			holder.textView2.setText(IMProvider.IMServiceNames[ca
					.getIMProviderType().ordinal()]);
			holder.textView2.setVisibility(View.VISIBLE);
		} else {
			holder.textView2.setVisibility(View.GONE);
		}
		if (this.useIMServiceIndicator) {
			IMProvider provider = IMProvider.findIMProvider(
					ca.getIMProviderType(), mIMProviders);
			if (provider != null) {
				boolean isLightTheme = ThemeOptions.isLightBackground(
						this.mContext, true);
				holder.vIndicator.setBackgroundColor(isLightTheme ? provider
						.getLightColor() : provider.getDarkColor());
				holder.vIndicator.setVisibility(View.VISIBLE);
			} else {
				holder.vIndicator.setVisibility(View.GONE);
			}
		} else {
			holder.vIndicator.setVisibility(View.GONE);
		}

		return convertView;

	}
}
