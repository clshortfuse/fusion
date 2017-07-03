package im.fsn.messenger;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ContactsListAdapter extends BaseAdapter implements Filterable {

	private Context mContext;
	private LayoutInflater mInflater;
	private ItemsFilter filter;
	private List<ContactItem> filtered;
	private SharedPreferences mPrefs;
	private Drawable defaultAvatarLight;
	private Drawable defaultAvatarDark;
	private int maxLinesInPreview = 2;
	private CharSequence lastQuery;
	private int mIMContactIndicatorWidth = 4;
	private int contactPixelSize = -1;

	public ContactsListAdapter(Context context) {
		super();
		this.mContext = context;
		this.mInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		this.filtered = new ArrayList<ContactItem>();
		if (MemoryCache.ContactsList != null) {
			for (int i = 0; i < MemoryCache.ContactsList.size(); i++) {
				ContactItem c = MemoryCache.ContactsList.get(i);
				if (c.getLastMessageItem() != null)
					filtered.add(c);
			}
		}

		this.defaultAvatarLight = context.getResources().getDrawable(
				R.drawable.ic_contact_picture_light);
		this.defaultAvatarDark = context.getResources().getDrawable(
				R.drawable.ic_contact_picture_dark);
		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.mPrefs
				.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

		this.contactPixelSize = (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, 64, this.mContext.getResources()
						.getDisplayMetrics());

		refreshPreferences();

	}

	private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key.equals("pfContactsMaxLines")
					|| key.equals("pfMessageItemIndicatorWidth")) {
				refreshPreferences();
				ContactsListAdapter.this.notifyDataSetChanged();
			}
		}
	};

	private void refreshPreferences() {
		this.maxLinesInPreview = mPrefs.getInt("pfContactsMaxLines", 2);
		this.mIMContactIndicatorWidth = mPrefs.getInt(
				"pfMessageItemIndicatorWidth", 4);
	}

	@Override
	public Filter getFilter() {

		if (filter == null)
			filter = new ItemsFilter();
		return filter;
	}

	public void refreshFilter() {
		this.getFilter().filter(this.lastQuery);
	}

	private class ItemsFilter extends Filter {
		protected FilterResults performFiltering(CharSequence query) {
			lastQuery = query;
			FilterResults results = new FilterResults();
			ArrayList<ContactItem> filtered = new ArrayList<ContactItem>();
			if (MemoryCache.ContactsList == null) {
				results.values = filtered;
				results.count = filtered.size();
			} else if (query == null) {
				for (int i = 0; i < MemoryCache.ContactsList.size(); i++) {
					ContactItem c = MemoryCache.ContactsList.get(i);
					if (c.getLastMessageItem() != null)
						filtered.add(c);
				}
				results.values = filtered;
				results.count = filtered.size();
			} else if (query.length() == 0) {
				results.values = MemoryCache.ContactsList;
				results.count = MemoryCache.ContactsList.size();
			} else {
				String sQuery = query.toString();

				boolean foundExactMatch = false;
				boolean isWellFormedSMS = PhoneUtilsLite
						.isWellFormedSmsAddress(sQuery);
				String parsedAddress = null;
				if (isWellFormedSMS)
					parsedAddress = PhoneUtilsLite.parseAddress(sQuery, null,
							null);

				synchronized (MemoryCache.ContactsList) {
					for (int i = 0; i < MemoryCache.ContactsList.size(); i++) {
						ContactItem c = MemoryCache.ContactsList.get(i);
						if (c.getDisplayName().toLowerCase().contains(query))
							filtered.add(c);
						else if (isWellFormedSMS) {
							ContactAddress[] contactAddresses = c
									.getContactAddresses();
							boolean foundCAMatch = false;
							for (int j = 0; j < contactAddresses.length; j++) {
								if (!foundCAMatch
										&& contactAddresses[j]
												.getParsedAddress().contains(
														parsedAddress)) {
									filtered.add(c);
									foundCAMatch = true;
								}
								if (!foundExactMatch
										&& contactAddresses[j]
												.getParsedAddress().equals(
														parsedAddress)) {
									foundExactMatch = true;
									break;
								}
							}
						}
					}
					if (isWellFormedSMS && !foundExactMatch) {

						ContactItem newContact = new ContactItem();
						newContact.setDisplayName(parsedAddress);
						newContact.setDisplayContactAddress(parsedAddress);
						newContact.setSortPriority(0);
						ContactAddress caSMS = new ContactAddress();
						caSMS.setDisplayAddress(sQuery);
						caSMS.setIMProviderType(IMProviderTypes.SMS);
						caSMS.setParsedAddress(parsedAddress);
						ContactAddress caGV = new ContactAddress();
						caGV.setDisplayAddress(sQuery);
						caGV.setIMProviderType(IMProviderTypes.GVoice);
						caGV.setParsedAddress(parsedAddress);
						newContact.setContactAddresses(new ContactAddress[] {
								caSMS, caGV });
						filtered.add(0, newContact);
					}
					results.values = filtered;
					results.count = filtered.size();
				}
			}

			return results;
		}

		@SuppressWarnings("unchecked")
		protected void publishResults(CharSequence prefix, FilterResults results) {
			ContactsListAdapter.this.filtered = (List<ContactItem>) results.values;
			notifyDataSetChanged();
		}
	}

	private class ViewHolder {
		public QuickContactBadge qcbAvatar;
		public TextView tvContactDisplayName;
		public TextView tvLastMessageText;
		public TextView tvMessageDate;
		public View vNewMessageIndicator;
		public long contactId;

	}

	@Override
	public int getCount() {
		return this.filtered.size();
	}

	@Override
	public ContactItem getItem(int position) {
		return this.filtered.get(position);
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		boolean isLightTheme = ThemeOptions.isLightBackground(this.mContext,
				false);
		ViewHolder viewHolder;
		if (convertView == null) {
			convertView = mInflater.inflate(
					R.layout.googlemms_contact_list_item, null);
			viewHolder = new ViewHolder();
			viewHolder.vNewMessageIndicator = (View) convertView
					.findViewById(R.id.vNewMessageIndicator);
			viewHolder.qcbAvatar = (QuickContactBadge) convertView
					.findViewById(R.id.qcbAvatar);

			viewHolder.tvContactDisplayName = (TextView) convertView
					.findViewById(R.id.tvContactDisplayName);
			viewHolder.tvLastMessageText = (TextView) convertView
					.findViewById(R.id.tvLastMessageText);
			viewHolder.tvMessageDate = (TextView) convertView
					.findViewById(R.id.tvMessageDate);

			convertView.setTag(viewHolder);
		} else
			viewHolder = (ViewHolder) convertView.getTag();

		ContactItem c = this.getItem(position);
		viewHolder.contactId = c.getId();

		viewHolder.tvContactDisplayName.setText(c.getDisplayName());
		MessageItem lastMessageItem = c.getLastMessageItem();

		CharSequence secondaryText = null;
		CharSequence dateText = null;
		int color = 0;

		if (lastMessageItem != null) {
			secondaryText = lastMessageItem.getText();

			long lDate = lastMessageItem.getCompletionDateTime();
			if (lDate == -1)
				lDate = lastMessageItem.getCreationDateTime();

			Calendar eventDateTime = Calendar.getInstance(
					TimeZone.getDefault(), Locale.getDefault());
			Calendar yesterday = Calendar.getInstance(TimeZone.getDefault(),
					Locale.getDefault());
			Calendar lastYear = Calendar.getInstance(TimeZone.getDefault(),
					Locale.getDefault());

			eventDateTime.setTimeInMillis(lDate);
			yesterday.add(Calendar.DATE, -1);
			lastYear.add(Calendar.YEAR, -1);

			Date dEventDateTime = new Date(lDate);

			if (eventDateTime.after(yesterday)) {
				dateText = android.text.format.DateFormat.getTimeFormat(
						mContext).format(dEventDateTime);
			} else if (eventDateTime.after(lastYear)) {
				SimpleDateFormat sdfOriginal = (SimpleDateFormat) SimpleDateFormat
						.getDateInstance(DateFormat.SHORT, Locale.getDefault());
				dateText = Utils.stripFieldFromPattern(sdfOriginal,
						dEventDateTime, DateFormat.Field.YEAR);

			} else {
				dateText = DateFormat.getDateInstance(DateFormat.SHORT,
						Locale.getDefault()).format(dEventDateTime);
			}

		} else
			secondaryText = c.getDisplayContactAddress();

		if (TextUtils.isEmpty(secondaryText)) {
			secondaryText = c.getDisplayName();
		}
		viewHolder.tvMessageDate.setText(dateText);

		if (c.isUnread() && MemoryCache.IMProviders != null) {
			IMProvider p = IMProvider.findIMProvider(
					lastMessageItem.getIMProviderType(),
					MemoryCache.IMProviders);
			if (p != null) {
				if (isLightTheme)
					color = p.getLightColor();
				else
					color = p.getDarkColor();
			}
		}
		if (color == 0) {
			if (isLightTheme)
				color = 0xffdddddd; // lighter_gray
			else
				color = 0xffaaaaaa; // darker_gray
		}
		viewHolder.vNewMessageIndicator.setBackgroundColor(color);
		viewHolder.vNewMessageIndicator.getLayoutParams().width = (int) TypedValue
				.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
						this.mIMContactIndicatorWidth, this.mContext
								.getResources().getDisplayMetrics());

		if (c.getId() != 0) {
			Uri contactUri = Uri.withAppendedPath(
					ContactsContract.Contacts.CONTENT_URI,
					String.valueOf(c.getId()));
			viewHolder.qcbAvatar.assignContactUri(contactUri);

			if (!c.isPhotoChecked()) {

				ProfileImageLoader loader = new ProfileImageLoader(viewHolder,
						c, contactPixelSize);
				loader.execute();

				// this.filtered.set(position, c);

			}
		} else {
			ContactAddress ca = c.getContactAddresses()[0];
			if (ca.getIMProviderType() == IMProviderTypes.SMS
					|| ca.getIMProviderType() == IMProviderTypes.GVoice)
				viewHolder.qcbAvatar.assignContactFromPhone(
						ca.getParsedAddress(), true);
		}
		viewHolder.qcbAvatar.setMode(ContactsContract.QuickContact.MODE_SMALL);

		Bitmap b = c.getThumbnail64x64();
		if (b != null)
			viewHolder.qcbAvatar.setImageBitmap(b);
		else {
			viewHolder.qcbAvatar.setImageBitmap(null);
			if (!isLightTheme) {
				viewHolder.qcbAvatar.setImageDrawable(defaultAvatarDark);
			} else {
				viewHolder.qcbAvatar.setImageDrawable(defaultAvatarLight);
			}
		}
		float size = viewHolder.tvLastMessageText.getTextSize();

		viewHolder.tvLastMessageText.setText(Emoji.getSmiledText(mContext,
				secondaryText, false, (int) size));
		viewHolder.tvLastMessageText.setMaxLines(maxLinesInPreview);

		return convertView;

	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return 0;
	}

	public class ProfileImageLoader extends AsyncTask<Void, Void, Bitmap> {

		private final ViewHolder viewHolder;
		private final int contactPixelSize;
		private final ContactItem contactItem;

		public ProfileImageLoader(ViewHolder viewHolder,
				ContactItem contactItem, int contactPixelSize) {

			this.viewHolder = viewHolder;
			this.contactPixelSize = contactPixelSize;
			this.contactItem = contactItem;

		}

		@Override
		protected Bitmap doInBackground(Void... params) {

			Uri contactUri = Uri.withAppendedPath(
					ContactsContract.Contacts.CONTENT_URI,
					String.valueOf(contactItem.getId()));
			InputStream input;
			if (VERSION.SDK_INT >= 14)
				input = V14.openHiResContactPhotoInputStream(
						mContext.getContentResolver(), contactUri);
			else
				input = ContactsContract.Contacts.openContactPhotoInputStream(
						mContext.getContentResolver(), contactUri);

			Bitmap b = null;
			if (input != null) {
				b = BitmapFactory.decodeStream(input);
				if (b != null) {
					if (b.getHeight() > contactPixelSize)
						b = Bitmap.createScaledBitmap(b, contactPixelSize,
								contactPixelSize, true);
				}

			}
			return b;

		}

		@Override
		protected void onPostExecute(Bitmap result) {

			super.onPostExecute(result);

			contactItem.setThumbnail64x64(result);

			contactItem.setPhotoChecked(true);
			MemoryCache.UpdateContactItem(contactItem);
			if (result != null && viewHolder.contactId == contactItem.getId())
				viewHolder.qcbAvatar.setImageBitmap(result);

		}

	}

}
