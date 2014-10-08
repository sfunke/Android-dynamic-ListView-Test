/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.listdeletetest.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.listdeletetest.R;
import com.example.listdeletetest.model.Tweet;
import com.squareup.picasso.Picasso;


public class TweetItemView extends RelativeLayout implements Checkable {
	private final ImageView mProfileImage;
	private final TextView mAuthorText;
	private final TextView mMessageText;
	private final ImageView mPostImage;
	private boolean mChecked;
	private Paint mDividerPaint;
	private final Paint mSelectionPaint;

	public TweetItemView(Context context) {
		super(context);
		LayoutInflater.from(context).inflate(R.layout.tweet_composite_view, this, true);
		int padding = getResources().getDimensionPixelSize(R.dimen.tweet_padding);
		setPadding(padding, padding, padding, padding);
		mProfileImage = (ImageView) findViewById(R.id.profile_image);
		mAuthorText = (TextView) findViewById(R.id.author_text);
		mMessageText = (TextView) findViewById(R.id.message_text);
		mPostImage = (ImageView) findViewById(R.id.post_image);

		// custom divider, since we disabled list divider
		mDividerPaint = new Paint();
		mDividerPaint.setColor(0xffb7b7b7);

		// cell selection
		mSelectionPaint = new Paint();
		mSelectionPaint.setColor(Color.argb(55, 255, 0, 0));
	}

	@Override
	public boolean shouldDelayChildPressedState() {
		return false;
	}

	public void update(Tweet tweet) {
		mAuthorText.setText(String.valueOf(tweet.getTimeStamp()) + " : " + tweet.getAuthorName());
		mMessageText.setText(tweet.getMessage());

		final Context context = getContext();
		Picasso.with(context)
				.load(tweet.getProfileImageUrl())
				.placeholder(R.drawable.tweet_placeholder_image)
				.error(R.drawable.tweet_placeholder_image)
				.into(mProfileImage);

		final boolean hasPostImage = !TextUtils.isEmpty(tweet.getPostImageUrl());
		mPostImage.setVisibility(hasPostImage ? View.VISIBLE : View.GONE);
		if (hasPostImage) {
			Picasso.with(context)
					.load(tweet.getPostImageUrl())
					.placeholder(R.drawable.tweet_placeholder_image)
					.error(R.drawable.tweet_placeholder_image)
					.into(mPostImage);
		}
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		int ch = canvas.getHeight();
		int cw = canvas.getWidth();
		if (mChecked) {
			// checked state
			canvas.drawRect(0, 0, cw, ch - 1, mSelectionPaint);
		}

		// divider line
		canvas.drawRect(0, ch - 1, cw, ch, mDividerPaint);
	}

	@Override
	public void setChecked(boolean checked) {
		mChecked = checked;
		invalidate();
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void toggle() {
		setChecked(!mChecked);
	}
}
