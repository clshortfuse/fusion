package im.fsn.messenger;

import im.fsn.messenger.DBMessagesAdapter.InsertOrUpdateResult;
import im.fsn.messenger.MessageItem.ExtraDataTypes;
import im.fsn.messenger.MessageItem.MessageStatuses;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;

import java.util.ArrayList;
import java.util.List;

import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

public class DBRequestHandlerThread extends Thread {

	private final Object syncObject = new Object();
	private List<MessageItem> messageQueue;
	private boolean isBusy = false;
	private List<ContactItem> markContactReadRequestQueue;
	private List<MessageItem> markMessageReadRequestQueue;
	private List<ContactItem> deleteConversationRequestQueue;
	private List<MessageItem[]> batchMessageQueue;
	private List<ContactItem> conversationRequestQueue;
	private boolean dbContactListRefreshRequested = true;
	private DBMessagesAdapter dbMessages;
	private IMProvider[] imServices;
	private Messenger callbackMessenger;

	public void setCallbackMessenger(Messenger callbackMessenger) {
		this.callbackMessenger = callbackMessenger;
	}

	public void setIMProviders(IMProvider[] imProviders) {
		this.imServices = imProviders;
	}

	public DBRequestHandlerThread(DBMessagesAdapter dbMessages,
			IMProvider[] imServices, Messenger callbackMessenger) {
		this.messageQueue = new ArrayList<MessageItem>();
		this.markContactReadRequestQueue = new ArrayList<ContactItem>();
		this.markMessageReadRequestQueue = new ArrayList<MessageItem>();
		this.batchMessageQueue = new ArrayList<MessageItem[]>();
		this.conversationRequestQueue = new ArrayList<ContactItem>();
		this.deleteConversationRequestQueue = new ArrayList<ContactItem>();
		this.dbMessages = dbMessages;
		this.imServices = imServices;
		this.callbackMessenger = callbackMessenger;
		this.setPriority(MIN_PRIORITY);
	}

	public void forceResume() {
		synchronized (syncObject) {
			syncObject.notifyAll();
		}
	}

	public void queueDeleteConversation(ContactItem contactItem) {
		synchronized (syncObject) {
			this.deleteConversationRequestQueue.add(contactItem);
			syncObject.notifyAll();
		}
	}

	public boolean queueDBContactListRefresh() {

		synchronized (syncObject) {
			this.dbContactListRefreshRequested = true;
			syncObject.notifyAll();
		}
		return true;
	}

	public boolean queueMessageItem(MessageItem msgItem) {

		synchronized (syncObject) {
			messageQueue.add(msgItem);
			syncObject.notifyAll();
		}
		return true;
	}

	public boolean queueMessageBatch(MessageItem[] msgItems) {

		synchronized (syncObject) {
			batchMessageQueue.add(msgItems);
			syncObject.notifyAll();
		}
		return true;
	}

	public boolean queueMarkContactAsRead(ContactItem contactItem) {

		synchronized (syncObject) {
			markContactReadRequestQueue.add(contactItem);
			syncObject.notifyAll();
		}
		return true;
	}

	public boolean queueMarkMessageAsRead(MessageItem messageItem) {

		synchronized (syncObject) {
			markMessageReadRequestQueue.add(messageItem);
			syncObject.notifyAll();
		}
		return true;
	}

	public boolean queueConversationRequestItem(ContactItem contactItem) {

		synchronized (syncObject) {
			conversationRequestQueue.add(contactItem);
			syncObject.notifyAll();
		}
		return true;
	}

