package im.fsn.messenger;

import java.util.Locale;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.SparseIntArray;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

public class PhoneUtilsLite {
	public static String stripSeparators(String phoneNumber) {
		if (phoneNumber == null) {
			return null;
		}
		int len = phoneNumber.length();
		StringBuilder ret = new StringBuilder(len);

		for (int i = 0; i < len; i++) {
			char c = phoneNumber.charAt(i);
			// Character.digit() supports ASCII and Unicode digits (fullwidth,
			// Arabic-Indic, etc.)
			int digit = Character.digit(c, 10);
			if (digit != -1) {
				ret.append(digit);
			} else if (isNonSeparator(c)) {
				ret.append(c);
			}
		}

		return ret.toString();
	}

	public final static boolean isNonSeparator(char c) {
		switch (c) {
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
		case '*':
		case '#':
		case '+':
		case 'N':
		case ';':
		case ',':
			return true;
		}
		return false;

	}

	public static String parseAddress(String address,
			PhoneNumberUtil phoneUtil, String countryCode) {
		if (address == null)
			return null;
		if (phoneUtil == null)
			phoneUtil = PhoneNumberUtil.getInstance();
		if (TextUtils.isEmpty(countryCode))
			countryCode = Locale.getDefault().getCountry();

		String number = stripSeparators(address);
		String normalizedNumber = normalizeNumber(number);
		if (!TextUtils.isEmpty(normalizedNumber)) {
			String result = formatNumberToE164(number, countryCode, phoneUtil);
			if (TextUtils.isEmpty(result))
				return normalizedNumber;
			else
				return result;
		}
		return null;
	}

	public static String formatNumberToE164(String phoneNumber,
			String defaultCountryIso, PhoneNumberUtil phoneUtil) {

		String result = null;
		try {
			PhoneNumber pn = new PhoneNumber();
			phoneUtil.parse(phoneNumber, defaultCountryIso, pn);
			if (phoneUtil.isValidNumber(pn)) {
				result = phoneUtil.format(pn, PhoneNumberFormat.E164);
			}
		} catch (NumberParseException e) {
		}
		return result;
	}

	public static boolean isISODigit(char c) {
		return c >= '0' && c <= '9';
	}

	public static String normalizeNumber(String phoneNumber) {
		StringBuilder sb = new StringBuilder();
		int len = phoneNumber.length();
		for (int i = 0; i < len; i++) {
			char c = phoneNumber.charAt(i);
			if ((i == 0 && c == '+') || isISODigit(c)) {
				sb.append(c);
			} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
				return normalizeNumber(convertKeypadLettersToDigits(phoneNumber));
			}
		}
		return sb.toString();
	}

	public static String convertKeypadLettersToDigits(String input) {
		if (input == null) {
			return input;
		}
		int len = input.length();
		if (len == 0) {
			return input;
		}

		char[] out = input.toCharArray();

		for (int i = 0; i < len; i++) {
			char c = out[i];
			// If this char isn't in KEYPAD_MAP at all, just leave it alone.
			out[i] = (char) KEYPAD_MAP.get(c, c);
		}

		return new String(out);
	}

	private static final SparseIntArray KEYPAD_MAP = new SparseIntArray();
	static {
		KEYPAD_MAP.put('a', '2');
		KEYPAD_MAP.put('b', '2');
		KEYPAD_MAP.put('c', '2');
		KEYPAD_MAP.put('A', '2');
		KEYPAD_MAP.put('B', '2');
		KEYPAD_MAP.put('C', '2');

		KEYPAD_MAP.put('d', '3');
		KEYPAD_MAP.put('e', '3');
		KEYPAD_MAP.put('f', '3');
		KEYPAD_MAP.put('D', '3');
		KEYPAD_MAP.put('E', '3');
		KEYPAD_MAP.put('F', '3');

		KEYPAD_MAP.put('g', '4');
		KEYPAD_MAP.put('h', '4');
		KEYPAD_MAP.put('i', '4');
		KEYPAD_MAP.put('G', '4');
		KEYPAD_MAP.put('H', '4');
		KEYPAD_MAP.put('I', '4');

		KEYPAD_MAP.put('j', '5');
		KEYPAD_MAP.put('k', '5');
		KEYPAD_MAP.put('l', '5');
		KEYPAD_MAP.put('J', '5');
		KEYPAD_MAP.put('K', '5');
		KEYPAD_MAP.put('L', '5');

		KEYPAD_MAP.put('m', '6');
		KEYPAD_MAP.put('n', '6');
		KEYPAD_MAP.put('o', '6');
		KEYPAD_MAP.put('M', '6');
		KEYPAD_MAP.put('N', '6');
		KEYPAD_MAP.put('O', '6');

		KEYPAD_MAP.put('p', '7');
		KEYPAD_MAP.put('q', '7');
		KEYPAD_MAP.put('r', '7');
		KEYPAD_MAP.put('s', '7');
		KEYPAD_MAP.put('P', '7');
		KEYPAD_MAP.put('Q', '7');
		KEYPAD_MAP.put('R', '7');
		KEYPAD_MAP.put('S', '7');

		KEYPAD_MAP.put('t', '8');
		KEYPAD_MAP.put('u', '8');
		KEYPAD_MAP.put('v', '8');
		KEYPAD_MAP.put('T', '8');
		KEYPAD_MAP.put('U', '8');
		KEYPAD_MAP.put('V', '8');

		KEYPAD_MAP.put('w', '9');
		KEYPAD_MAP.put('x', '9');
		KEYPAD_MAP.put('y', '9');
		KEYPAD_MAP.put('z', '9');
		KEYPAD_MAP.put('W', '9');
		KEYPAD_MAP.put('X', '9');
		KEYPAD_MAP.put('Y', '9');
		KEYPAD_MAP.put('Z', '9');
	}

	public static boolean isWellFormedSmsAddress(String address) {
		String networkPortion = extractNetworkPortion(address);

		return (!(networkPortion.equals("+") || TextUtils
				.isEmpty(networkPortion))) && isDialable(networkPortion);
	}

	public static String extractNetworkPortion(String phoneNumber) {
		if (phoneNumber == null) {
			return null;
		}

		int len = phoneNumber.length();
		StringBuilder ret = new StringBuilder(len);

		for (int i = 0; i < len; i++) {
			char c = phoneNumber.charAt(i);
			// Character.digit() supports ASCII and Unicode digits (fullwidth,
			// Arabic-Indic, etc.)
			int digit = Character.digit(c, 10);
			if (digit != -1) {
				ret.append(digit);
			} else if (c == '+') {
				// Allow '+' as first character or after CLIR MMI prefix
				String prefix = ret.toString();
				if (prefix.length() == 0 || prefix.equals(CLIR_ON)
						|| prefix.equals(CLIR_OFF)) {
					ret.append(c);
				}
			} else if (isDialable(c)) {
				ret.append(c);
			} else if (isStartsPostDial(c)) {
				break;
			}
		}

		return ret.toString();
	}

	public final static boolean isDialable(char c) {
		return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+'
				|| c == WILD;
	}

	public final static boolean isStartsPostDial(char c) {
		return c == PAUSE || c == WAIT;
	}

	private static boolean isDialable(String address) {
		for (int i = 0, count = address.length(); i < count; i++) {
			if (!isDialable(address.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public static final char PAUSE = ',';
	public static final char WAIT = ';';
	public static final char WILD = 'N';

	private static final String CLIR_ON = "*31#";
	private static final String CLIR_OFF = "#31#";
}
