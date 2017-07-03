package im.fsn.messenger;

import im.fsn.messenger.MessageItem.ExtraDataTypes;
import im.fsn.messenger.apisupport.V11;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build.VERSION;
import android.util.Log;

public class DBMessagesAdapter {

	private static final String DATABASE_FILE = "messages.db";
	private static final int DATABASE_VERSION = 4;
	private static final String DATABASE_TABLE = "Messages";
	private final Context mContext;

	private SQLiteDatabase db;
	//@formatter:off
	private static final String DATABASE_CREATE =
		"CREATE TABLE Messages "
		+ "("
			+ "MessageID INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ "ProviderID INTEGER NOT NULL, "
			+ "MessageStatusID UNSIGNED INTEGER NOT NULL, "
			+ "IsIncoming BIT NOT NULL, "
			+ "InternalAddress NVARCHAR(128) NULL, "
			+ "ExternalAddress VARCHAR(128) NOT NULL, "
			+ "CreationDateTime UNSIGNED LONG NOT NULL, "
			+ "LastSendAttemptDateTime UNSIGNED LONG NULL, "
			+ "CompletionDateTime UNSIGNED LONG NULL, "
			+ "MessageText TEXT NOT NULL, "
			+ "ExtraData BLOB NULL, "
			+ "ExtraDataTypeID UNSIGNED INTEGER NULL, "
			+ "ImportMessageID NVARCHAR(40) NULL, "
			+ "ImportConversationID NVARCHAR(40) NULL, " 
			+ "IsRead BIT NOT NULL" 
		+ ")";
	//@formatter:on

	//@formatter:off
	private static final String DATABASE_INDEXES_CREATEBYDATE =
		"CREATE INDEX IX_ProviderID_ExternalAddress ON MESSAGES "
		+ "("
			+ "ProviderID ASC, "
			+ "ExternalAddress ASC, "			
			+ "CreationDateTime ASC "
		+ ")";
	//@formatter:on

	//@formatter:off
	private static final String DATABASE_INDEXES_CREATEBYREAD =
		"CREATE INDEX IX_ProviderID_ExternalAddress_IsRead ON MESSAGES "
		+ "("
			+ "ProviderID ASC, "
			+ "ExternalAddress ASC, "			
			+ "IsRead ASC "
		+ ")";
	//@formatter:on

	//@formatter:off
	private static final String DATABASE_INDEXES_CREATEBYSTATUS =
		"CREATE INDEX IX_ProviderID_ExternalAddress_Status ON MESSAGES "
		+ "("
			+ "ProviderID ASC, "
			+ "ExternalAddress ASC, "			
			+ "MessageStatusID ASC "
		+ ")";
	//@formatter:on

	private DatabaseHelper DBHelper;

