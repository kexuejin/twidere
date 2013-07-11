/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.activity;

import java.util.HashMap;
import java.util.Map;

import org.mariotaku.popupmenu.PopupMenu;
import org.mariotaku.popupmenu.PopupMenu.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.DraftItem;
import org.mariotaku.twidere.provider.TweetStore.Drafts;
import org.mariotaku.twidere.util.AsyncTwitterWrapper;
import org.mariotaku.twidere.util.ImageLoaderWrapper;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;

public class DraftsActivity extends BaseDialogWhenLargeActivity implements LoaderCallbacks<Cursor>,
		OnItemClickListener, OnItemLongClickListener, OnMenuItemClickListener {

	private ContentResolver mResolver;
	private AsyncTwitterWrapper mTwitterWrapper;
	private SharedPreferences mPreferences;

	private DraftsAdapter mAdapter;
	private ListView mListView;

	private PopupMenu mPopupMenu;

	private Cursor mCursor;
	private float mTextSize;
	private DraftItem mDraftItem;
	private long mSelectedId;

	private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_DRAFTS_DATABASE_UPDATED.equals(action)) {
				getSupportLoaderManager().restartLoader(0, null, DraftsActivity.this);
			}
		}
	};

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		final Uri uri = Drafts.CONTENT_URI;
		final String[] cols = Drafts.COLUMNS;
		return new CursorLoader(this, uri, cols, null, null, null);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu_drafts, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		if (mCursor != null) {
			mSelectedId = id;
			final DraftItem draft = new DraftItem(mCursor, position);
			editDraft(draft);
		}
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		if (mCursor != null && position >= 0 && position < mCursor.getCount()) {
			mDraftItem = new DraftItem(mCursor, position);
			mCursor.moveToPosition(position);
			mSelectedId = mCursor.getLong(mCursor.getColumnIndex(Drafts._ID));
			mPopupMenu = PopupMenu.getInstance(this, view);
			mPopupMenu.inflate(R.menu.action_draft);
			mPopupMenu.setOnMenuItemClickListener(this);
			mPopupMenu.show();
			return true;
		}

		return false;
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
		mCursor = null;
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		mAdapter.swapCursor(cursor);
		mCursor = cursor;
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_SEND: {
				sendDraft(mDraftItem);
				getSupportLoaderManager().restartLoader(0, null, this);
				break;
			}
			case MENU_EDIT: {
				editDraft(mDraftItem);
				break;
			}
			case MENU_DELETE: {
				mResolver.delete(Drafts.CONTENT_URI, Drafts._ID + " = " + mSelectedId, null);
				getSupportLoaderManager().restartLoader(0, null, this);
				break;
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case MENU_HOME: {
				onBackPressed();
				break;
			}
			case MENU_DELETE_ALL: {
				mResolver.delete(Drafts.CONTENT_URI, null, null);
				getSupportLoaderManager().restartLoader(0, null, this);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		final IntentFilter filter = new IntentFilter(BROADCAST_DRAFTS_DATABASE_UPDATED);
		registerReceiver(mStatusReceiver, filter);
		mTwitterWrapper.clearNotification(NOTIFICATION_ID_DRAFTS);
		super.onStart();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mResolver = getContentResolver();
		mTwitterWrapper = getTwidereApplication().getTwitterWrapper();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mTextSize = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		setContentView(R.layout.base_list);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		mAdapter = new DraftsAdapter(this);
		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		getSupportLoaderManager().initLoader(0, null, this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final float text_size = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		mAdapter.setTextSize(text_size);
		if (mTextSize != text_size) {
			mTextSize = text_size;
			mListView.invalidateViews();
		}
	}

	@Override
	protected void onStop() {
		unregisterReceiver(mStatusReceiver);
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		super.onStop();
	}

	private void editDraft(final DraftItem draft) {
		final Intent intent = new Intent(INTENT_ACTION_EDIT_DRAFT);
		final Bundle bundle = new Bundle();
		bundle.putParcelable(INTENT_KEY_DRAFT, draft);
		intent.putExtras(bundle);
		mResolver.delete(Drafts.CONTENT_URI, Drafts._ID + " = " + draft._id, null);
		startActivityForResult(intent, REQUEST_COMPOSE);
	}

	private void sendDraft(final DraftItem draft) {
		if (draft == null) return;
		final Uri uri = draft.media_uri == null ? null : Uri.parse(draft.media_uri);
		mResolver.delete(Drafts.CONTENT_URI, Drafts._ID + " = " + draft._id, null);
		mTwitterWrapper.updateStatus(draft.account_ids, draft.text, draft.location, uri, draft.in_reply_to_status_id,
				draft.is_possibly_sensitive, draft.is_photo_attached && !draft.is_image_attached);
	}

	static class DraftsAdapter extends SimpleCursorAdapter implements ImageLoadingListener {

		private static final String[] FROM = new String[] { Drafts.TEXT };
		private static final int[] TO = new int[] { R.id.text };
		private final Map<View, String> mLoadingViewsMap = new HashMap<View, String>();
		private float mTextSize;
		private int mImageUriIdx;
		private ImageLoaderWrapper mImageLoader;

		public DraftsAdapter(final Context context) {
			super(context, R.layout.draft_list_item, null, FROM, TO, 0);
			mImageLoader = TwidereApplication.getInstance(context).getImageLoaderWrapper();
		}

		public Cursor swapCursor(final Cursor c) {
			if (c != null) {
				mImageUriIdx = c.getColumnIndex(Drafts.IMAGE_URI);
			}
			return super.swapCursor(c);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor) {
			super.bindView(view, context, cursor);
			final TextView text = (TextView) view.findViewById(R.id.text);
			final ImageView image = (ImageView) view.findViewById(R.id.image_preview);
			text.setTextSize(mTextSize);
			final boolean empty_content = text.length() == 0;
			if (empty_content) {
				text.setText(R.string.empty_content);
			}
			text.setTypeface(Typeface.DEFAULT, empty_content ? Typeface.ITALIC : Typeface.NORMAL);
			final String image_uri = cursor.getString(mImageUriIdx);
			final View image_preview_container = view.findViewById(R.id.image_preview_container);
			image_preview_container.setVisibility(TextUtils.isEmpty(image_uri) ? View.GONE : View.VISIBLE);
			if (!TextUtils.isEmpty(image_uri)) {
				mImageLoader.displayPreviewImage(image, image_uri, this);
			}
		}

		public void setTextSize(final float text_size) {
			mTextSize = text_size;
		}

		@Override
		public void onLoadingCancelled(final String url, final View view) {
			if (view == null || url == null || url.equals(mLoadingViewsMap.get(view))) return;
			mLoadingViewsMap.remove(view);
			final View parent = (View) view.getParent();
			final View progress = parent.findViewById(R.id.image_preview_progress);
			if (progress != null) {
				progress.setVisibility(View.GONE);
			}
		}

		@Override
		public void onLoadingComplete(final String url, final View view, final Bitmap bitmap) {
			if (view == null) return;
			mLoadingViewsMap.remove(view);
			final View parent = (View) view.getParent();
			final View progress = parent.findViewById(R.id.image_preview_progress);
			if (progress != null) {
				progress.setVisibility(View.GONE);
			}
		}

		@Override
		public void onLoadingFailed(final String url, final View view, final FailReason reason) {
			if (view == null) return;
			mLoadingViewsMap.remove(view);
			final View parent = (View) view.getParent();
			final View progress = parent.findViewById(R.id.image_preview_progress);
			if (progress != null) {
				progress.setVisibility(View.GONE);
			}
		}

		@Override
		public void onLoadingProgressChanged(final String imageUri, final View view, final int current, final int total) {
			if (total == 0) return;
			final View parent = (View) view.getParent();
			final ProgressBar progress = (ProgressBar) parent.findViewById(R.id.image_preview_progress);
			if (progress != null) {
				progress.setIndeterminate(false);
				progress.setProgress(100 * current / total);
			}
		}

		@Override
		public void onLoadingStarted(final String url, final View view) {
			if (view == null || url == null || url.equals(mLoadingViewsMap.get(view))) return;
			mLoadingViewsMap.put(view, url);
			final View parent = (View) view.getParent();
			final ProgressBar progress = (ProgressBar) parent.findViewById(R.id.image_preview_progress);
			if (progress != null) {
				progress.setVisibility(View.VISIBLE);
				progress.setIndeterminate(true);
				progress.setMax(100);
			}
		}
	}
}
