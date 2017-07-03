package im.fsn.messenger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

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

public class CacheAdapter {

	private static final String DATABASE_FILE = "cache.db";
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_TABLE = "ParsedSMS";
	private final Context mContext;

	private SQLiteDatabase db;
	//@formatter:off
	private static final String DATABASE_CREATE =
		"CREATE TABLE ParsedSMS "
		+ "("
			+ "RawNumber VARCHAR(50) NOT NULL, "
			+ "ParsedNumber VARCHAR(20) NOT NULL "
			+ ")";
	//@formatter:on

	//@formatter:off
	
	private DatabaseHelper DBHelper;

	public CacheAdapter(Context mContext) {
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
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion,
				int newVersion) {
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int currentVersion,
				int latestVersion) {			

		}
		
		
	};

	public CacheAdapter open() throws SQLiteException {		
		this.db = DBHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		this.DBHelper.close();
	}

	
	private final Object syncObject = new Object();
	public Map<String,String> getList() {
		synchronized(syncObject)
		{
			this.open();
			Cursor c = this.db.query(DATABASE_TABLE, new String[] {"RawNumber", "ParsedNumber"}, null, null, null, null, null, null);
			Map<String,String> result = new HashMap<String,String>();			
			while (c.moveToNext())			
				result.put(c.getString(0), c.getString(1));
			c.close();
			this.close();
			return result;
			
		}	
	}
	
	public void putList(Map<String,String> list) {
		synchronized(syncObject)
		{
			this.open();			
			this.db.delete(DATABASE_TABLE, null, null);
			ContentValues cv = new ContentValues();
			Iterator<Entry<String, String>> it = list.entrySet().iterator();
			while(it.hasNext())			
			{				
				Map.Entry<String, String> pairs = (Map.Entry<String, String>)it.next();
				cv.put("RawNumber", pairs.getKey());
				cv.put("ParsedNumber", pairs.getValue());				
				db.insert(DATABASE_TABLE, null, cv);
				it.remove();
			}
			this.close();					
		}	
	}
}
