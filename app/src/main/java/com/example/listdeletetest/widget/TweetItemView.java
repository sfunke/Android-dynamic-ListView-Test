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
import android.view.LayoutInflater;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.listdeletetest.R;
import com.example.listdeletetest.model.Tweet;
import com.squareup.picasso.Picasso;


public class TweetItemView extends FrameLayout implements Checkable {
	private final ImageView mProfileImage;
	private final TextView mAuthorText;
	private final TextView mDebugText;
	private boolean mChecked;
	private final Paint mPressedPaint;
	private final Paint mSelectionPaint;
	private float mRatio = 0f;
	private int mPicW;
	private int mPicH;

	public TweetItemView(Context context) {
		super(context);
		LayoutInflater.from(context).inflate(R.layout.tweet_composite_view_staggered, this, true);

		mProfileImage = (ImageView) findViewById(R.id.profile_image);
		mAuthorText = (TextView) findViewById(R.id.author_text);
		mDebugText = (TextView) findViewById(R.id.debug);
//		mDebugText.setVisibility(GONE);

		// cell selection
		mSelectionPaint = new Paint();
		mSelectionPaint.setColor(Color.argb(55, 255, 0, 0));

		mPressedPaint = new Paint();
		mPressedPaint.setColor(Color.argb(55, 0, 0, 255));
	}

	@Override
	public boolean shouldDelayChildPressedState() {
		return false;
	}

	public void update(Tweet tweet) {
		mAuthorText.setText(String.valueOf(tweet.getTimeStamp()) + " : " + tweet.getAuthorName());

		// hack: get width and height of image url, for Ratio:
		// e.g. http://lorempixel.com/318/770/people/6"
		String profileImageUrl = tweet.getProfileImageUrl();
		String[] split = profileImageUrl.split("/");
		mPicW = Integer.parseInt(split[3]);
		mPicH = Integer.parseInt(split[4]);

		mRatio = (float) mPicH / (float) mPicW;


		requestLayout(); // <= IMPORTANT! sets correct ratio of this tile


		// load contents
		final Context context = getContext();
		Picasso picasso = Picasso.with(context);
		picasso.setIndicatorsEnabled(true);
		picasso
				.load(profileImageUrl)
				.placeholder(R.drawable.tweet_placeholder_image)
				.error(R.drawable.tweet_placeholder_image)
				.into(mProfileImage);

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int measuredWidth = getMeasuredWidth();
		if (mRatio > 0) {
			int measuredHeight = (int) (measuredWidth * mRatio);
			int newHeightSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
			int newWidthSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
			mDebugText.setText(
					String.format("pic: %d | %d", mPicW, mPicH) + "\n" +
					String.format("meas: %d | %d", measuredWidth, measuredHeight)
			);
			super.onMeasure(newWidthSpec, newHeightSpec);
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
		if (isPressed()) {
//			checked state
			canvas.drawRect(0, 0, cw, ch - 1, mPressedPaint);
		}
	}

	@Override
	public void setPressed(boolean pressed) {
		super.setPressed(pressed);
		invalidate();
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
