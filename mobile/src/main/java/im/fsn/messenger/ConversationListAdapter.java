package im.fsn.messenger;

import im.fsn.messenger.MessageItem.ExtraDataTypes;
import im.fsn.messenger.MessageItem.MessageStatuses;
import im.fsn.messenger.apisupport.V12;
import im.fsn.messenger.apisupport.V14;
import im.fsn.messenger.mms.HttpUtils;
import im.fsn.messenger.mms.PduParser;
import im.fsn.messenger.mms.RetrieveConf;
import im.fsn.messenger.mms.SqliteWrapper;
import im.fsn.messenger.mms.Telephony.Mms;
import im.fsn.messenger.mms.TransactionSettings;
import im.fsn.messenger.mms.GenericPdu;
import im.fsn.messenger.mms.MultimediaMessagePdu;
import im.fsn.messenger.mms.PduBody;
import im.fsn.messenger.mms.PduPersister;
import im.fsn.messenger.providers.IMProvider;
import im.fsn.messenger.providers.IMProvider.IMProviderTypes;
import im.fsn.messenger.ui.SlideDownAnimation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.text.DateFormat;
import java.text.DateFormat.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import im.fsn.messenger.R;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationListAdapter extends BaseAdapter implements Filterable {

	private Context mContext;
	private LayoutInflater mInflater;
	private SharedPreferences mPrefs;
	private ArrayList<MessageItem> filtered;
	private ContactItem mContactItem;
	private Bitmap mProfilePhoto = null;
	private long mProfileId = 0;
	private ItemsFilter filter;
	private CharSequence lastQuery;
	private Drawable defaultAvatarLight;
	private Drawable defaultAvatarDark;
	private Bitmap userAvatar;

	private int mIMServiceIndicatorWidth = 4;
	private boolean useIMServiceIcon = false;
	private boolean useContactPicture = true;
	private boolean useColoredStatus;
	private int messageTextColor;
	private int messageStatusColor;

	public ConversationListAdapter(Context context, ContactItem contactItem) {
		super();
		this.mContext = context;
		this.mInflater = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.mContactItem = contactItem;
		this.filtered = new ArrayList<MessageItem>();
		List<MessageItem> newMessages = MemoryCache.GetMessages(contactItem);
		if (newMessages != null) {
			for (MessageItem m : newMessages)
				this.filtered.add(m.clone());
		}

		this.defaultAvatarLight = context.getResources().getDrawable(
				R.drawable.ic_contact_picture_light);
		this.defaultAvatarDark = context.getResources().getDrawable(
				R.drawable.ic_contact_picture_dark);
		this.mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		this.mPrefs
				.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
		if (VERSION.SDK_INT >= 14) {
			Uri contactUri = V14.getProfileContentUri();
			InputStream input = V14.openHiResContactPhotoInputStream(
					mContext.getContentResolver(), contactUri);
			if (input != null) {
				mProfilePhoto = BitmapFactory.decodeStream(input);
				if (mProfilePhoto != null) {
					int contactPixelSize = (int) TypedValue.applyDimension(
							TypedValue.COMPLEX_UNIT_DIP, 64, this.mContext
									.getResources().getDisplayMetrics());

					if (mProfilePhoto.getHeight() > contactPixelSize) {
						mProfilePhoto = Bitmap.createScaledBitmap(
								mProfilePhoto, contactPixelSize,
								contactPixelSize, true);
					}
				}
			}
			Cursor cursor = mContext.getContentResolver().query(contactUri,
					new String[] { Phone._ID }, null, null, null);
			if (cursor != null && cursor.moveToFirst())
				mProfileId = cursor.getLong(0);
			cursor.close();
		}
		refreshPreferences();

	}

	private OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key.equals("pfThemeOption")
					|| key.equals("pfMessageItemIndicator")
					|| key.equals("pfMessageItemIcon")
					|| key.equals("pfMessageItemPicture")
					|| key.equals("pfMessageItemColoredStatus")) {
				refreshPreferences();
				ConversationListAdapter.this.notifyDataSetChanged();
			}
		}
	};

	private void refreshPreferences() {
		this.mIMServiceIndicatorWidth = mPrefs.getInt(
				"pfMessageItemIndicatorWidth", 4);
		this.useIMServiceIcon = mPrefs.getBoolean("pfMessageItemIcon", false);
		this.useContactPicture = mPrefs.getBoolean("pfMessageItemPicture",
				false);
		this.useColoredStatus = mPrefs.getBoolean("pfMessageItemColoredStatus",
				false);

		TypedValue tv = new TypedValue();
		mContext.getTheme().resolveAttribute(android.R.attr.textColorPrimary,
				tv, true);
		messageTextColor = mContext.getResources().getColor(tv.resourceId);
		mContext.getTheme().resolveAttribute(android.R.attr.textColorSecondary,
				tv, true);
		messageStatusColor = mContext.getResources().getColor(tv.resourceId);

	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		MessageItem msgItem = this.getItem(position);
		if (msgItem == null || msgItem.isIncoming())
			return 0;
		return 1;
	}

	private class ViewHolder {
		public View vServiceIndicator;
		public QuickContactBadge qcbAvatar;
		public ImageView ivService;
		public LinearLayout message_block;
		public TextView text_view;
		public TextView date_view;
		public LinearLayout llMediaLayout;
		public String importMessageId;
		public Button btnDownloadMMS;
	}

	private final int msPerDay = 24 * 60 * 60 * 1000; // hours*minutes*seconds*milliseconds
	private final int msPerYear = 365 * 24 * 60 * 60 * 1000; // hours*minutes*seconds*milliseconds

	// DST will probably not work right
	private String getMmsText(String id) {
		Uri partURI = Uri.parse("content://mms/part/" + id);
		InputStream is = null;
		StringBuilder sb = new StringBuilder();
		try {
			is = mContext.getContentResolver().openInputStream(partURI);
			if (is != null) {
				InputStreamReader isr = new InputStreamReader(is, "UTF-8");
				BufferedReader reader = new BufferedReader(isr);
				String temp = reader.readLine();
				while (temp != null) {
					sb.append(temp);
					temp = reader.readLine();
				}
			}
		} catch (IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return sb.toString();
	}

	private static Bitmap getMmsImage(Context mContext, String _id) {
		Uri partURI = Uri.parse("content://mms/part/" + _id);
		InputStream is = null;
		Bitmap bitmap = null;
		try {
			is = mContext.getContentResolver().openInputStream(partURI);
			bitmap = BitmapFactory.decodeStream(is);
		} catch (IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return bitmap;
	}

	private void buildMediaMessage(final MMSObject result,
			LinearLayout llMediaLayout, Button btnDownloadMMS, TextView tvText,
			final MessageItem msgItem) {

		int messageType = result.getMsgType();
		String[] partIds = result.getImagePartIds();
		Bitmap[] previews = result.getImagePreviews();
		Point[] sizes = result.getPreviewSizes();
		if (messageType == 130) {

			if (btnDownloadMMS != null) {
				btnDownloadMMS.setVisibility(View.VISIBLE);

				btnDownloadMMS.setFocusable(false);
				btnDownloadMMS.setFocusableInTouchMode(false);
				btnDownloadMMS.setText("Download MMS (" + result.getMsgSize()
						/ 1024 + "KB)");

				btnDownloadMMS.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						if (mOnDownloadMMSButtonClickListener != null)
							mOnDownloadMMSButtonClickListener.onClick(v,
									result, msgItem);

					}
				});

			}
			if (llMediaLayout != null)
				llMediaLayout.setVisibility(View.GONE);
		} else if (llMediaLayout != null) {

			if (btnDownloadMMS != null)
				btnDownloadMMS.setVisibility(View.GONE);

			if (partIds.length == 0)
				llMediaLayout.setVisibility(View.GONE);
			else
				llMediaLayout.setVisibility(View.VISIBLE);
			if (partIds.length != llMediaLayout.getChildCount())
				llMediaLayout.removeAllViews();

			for (int i = 0; i < partIds.length; i++) {
				final String key = partIds[i];
				ImageButton ib = (ImageButton) llMediaLayout.getChildAt(i);
				if (ib == null) {
					ib = new ImageButton(llMediaLayout.getContext());
					ib.setBackgroundResource(R.drawable.abc_list_divider_holo_dark);
					ib.setPadding(8, 8, 8, 8);
					ib.setScaleType(ScaleType.CENTER);
					ib.setFocusable(false);
					ib.setFocusableInTouchMode(false);
					ib.setLayoutParams(new ViewGroup.LayoutParams(sizes[i].x,
							sizes[i].y));
					llMediaLayout.addView(ib);
				} else {
					BitmapDrawable bd = (BitmapDrawable) ib.getDrawable();
					Bitmap bm = bd.getBitmap();
					/*
					 * if (bm == previews[i]) continue; if (VERSION.SDK_INT >=
					 * 12) { if (V12.sameAs(bm, previews[i])) continue; } if
					 * (bm.equals(previews[i])) continue;
					 * Log.w("ConversationListAdapter", "New bitmap!?");
					 */

				}
				if (previews != null)
					ib.setImageBitmap(previews[i]);
				else
					ib.setImageBitmap(null);
				ib.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(Intent.ACTION_VIEW);
						Uri uri = Uri.parse("content://mms/part");
						Cursor cGetType = v
								.getContext()
								.getContentResolver()
								.query(uri, new String[] { "ct" }, "_id = ?",
										new String[] { key }, null);

						cGetType.moveToFirst();
						String type = cGetType.getString(0);
						cGetType.close();
						String ext = type.split("/")[1];
						InputStream is = null;
						FileOutputStream fo = null;
						try {
							Uri partURI = Uri
									.parse("content://mms/part/" + key);

							fo = v.getContext().openFileOutput(key + "." + ext,
									Context.MODE_WORLD_READABLE);

							is = v.getContext().getContentResolver()
									.openInputStream(partURI);
							byte[] buffer = new byte[1024];
							int len;
							while ((len = is.read(buffer)) != -1) {
								fo.write(buffer, 0, len);
							}

						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (is != null)
								try {
									is.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							if (fo != null)
								try {
									fo.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
						}
						File f = new File(v.getContext().getFilesDir()
								.getAbsolutePath(), key + "." + ext);
						intent.setDataAndType(Uri.fromFile(f), type);
						f.deleteOnExit();
						v.getContext().startActivity(intent);
					}
				});
				ib.setOnLongClickListener(new OnLongClickListener() {

					@Override
					public boolean onLongClick(View v) {
						final Context mContext = v.getContext();
						ActionBarActivity a = (ActionBarActivity) mContext;
						a.startSupportActionMode(new Callback() {
							MenuItem mnuInfo;
							MenuItem mnuShare;

							@Override
							public boolean onCreateActionMode(ActionMode mode,
									Menu menu) {
								boolean isLight = ThemeOptions
										.isLightBackground(mContext, true);
								this.mnuInfo = menu.add("Info");
								this.mnuInfo
										.setIcon(
												isLight ? R.drawable.action_about_light
														: R.drawable.action_about_dark)
										.setShowAsAction(
												MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

								this.mnuShare = menu.add("Share");
								this.mnuShare
										.setIcon(
												isLight ? R.drawable.social_share_light
														: R.drawable.social_share_dark)
										.setShowAsAction(
												MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

								return true;
							}

							@Override
							public boolean onPrepareActionMode(ActionMode mode,
									Menu menu) {
								// TODO Auto-generated method stub
								return false;
							}

							@Override
							public boolean onActionItemClicked(ActionMode mode,
									MenuItem item) {
								if (item == mnuInfo) {
									Toast.makeText(mContext,
											"It's a picture, Jim.",
											Toast.LENGTH_SHORT).show();
								} else if (item == mnuShare) {
									Intent intent = new Intent(
											Intent.ACTION_SEND);

									Uri uri = Uri.parse("content://mms/part");
									Cursor cGetType = mContext
											.getContentResolver().query(uri,
													new String[] { "ct" },
													"_id = ?",
													new String[] { key }, null);

									cGetType.moveToFirst();
									String type = cGetType.getString(0);
									cGetType.close();
									String ext = type.split("/")[1];
									InputStream is = null;
									FileOutputStream fo = null;
									try {
										Uri partURI = Uri
												.parse("content://mms/part/"
														+ key);

										fo = mContext.openFileOutput(key + "."
												+ ext,
												Context.MODE_WORLD_READABLE);

										is = mContext.getContentResolver()
												.openInputStream(partURI);
										byte[] buffer = new byte[1024];
										int len;
										while ((len = is.read(buffer)) != -1) {
											fo.write(buffer, 0, len);
										}

									} catch (IOException e) {
										e.printStackTrace();
									} finally {
										if (is != null)
											try {
												is.close();
											} catch (IOException e) {
												e.printStackTrace();
											}
										if (fo != null)
											try {
												fo.close();
											} catch (IOException e) {
												e.printStackTrace();
											}
									}
									File f = new File(mContext.getFilesDir()
											.getAbsolutePath(), key + "." + ext);
									intent.setType(type);
									intent.putExtra(Intent.EXTRA_STREAM,
											Uri.fromFile(f));
									f.deleteOnExit();

									mContext.startActivity(intent);
								}
								mode.finish();
								return true;
							}

							@Override
							public void onDestroyActionMode(ActionMode mode) {
								// TODO Auto-generated method stub

							}

						});

						return true;
					}
				});

			}

		}
		if (tvText != null) {
			if (TextUtils.isEmpty(result.getText()))
				tvText.setVisibility(View.GONE);
			else {
				tvText.setVisibility(View.VISIBLE);
			}
			tvText.setText(result.getText());
		}
	}

	private class ImageLoader extends AsyncTask<Void, Void, MMSObject> {

		private final WeakReference<ViewHolder> viewHolder;
		private MessageItem msgItem;
		private final String importMessageId;

		public ImageLoader(ViewHolder viewHolder, MessageItem msgItem) {

			this.viewHolder = new WeakReference<ViewHolder>(viewHolder);
			this.importMessageId = viewHolder.importMessageId;
			this.msgItem = msgItem;

		}

		@Override
		protected void onPostExecute(MMSObject result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);

			ViewHolder vh = viewHolder.get();
			if (vh == null)
				return;
			if (!this.importMessageId.equals(vh.importMessageId))
				return;
			LinearLayout llMediaLayout = vh.llMediaLayout;
			Button btnDownloadMMS = vh.btnDownloadMMS;
			TextView tvText = vh.text_view;

			buildMediaMessage(result, llMediaLayout, btnDownloadMMS, tvText,
					msgItem);
			MemoryCache.putMMSCache(importMessageId, result);
		}

		@Override
		protected MMSObject doInBackground(Void... params) {
			// Move this to a static class
			Log.w("ConversationListAdapter", "Start background MMS");
			boolean showImageIcon = true;
			MMSObject m = new MMSObject();

			List<Bitmap> previews = new ArrayList<Bitmap>();
			List<Point> sizes = new ArrayList<Point>();
			List<String> partIds = new ArrayList<String>();
			String text = new String();

			int msgType = -1;
			long msgExpiration = -1;
			long msgSize = -1;
			boolean messageExists = false;
			String contentLocation = null;
			Cursor cMessageInfo = mContext.getContentResolver().query(
					Uri.parse("content://mms/"),
					new String[] { "m_type", "exp", "m_size", "ct_l" },
					"_id = ?", new String[] { importMessageId }, null);
			if (cMessageInfo != null) {
				if (cMessageInfo.moveToFirst()) {

					msgType = cMessageInfo.getInt(0);
					msgExpiration = cMessageInfo.getLong(1);
					msgSize = cMessageInfo.getLong(2);
					if (!cMessageInfo.isNull(3))
						contentLocation = cMessageInfo.getString(3);
					messageExists = true;
				}
				cMessageInfo.close();
			}
			if (!messageExists) {
				text = "This message has been deleted";
			} else if (msgType == 130) {
				text = new String();
			} else if (msgType == 128 || msgType == 132) {

				String selectionPart = "mid=" + this.importMessageId;
				Uri uri = Uri.parse("content://mms/part");
				Cursor cPart = mContext.getContentResolver().query(uri, null,
						selectionPart, null, "_id");
				if (cPart.moveToFirst()) {
					do {
						String partId = cPart.getString(cPart
								.getColumnIndex("_id"));
						String type = cPart.getString(cPart
								.getColumnIndex("ct"));
						if ("text/plain".equals(type)) {
							String data = cPart.getString(cPart
									.getColumnIndex("_data"));
							String body;
							if (data != null) {
								// implementation of this method below
								text += getMmsText(partId) + '\n';
							} else {
								text += cPart.getString(cPart
										.getColumnIndex("text")) + '\n';
							}
						} else if ("image/jpeg".equals(type)
								|| "image/bmp".equals(type)
								|| "image/gif".equals(type)
								|| "image/jpg".equals(type)
								|| "image/png".equals(type)) {
							int contactPixelSize = (int) TypedValue
									.applyDimension(
											TypedValue.COMPLEX_UNIT_DIP, 128,
											mContext.getResources()
													.getDisplayMetrics());
							Bitmap bitmap = getMmsImage(mContext, partId);
							float width = bitmap.getWidth();
							float height = bitmap.getHeight();
							float scale = 1.0f;

							if (height >= width && height > contactPixelSize)
								scale = (float) height
										/ (float) contactPixelSize;
							else if (width > height && width > contactPixelSize)
								scale = (float) width
										/ (float) contactPixelSize;

							if (scale != 1.0f) {
								width = (int) (width / scale);
								height = (int) (height / scale);
								bitmap = Bitmap.createScaledBitmap(bitmap,
										(int) width, (int) height, true);
							}
							Log.w("ConversationListAdapter", "Set Image MMS");

							sizes.add(new Point((int) width, (int) height));
							previews.add(bitmap);
							partIds.add(partId);

						}
					} while (cPart.moveToNext());

				}
				cPart.close();
			} else {
				text = "Message could not be read";
			}

			if (text != null)
				text = text.trim();

			m.setMsgExpiration(msgExpiration);
			m.setMsgType(msgType);
			m.setMsgSize(msgSize);
			m.setText(text);
			m.setPreviewSizes(sizes.toArray(new Point[sizes.size()]));
			m.setImagePreviews(previews.toArray(new Bitmap[previews.size()]));
			m.setImagePartIds(partIds.toArray(new String[partIds.size()]));
			m.setImportMessageId(importMessageId);
			m.setContentLocation(contentLocation);
			// textView.setVisibility(text.length() == 0 ? View.GONE :
			// View.VISIBLE);
			// textView.setText(text);
			Log.w("ConversationListAdapter", "Finished Set MMS");

			return m;

		}
	}

	private OnDownloadMMSButtonClickListener mOnDownloadMMSButtonClickListener;

	public void setOnDownloadMMSClickListener(
			OnDownloadMMSButtonClickListener listener) {
		this.mOnDownloadMMSButtonClickListener = listener;

	}

	public static abstract class OnDownloadMMSButtonClickListener implements
			OnClickListener {

		public abstract void onClick(View v, MMSObject mmsObject,
				MessageItem msgItem);
	}

	public static class MMSDownloader extends AsyncTask<Void, Void, Uri> {

		private MMSObject mmsObject;
		private Context mContext;
		private WorkerService workerService;
		private MessageItem msgItem;

		public MMSDownloader(Context mContext, MMSObject mmsObject,
				WorkerService workerService, MessageItem msgItem) {

			this.mmsObject = mmsObject;
			this.mContext = mContext;
			this.workerService = workerService;
			this.msgItem = msgItem;
		}

		@Override
		protected void onPostExecute(Uri result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			if (result == null) {

				if (Build.VERSION.SDK_INT >= 17) {
					SharedPreferences spTemp = PreferenceManager
							.getDefaultSharedPreferences(mContext);
					if (spTemp.getBoolean("pfMMSManualProxySettings", false) == false) {
						Toast.makeText(
								mContext,
								"Manual MMS settings must be configured in Android 4.2+. Blame Google.",
								Toast.LENGTH_LONG).show();
						return;
					}
				}

				Toast.makeText(mContext, "Downloading MMS: Failed",
						Toast.LENGTH_LONG).show();

			}

		}

		@Override
		protected Uri doInBackground(Void... params) {
			SharedPreferences spTemp = PreferenceManager
					.getDefaultSharedPreferences(mContext);
			TransactionSettings mTransactionSettings;
			try {
				if (spTemp.getBoolean("pfMMSManualProxySettings", false) == false) {
					mTransactionSettings = new TransactionSettings(mContext,
							null);
				} else {
					mTransactionSettings = new TransactionSettings(
							spTemp.getString("pfMMSProxyAddress", null),
							spTemp.getString("pfMMSProxyPort", null));
				}
			} catch (SecurityException secEx) {

				if (spTemp.getBoolean("pfMMSManualProxySettings", false) == false)
					return null;
				mTransactionSettings = new TransactionSettings(
						spTemp.getString("pfMMSProxyAddress", null),
						spTemp.getString("pfMMSProxyPort", null));

			}
			String url = mmsObject.getContentLocation();
			try {
				byte[] resp = HttpUtils.httpConnection(mContext, -1, url, null,
						HttpUtils.HTTP_GET_METHOD,
						mTransactionSettings.isProxySet(),
						mTransactionSettings.getProxyAddress(),
						mTransactionSettings.getProxyPort());

				RetrieveConf retrieveConf = (RetrieveConf) new PduParser(resp)
						.parse();
				if (null == retrieveConf)
					throw new Exception("Invalid M-Retrieve.conf PDU.");

				// Store M-Retrieve.conf into Inbox
				im.fsn.messenger.mms.PduPersister persister = im.fsn.messenger.mms.PduPersister
						.getPduPersister(mContext);
				Uri msgUri = persister.persist(retrieveConf,
						Mms.Inbox.CONTENT_URI, true, false, null);

				// Use local time instead of PDU time

				// updateContentLocation(mContext, msgUri,
				// mContentLocation, mLocked);

				// Delete the corresponding M-Notification.ind.
				// SqliteWrapper.delete(mContext,
				// mContext.getContentResolver(), mUri, null, null);

				// Send ACK to the Proxy-Relay to indicate we have
				// fetched the
				// MM successfully.
				// Don't mark the transaction as failed if we failed
				// to send it.
				// sendAcknowledgeInd(retrieveConf);
				if (workerService != null) {
					msgItem.setMessageStatus(MessageStatuses.Deleted);
					workerService.queueMessageItem(msgItem);
				}

				return msgUri;
			} catch (Exception e) {

				e.printStackTrace();
			}
			return null;

		}
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		MessageItem msgItem = (MessageItem) this.getItem(position);
		if (msgItem == null)
			return null;
		boolean isLightTheme = ThemeOptions.isLightBackground(this.mContext,
				false);
		ViewHolder viewHolder;
		if (convertView == null) {
			if (msgItem.isIncoming())
				convertView = mInflater.inflate(
						R.layout.googlemms_conversation_list_item_recv, null);
			else
				convertView = mInflater.inflate(
						R.layout.googlemms_conversation_list_item_send, null);
			viewHolder = new ViewHolder();
			viewHolder.vServiceIndicator = convertView
					.findViewById(R.id.vServiceIndicator);
			viewHolder.qcbAvatar = (QuickContactBadge) convertView
					.findViewById(R.id.qcbAvatar);
			viewHolder.ivService = (ImageView) convertView
					.findViewById(R.id.ivService);
			viewHolder.message_block = (LinearLayout) convertView
					.findViewById(R.id.message_block);
			viewHolder.text_view = (TextView) convertView
					.findViewById(R.id.text_view);
			viewHolder.date_view = (TextView) convertView
					.findViewById(R.id.date_view);
			viewHolder.llMediaLayout = (LinearLayout) convertView
					.findViewById(R.id.llMediaLayout);
			viewHolder.btnDownloadMMS = (Button) convertView
					.findViewById(R.id.btnDownloadMMS);
			convertView.setTag(viewHolder);
		} else
			viewHolder = (ViewHolder) convertView.getTag();

		String text = msgItem.getText();
		viewHolder.llMediaLayout.setVisibility(View.GONE);
		viewHolder.btnDownloadMMS.setVisibility(View.GONE);
		viewHolder.text_view.setVisibility(text.length() == 0 ? View.GONE
				: View.VISIBLE);
		viewHolder.importMessageId = msgItem.getImportMessageId();

		if (text.length() == 0
				&& msgItem.getExtraDataType() != ExtraDataTypes.None) {
			if (msgItem.getExtraDataType() == ExtraDataTypes.Unknown
					&& msgItem.getIMProviderType() == IMProviderTypes.SMS) {
				MMSObject m = MemoryCache.getMMSCache(msgItem
						.getImportMessageId());
				if (m != null) {
					buildMediaMessage(m, viewHolder.llMediaLayout,
							viewHolder.btnDownloadMMS, viewHolder.text_view,
							msgItem);
					if (m.getImagePreviews() == null) {
						ImageLoader id = new ImageLoader(viewHolder, msgItem);
						id.execute();
					}
				} else {
					viewHolder.text_view.setText("Loading MMS...");
					viewHolder.text_view.setVisibility(View.VISIBLE);

					ImageLoader id = new ImageLoader(viewHolder, msgItem);
					id.execute();
				}
			} else {
			}
		} else {
			float size = viewHolder.text_view.getTextSize();
			viewHolder.text_view.setText(Emoji.getSmiledText(mContext, text,
					false, (int) size));
		}

		String subText = new String();
		boolean showDelivered = false;
		switch (msgItem.getMessageStatus()) {
		case Queued:
			subText = "Queued"; // localize me
			break;
		case OnRoute:
			subText = "Sending"; // localize me
			break;
		case Delivered:
			showDelivered = true;
		case Sent:

			// Some hardware overrides getInstance() so we're explicitly stating
			// it here.
			Calendar eventDateTime = Calendar.getInstance(
					TimeZone.getDefault(), Locale.getDefault());
			Calendar yesterday = Calendar.getInstance(TimeZone.getDefault(),
					Locale.getDefault());
			Calendar lastYear = Calendar.getInstance(TimeZone.getDefault(),
					Locale.getDefault());

			eventDateTime.setTimeInMillis(msgItem.getCompletionDateTime());
			yesterday.add(Calendar.DATE, -1);
			lastYear.add(Calendar.YEAR, -1);

			Date dEventDateTime = new Date(msgItem.getCompletionDateTime());

			String timeString;
			String providerName;
			if (eventDateTime.after(yesterday)) {
				timeString = android.text.format.DateFormat.getTimeFormat(
						mContext).format(dEventDateTime);
				providerName = IMProvider.IMServiceNames[msgItem
						.getIMProviderType().ordinal()];
			} else if (eventDateTime.after(lastYear)) {
				SimpleDateFormat sdfOriginal = (SimpleDateFormat) SimpleDateFormat
						.getDateTimeInstance(DateFormat.SHORT,
								DateFormat.SHORT, Locale.getDefault());
				if (android.text.format.DateFormat.is24HourFormat(mContext)) {
					sdfOriginal.applyLocalizedPattern(Utils
							.stripCharactersFromPattern(
									sdfOriginal.toLocalizedPattern(),
									new char[] { 'a', 'y' }).replace('h', 'H')
							.replace('K', 'k'));
					timeString = sdfOriginal.format(dEventDateTime);
				} else {
					timeString = Utils.stripFieldFromPattern(sdfOriginal,
							dEventDateTime, DateFormat.Field.YEAR);
				}

				providerName = IMProvider.IMServiceNames[msgItem
						.getIMProviderType().ordinal()];
			} else {

				SimpleDateFormat sdfOriginal = (SimpleDateFormat) SimpleDateFormat
						.getDateTimeInstance(DateFormat.SHORT,
								DateFormat.SHORT, Locale.getDefault());
				if (android.text.format.DateFormat.is24HourFormat(mContext)) {
					sdfOriginal.applyLocalizedPattern(Utils
							.stripCharactersFromPattern(
									sdfOriginal.toLocalizedPattern(),
									new char[] { 'a' }).replace('K', 'H')
							.replace('k', 'h'));
				}
				timeString = sdfOriginal.format(dEventDateTime);
				providerName = IMProvider.IMServiceShortNames[msgItem
						.getIMProviderType().ordinal()];
			}

			String format = "%1$s";
			if (!this.useColoredStatus && !this.useIMServiceIcon
					&& this.mIMServiceIndicatorWidth == 0)
				format += " via %2$s";
			subText = String.format(Locale.getDefault(), format, timeString,
					providerName);
			break;
		case Failed:
			subText = "Failed"; // localize me
		}
		viewHolder.date_view.setText(subText);

		IMProvider provider = IMProvider.findIMProvider(
				msgItem.getIMProviderType(), MemoryCache.IMProviders);

		if (provider != null) {
			if (this.useColoredStatus)
				viewHolder.date_view.setTextColor(isLightTheme ? provider
						.getDarkColor() : provider.getLightColor());
			viewHolder.vServiceIndicator
					.setBackgroundColor(isLightTheme ? provider.getLightColor()
							: provider.getDarkColor());
		} else {
			viewHolder.date_view.setTextColor(messageStatusColor);
			viewHolder.vServiceIndicator.setBackgroundColor(messageStatusColor);
		}

		viewHolder.qcbAvatar
				.setVisibility(this.useContactPicture ? View.VISIBLE
						: View.GONE);
		if (this.mIMServiceIndicatorWidth == 0)
			viewHolder.vServiceIndicator.setVisibility(View.GONE);
		else {
			viewHolder.vServiceIndicator.setVisibility(View.VISIBLE);
			viewHolder.vServiceIndicator.getLayoutParams().width = (int) TypedValue
					.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
							this.mIMServiceIndicatorWidth, this.mContext
									.getResources().getDisplayMetrics());
		}
		viewHolder.ivService.setVisibility(this.useIMServiceIcon ? View.VISIBLE
				: View.GONE);

		Bitmap b = null;
		if (msgItem.isIncoming()) {
			if (this.mContactItem != null) {
				if (this.mContactItem.getId() != 0) {
					Uri contactUri = Uri.withAppendedPath(
							ContactsContract.Contacts.CONTENT_URI,
							String.valueOf(mContactItem.getId()));
					viewHolder.qcbAvatar.assignContactUri(contactUri);
					if (!mContactItem.isPhotoChecked()) {
						InputStream input;
						if (VERSION.SDK_INT >= 14)
							input = V14.openHiResContactPhotoInputStream(
									mContext.getContentResolver(), contactUri);
						else
							input = ContactsContract.Contacts
									.openContactPhotoInputStream(
											mContext.getContentResolver(),
											contactUri);

						if (input != null) {
							b = BitmapFactory.decodeStream(input);
							if (b != null) {
								int contactPixelSize = (int) TypedValue
										.applyDimension(
												TypedValue.COMPLEX_UNIT_DIP,
												64, this.mContext
														.getResources()
														.getDisplayMetrics());
								if (b.getHeight() > contactPixelSize)
									b = Bitmap.createScaledBitmap(b,
											contactPixelSize, contactPixelSize,
											true);
								mContactItem.setThumbnail64x64(b);
							}
						}
						mContactItem.setPhotoChecked(true);
					}

				} else {
					ContactAddress ca = mContactItem.getContactAddresses()[0];
					if (ca.getIMProviderType() == IMProviderTypes.SMS
							|| ca.getIMProviderType() == IMProviderTypes.GVoice)
						viewHolder.qcbAvatar.assignContactFromPhone(
								ca.getParsedAddress(), true);
					else
						viewHolder.qcbAvatar.assignContactUri(null);
				}
				b = this.mContactItem.getThumbnail64x64();
			}
		} else {
			if (Build.VERSION.SDK_INT >= 14 && mProfileId > 0) {
				Uri contactUri = Uri.withAppendedPath(
						ContactsContract.Contacts.CONTENT_URI,
						String.valueOf(mProfileId));
				viewHolder.qcbAvatar.assignContactUri(contactUri);
			} else
				viewHolder.qcbAvatar.assignContactUri(null);
			b = this.mProfilePhoto;
		}
		viewHolder.qcbAvatar.setMode(ContactsContract.QuickContact.MODE_SMALL);
		viewHolder.qcbAvatar.setImageBitmap(b);

		if (b == null) {
			if (!isLightTheme)
				viewHolder.qcbAvatar.setImageDrawable(defaultAvatarDark);
			else
				viewHolder.qcbAvatar.setImageDrawable(defaultAvatarLight);
		}

		if (!isLightTheme) {
			switch (msgItem.getIMProviderType()) {
			default:
				viewHolder.ivService
						.setImageResource(R.drawable.ic_contact_picture_dark);
				break;
			case SMS:
				viewHolder.ivService.setImageResource(R.drawable.sms_icon);
				break;
			case GVoice:
				viewHolder.ivService
						.setImageResource(R.drawable.google_voice_icon);
				break;
			}
		} else {
			switch (msgItem.getIMProviderType()) {

			default:
				viewHolder.ivService
						.setImageResource(R.drawable.ic_contact_picture_light);
				break;
			case SMS:
				viewHolder.ivService.setImageResource(R.drawable.sms_icon);
				break;
			case GVoice:
				viewHolder.ivService
						.setImageResource(R.drawable.google_voice_icon);
				break;
			}
		}

		return convertView;
	}

	//@formatter:off
	private ColorMatrixColorFilter invertColorMatrix = new ColorMatrixColorFilter(new float[] { 
		-1, 0, 0, 0, 0, 
		0, -1, 0, 0, 0, 
		0, 0, -1, 0, 0, 
		0, 0, 0, 1, 0, 
		1, 1, 1, 0, 1 });
	//@formatter:on

	public Bitmap getUserAvatar() {
		return userAvatar;
	}

	public void setUserAvatar(Bitmap userAvatar) {
		this.userAvatar = userAvatar;
	}

	@Override
	public int getCount() {
		return this.filtered.size();
	}

	@Override
	public MessageItem getItem(int position) {
		return this.filtered.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
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
			ArrayList<MessageItem> filtered = new ArrayList<MessageItem>();
			List<MessageItem> messages = MemoryCache.GetMessages(mContactItem);
			if (messages == null)
				messages = new ArrayList<MessageItem>();
			if (query == null || query.length() == 0) {
				for (int i = 0; i < messages.size(); i++)
					filtered.add(messages.get(i).clone());
			} else {
				for (int i = 0; i < messages.size(); i++) {
					MessageItem m = messages.get(i);
					if (m.getText() != null
							&& m.getText().toLowerCase().contains(query))
						filtered.add(m.clone());
				}

			}
			results.values = filtered;
			results.count = filtered.size();

			return results;
		}

		@SuppressWarnings("unchecked")
		protected void publishResults(CharSequence prefix, FilterResults results) {
			ConversationListAdapter.this.filtered = (ArrayList<MessageItem>) results.values;
			notifyDataSetChanged();
		}
	}

}