	public DBMessagesAdapter(Context mContext) {
		this.mContext = mContext;
		this.DBHelper = new DatabaseHelper(this.mContext);
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context mContext) {
			super(mContext, DATABASE_FILE, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE);
			db.execSQL(DATABASE_INDEXES_CREATEBYDATE);
			db.execSQL(DATABASE_INDEXES_CREATEBYREAD);
			db.execSQL(DATABASE_INDEXES_CREATEBYSTATUS);

		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion,
				int newVersion) {
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int currentVersion,
				int latestVersion) {
			if (currentVersion == 1) {
				Cursor c = db.query(DATABASE_TABLE, new String[] {
						"ProviderID", "ExternalAddress" },
						"ProviderID=? OR ProviderID=?",
						new String[] { String.valueOf(0), String.valueOf(1) },
						"ProviderID, ExternalAddress", null, null, null);
				if (c != null) {
					PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
					while (c.moveToNext()) {
						int providerId = c.getInt(0);
						String externalAddress = c.getString(1);
						String formatted = null;
						try {
							PhoneNumber number = phoneUtil.parse(
									externalAddress, Locale.getDefault()
											.getCountry());
							formatted = phoneUtil.format(number,
									PhoneNumberFormat.E164);
						} catch (NumberParseException e) {
							formatted = externalAddress;
						}
						ContentValues initialValues = new ContentValues();
						initialValues.put("ExternalAddress", formatted);
						if (!formatted.equals(externalAddress)) {
							int updates = db.update(DATABASE_TABLE,
									initialValues,
									"ProviderID=? AND ExternalAddress=?",
									new String[] { String.valueOf(providerId),
											externalAddress });
							if (updates > 0)
								Log.d("DBMESSAGES", "Updated " + updates);
						}

					}
				}
				c.close();
				currentVersion = 2;
			}
			if (currentVersion == 2) {
				int deleted = db
						.delete(DATABASE_TABLE,
								"ProviderID=0 AND ImportMessageID IS NULL AND IsIncoming=1",
								null);
				currentVersion = 3;
			}

			if (currentVersion == 3) {
				db.execSQL(DATABASE_INDEXES_CREATEBYREAD);
				db.execSQL(DATABASE_INDEXES_CREATEBYSTATUS);
				currentVersion = 4;
			}

		}
	};

	public DBMessagesAdapter open() throws SQLiteException {
		this.db = DBHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		this.DBHelper.close();
	}

	private static ContentValues putValue(ContentValues cv, String fieldName,
			String stringValue) {
		if (stringValue == null || stringValue.length() == 0)
			cv.putNull(fieldName);
		else
			cv.put(fieldName, stringValue);
		return cv;

	}

	private static ContentValues putValue(ContentValues cv, String fieldName,
			long dateTime) {
		if (dateTime == -1)
			cv.putNull(fieldName);
		else
			cv.put(fieldName, dateTime);
		return cv;

	}

	// lazy

	public class InsertOrUpdateResult {
		private long messageId;
		private int updateCount;
		private int insertCount;
		private boolean deleted = false;
		private boolean changed = false;

		public InsertOrUpdateResult() {
		}

		public InsertOrUpdateResult(long messageId, int insertCount,
				int updateCount) {
			this.messageId = messageId;
			this.insertCount = insertCount;
			this.updateCount = updateCount;

		}

		public long getMessageId() {
			return messageId;
		}

		public void setMessageId(long messageId) {
			this.messageId = messageId;
		}

		public int getUpdateCount() {
			return updateCount;
		}

		public void setUpdateCount(int updateCount) {
			this.updateCount = updateCount;
		}

		public int getInsertCount() {
			return insertCount;
		}

		public void setInsertCount(int insertCount) {
			this.insertCount = insertCount;
		}

		public boolean isChanged() {
			return changed;
		}

		public void setChanged(boolean changed) {
			this.changed = changed;
		}

		public boolean isDeleted() {
			return deleted;
		}

		public void setDeleted(boolean deleted) {
			this.deleted = deleted;
		}

	}

	public InsertOrUpdateResult insertOrUpdateMessage(MessageItem msgItem) {

		InsertOrUpdateResult returnValue = new InsertOrUpdateResult();

		ContentValues initialValues = new ContentValues();
		initialValues.put("ProviderID", msgItem.getIMProviderType().ordinal());
		initialValues.put("MessageStatusID", msgItem.getMessageStatus()
				.ordinal());
		initialValues.put("IsIncoming", msgItem.isIncoming());

		initialValues = putValue(initialValues, "InternalAddress",
				msgItem.getInternalAddress());

		initialValues = putValue(initialValues, "ExternalAddress",
				msgItem.getExternalAddress());

		initialValues = putValue(initialValues, "CreationDateTime",
				msgItem.getCreationDateTime());
		initialValues = putValue(initialValues, "LastSendAttemptDateTime",
				msgItem.getLastSendAttemptDateTime());

		initialValues = putValue(initialValues, "CompletionDateTime",
				msgItem.getCompletionDateTime());

		initialValues.put("MessageText", msgItem.getText());


		if (msgItem.getExtraData() == null
				|| msgItem.getExtraData().length == 0)
			initialValues.putNull("ExtraData");
		else
			initialValues.put("ExtraData", msgItem.getExtraData());

		if (msgItem.getExtraDataType() == ExtraDataTypes.None)
			initialValues.putNull("ExtraDataTypeID");
		else
			initialValues.put("ExtraDataTypeID", msgItem.getExtraDataType()
					.ordinal());

		initialValues = putValue(initialValues, "ImportMessageID",
				msgItem.getImportMessageId());

		initialValues = putValue(initialValues, "ImportConversationID",
				msgItem.getImportConversationId());
		
		initialValues.put("IsRead", msgItem.isRead());

		if (msgItem.getMessageId() != -1) {
			int updateCount = db.update(DATABASE_TABLE, initialValues,
					"MessageID=?",
					new String[] { String.valueOf(msgItem.getMessageId()) });
			returnValue.setUpdateCount(updateCount);
			returnValue.setMessageId(msgItem.getMessageId());
			return returnValue;

		} else {
			if (msgItem.getImportMessageId() != null
					&& msgItem.getImportMessageId().length() != 0) {
				String whereClause;
				String[] whereArgs;
				if (msgItem.getImportConversationId() == null) {
					whereClause = "ProviderID=? AND ImportMessageID=?";
					whereArgs = new String[] {
							String.valueOf(msgItem.getIMProviderType()
									.ordinal()),
							String.valueOf(msgItem.getImportMessageId()) };
				} else {
					whereClause = "ProviderID=? AND (ImportMessageID=? OR (ImportMessageID IS NULL AND MessageText=? AND ImportConversationID=?))";
					whereArgs = new String[] {
							String.valueOf(msgItem.getIMProviderType()
									.ordinal()),
							String.valueOf(msgItem.getImportMessageId()),
							msgItem.getText(),
							msgItem.getImportConversationId() };
				}

				Cursor deleteCheckCursor = db.query(DATABASE_TABLE,
						new String[] { "MessageID" }, "MessageStatusID=5 AND "
								+ whereClause, whereArgs, null, null, null,
						null);
				if (deleteCheckCursor.moveToNext()) {
					returnValue.updateCount = 0;
					returnValue.changed = false;
					returnValue.setDeleted(true);
					returnValue.insertCount = 0;
					returnValue.messageId = deleteCheckCursor.getLong(0);
					deleteCheckCursor.close();
					return returnValue;
				}
				deleteCheckCursor.close();

				int updateCount;
				updateCount = db.update(DATABASE_TABLE, initialValues,
						whereClause, whereArgs);

				returnValue.setUpdateCount(updateCount);

				if (updateCount != 0) {

					Cursor c = db.query(DATABASE_TABLE, allFields, whereClause,
							whereArgs, null, null, null);
					c.moveToNext();
					MessageItem newItem = parseMessageItemCursor(c);
					returnValue.setMessageId(newItem.getMessageId());
					c.close();
					newItem.setMessageId(msgItem.getMessageId());
					returnValue.setChanged(!msgItem.equals(newItem));
					return returnValue;

				}
			}

			long messageId = db.insert(DATABASE_TABLE, null, initialValues); // messageId
			returnValue.setMessageId(messageId);
			returnValue.setInsertCount(1);
			return returnValue;

		}

	}

	public boolean deleteMessage(long messageId) {
		return db.delete(DATABASE_TABLE, "MessageID=?",
				new String[] { String.valueOf(messageId) }) > 0;
	}

	private String[] allFields = new String[] { "MessageID", "ProviderID",
			"MessageStatusID", "IsIncoming", "InternalAddress",
			"ExternalAddress", "CreationDateTime", "LastSendAttemptDateTime",
			"CompletionDateTime", "MessageText", "ExtraData",
			"ExtraDataTypeID", "ImportMessageID", "ImportConversationID",
			"IsRead" };

	public static MessageItem parseMessageItemCursor(Cursor c) {
		int index = 0;
		MessageItem msgItem = new MessageItem();
		msgItem.setMessageId(c.getLong(index++));
		msgItem.setIMProviderType(IMProvider.IMProviderTypes.values()[c
				.getInt(index++)]);
		msgItem.setMessageStatus(MessageItem.MessageStatuses.values()[c
				.getInt(index++)]);
		msgItem.setIncoming(c.getInt(index++) == 1);
		msgItem.setInternalAddress(c.isNull(index) ? null : c.getString(index));
		index++;
		msgItem.setExternalAddress(c.getString(index++));
		msgItem.setCreationDateTime(c.isNull(index) ? -1 : c.getLong(index));
		index++;

		msgItem.setLastSendAttemptDateTime(c.isNull(index) ? -1 : c
				.getLong(index));
		index++;

		msgItem.setCompletionDateTime(c.isNull(index) ? -1 : c.getLong(index));
		index++;

		msgItem.setText(c.getString(index++));

		msgItem.setExtraData(c.isNull(index) ? null : c.getBlob(index));
		index++;

		msgItem.setExtraDataType(c.isNull(index) ? ExtraDataTypes.None
				: ExtraDataTypes.values()[c.getInt(index)]);
		index++;

		msgItem.setImportMessageId(c.isNull(index) ? null : c.getString(index));
		index++;

		msgItem.setImportConversationId(c.isNull(index) ? null : c
				.getString(index));
		index++;

		msgItem.setRead(c.getInt(index++) == 1);

		return msgItem;
	}

	public MessageItem getMessageItem(long messageId) {
		Cursor c = db.query(DATABASE_TABLE, allFields, "MessageID=?",
				new String[] { String.valueOf(messageId) }, null, null, null);
		if (c == null)
			return null;
		c.moveToNext();
		MessageItem msgItem = parseMessageItemCursor(c);
		c.close();
		return msgItem;

	}

	public boolean updateMessageStatus(long messageId,
			MessageItem.MessageStatuses messageStatus) {
		ContentValues args = new ContentValues();
		args.put("MessageStatusID", messageStatus.ordinal());
		return db.update(DATABASE_TABLE, args, "MessageID=?",
				new String[] { String.valueOf(messageId) }) > 0;
	}

	public boolean updateCompletionDateTime(long messageId,
			long completionDateTime) {
		ContentValues args = new ContentValues();
		if (completionDateTime == -1)
			args.putNull("CompletionDateTime");
		else
			args.put("CompletionDateTime", completionDateTime);
		return db.update(DATABASE_TABLE, args, "MessageID=?",
				new String[] { String.valueOf(messageId) }) > 0;

	}

	private boolean updateLastSendAttemptDateTime(long messageId,
			long lastSendAttemptDateTime) {
		ContentValues args = new ContentValues();
		if (lastSendAttemptDateTime == -1)
			args.putNull("LastSendAttemptDateTime");
		else
			args.put("LastSendAttemptDateTime", lastSendAttemptDateTime);
		return db.update(DATABASE_TABLE, args, "MessageID=?",
				new String[] { String.valueOf(messageId) }) > 0;

	}

	public boolean markContactRead(ContactItem contactItem) {

		ContactAddress[] ca = contactItem.getContactAddresses();
		int count = ca.length;
		if (count == 0)
			return false;
		String whereClause = "IsRead = 0 AND (";
		String[] whereArgs = new String[count * 2];
		for (int i = 0; i < count; i++) {
			whereClause += "(ProviderID=? AND ExternalAddress=?)";
			if (i != count - 1)
				whereClause += " OR ";
			whereArgs[i * 2] = String.valueOf(ca[i].getIMProviderType()
					.ordinal());
			whereArgs[i * 2 + 1] = ca[i].getParsedAddress();
		}
		whereClause += ")";
		ContentValues values = new ContentValues();
		values.put("IsRead", true);
		return db.update(DATABASE_TABLE, values, whereClause, whereArgs) > 0;

	}

	private boolean updateMessageIsRead(long messageId, boolean isRead) {
		ContentValues args = new ContentValues();
		args.put("IsRead", isRead);
		return db.update(DATABASE_TABLE, args, "MessageID=?",
				new String[] { String.valueOf(messageId) }) > 0;

	}

	public List<MessageItem> getConversation(ContactItem contactItem,
			boolean unreadOnly, boolean excludeDeleted) {
		if (contactItem == null)
			return null;
		String selection = null;
		ContactAddress[] ca = contactItem.getContactAddresses();
		int count = ca.length;
		if (count == 0)
			return null;

		if (unreadOnly && excludeDeleted)
			selection = "(MessageStatusID != 5 AND IsRead = 0) AND (";
		else if (unreadOnly)
			selection = "IsRead = 0 AND (";
		else if (excludeDeleted)
			selection = "MessageStatusID != 5 AND (";
		else
			selection = "(";

		String[] selectionArgs = new String[count * 2];
		for (int i = 0; i < count; i++) {
			selection += "(ProviderID=? AND ExternalAddress=?)";
			if (i != count - 1)
				selection += " OR ";
			selectionArgs[i * 2] = String.valueOf(ca[i].getIMProviderType()
					.ordinal());
			selectionArgs[i * 2 + 1] = ca[i].getParsedAddress();
		}
		selection += ")";
		Cursor c = db.query(DATABASE_TABLE, allFields, selection,
				selectionArgs, null, null, "CreationDateTime", null);
		if (c == null)
			return new ArrayList<MessageItem>();
		List<MessageItem> conversation = new ArrayList<MessageItem>(
				c.getCount());
		while (c.moveToNext())
			conversation.add(DBMessagesAdapter.parseMessageItemCursor(c));
		c.close();
		return conversation;

	}

	public MessageItem[] getConversationRaw(ContactItem contactItem,
			boolean unreadOnly, boolean excludeDeleted) {
		if (contactItem == null)
			return null;

		List<String> rawQueries = new ArrayList<String>();

		ContactAddress[] ca = contactItem.getContactAddresses();
		int count = ca.length;
		String[] selectionArgs = new String[count * 2];
		for (int i = 0; i < count; i++) {
			String rawQuery = ""
					+ "SELECT "
					+ "  MessageID, "
					+ "  ProviderID, "
					+ "  MessageStatusID, "
					+ "  IsIncoming, "
					+ "  InternalAddress, "
					+ "  ExternalAddress, "
					+ "  CreationDateTime, "
					+ "  LastSendAttemptDateTime, "
					+ "  CompletionDateTime, "
					+ "  MessageText, "
					+ "  ExtraData, "
					+ "  ExtraDataTypeID, "
					+ "  ImportMessageID, "
					+ "  ImportConversationID, "
					+ "  IsRead "
					+ "FROM "
					+ "  Messages INDEXED BY IX_ProviderID_ExternalAddress "
					+ "WHERE "
					+ "  (ProviderID=? AND ExternalAddress=?)                                     ";
			rawQueries.add(rawQuery);
			selectionArgs[i * 2] = String.valueOf(ca[i].getIMProviderType()
					.ordinal());
			selectionArgs[i * 2 + 1] = ca[i].getParsedAddress();

		}
		String query = "SELECT * FROM (";
		for (int i = 0; i < rawQueries.size(); i++) {
			if (i != 0)
				query += " UNION ";
			query += rawQueries.get(i);

		}
		query += ") ";
		if (unreadOnly)
			query += "WHERE IsRead = 0 ";
		else if (excludeDeleted)
			query += "WHERE MessageStatusID != 5";
		query += " ORDER BY CreationDateTime ASC";
		Cursor c = db.rawQuery(query, selectionArgs);
		if (c == null)
			return new MessageItem[0];
		MessageItem[] conversation = new MessageItem[c.getCount()];
		while (c.moveToNext())
			conversation[c.getPosition()] = DBMessagesAdapter
					.parseMessageItemCursor(c);
		c.close();
		return conversation;

	}

	public List<ContactItem> getContactsList() {

		String rawQuery = ""
				+ "SELECT "
				+ "  M2.MessageID, "
				+ "  M2.ProviderID, "
				+ "  M2.MessageStatusID, "
				+ "  M2.IsIncoming, "
				+ "  M2.InternalAddress, "
				+ "  M2.ExternalAddress, "
				+ "  M2.CreationDateTime, "
				+ "  M2.LastSendAttemptDateTime, "
				+ "  M2.CompletionDateTime, "
				+ "  M2.MessageText, "
				+ "  M2.ExtraData, "
				+ "  M2.ExtraDataTypeID, "
				+ "  M2.ImportMessageID, "
				+ "  M2.ImportConversationID, "
				+ "  M2.IsRead, "
				+ "  LastMessageData.ProviderID, "
				+ "  LastMessageData.ExternalAddress, "
				+ "  LastMessageData.UnreadCount "
				+ "FROM "
				+ "  ( "
				+ "    SELECT "
				+ "      M1.ProviderID, "
				+ "      M1.ExternalAddress, "
				+ "      COUNT(*) - SUM(IsRead) [UnreadCount], "
				+ "      MAX(CreationDateTime) [LastMessageTime] "
				+ "    FROM "
				+ "      Messages M1 "
				+ "    WHERE "
				+ "    	 M1.MessageStatusID != 5 "
				+ "    GROUP BY "
				+ "      M1.ProviderID, "
				+ "      M1.ExternalAddress "
				+ "  ) AS LastMessageData "
				+ "  LEFT JOIN Messages M2 ON  "
				+ "    M2.CreationDateTime = LastMessageData.LastMessageTime "
				+ "    AND M2.ExternalAddress = LastMessageData.ExternalAddress "
				+ "    AND M2.ProviderID = LastMessageData.ProviderID "
				+ "WHERE " + "  M2.MessageStatusID != 5 "
				+ "ORDER BY                   "
				+ "  LastMessageData.ProviderID, "
				+ "  LastMessageData.ExternalAddress,                "
				+ "  M2.MessageID";

		List<ContactItem> contactList = new ArrayList<ContactItem>();
		Cursor cursor = null;
		try {
			cursor = db.rawQuery(rawQuery, null);
		} catch (SQLiteException e) {
			// fails with blank database on old APIs
			if (cursor != null)
				cursor.close();
			e.printStackTrace();
			return contactList;
		}

		if (cursor != null) {
			while (cursor.moveToNext()) {
				ContactItem c = new ContactItem();
				MessageItem m = parseMessageItemCursor(cursor);
				int index = allFields.length;
				int providerId = cursor.getInt(index++);
				String address = cursor.getString(index++);
				int unreadCount = cursor.getInt(index++);
				c.setDisplayName(address);
				c.setDisplayContactAddress(address);
				ContactAddress ca = new ContactAddress();
				ca.setDisplayAddress(address);
				ca.setParsedAddress(address);
				ca.setIMProviderType(IMProviderTypes.values()[providerId]);
				c.setContactAddresses(new ContactAddress[] { ca });
				c.setLastMessageItem(m);
				c.setUnreadCount(unreadCount);
				// check for extremely rare chance of exact same time
				int count = contactList.size();
				boolean found = false;
				for (int i = 0; i < count; i++) {
					ContactItem c2 = contactList.get(i);
					if (c2.getContactAddresses()[0].equals(ca)) {
						found = true;
						break;
					}
				}
				if (!found) {
					contactList.add(c);
				}

			}
		}
		cursor.close();

		return contactList;

	}
}