	private void processConversationRequestQueue() {
		ContactItem[] tmpQueue = null;
		synchronized (syncObject) {
			tmpQueue = new ContactItem[conversationRequestQueue.size()];
			conversationRequestQueue.toArray(tmpQueue);
			// doublecast tmpQueue = (ContactItem[]) (Object[])
			// conversationRequestQueue.toArray();
		}
		for (ContactItem contactItem : tmpQueue) {
			if (contactItem == null)
				continue;
			long startTime = SystemClock.elapsedRealtime();
			Conversation conversation = new Conversation(contactItem,
					dbMessages.getConversation(contactItem, false, true));
			long endTime = SystemClock.elapsedRealtime();
			Log.d(DBRequestHandlerThread.class.getSimpleName(),
					"processConversationRequestQueue: " + (endTime - startTime)
							+ "ms");
			MemoryCache.UpdateConversation(conversation);
			Message msg = Message.obtain(null,
					HandlerMessages.MSG_CONVERSATIONUPDATED, contactItem);
			try {
				this.callbackMessenger.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			synchronized (conversationRequestQueue) {
				conversationRequestQueue.remove(contactItem); //
			}
		}

	}

	private void processMarkContactAsReadQueue() {
		ContactItem[] tmpQueue = null;
		synchronized (syncObject) {
			tmpQueue = new ContactItem[markContactReadRequestQueue.size()];
			markContactReadRequestQueue.toArray(tmpQueue);
		}

		for (ContactItem contactItem : tmpQueue) {
			long startTime = SystemClock.elapsedRealtime();
			List<MessageItem> unreadMessages = dbMessages.getConversation(
					contactItem, true, false);
			long endTime = SystemClock.elapsedRealtime();
			Log.d(DBRequestHandlerThread.class.getSimpleName(), "getUnread: "
					+ (endTime - startTime) + "ms");

			startTime = SystemClock.elapsedRealtime();
			List<IMProviderTypes> providerTypes = new ArrayList<IMProviderTypes>();
			for (MessageItem unreadMessage : unreadMessages) {
				IMProviderTypes imProviderType = unreadMessage
						.getIMProviderType();
				if (!providerTypes.contains(imProviderType)) {
					providerTypes.add(imProviderType);
				}
			}
			for (IMProviderTypes imProviderType : providerTypes) {
				IMProvider s = IMProvider.findIMProvider(imProviderType,
						this.imServices);

				s.markAsRead(unreadMessages
						.toArray(new MessageItem[unreadMessages.size()]));
			}
			endTime = SystemClock.elapsedRealtime();
			Log.d(DBRequestHandlerThread.class.getSimpleName(),
					"ServiceUnread: " + (endTime - startTime) + "ms");

			startTime = SystemClock.elapsedRealtime();
			if (unreadMessages.size() > 0)
				dbMessages.markContactRead(contactItem);
			endTime = SystemClock.elapsedRealtime();
			Log.d(DBRequestHandlerThread.class.getSimpleName(), "WriteUnread: "
					+ (endTime - startTime) + "ms");

			MemoryCache.MarkContactAsRead(contactItem);
			try {
				callbackMessenger.send(Message.obtain(null,
						HandlerMessages.MSG_NOTIFYCONTACTASREAD, contactItem));
			} catch (RemoteException e) {

				e.printStackTrace();
			}
			synchronized (syncObject) {
				markContactReadRequestQueue.remove(contactItem);
			}
		}
		Log.d(DBRequestHandlerThread.class.getSimpleName(),
				"processMarkContactAsReadQueue");
	}

	private void processMarkMessageAsReadQueue() {
		MessageItem[] tmpQueue = null;
		synchronized (syncObject) {
			tmpQueue = new MessageItem[markMessageReadRequestQueue.size()];
			markMessageReadRequestQueue.toArray(tmpQueue);
		}

		for (MessageItem messageItem : tmpQueue) {
			messageItem.setRead(true);

			IMProvider s = IMProvider.findIMProvider(
					messageItem.getIMProviderType(), this.imServices);

			s.markAsRead(messageItem);
			dbMessages.insertOrUpdateMessage(messageItem);

			MemoryCache.UpdateMessage(messageItem);
			try {
				callbackMessenger.send(Message.obtain(null,
						HandlerMessages.MSG_MESSAGECHANGED, messageItem));
			} catch (RemoteException e) {

				e.printStackTrace();
			}
			synchronized (syncObject) {
				markMessageReadRequestQueue.remove(messageItem);
			}
		}
		Log.d(DBRequestHandlerThread.class.getSimpleName(),
				"processMarkMessageAsReadQueue");
	}

	private void processMessageQueue() {
		MessageItem[] tmpQueue = null;
		synchronized (syncObject) {
			tmpQueue = new MessageItem[messageQueue.size()];
			messageQueue.toArray(tmpQueue);
		}
		List<IMProvider> busyIMProviders = new ArrayList<IMProvider>();

		for (MessageItem msgItem : tmpQueue) {
			boolean removeItem = true;
			long messageId = -1;
			IMProvider s = null;
			switch (msgItem.getMessageStatus()) {
			case Deleted:
				s = IMProvider.findIMProvider(msgItem.getIMProviderType(),
						this.imServices);
				if (s.delete(msgItem))
					this.dbMessages.deleteMessage(msgItem.getMessageId());
				else
					this.dbMessages.insertOrUpdateMessage(msgItem);
				MemoryCache.RemoveMessage(msgItem);
				try {
					callbackMessenger.send(Message.obtain(null,
							HandlerMessages.MSG_MESSAGEDELETED, msgItem));
				} catch (RemoteException e) {

					e.printStackTrace();
				}
				break;
			case Failed:
				s = IMProvider.findIMProvider(msgItem.getIMProviderType(),
						this.imServices);
				msgItem.setInternalAddress(s.parseAddress(msgItem
						.getInternalAddress()));
				msgItem.setExternalAddress(s.parseAddress(msgItem
						.getExternalAddress()));

				this.dbMessages.insertOrUpdateMessage(msgItem);
				MemoryCache.UpdateMessage(msgItem);
				try {
					callbackMessenger.send(Message.obtain(null,
							HandlerMessages.MSG_MESSAGEFAILED, msgItem));
				} catch (RemoteException e) {

					e.printStackTrace();
				}

				removeItem = true;
				break;
			case Delivered:
			case Sent:
				s = IMProvider.findIMProvider(msgItem.getIMProviderType(),
						this.imServices);
				msgItem.setInternalAddress(s.parseAddress(msgItem
						.getInternalAddress()));
				msgItem.setExternalAddress(s.parseAddress(msgItem
						.getExternalAddress()));

				InsertOrUpdateResult res = this.dbMessages
						.insertOrUpdateMessage(msgItem);
				msgItem.setMessageId(res.getMessageId());
				MemoryCache.UpdateMessage(msgItem);
				try {
					callbackMessenger.send(Message.obtain(null, (msgItem
							.isIncoming() ? HandlerMessages.MSG_MESSAGERECEIVED
							: HandlerMessages.MSG_MESSAGESENT), msgItem));
				} catch (RemoteException e) {

					e.printStackTrace();
				}
				s.disableSyncLock();
				removeItem = true;
				break;
			case Queued:
				s = IMProvider.findIMProvider(msgItem.getIMProviderType(),
						this.imServices);
				boolean continueRequired = false;
				if (busyIMProviders.contains(s)) {
					removeItem = false;
					continueRequired = true;
				}
				if (s.isBusy()) {
					busyIMProviders.add(s);
					removeItem = false;
					continueRequired = true;
				}

				msgItem.setInternalAddress(s.parseAddress(msgItem
						.getInternalAddress()));
				msgItem.setExternalAddress(s.parseAddress(msgItem
						.getExternalAddress()));

				messageId = msgItem.getMessageId();
				if (messageId == -1) {
					InsertOrUpdateResult result = this.dbMessages
							.insertOrUpdateMessage(msgItem);
					messageId = result.getMessageId();
					msgItem.setMessageId(messageId);
					MemoryCache.UpdateMessage(msgItem);
					try {
						callbackMessenger.send(Message.obtain(null,
								HandlerMessages.MSG_MESSAGEQUEUED, msgItem));
					} catch (RemoteException e) {

						e.printStackTrace();
					}
				}

				if (continueRequired) {
					continue;
				}

				msgItem = s.sendMessage(msgItem);
				MessageStatuses msgStatus = msgItem.getMessageStatus();
				int handleMessage;
				switch (msgStatus) {
				case OnRoute:
					handleMessage = HandlerMessages.MSG_MESSAGEONROUTE;
					break;
				case Sent:
					handleMessage = HandlerMessages.MSG_MESSAGESENT;
					msgItem.setCompletionDateTime(System.currentTimeMillis());
					break;
				default:
				case Failed:
					handleMessage = HandlerMessages.MSG_MESSAGEFAILED;
					break;
				}

				this.dbMessages.insertOrUpdateMessage(msgItem);

				MemoryCache.UpdateMessage(msgItem);
				try {
					callbackMessenger.send(Message.obtain(null, handleMessage,
							msgItem));
				} catch (RemoteException e) {

					e.printStackTrace();
				}
				break;
			default:
				break;
			}
			if (removeItem)
				synchronized (syncObject) {
					messageQueue.remove(msgItem); //
				}
		}
		Log.d(DBRequestHandlerThread.class.getSimpleName(),
				"processMessageQueue");

	}

	private void processBatchMessageQueue() {

		MessageItem[] conversation = null;
		synchronized (syncObject) {
			if (batchMessageQueue.size() != 0)
				conversation = batchMessageQueue.get(0);
		}
		if (conversation == null || conversation.length == 0)
			return;
		ContactItem c = new ContactItem();
		ContactAddress ca = new ContactAddress();
		ca.setIMProviderType(conversation[0].getIMProviderType());
		ca.setParsedAddress(conversation[0].getExternalAddress());
		ca.setDisplayAddress(conversation[0].getExternalAddress());
		c.setContactAddresses(new ContactAddress[] { ca });
		List<MessageItem> dbConversation = dbMessages.getConversation(c, false,
				true);

		MessageItem notifyMessageItem = null;
		List<MessageItem> validMessages = new ArrayList<MessageItem>();
		for (int mbrI = 0; mbrI < conversation.length; mbrI++) {

			MessageItem msgItem = conversation[mbrI];

			boolean found = false;
			for (MessageItem conv : dbConversation) {
				if (conv.dataMatches(msgItem)) {
					if (conv.getMessageStatus() != msgItem.getMessageStatus())
						found = (conv.getMessageStatus() == MessageStatuses.Deleted);
					else
						found = true;
					break;
				}
			}

			if (found)
				continue;
			if (msgItem.getText() == null
					&& msgItem.getExtraDataType() == ExtraDataTypes.None)
				continue;
			InsertOrUpdateResult result = dbMessages
					.insertOrUpdateMessage(msgItem);
			if (result.isDeleted())
				continue;
			msgItem.setMessageId(result.getMessageId());
			validMessages.add(msgItem);

			if (result.isChanged()) {
				MemoryCache.UpdateMessage(msgItem);
				try {
					callbackMessenger.send(Message.obtain(null,
							HandlerMessages.MSG_MESSAGECHANGED, msgItem));
				} catch (RemoteException e) {

					e.printStackTrace();
				}
			}
			if (notifyMessageItem == null && result.getInsertCount() != 0
					&& !msgItem.isRead() && msgItem.isIncoming()) {
				notifyMessageItem = msgItem;
			}
		}
		if (notifyMessageItem != null) {
			try {
				MemoryCache.UpdateMessage(notifyMessageItem);
				callbackMessenger
						.send(Message.obtain(null,
								HandlerMessages.MSG_MESSAGERECEIVED,
								notifyMessageItem));
			} catch (RemoteException e) {

				e.printStackTrace();
			}
		}
		if (validMessages.size() != 0) {
			MessageItem[] messageArray = new MessageItem[validMessages.size()];
			validMessages.toArray(messageArray);

			try {
				callbackMessenger
						.send(Message.obtain(null,
								HandlerMessages.MSG_MESSAGEBATCHRECEIVED,
								messageArray));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		synchronized (syncObject) {
			batchMessageQueue.remove(conversation); //
		}

		Log.d(DBRequestHandlerThread.class.getSimpleName(),
				"processBatchMessageQueue");
	}

	@Override
	public void run() {
		long startTime = SystemClock.elapsedRealtime();
		this.setName("IMProvidersHandlerThread-" + startTime);
		dbMessages.open();
		while (!Thread.interrupted()) {
			isBusy = true;

			if (dbContactListRefreshRequested)
				getDBContactsList();

			if (messageQueue.size() != 0)
				processMessageQueue();

			if (conversationRequestQueue.size() != 0)
				processConversationRequestQueue();

			if (batchMessageQueue.size() != 0)
				processBatchMessageQueue();

			if (markContactReadRequestQueue.size() != 0)
				processMarkContactAsReadQueue();

			if (markMessageReadRequestQueue.size() != 0)
				processMarkMessageAsReadQueue();

			if (this.deleteConversationRequestQueue.size() != 0)
				processDeleteConversationRequestQueue();

			isBusy = false;
			synchronized (syncObject) {
				if (conversationRequestQueue.size() == 0
						&& messageQueue.size() == 0
						&& batchMessageQueue.size() == 0
						&& markContactReadRequestQueue.size() == 0
						&& markMessageReadRequestQueue.size() == 0
						&& deleteConversationRequestQueue.size() == 0
						&& !dbContactListRefreshRequested)

					try {
						syncObject.wait(5000);
					} catch (InterruptedException e) {
						break;
					}
			}
		}
		dbMessages.close();
	}

	private void processDeleteConversationRequestQueue() {
		ContactItem[] tmpQueue = null;
		synchronized (syncObject) {
			tmpQueue = new ContactItem[this.deleteConversationRequestQueue
					.size()];
			deleteConversationRequestQueue.toArray(tmpQueue);

		}
		for (ContactItem contactItem : tmpQueue) {
			if (contactItem == null)
				continue;
			List<MessageItem> messages = dbMessages.getConversation(
					contactItem, false, true);

			for (int i = 0; i < this.imServices.length; i++) {
				int len = messages.size();
				List<MessageItem> serviceMessages = new ArrayList<MessageItem>();
				for (int j = len - 1; j >= 0; j--) {

					if (messages.get(j).getIMProviderType() == imServices[i]
							.getIMProviderType()) {
						serviceMessages.add(0, messages.get(j));

						this.dbMessages.deleteMessage(messages.get(j)
								.getMessageId());
						messages.remove(j);
					}
				}
				imServices[i].delete(serviceMessages
						.toArray(new MessageItem[serviceMessages.size()]));
			}

			this.dbContactListRefreshRequested = true;
			synchronized (deleteConversationRequestQueue) {
				deleteConversationRequestQueue.remove(contactItem); //
			}
		}

	}

	private void getDBContactsList() {
		dbContactListRefreshRequested = false;
		List<ContactItem> dbContacts = dbMessages.getContactsList();
		Message msg = Message.obtain(null,
				HandlerMessages.MSG_DBCONTACTSRECEIVED, dbContacts);
		try {
			this.callbackMessenger.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		Log.d(DBRequestHandlerThread.class.getSimpleName(), "getDBContactsList");
	}

}