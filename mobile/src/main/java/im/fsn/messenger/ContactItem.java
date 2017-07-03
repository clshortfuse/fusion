package im.fsn.messenger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Build.VERSION;
import android.provider.ContactsContract;
import android.util.TypedValue;

public class ContactItem implements Comparable<ContactItem>, Parcelable {

	public ContactItem() {
	}

	private long id;
	private String displayName;
	private ContactAddress[] contactAddresses = new ContactAddress[0];
	private String displayContactAddress;
	private MessageItem lastMessageItem;
	private Bitmap thumbnail64x64;
	private long unreadCount;
	private boolean photoChecked = false;
	private int sortPriority = Integer.MAX_VALUE;

	public String getIdentifierTag() {
		if (id != 0)
			return String.valueOf(this.id);
		else
			return contactAddresses[0].getIMProviderType().ordinal() + ":"
					+ contactAddresses[0].getParsedAddress();

	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayContactAddress() {
		return displayContactAddress;
	}

	public void setDisplayContactAddress(String displayContactAddress) {
		this.displayContactAddress = displayContactAddress;
	}

	public ContactAddress[] getContactAddresses() {
		return contactAddresses;
	}

	public void setContactAddresses(ContactAddress[] contactAddresses) {
		this.contactAddresses = contactAddresses;
	}

	public MessageItem getLastMessageItem() {
		return lastMessageItem;
	}

	public void setLastMessageItem(MessageItem lastMessageItem) {
		this.lastMessageItem = lastMessageItem;
	}

	@Override
	public int compareTo(ContactItem another) {
		if (this.sortPriority > another.sortPriority)
			return 1;
		if (this.sortPriority > another.sortPriority)
			return -1;

		if (this.getLastMessageItem() == null) {
			if (another.getLastMessageItem() != null)
				return 1;
			if (this.getDisplayName() == null)
				if (another.getDisplayName() == null)
					return 0;
				else
					return 1;
			else if (another.getDisplayName() == null)
				return -1;
			else
				return this.getDisplayName().compareToIgnoreCase(
						another.getDisplayName());

		} else if (another.getLastMessageItem() == null)
			return -1;
		long c1l = this.getLastMessageItem().getCreationDateTime();
		long cl2 = another.getLastMessageItem().getCreationDateTime();
		return (c1l > cl2 ? -1 : c1l < cl2 ? 1 : 0);
	}

	@Override
	public boolean equals(Object object) {
		ContactItem another = (ContactItem) object;
		if (this.id == 0L) {
			if (another.id != 0L)
				return false;
			return this.contactAddresses[0].equals(another.contactAddresses[0]);
		}
		return this.id == another.id;
	}

	public boolean isMessageItemIsValid(MessageItem msgItem) {
		if (this.contactAddresses == null || msgItem == null)
			return false;
		for (ContactAddress ca : this.contactAddresses)
			if (ca.getIMProviderType() == msgItem.getIMProviderType()
					&& ca.getParsedAddress().equals(
							msgItem.getExternalAddress()))
				return true;
		return false;
	}

	public ContactAddress getLastContactAddress() {
		if (this.lastMessageItem == null)
			return contactAddresses[0];

		for (int i = 0; i < contactAddresses.length; i++) {
			if (contactAddresses[i].getIMProviderType() == this.lastMessageItem
					.getIMProviderType()
					&& contactAddresses[i].getParsedAddress().equals(
							this.lastMessageItem.getExternalAddress()))
				return contactAddresses[i];

		}
		return null;

	}

	// Parcelable
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(this.id);
		dest.writeString(this.displayName);
		dest.writeInt(contactAddresses == null ? 0 : contactAddresses.length);
		dest.writeTypedArray(contactAddresses, 0);
		dest.writeString(this.displayContactAddress);
		dest.writeLong(this.unreadCount);
		dest.writeParcelable(lastMessageItem, 0);
		dest.writeByte(photoChecked ? (byte) 1 : (byte) 0);
		if (this.thumbnail64x64 == null)
			dest.writeInt(0);
		else {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			this.thumbnail64x64.compress(Bitmap.CompressFormat.PNG, 0, bs);
			byte[] data = bs.toByteArray();
			dest.writeInt(data.length);
			dest.writeByteArray(data);
		}

	}

