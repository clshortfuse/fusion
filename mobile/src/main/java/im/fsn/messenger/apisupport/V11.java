package im.fsn.messenger.apisupport;

import im.fsn.messenger.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

@TargetApi(11)
public class V11 {
	public static <T> void addAll(ArrayAdapter<T> adapter,
			Collection<? extends T> collection) {
		adapter.addAll(collection);
	}

	public static <T> void addAll(ArrayAdapter<T> adapter, T... items) {
		adapter.addAll(items);
	}



	public static int getHoloListViewStyle(boolean light) {
		if (light)
			return android.R.style.Widget_Holo_Light_ListView;
		else
			return android.R.style.Widget_Holo_ListView;
	}

	public static View buildSearchView(Context mContext) {
		return new SearchView(mContext);
	}

	public static abstract class SearchViewSetOnQueryTextListener {
		public abstract boolean onQueryTextSubmit(String query);

		public abstract boolean onQueryTextChange(String newText);
	}

	public static abstract class SearchViewSetOnQueryTextFocusChangeListener {
		public abstract boolean onQueryTextSubmit(String query);

		public abstract boolean onQueryTextChange(String newText);
	}

	public static void SearchViewSetQuery(View sv, CharSequence query,
			boolean submit) {
		((SearchView) sv).setQuery(query, submit);
	}

	public static void SearchViewSetIconified(View sv, boolean iconify) {
		((SearchView) sv).setIconified(iconify);
	}

	public static void SearchViewSetQueryHint(View sv, CharSequence hint) {
		((SearchView) sv).setQueryHint(hint);
	}

	public static void setOnQueryTextFocusChangeListener(View sv,
			View.OnFocusChangeListener listener) {
		((SearchView) sv).setOnQueryTextFocusChangeListener(listener);
	}

	public static CharSequence SearchViewGetQuery(View sv) {
		return ((SearchView) sv).getQuery();
	}

	public static void SetOnQueryTextListener(View sv,
			final SearchViewSetOnQueryTextListener listener) {

		((SearchView) sv).setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				return listener.onQueryTextSubmit(query);
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return listener.onQueryTextChange(newText);
			}
		});

	}

	public static void enableWriteAheadLogging(SQLiteDatabase db) {
		db.enableWriteAheadLogging();
	}

	public static void SetClipboard(Context mContext, CharSequence label,
			CharSequence text) {
		android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext
				.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText(label, text);
		clipboard.setPrimaryClip(clip);
	}

	public static int getActionBarStyleResId() {
		return android.R.attr.actionBarStyle;
	}

	public static int getActionBarTabTextStyleResId() {
		return android.R.attr.actionBarTabTextStyle;
	}

	public static int getActionBarTabStyleResId() {
		return android.R.attr.actionBarTabStyle;
	}

}
