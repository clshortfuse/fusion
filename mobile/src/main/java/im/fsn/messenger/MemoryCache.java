package im.fsn.messenger;

import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class MemoryCache {

	public static List<ContactItem> QuickReplyContactsList = new ArrayList<ContactItem>();
	public static List<ContactItem> MainUIContactsList = new ArrayList<ContactItem>();
	public static List<Conversation> CachedConversations = new ArrayList<Conversation>();
	public static List<ContactItem> ContactsList = new ArrayList<ContactItem>();
	public static HashMap<String, MMSObject> MMSCache = new HashMap<String, MMSObject>();
	public static IMProvider[] IMProviders;

	public synchronized static MMSObject getMMSCache(String MessageId) {
		return MMSCache.get(MessageId);

	}

	public synchronized static void putMMSCache(String MessageId,
			MMSObject mmsObject) {
		MMSCache.put(MessageId, mmsObject);
	}

	private static Object syncObject = new Object();

	public static synchronized List<MessageItem> GetMessages(
			ContactItem contactItem) {

		for (Conversation c : CachedConversations) {
			if (c.getContactItem().equals(contactItem))
				return c.getMessages();
		}

		return null;
	}

	public static synchronized boolean RemoveMessage(MessageItem messageItem) {
		long messageId = messageItem.getMessageId();
		if (messageId == -1)
			return false;
		int size = CachedConversations.size();

		for (int i = 0; i < size; i++) {
			Conversation rc = CachedConversations.get(i);
			if (rc.getContactItem().isMessageItemIsValid(messageItem)) {
				List<MessageItem> messages = rc.getMessages();
				if (messages == null)
					return false;
				int messageCount = messages.size();
				for (int j = messageCount - 1; j >= 0; j--) {
					MessageItem currentMessageItem = messages.get(j);
					if (currentMessageItem.getMessageId() == messageId) {
						messages.remove(j);
						rc.setMessages(messages);
						CachedConversations.set(i, rc);
						return true;
					}
				}
				return false;
			}
		}
		return false;
	}

	public static synchronized ContactItem getContact(
			IMProviderTypes imProviderType, String externalAddress) {

		ContactItem ci = new ContactItem();
		ContactAddress ca = new ContactAddress();
		ca.setDisplayAddress(externalAddress);
		ca.setParsedAddress(externalAddress);
		ca.setIMProviderType(imProviderType);
		ci.setContactAddresses(new ContactAddress[] { ca });
		ci.setDisplayContactAddress(externalAddress);
		ci.setDisplayName(externalAddress);
		int size = ContactsList.size();
		for (int i = 0; i < size; i++) {
			ContactItem c = ContactsList.get(i);
			for (ContactAddress ca2 : c.getContactAddresses())
				if (ca.equals(ca2))
					return c;
		}
		return ci;

	}

	public static synchronized boolean UpdateMessage(MessageItem messageItem) {
		long messageId = messageItem.getMessageId();
		if (messageId == -1)
			return false;
		int size = CachedConversations.size();
		boolean found = false;
		for (int i = 0; i < size; i++) {
			Conversation rc = CachedConversations.get(i);
			if (rc.getContactItem().isMessageItemIsValid(messageItem)) {
				List<MessageItem> messages = rc.getMessages();
				if (messages == null)
					break;
				int messageCount = messages.size();
				for (int j = messageCount - 1; j >= 0; j--) {
					MessageItem currentMessageItem = messages.get(j);
					if (currentMessageItem.getMessageId() == messageId) {
						messages.set(j, messageItem);
						if (currentMessageItem.getCreationDateTime() != messageItem
								.getCreationDateTime())
							Collections.sort(messages);
						rc.setMessages(messages);
						CachedConversations.set(i, rc);
						found = true;
						break;
					}
				}
				if (found)
					break;
				messages.add(messageItem);
				Collections.sort(messages);
				rc.setMessages(messages);
				CachedConversations.set(i, rc);
				found = true;
				;
			}
		}
		UpdateLastMessage(messageItem);
		return found;
	}

	public static synchronized boolean UpdateLastMessage(MessageItem msgItem) {
		int count = ContactsList.size();
		for (int i = 0; i < count; i++) {
			ContactItem c = ContactsList.get(i);
			if (c.isMessageItemIsValid(msgItem)) {
				if (c.getLastMessageItem() != null
						&& c.getLastMessageItem().getCreationDateTime() > msgItem
								.getCreationDateTime())
					return false;
				c.setLastMessageItem(msgItem);
				ContactsList.set(i, c);
				return true;
			}
		}
		return false;
	}

	public static synchronized boolean MarkContactAsRead(ContactItem contactItem) {
		int index = ContactsList.indexOf(contactItem);
		if (index != -1) {
			ContactItem c = ContactsList.get(index);
			MessageItem newMessageItem = c.getLastMessageItem();
			if (newMessageItem == null)
				return false;
			if (newMessageItem.isRead())
				return false;
			newMessageItem.setRead(true);
			c.setLastMessageItem(newMessageItem);
			c.setUnreadCount(0);
			ContactsList.set(index, c);
			return true;
		}
		return false;
	}

	public static synchronized void UpdateConversation(Conversation conversation) {

		int size = CachedConversations.size();

		for (int i = 0; i < size; i++) {
			Conversation rc = CachedConversations.get(i);
			if (rc.getContactItem().equals(conversation.getContactItem())) {
				rc.setDirty(false);
				rc.setMessages(conversation.getMessages());
				CachedConversations.set(i, rc);
				return;
			}
		}
		conversation.setDirty(false);
		CachedConversations.add(conversation);

	}

	public static synchronized boolean RequiresRefresh(ContactItem contactItem) {

		for (Conversation c : CachedConversations) {
			if (c.equals(contactItem))
				return c.isDirty();
		}

		return true;
	}

	public static synchronized void UpdateContactItem(ContactItem contactItem) {
		int index = ContactsList.indexOf(contactItem);

		if (index != -1) {
			ContactsList.set(index, contactItem);
		}

		index = MainUIContactsList.indexOf(contactItem);
		if (index != -1)
			MainUIContactsList.set(index, contactItem);

		index = QuickReplyContactsList.indexOf(contactItem);
		if (index != -1)
			QuickReplyContactsList.set(index, contactItem);

		int size = CachedConversations.size();

		for (int i = 0; i < size; i++) {
			Conversation rc = CachedConversations.get(i);
			if (rc.getContactItem().equals(contactItem)) {
				rc.setContactItem(contactItem);
				CachedConversations.set(i, rc);
				return;
			}
		}

	}
}