	public ContactItem(Parcel in) {
		this();
		ClassLoader loader = ContactItem.class.getClassLoader();
		this.setId(in.readLong());
		this.setDisplayName(in.readString());
		ContactAddress[] caArray = new ContactAddress[in.readInt()];
		in.readTypedArray(caArray, ContactAddress.CREATOR);
		this.setContactAddresses(caArray);
		this.setDisplayContactAddress(in.readString());
		this.setUnreadCount(in.readLong());
		this.setLastMessageItem((MessageItem) in.readParcelable(loader));
		this.setPhotoChecked(in.readByte() == 1);
		int byteArraySize = in.readInt();
		if (byteArraySize != 0) {
			byte[] thumb = new byte[byteArraySize];
			in.readByteArray(thumb);
			this.setThumbnail64x64(BitmapFactory.decodeByteArray(thumb, 0,
					byteArraySize));
		} else
			this.setThumbnail64x64(null);
	}

	public Bitmap getThumbnail64x64() {
		return thumbnail64x64;
	}

	public void setThumbnail64x64(Bitmap thumbnail64x64) {
		this.thumbnail64x64 = thumbnail64x64;
	}

	public static final Parcelable.Creator<ContactItem> CREATOR = new Parcelable.Creator<ContactItem>() {
		public ContactItem createFromParcel(Parcel in) {
			return new ContactItem(in);
		}

		public ContactItem[] newArray(int size) {
			return new ContactItem[size];
		}
	};

	public ContactItem clone() {
		ContactItem c = new ContactItem();
		c.id = this.id;
		c.displayName = this.displayName;
		c.contactAddresses = this.contactAddresses;
		c.displayContactAddress = this.displayContactAddress;
		c.lastMessageItem = this.lastMessageItem;
		c.thumbnail64x64 = this.thumbnail64x64;
		c.unreadCount = this.unreadCount;
		return c;
	}

	public long getUnreadCount() {
		return unreadCount;
	}

	public void setUnreadCount(long unreadCount) {
		this.unreadCount = unreadCount;
	}

	public boolean isPhotoChecked() {
		return photoChecked;
	}

	public void setPhotoChecked(boolean photoChecked) {
		this.photoChecked = photoChecked;
	}

	public int getSortPriority() {
		return sortPriority;
	}

	public void setSortPriority(int sortPriority) {
		this.sortPriority = sortPriority;
	}

	public Bitmap loadContactPictureFromDevice(Context context) {
		if (this.id == 0)
			return null;

		Uri contactUri = Uri.withAppendedPath(
				ContactsContract.Contacts.CONTENT_URI, String.valueOf(this.id));

		InputStream input;
		if (VERSION.SDK_INT >= 14)
			input = V14.openHiResContactPhotoInputStream(
					context.getContentResolver(), contactUri);
		else
			input = ContactsContract.Contacts.openContactPhotoInputStream(
					context.getContentResolver(), contactUri);

		if (input != null) {
			Bitmap fullScale = BitmapFactory.decodeStream(input);
			if (fullScale != null) {
				int contactPixelSize = (int) TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP, 64, context.getResources()
								.getDisplayMetrics());
				if (fullScale.getHeight() > contactPixelSize)
					this.setThumbnail64x64(Bitmap.createScaledBitmap(fullScale,
							contactPixelSize, contactPixelSize, true));
				else
					this.setThumbnail64x64(fullScale);
				return fullScale;
			}
		}
		return null;
	}

	public boolean isUnread() {
		return this.unreadCount > 0
				|| (this.lastMessageItem != null && !this.lastMessageItem
						.isRead());
	}
}
