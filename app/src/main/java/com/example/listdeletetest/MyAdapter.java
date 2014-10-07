package com.example.listdeletetest;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.widget.TweetItemView;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MyAdapter extends ArrayAdapter<Tweet> {
	private final static int VIEW_TYPE_INVISIBLE = 0;
	private static int VIEW_TYPE_DEFAULT = 1;

	private LinkedList<Tweet> mList;
	private Set<Integer> mInvisibleItems = new HashSet<Integer>();

	public static MyAdapter istantiate(Context context) {
		return new MyAdapter(context, new LinkedList<Tweet>());
	}

	private MyAdapter(Context context, LinkedList<Tweet> list) {
		super(context, 0, list);
		mList = list;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		switch (getItemViewType(position)) {
			case VIEW_TYPE_INVISIBLE:
				if (convertView == null) {
					convertView = new View(getContext());
				}
				return convertView;

			default:
				TweetItemView itemView;
				if (convertView == null) {
					itemView = new TweetItemView(getContext());
				} else {
					itemView = (TweetItemView) convertView;
				}
				Tweet tweet = getItem(position);
				itemView.update(tweet);
				return itemView;
		}
	}

	@Override
	public int getItemViewType(int position) {
		Tweet item = getItem(position);
		if (mInvisibleItems.contains(item.hashCode())) {
			return VIEW_TYPE_INVISIBLE;
		}
		return VIEW_TYPE_DEFAULT;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public long getItemId(int position) {
		Tweet item = getItem(position);
		int i = item.hashCode();
		return i;
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	public void replaceAll(List<Tweet> tweets) {
		synchronized (this) {
			mList.clear();
			mList.addAll(tweets);
			notifyDataSetChanged();
		}
	}

	public void makeInvisible(List<Tweet> items) {
		for (Tweet tweet : items) {
			mInvisibleItems.add(tweet.hashCode());
		}
		notifyDataSetChanged();
	}

	public void makeAllVisible() {
		mInvisibleItems.clear();
		notifyDataSetChanged();
	}
}
