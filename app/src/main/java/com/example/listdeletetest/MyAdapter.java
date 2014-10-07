package com.example.listdeletetest;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filterable;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.widget.TweetItemView;

import java.util.LinkedList;
import java.util.List;

public class MyAdapter extends ArrayAdapter<Tweet> implements Filterable {
	private LinkedList<Tweet> mList;

	public static MyAdapter istantiate(Context context) {
		return new MyAdapter(context, new LinkedList<Tweet>());
	}

	private MyAdapter(Context context, LinkedList<Tweet> list) {
		super(context, 0, list);
		mList = list;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final TweetItemView itemView;
		if(convertView == null) {
			itemView = new TweetItemView(getContext());
		} else {
			itemView = (TweetItemView) convertView;
		}
		Tweet tweet = getItem(position);
		itemView.update(tweet);

		return itemView;
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).getId();
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	public void addAllToHead(List<Tweet> tweets) {
		for(Tweet tweet : tweets) {
			mList.add(0, tweet);
		}
		notifyDataSetChanged();
	}

	public void replaceAll(List<Tweet> tweets) {
		mList.clear();
		mList.addAll(tweets);
		notifyDataSetChanged();
	}
}
