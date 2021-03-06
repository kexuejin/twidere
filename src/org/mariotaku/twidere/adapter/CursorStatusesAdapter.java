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

package org.mariotaku.twidere.adapter;

import static android.text.format.DateUtils.getRelativeTimeSpanString;
import static org.mariotaku.twidere.util.Utils.configBaseAdapter;
import static org.mariotaku.twidere.util.Utils.findStatusInDatabases;
import static org.mariotaku.twidere.util.Utils.formatSameDayTime;
import static org.mariotaku.twidere.util.Utils.getAccountColor;
import static org.mariotaku.twidere.util.Utils.getAccountScreenName;
import static org.mariotaku.twidere.util.Utils.getImagePreviewDisplayOptionInt;
import static org.mariotaku.twidere.util.Utils.getNameDisplayOptionInt;
import static org.mariotaku.twidere.util.Utils.getStatusBackground;
import static org.mariotaku.twidere.util.Utils.getUserColor;
import static org.mariotaku.twidere.util.Utils.isFiltered;
import static org.mariotaku.twidere.util.Utils.openImage;
import static org.mariotaku.twidere.util.Utils.openUserProfile;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.iface.IStatusesAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.model.PreviewImage;
import org.mariotaku.twidere.model.StatusCursorIndices;
import org.mariotaku.twidere.util.ImageLoaderWrapper;
import org.mariotaku.twidere.util.MultiSelectManager;
import org.mariotaku.twidere.util.OnLinkClickHandler;
import org.mariotaku.twidere.util.TwidereLinkify;
import org.mariotaku.twidere.view.holder.StatusViewHolder;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;

