package im.fsn.messenger;

import java.io.ByteArrayOutputStream;

import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;

public class MessageItem implements Comparable<MessageItem>, Parcelable {
	private String text;
	private boolean incoming;
	private IMProviderTypes imProviderType;
	private String internalAddress;
	private String externalAddress;
	private MessageStatuses messageStatus;
	private long messageId = -1;
	private long creationDateTime = -1;
	private long lastSendAttemptDateTime = -1;
	private long completionDateTime = -1;
	private byte[] extraData;
	private ExtraDataTypes extraDataType = ExtraDataTypes.None;
	private String importConversationId;
	private String importMessageId;
	private boolean read = true;

	public static enum ExtraDataTypes {
		None, Unknown, SingleImage, MultipleImages, Slideshow, Video, Audio
	}

	public static enum MessageStatuses {
		Queued, OnRoute, Sent, Delivered, Failed, Deleted
	}

	public long getCreationDateTime() {
		return creationDateTime;
	}

	public void setCreationDateTime(long creationDateTime) {
		this.creationDateTime = creationDateTime;
	}

	public long getLastSendAttemptDateTime() {
		return lastSendAttemptDateTime;
	}

	public void setLastSendAttemptDateTime(long lastSendAttemptDateTime) {
		this.lastSendAttemptDateTime = lastSendAttemptDateTime;
	}

	public long getCompletionDateTime() {
		return completionDateTime;
	}

	public void setCompletionDateTime(long completionDateTime) {
		this.completionDateTime = completionDateTime;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public boolean isIncoming() {
		return incoming;
	}

	public void setIncoming(boolean incoming) {
		this.incoming = incoming;
	}

	public long getMessageId() {
		return messageId;
	}

	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}

	public MessageStatuses getMessageStatus() {
		return messageStatus;
	}

	public void setMessageStatus(MessageStatuses messageStatus) {
		this.messageStatus = messageStatus;
	}

	public IMProviderTypes getIMProviderType() {
		return this.imProviderType;
	}

