package im.fsn.messenger.providers;

import im.fsn.messenger.MessageItem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONObject;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Messenger;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

public class GVoiceProviderWebAPI extends IMProvider {

	TelephonyManager telManager;
	PhoneNumberUtil phoneUtil = null;
	private String countryCode;

	public GVoiceProviderWebAPI(Context mContext, Messenger mMessenger) {
		super(mContext, mMessenger);
		this.loginVariables = new HashMap<String, String>();
		telManager = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);
		phoneUtil = PhoneNumberUtil.getInstance();
		countryCode = telManager.getNetworkCountryIso().toUpperCase(Locale.US);
	}

	private String rnr;
	private HashMap<String, String> loginVariables;

	@Override
	public void start() {
		this.running = true;
		login();
	}

	private boolean loggedIn = false;

	private void login() {

		// Cleaned up some code. Made the calls to the HTTP server static
		// Manual redirects (not supported by Java)
		// Manual cookie manipulation

		String sLoginScreen = getHttpResponse(
				"https://www.google.com/accounts/ServiceLogin", null);
		if (sLoginScreen == null)
			return;
		this.loginVariables = GVoiceProviderWebAPI
				.getInputVariables(sLoginScreen);

		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String userName = pref.getString("pfGoogleVoiceUsername", new String());
		String password = pref.getString("pfGoogleVoicePassword", new String());
		loginVariables.put("Email", userName);
		loginVariables.put("Passwd", password);
		String sLoginResponse = getHttpResponse(
				"https://accounts.google.com/ServiceLogin?service=grandcentral&continue=https://www.google.com/voice/&followup=https://www.google.com/voice/&ltmpl=open",
				this.loginVariables);
		String sGoogleVoiceMainPage = getHttpResponse(
				"https://www.google.com/voice/b/0", null);
		HashMap<String, String> map = GVoiceProviderWebAPI
				.getInputVariables(sGoogleVoiceMainPage);
		if (map.containsKey("_rnr_se")) {
			this.rnr = map.get("_rnr_se");
			this.loggedIn = true;
		} else
			this.loggedIn = false;

	}

	private static HashMap<String, String> getInputVariables(String input) {
		if (input == null)
			return null;
		HashMap<String, String> variables = new HashMap<String, String>();

		String lcInput = input.toLowerCase(Locale.US);
		int indexOfInput = lcInput.indexOf("<input");

		while (indexOfInput != -1) {
			int indexOfName = lcInput.indexOf("name=", indexOfInput);

			int indexOfNameStringStart = indexOfName + "name=".length();

			char nameSeparator = lcInput.charAt(indexOfNameStringStart);
			int indexOfNameEnd = lcInput.indexOf(nameSeparator,
					indexOfNameStringStart + 1);

			String name = input.substring(indexOfNameStringStart + 1,
					indexOfNameEnd);

			int indexOfValue = lcInput.indexOf("value=", indexOfInput);

			int indexOfValueStringStart = indexOfValue + "value=".length();

			char valueSeparator = lcInput.charAt(indexOfValueStringStart);
			int indexOfValueEnd = lcInput.indexOf(valueSeparator,
					indexOfValueStringStart + 1);

			String value = input.substring(indexOfValueStringStart + 1,
					indexOfValueEnd);
			variables.put(name, value);
			indexOfInput = lcInput.indexOf("<input", indexOfInput + 1);
		}
		return variables;

	}

	private static List<String> cookies = new ArrayList<String>();

	private static String getHttpResponse(String urlString,
			HashMap<String, String> postVariables) {

		try {

			String postData = new String();
			if (postVariables != null) {
				Iterator<Entry<String, String>> it = postVariables.entrySet()
						.iterator();
				while (it.hasNext()) {
					Map.Entry<String, String> keyPair = (Map.Entry<String, String>) it
							.next();

					postData += keyPair.getKey() + '='
							+ URLEncoder.encode(keyPair.getValue(), "UTF-8");

					if (it.hasNext())
						postData += '&';
				}
			}
			URL loginURL = new URL(urlString);
			HttpURLConnection httpConnection = (HttpURLConnection) loginURL
					.openConnection();
			httpConnection.setUseCaches(false);

			httpConnection.setRequestProperty("User-Agent", "fusion");
			httpConnection.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded;charset=utf-8");
			httpConnection.setRequestProperty("Connection", "Close");
			httpConnection.setConnectTimeout(15 * 1000);
			httpConnection.setInstanceFollowRedirects(false);

			if (cookies != null)
				for (String cookie : cookies)
					httpConnection.addRequestProperty("Cookie",
							cookie.split(";", 2)[0]);

			if (postData.length() != 0) {
				httpConnection.setRequestProperty("Content-Length",
						String.valueOf(postData.length()));
				httpConnection.setRequestMethod("POST");
				httpConnection.setDoOutput(true);

				OutputStreamWriter osw = new OutputStreamWriter(
						httpConnection.getOutputStream());
				osw.write(postData);
				osw.flush();
				osw.close();
			}

			httpConnection.connect();
			int responseCode = httpConnection.getResponseCode();

			// cookie manipulation
			List<String> tmpCookies = httpConnection.getHeaderFields().get(
					"Set-Cookie");
			if (tmpCookies != null) {
				int iCount = tmpCookies.size();
				for (int i = 0; i < iCount; i++) {
					String tmpCookie = tmpCookies.get(i);
					boolean found = false;
					int jCount = cookies.size();
					for (int j = 0; j < jCount; j++) {
						String cookie = cookies.get(j);
						if (cookie.split("=")[0]
								.equals(tmpCookie.split("=")[0])) {
							found = true;
							cookies.set(j, tmpCookie);
							break;
						}
					}
					if (!found)
						cookies.add(tmpCookie);
				}
			}

			switch (responseCode) {
			case HttpURLConnection.HTTP_MOVED_PERM:
			case HttpURLConnection.HTTP_MOVED_TEMP:
			case HttpURLConnection.HTTP_SEE_OTHER:
			case 307: // TempRedirect
				// manual redirects
				String location = httpConnection.getHeaderField("Location");
				httpConnection.disconnect();
				return getHttpResponse(location, null);
			case HttpURLConnection.HTTP_OK:
				InputStream is = httpConnection.getInputStream();

				BufferedInputStream bis = new BufferedInputStream(is);
				ByteArrayBuffer baf = new ByteArrayBuffer(1024);
				int current = 0;
				while ((current = bis.read()) != -1)
					baf.append((byte) current);
				bis.close();
				httpConnection.disconnect();
				return new String(baf.toByteArray());
			}

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void stop() {
		// logout
		this.running = false;
	}

	@Override
	public MessageItem sendMessage(MessageItem msgItem) {
		HashMap<String, String> variables = new HashMap<String, String>();
		variables.put("phoneNumber", msgItem.getExternalAddress());
		variables.put("text", msgItem.getText());
		variables.put("_rnr_se", this.rnr);
		try {
			String response = getHttpResponse(
					"https://www.google.com/voice/sms/send/", variables);
			JSONObject jsonObject = new JSONObject(response);
			if (jsonObject.getBoolean("ok")) {
				msgItem.setMessageStatus(MessageItem.MessageStatuses.Sent);
				return msgItem;
			}
		} catch (Exception e) {

		}
		msgItem.setMessageStatus(MessageItem.MessageStatuses.Failed);
		return msgItem;
	}

	@Override
	public IMProviderTypes getIMProviderType() {
		return IMProviderTypes.GVoice;
	}

	@Override
	public String parseAddress(String address) {
		if (address == null)
			return null;
		if (phoneUtil == null)
			phoneUtil = PhoneNumberUtil.getInstance();
		try {
			if (countryCode.equals(""))
				countryCode = telManager.getNetworkCountryIso().toUpperCase(
						Locale.US);
			if (countryCode.equals(""))
				countryCode = Locale.getDefault().getCountry();

			PhoneNumber number = phoneUtil.parse(address, countryCode);
			return phoneUtil.format(number, PhoneNumberFormat.E164);
		} catch (NumberParseException e) {
			System.err.println("NumberParseException was thrown: "
					+ e.toString());
			return null;
		}
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public String getSender() {
		return null;
	}

	private boolean available;

	@Override
	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	private boolean running;

	private int mDarkColor = 0xff0099cc; // holo_blue_dark
	private int mLightColor = 0xff33b5e5; // holo_blue_light

	@Override
	public int getDarkColor() {
		return mDarkColor;
	}

	@Override
	public void setDarkColor(int color) {
		this.mDarkColor = color;
	}

	@Override
	public int getLightColor() {
		return mLightColor;
	}

	@Override
	public void setLightColor(int color) {
		this.mLightColor = color;
	}

	@Override
	public void setActivity(Activity activity) {

	}

	@Override
	public boolean markAsRead(MessageItem msgItem) {

		return false;
	}

	@Override
	public boolean markAsRead(MessageItem[] items) {
		return false;
	}

	@Override
	public boolean delete(MessageItem item) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean delete(MessageItem[] items) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean syncMessages(boolean force) {
		return false;
	}

	@Override
	public void disableSyncLock() {
		// TODO Auto-generated method stub
		
	}
}
