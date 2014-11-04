package com.example.listdeletetest.adapter;

import android.content.Context;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.widget.TweetItemView;

public class TweetItemPresenter implements IPresenter<TweetItemView, Tweet> {
	@Override
	public TweetItemView getView(Context context) {
		return new TweetItemView(context);
	}

	@Override
	public void present(TweetItemView itemView, Tweet item) {
		itemView.update(item);
	}
}
