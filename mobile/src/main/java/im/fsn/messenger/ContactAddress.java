package im.fsn.messenger;

import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import android.os.Parcel;
import android.os.Parcelable;

public class ContactAddress implements Parcelable {

	private String displayAddress;
	private IMProviderTypes imProviderType;
	private String parsedAddress;
	private int addressType;
	private String addressLabel;

	public IMProviderTypes getIMProviderType() {
		return imProviderType;
	}

	public void setIMProviderType(IMProviderTypes imProviderType) {
		this.imProviderType = imProviderType;
	}

	@Override
	public boolean equals(Object object) {
		ContactAddress another = (ContactAddress) object;
		if (this.imProviderType != another.getIMProviderType())
			return false;
		if (this.parsedAddress == null || another.getParsedAddress() == null)
			return false;
		return (this.imProviderType == another.getIMProviderType() && this.parsedAddress
				.equals(another.getParsedAddress()));
	}

	public String getParsedAddress() {
		return parsedAddress;
	}

	public void setParsedAddress(String parsedAddress) {
		this.parsedAddress = parsedAddress;
	}

	public String getDisplayAddress() {
		return displayAddress;
	}

	public void setDisplayAddress(String displayAddress) {
		this.displayAddress = displayAddress;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.displayAddress);
		dest.writeInt(this.imProviderType.ordinal());
		dest.writeString(this.parsedAddress);
		dest.writeInt(this.addressType);
		dest.writeString(this.addressLabel);
	}

	public ContactAddress() {
	}

	public ContactAddress(Parcel in) {
		this.setDisplayAddress(in.readString());
		this.setIMProviderType(IMProviderTypes.values()[in.readInt()]);
		this.setParsedAddress(in.readString());
		this.setAddressType(in.readInt());
		this.setAddressLabel(in.readString());
	}

	public static ContactAddress buildFromMessageItem(MessageItem msgItem) {
		ContactAddress returnValue = new ContactAddress();
		returnValue.setDisplayAddress(msgItem.getExternalAddress());
		returnValue.setParsedAddress(msgItem.getExternalAddress());
		returnValue.setIMProviderType(msgItem.getIMProviderType());
		return returnValue;
	}

	public int getAddressType() {
		return addressType;
	}

	public void setAddressType(int addressType) {
		this.addressType = addressType;
	}

	public String getAddressLabel() {
		return addressLabel;
	}

	public void setAddressLabel(String addressLabel) {
		this.addressLabel = addressLabel;
	}

	public static final Parcelable.Creator<ContactAddress> CREATOR = new Parcelable.Creator<ContactAddress>() {
		public ContactAddress createFromParcel(Parcel in) {
			return new ContactAddress(in);
		}

		public ContactAddress[] newArray(int size) {
			return new ContactAddress[size];
		}
	};

}
