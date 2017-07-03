package im.fsn.messenger;

import java.util.List;

public class Conversation {

	public Conversation(ContactItem contactItem, List<MessageItem> messages) {
		this.contactItem = contactItem;
		this.messages = messages;
		this.dirty = true;
	}

	private List<MessageItem> messages;
	private ContactItem contactItem;
	private boolean dirty;

	public List<MessageItem> getMessages() {
		return messages;
	}

	public void setMessages(List<MessageItem> messages) {
		this.messages = messages;
	}

	public ContactItem getContactItem() {
		return contactItem;
	}

	public void setContactItem(ContactItem contactItem) {
		this.contactItem = contactItem;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

}
