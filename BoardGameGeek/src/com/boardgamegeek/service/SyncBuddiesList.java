package com.boardgamegeek.service;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.boardgamegeek.R;
import com.boardgamegeek.auth.Authenticator;
import com.boardgamegeek.io.RemoteBuddiesHandler;
import com.boardgamegeek.io.RemoteExecutor;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.util.HttpUtils;

public class SyncBuddiesList extends SyncTask {

	@Override
	public void execute(RemoteExecutor executor, Context context) throws IOException, XmlPullParserException {

		ContentResolver resolver = context.getContentResolver();
		Account account = Authenticator.getAccount(context);
		if (account == null) {
			return;
		}

		long startTime = System.currentTimeMillis();
		insertSelf(resolver, account.name);
		RemoteBuddiesHandler handler = new RemoteBuddiesHandler();
		executor.executePagedGet(HttpUtils.constructUserUrl(account.name, true), handler);
		if (handler.isBggDown()) {
			setIsBggDown(true);
		} else {
			// TODO: delete avatar images associated with this list
			resolver.delete(Buddies.CONTENT_URI, Buddies.UPDATED_LIST + "<?",
				new String[] { String.valueOf(startTime) });
		}
	}

	@Override
	public int getNotification() {
		return R.string.notification_text_buddies_list;
	}

	private void insertSelf(ContentResolver resolver, String username) {
		int selfId = 0;
		Uri uri = Buddies.buildBuddyUri(selfId);

		ContentValues values = new ContentValues();
		values.put(Buddies.UPDATED_LIST, System.currentTimeMillis());
		values.put(Buddies.BUDDY_NAME, username);

		Cursor cursor = null;
		try {
			cursor = resolver.query(uri, new String[] { BaseColumns._ID, }, null, null, null);
			if (cursor.moveToFirst()) {
				resolver.update(uri, values, null, null);
			} else {
				values.put(Buddies.BUDDY_ID, selfId);
				resolver.insert(Buddies.CONTENT_URI, values);
			}
		} finally {
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
	}
}