	public void setIMProviderType(IMProviderTypes imProviderType) {
		this.imProviderType = imProviderType;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.text);
		dest.writeByte((byte) (this.incoming ? 1 : 0));
		dest.writeInt(this.imProviderType.ordinal());
		dest.writeString(this.internalAddress);
		dest.writeString(this.externalAddress);
		dest.writeInt(this.messageStatus.ordinal());
		dest.writeLong(this.messageId);
		dest.writeLong(this.creationDateTime);
		dest.writeLong(this.lastSendAttemptDateTime);
		dest.writeLong(this.completionDateTime);
		if (this.extraData == null)
			dest.writeInt(0);
		else {
			dest.writeInt(this.extraData.length);
			dest.writeByteArray(this.extraData);
		}
		dest.writeInt(this.extraDataType.ordinal());
		dest.writeString(this.importConversationId);
		dest.writeString(this.importMessageId);
		dest.writeByte((byte) (this.read ? 1 : 0));
	}

	public MessageItem() {
	}

	public MessageItem(Parcel in) {
		this.setText(in.readString());
		this.setIncoming(in.readByte() == 1);
		this.setIMProviderType(IMProviderTypes.values()[in.readInt()]);
		this.setInternalAddress(in.readString());
		this.setExternalAddress(in.readString());
		this.setMessageStatus(MessageStatuses.values()[in.readInt()]);
		this.setMessageId(in.readLong());
		this.setCreationDateTime(in.readLong());
		this.setLastSendAttemptDateTime(in.readLong());
		this.setCompletionDateTime(in.readLong());
		int byteArraySize = in.readInt();
		if (byteArraySize != 0) {
			byte[] extraData = new byte[byteArraySize];
			in.readByteArray(extraData);
			this.extraData = extraData;
		} else
			this.extraData = null;
		this.extraDataType = ExtraDataTypes.values()[in.readInt()];
		this.importConversationId = (in.readString());
		this.importMessageId = (in.readString());
		this.read = (in.readByte() == 1);

	}

	public String getImportConversationId() {
		return importConversationId;
	}

	public void setImportConversationId(String importConversationId) {
		this.importConversationId = importConversationId;
	}

	public String getImportMessageId() {
		return importMessageId;
	}

	public void setImportMessageId(String importMessageId) {
		this.importMessageId = importMessageId;
	}

	public byte[] getExtraData() {
		return extraData;
	}

	public void setExtraData(byte[] extraData) {
		this.extraData = extraData;
	}

	public ExtraDataTypes getExtraDataType() {
		return extraDataType;
	}

	public void setExtraDataType(ExtraDataTypes extraDataType) {
		this.extraDataType = extraDataType;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public String getInternalAddress() {
		return internalAddress;
	}

	public void setInternalAddress(String internalAddress) {
		this.internalAddress = internalAddress;
	}

	public String getExternalAddress() {
		return externalAddress;
	}

	public void setExternalAddress(String externalAddress) {
		this.externalAddress = externalAddress;
	}

	public int compareTo(MessageItem rhs) {
		long rhsCreationDateTime = rhs.getCreationDateTime();
		if (this.creationDateTime == rhsCreationDateTime)
			return 0;
		if (this.creationDateTime > rhsCreationDateTime)
			return 1;
		return -1;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == null && obj == null)
			return true;
		if (obj == null)
			return false;

		MessageItem m = (MessageItem) obj;
		if (m.messageId != this.messageId)
			return false;
		if (this.imProviderType != m.imProviderType)
			return false;
		return dataMatches(m);
	}

	public boolean dataMatches(MessageItem m) {

		boolean numericCheck = this.completionDateTime == m.completionDateTime
				&& this.incoming == m.incoming && this.read == m.read
				&& this.creationDateTime == m.creationDateTime
				&& this.lastSendAttemptDateTime == m.lastSendAttemptDateTime
				&& this.messageStatus == m.messageStatus;
		if (!numericCheck)
			return false;
		// in-line null comparisons
		return (this.text == null ? m.text == null : m.text == null ? false
				: this.text.equals(m.text))
				&& (this.internalAddress == null ? m.internalAddress == null
						: m.internalAddress == null ? false
								: this.internalAddress
										.equals(m.internalAddress))
				&& (this.externalAddress == null ? m.externalAddress == null
						: m.externalAddress == null ? false
								: this.externalAddress
										.equals(m.externalAddress))
				&& (this.importConversationId == null ? m.importConversationId == null
						: m.importConversationId == null ? false
								: this.importConversationId
										.equals(m.importConversationId))
				&& (this.importMessageId == null ? m.importMessageId == null
						: m.importMessageId == null ? false
								: this.importMessageId
										.equals(m.importMessageId));

	}

	public MessageItem clone() {
		MessageItem m = new MessageItem();
		m.completionDateTime = this.completionDateTime;
		m.creationDateTime = this.creationDateTime;
		m.externalAddress = this.externalAddress;
		m.extraData = this.extraData;
		m.extraDataType = this.extraDataType;
		m.importConversationId = this.importConversationId;
		m.importMessageId = this.importMessageId;

		m.imProviderType = this.imProviderType;
		m.incoming = this.incoming;
		m.internalAddress = this.internalAddress;
		m.lastSendAttemptDateTime = this.lastSendAttemptDateTime;
		m.messageId = this.messageId;
		m.messageStatus = this.messageStatus;
		m.read = this.read;
		m.text = this.text;

		return m;
	}

	public static final Parcelable.Creator<MessageItem> CREATOR = new Parcelable.Creator<MessageItem>() {
		public MessageItem createFromParcel(Parcel in) {
			return new MessageItem(in);
		}

		public MessageItem[] newArray(int size) {
			return new MessageItem[size];
		}
	};

}