public class CursorStatusesAdapter extends SimpleCursorAdapter implements IStatusesAdapter<Cursor>, OnClickListener,
		ImageLoadingListener {

	private final Context mContext;
	private final ImageLoaderWrapper mImageLoader;
	private final MultiSelectManager mMultiSelectManager;
	private final TwidereLinkify mLinkify;
	private final SQLiteDatabase mDatabase;
	private final Map<View, String> mLoadingViewsMap = new HashMap<View, String>();

	private boolean mDisplayProfileImage, mShowAccountColor, mShowAbsoluteTime, mGapDisallowed, mMultiSelectEnabled,
			mMentionsHighlightDisabled, mDisplaySensitiveContents, mIndicateMyStatusDisabled, mLinkHighlightingEnabled,
			mIsLastItemFiltered, mFiltersEnabled = true;
	private float mTextSize;
	private int mNameDisplayOption, mImagePreviewDisplayOption, mLinkHighlightStyle;
	private boolean mFilterIgnoreSource, mFilterIgnoreScreenName, mFilterIgnoreTextHtml, mFilterIgnoreTextPlain;

	private StatusCursorIndices mIndices;

	public CursorStatusesAdapter(final Context context) {
		super(context, R.layout.status_list_item, null, new String[0], new int[0], 0);
		mContext = context;
		final TwidereApplication application = TwidereApplication.getInstance(context);
		mMultiSelectManager = application.getMultiSelectManager();
		mImageLoader = application.getImageLoaderWrapper();
		mDatabase = application.getSQLiteDatabase();
		mLinkify = new TwidereLinkify(new OnLinkClickHandler(mContext));
		configBaseAdapter(context, this);
	}

	@Override
	public void bindView(final View view, final Context context, final Cursor cursor) {
		final int position = cursor.getPosition();
		final StatusViewHolder holder = (StatusViewHolder) view.getTag();

		// Clear images in prder to prevent images in recycled view shown.
		// holder.profile_image.setImageDrawable(null);
		// holder.my_profile_image.setImageDrawable(null);
		// holder.image_preview.setImageDrawable(null);

		final boolean is_gap = cursor.getShort(mIndices.is_gap) == 1;
		final boolean show_gap = is_gap && !mGapDisallowed && position != getCount() - 1;

		holder.setShowAsGap(show_gap);

		if (!show_gap) {

			final long account_id = cursor.getLong(mIndices.account_id);
			final long user_id = cursor.getLong(mIndices.user_id);
			final long status_id = cursor.getLong(mIndices.status_id);
			final long status_timestamp = cursor.getLong(mIndices.status_timestamp);
			final long retweet_count = cursor.getLong(mIndices.retweet_count);

			final String retweeted_by_name = cursor.getString(mIndices.retweeted_by_name);
			final String retweeted_by_screen_name = cursor.getString(mIndices.retweeted_by_screen_name);
			final String text = mLinkHighlightingEnabled ? cursor.getString(mIndices.text_html) : cursor
					.getString(mIndices.text_unescaped);
			final String screen_name = cursor.getString(mIndices.user_screen_name);
			final String name = cursor.getString(mIndices.user_name);
			final String in_reply_to_screen_name = cursor.getString(mIndices.in_reply_to_screen_name);
			final String account_screen_name = getAccountScreenName(mContext, account_id);
			final String image_preview_url = cursor.getString(mIndices.image_preview_url);

			// Tweet type (favorite/location/media)
			final boolean is_favorite = cursor.getShort(mIndices.is_favorite) == 1;
			final boolean has_location = !TextUtils.isEmpty(cursor.getString(mIndices.location));
			final boolean is_possibly_sensitive = cursor.getInt(mIndices.is_possibly_sensitive) == 1;
			final boolean has_media = image_preview_url != null;

			// User type (protected/verified)
			final boolean is_verified = cursor.getShort(mIndices.is_verified) == 1;
			final boolean is_protected = cursor.getShort(mIndices.is_protected) == 1;

			final boolean is_retweet = !TextUtils.isEmpty(retweeted_by_name)
					&& cursor.getShort(mIndices.is_retweet) == 1;
			final boolean is_reply = !TextUtils.isEmpty(in_reply_to_screen_name)
					&& cursor.getLong(mIndices.in_reply_to_status_id) > 0;
			final boolean is_mention = TextUtils.isEmpty(text) || TextUtils.isEmpty(account_screen_name) ? false : text
					.toLowerCase(Locale.US).contains('@' + account_screen_name.toLowerCase(Locale.US));
			final boolean is_my_status = account_id == user_id;

			if (mMultiSelectEnabled) {
				holder.setSelected(mMultiSelectManager.isStatusSelected(status_id));
			} else {
				holder.setSelected(false);
			}

			holder.setUserColor(getUserColor(mContext, user_id));
			holder.setHighlightColor(getStatusBackground(mMentionsHighlightDisabled ? false : is_mention, is_favorite,
					is_retweet));

			holder.setAccountColorEnabled(mShowAccountColor);

			if (mShowAccountColor) {
				holder.setAccountColor(getAccountColor(mContext, account_id));
			}

			holder.setTextSize(mTextSize);

			holder.setIsMyStatus(is_my_status && !mIndicateMyStatusDisabled);
			if (mLinkHighlightingEnabled) {
				holder.text.setText(Html.fromHtml(text));
				mLinkify.applyAllLinks(holder.text, account_id, is_possibly_sensitive);
			} else {
				holder.text.setText(text);
			}
			holder.text.setMovementMethod(null);
			holder.setUserType(is_verified, is_protected);
			holder.setNameDisplayOption(mNameDisplayOption);
			holder.setName(name, screen_name);
			if (mLinkHighlightingEnabled) {
				mLinkify.applyUserProfileLink(holder.name, account_id, user_id, screen_name);
				mLinkify.applyUserProfileLink(holder.screen_name, account_id, user_id, screen_name);
				holder.name.setMovementMethod(null);
				holder.screen_name.setMovementMethod(null);
			}
			if (mShowAbsoluteTime) {
				holder.time.setText(formatSameDayTime(context, status_timestamp));
			} else {
				holder.time.setText(getRelativeTimeSpanString(status_timestamp));
			}
			holder.setStatusType(is_favorite, has_location, has_media, is_possibly_sensitive);

			holder.setIsReplyRetweet(is_reply, is_retweet);
			if (is_retweet) {
				holder.setRetweetedBy(retweet_count, retweeted_by_name, retweeted_by_screen_name);
			} else if (is_reply) {
				holder.setReplyTo(in_reply_to_screen_name);
			}

			if (mDisplayProfileImage) {
				final String profile_image_url = cursor.getString(mIndices.user_profile_image_url);
				mImageLoader.displayProfileImage(holder.my_profile_image, profile_image_url);
				mImageLoader.displayProfileImage(holder.profile_image, profile_image_url);
				holder.profile_image.setTag(position);
				holder.my_profile_image.setTag(position);
			} else {
				holder.profile_image.setVisibility(View.GONE);
				holder.my_profile_image.setVisibility(View.GONE);
			}
			final boolean has_preview = mImagePreviewDisplayOption != IMAGE_PREVIEW_DISPLAY_OPTION_CODE_NONE
					&& has_media;
			holder.image_preview_container.setVisibility(has_preview ? View.VISIBLE : View.GONE);
			if (has_preview) {
				holder.setImagePreviewDisplayOption(mImagePreviewDisplayOption);
				if (is_possibly_sensitive && !mDisplaySensitiveContents) {
					holder.image_preview.setImageResource(R.drawable.image_preview_nsfw);
					holder.image_preview_progress.setVisibility(View.GONE);
				} else if (!image_preview_url.equals(mLoadingViewsMap.get(holder.image_preview))) {
					mImageLoader.displayPreviewImage(holder.image_preview, image_preview_url, this);
				}
				holder.image_preview.setTag(position);
			}
		}
	}

	@Override
	public long findItemIdByPosition(final int position) {
		if (position >= 0 && position < getCount()) return getItem(position).getLong(mIndices.status_id);
		return -1;
	}

	@Override
	public int findItemPositionByStatusId(final long status_id) {
		final int count = getCount();
		for (int i = 0; i < count; i++) {
			if (getItem(i).getLong(mIndices.status_id) == status_id) return i;
		}
		return -1;
	}

	@Override
	public int getCount() {
		final int count = super.getCount();
		return mFiltersEnabled && mIsLastItemFiltered && count > 0 ? count - 1 : count;
	}

	@Override
	public Cursor getItem(final int position) {
		return (Cursor) super.getItem(position);
	}

	@Override
	public ParcelableStatus getLastStatus() {
		final Cursor cur = getCursor();
		if (cur == null || cur.getCount() == 0) return null;
		cur.moveToLast();
		final long account_id = cur.getLong(mIndices.account_id);
		final long status_id = cur.getLong(mIndices.status_id);
		return findStatusInDatabases(mContext, account_id, status_id);
	}

	@Override
	public ParcelableStatus getStatus(final int position) {
		final Cursor cur = getItem(position);
		final long account_id = cur.getLong(mIndices.account_id);
		final long status_id = cur.getLong(mIndices.status_id);
		return findStatusInDatabases(mContext, account_id, status_id);
	}

	@Override
	public boolean isLastItemFiltered() {
		return mFiltersEnabled && mIsLastItemFiltered;
	}

	@Override
	public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
		final View view = super.newView(context, cursor, parent);
		final Object tag = view.getTag();
		if (!(tag instanceof StatusViewHolder)) {
			final StatusViewHolder holder = new StatusViewHolder(view);
			holder.profile_image.setOnClickListener(this);
			holder.my_profile_image.setOnClickListener(this);
			holder.image_preview.setOnClickListener(this);
			view.setTag(holder);
		}
		return view;
	}

	@Override
	public void onClick(final View view) {
		if (mMultiSelectEnabled) return;
		final Object tag = view.getTag();
		final ParcelableStatus status = tag instanceof Integer ? getStatus((Integer) tag) : null;
		if (status == null) return;
		switch (view.getId()) {
			case R.id.image_preview: {
				final PreviewImage spec = PreviewImage.getAllAvailableImage(status.image_original_url, true);
				if (spec != null) {
					openImage(mContext, spec.image_full_url, spec.image_original_url, status.is_possibly_sensitive);
				} else {
					openImage(mContext, status.image_original_url, null, status.is_possibly_sensitive);
				}
				break;
			}
			case R.id.my_profile_image:
			case R.id.profile_image: {
				if (mContext instanceof Activity) {
					openUserProfile((Activity) mContext, status.account_id, status.user_id, status.user_screen_name);
				}
				break;
			}
		}
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

	@Override
	public void setData(final Cursor data) {
		swapCursor(data);
	}

	@Override
	public void setDisplayProfileImage(final boolean display) {
		if (display == mDisplayProfileImage) return;
		mDisplayProfileImage = display;
		notifyDataSetChanged();
	}

	@Override
	public void setDisplaySensitiveContents(final boolean display) {
		if (display == mDisplaySensitiveContents) return;
		mDisplaySensitiveContents = display;
		notifyDataSetChanged();
	}

	@Override
	public void setFiltersEnabled(final boolean enabled) {
		if (mFiltersEnabled == enabled) return;
		mFiltersEnabled = enabled;
		rebuildFilterInfo();
		notifyDataSetChanged();
	}

	@Override
	public void setGapDisallowed(final boolean disallowed) {
		if (mGapDisallowed == disallowed) return;
		mGapDisallowed = disallowed;
		notifyDataSetChanged();
	}

	@Override
	public void setIgnoredFilterFields(final boolean text_plain, final boolean text_html, final boolean screen_name,
			final boolean source) {
		mFilterIgnoreTextPlain = text_plain;
		mFilterIgnoreTextHtml = text_html;
		mFilterIgnoreScreenName = screen_name;
		mFilterIgnoreSource = source;
		rebuildFilterInfo();
		notifyDataSetChanged();
	}

	@Override
	public void setImagePreviewDisplayOption(final String option) {
		final int option_int = getImagePreviewDisplayOptionInt(option);
		if (option_int == mImagePreviewDisplayOption) return;
		mImagePreviewDisplayOption = option_int;
		notifyDataSetChanged();
	}

	@Override
	public void setIndicateMyStatusDisabled(final boolean disable) {
		if (mIndicateMyStatusDisabled == disable) return;
		mIndicateMyStatusDisabled = disable;
		notifyDataSetChanged();
	}

	@Override
	public void setLinkHightlightingEnabled(final boolean enable) {
		if (mLinkHighlightingEnabled == enable) return;
		mLinkHighlightingEnabled = enable;
		notifyDataSetChanged();
	}

	@Override
	public void setLinkUnderlineOnly(final boolean underline_only) {
		final int style = underline_only ? TwidereLinkify.HIGHLIGHT_STYLE_UNDERLINE
				: TwidereLinkify.HIGHLIGHT_STYLE_COLOR;
		if (mLinkHighlightStyle == style) return;
		mLinkify.setHighlightStyle(style);
		mLinkHighlightStyle = style;
		notifyDataSetChanged();
	}

	@Override
	public void setMentionsHightlightDisabled(final boolean disable) {
		if (disable == mMentionsHighlightDisabled) return;
		mMentionsHighlightDisabled = disable;
		notifyDataSetChanged();
	}

	@Override
	public void setMultiSelectEnabled(final boolean multi) {
		if (mMultiSelectEnabled == multi) return;
		mMultiSelectEnabled = multi;
		notifyDataSetChanged();
	}

	@Override
	public void setNameDisplayOption(final String option) {
		final int option_int = getNameDisplayOptionInt(option);
		if (option_int == mNameDisplayOption) return;
		mNameDisplayOption = option_int;
		notifyDataSetChanged();
	}

	@Override
	public void setShowAbsoluteTime(final boolean show) {
		if (show == mShowAbsoluteTime) return;
		mShowAbsoluteTime = show;
		notifyDataSetChanged();
	}

	@Override
	public void setShowAccountColor(final boolean show) {
		if (show == mShowAccountColor) return;
		mShowAccountColor = show;
		notifyDataSetChanged();
	}

	@Override
	public void setTextSize(final float text_size) {
		if (text_size == mTextSize) return;
		mTextSize = text_size;
		notifyDataSetChanged();
	}

	@Override
	public Cursor swapCursor(final Cursor cursor) {
		mIndices = cursor != null ? new StatusCursorIndices(cursor) : null;
		rebuildFilterInfo();
		return super.swapCursor(cursor);
	}

	private void rebuildFilterInfo() {
		final Cursor cursor = getCursor();
		if (cursor != null && mIndices != null && cursor.getCount() > 0) {
			if (cursor.getCount() > 0) {
				cursor.moveToLast();
				final String text_plain = mFilterIgnoreTextPlain ? null : cursor.getString(mIndices.text_plain);
				final String text_html = mFilterIgnoreTextHtml ? null : cursor.getString(mIndices.text_html);
				final String screen_name = mFilterIgnoreScreenName ? null : cursor.getString(mIndices.user_screen_name);
				final String source = mFilterIgnoreSource ? null : cursor.getString(mIndices.source);
				mIsLastItemFiltered = isFiltered(mDatabase, text_plain, text_html, screen_name, source);
			} else {
				mIsLastItemFiltered = false;
			}
		} else {
			mIsLastItemFiltered = false;
		}
	}

}
