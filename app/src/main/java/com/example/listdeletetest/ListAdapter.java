package com.example.listdeletetest;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.widget.TweetItemView;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.ItemViewHolder> {
	private final static int VIEW_TYPE_INVISIBLE = 0;
	private static int VIEW_TYPE_DEFAULT = 1;

	private LinkedList<Tweet> mList;
	private Set<Integer> mInvisibleItems = new HashSet<Integer>();
	private Context mContext;

	public static ListAdapter instantiate(Context context) {
		return new ListAdapter(context, new LinkedList<Tweet>());
	}

	private ListAdapter(Context context, LinkedList<Tweet> list) {
		super();
		setHasStableIds(true);
		mContext = context;
		mList = list;
	}

	@Override
	public int getItemViewType(int position) {
		Tweet item = mList.get(position);
		if (mInvisibleItems.contains(item.hashCode())) {
			return VIEW_TYPE_INVISIBLE;
		}
		return VIEW_TYPE_DEFAULT;
	}

	@Override
	public long getItemId(int position) {
		Tweet item = mList.get(position);
		int i = item.hashCode();
		return i;
	}


	//----------------------------------
	//  RecyclerView
	//----------------------------------
	@Override
	public ItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
		switch (viewType) {
			case VIEW_TYPE_INVISIBLE:
					View view = new View(mContext);
					view.requestLayout();
					view.setVisibility(View.GONE);
					return new ItemViewHolder(null, view);

			default:
				TweetItemView itemView = new TweetItemView(mContext);
				itemView.setVisibility(View.VISIBLE);
				return new ItemViewHolder(itemView, null);
		}
	}

	@Override
	public void onBindViewHolder(ItemViewHolder viewHolder, int position) {
		if(viewHolder.itemView != null) {
			TweetItemView itemView = (TweetItemView)viewHolder.itemView;
			Tweet tweet = mList.get(position);
			itemView.update(tweet);
		}
	}


	@Override
	public int getItemCount() {
		return mList.size();
	}


	//----------------------------------
	//  Custom
	//----------------------------------
	public void replaceAll(List<Tweet> tweets) {
		if(tweets == null || tweets.size() == 0)
			return;

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

	public void makeAllVisibleAndNotify(boolean notifyChanged) {
		mInvisibleItems.clear();
		if(notifyChanged) {
			notifyDataSetChanged();
		}
	}


	//----------------------------------
	//  ViewHolders
	//----------------------------------
	public static class ItemViewHolder extends RecyclerView.ViewHolder {
		private final View mNullView;

		public ItemViewHolder(View itemView, View nullView) {
			super(itemView);
			mNullView = nullView;
		}

		public View getNullView() {
			return mNullView;
		}
	}
}
