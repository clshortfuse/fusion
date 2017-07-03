package im.fsn.messenger;

import java.lang.ref.SoftReference;

import android.graphics.Bitmap;
import android.graphics.Point;

public class MMSObject {
	private SoftReference<Bitmap[]> imagePreviews;
	private Point[] previewSizes;
	private String[] imagePartIds;
	private String text;
	private int msgType;
	private long msgSize;
	private long msgExpiration;
	private String importMessageId;
	private String contentLocation;

	public Bitmap[] getImagePreviews() {
		if (imagePreviews == null)
			return null;
		return imagePreviews.get();
	}

	public void setImagePreviews(Bitmap[] imagePreviews) {
		this.imagePreviews = new SoftReference<Bitmap[]>(imagePreviews);
	}

	public Point[] getPreviewSizes() {
		return previewSizes;
	}

	public void setPreviewSizes(Point[] previewSizes) {
		this.previewSizes = previewSizes;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String[] getImagePartIds() {
		return imagePartIds;
	}

	public void setImagePartIds(String[] imagePartIds) {
		this.imagePartIds = imagePartIds;
	}

	public int getMsgType() {
		return msgType;
	}

	public void setMsgType(int msgType) {
		this.msgType = msgType;
	}

	public long getMsgSize() {
		return msgSize;
	}

	public void setMsgSize(long msgSize) {
		this.msgSize = msgSize;
	}

	public long getMsgExpiration() {
		return msgExpiration;
	}

	public void setMsgExpiration(long msgExpiration) {
		this.msgExpiration = msgExpiration;
	}

	public String getContentLocation() {
		return contentLocation;
	}

	public void setContentLocation(String contentLocation) {
		this.contentLocation = contentLocation;
	}

	public String getImportMessageId() {
		return importMessageId;
	}

	public void setImportMessageId(String importMessageId) {
		this.importMessageId = importMessageId;
	}

}
