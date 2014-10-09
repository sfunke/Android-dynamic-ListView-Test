package com.example.listdeletetest.webservice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.listdeletetest.R;
import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.utils.RawResource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class FauxWebService implements WebService {
	private Context mContext;

	private List<Tweet> mTweets;

	private Handler mHandler = new Handler(Looper.getMainLooper());

	private Delegate mDelegate;

	public FauxWebService(Context context) {
		mContext = context;
		loadFromResource(R.raw.tweets);
	}

	private void loadFromResource(int resID) {
		if (mTweets != null) {
			return;
		}

		try {
			final JSONArray tweets = RawResource.getAsJSON(mContext, resID);
			mTweets = new ArrayList<Tweet>();

			final int count = tweets.length();
			long now = count; // <= fake timestamp to add to each tweet to allow since / before

			for (int i = 0; i < count; i++) {
				final JSONObject tweet = (JSONObject) tweets.get(i);
				mTweets.add(new Tweet(tweet, now));
				now -= 1;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setDelegate(Delegate delegate) {
		mDelegate = delegate;
	}


	@Override
	public void delete(List<Tweet> tweets) {
		synchronized (this) {
			mTweets.removeAll(tweets);
		}
	}

	@Override
	public void fetchBefore(final long timeStamp, final int limit) {
		Log.d("XXX", "fetchBefore timestamp: " + timeStamp);
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				synchronized (this) {
					List<Tweet> result = new ArrayList<Tweet>();
					Log.d("XXX", "fetchBefore lim: " + limit);
					Iterator<Tweet> iterator = mTweets.iterator();
					while(iterator.hasNext() && result.size() < limit) {
						Tweet item = iterator.next();
						if(item.getTimeStamp() < timeStamp) {
							Log.d("XXX", "adding item with timestamp: " + item.getTimeStamp());
							result.add(item);
						}
					}

					mDelegate.handleResultNext(result);
				}
			}
		}, 1000);
	}

	@Override
	public void fetchSince(final long timeStamp, final int limit) {
		Log.d("XXX", "fetchSince timestamp: " + timeStamp);
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				synchronized (this) {
					List<Tweet> result = new ArrayList<Tweet>();

					int lim = (int) Math.floor(Math.random() * (limit))+1; // <= randomize count of new items a bit
					Log.d("XXX", "fetchSince lim: " + lim);
					ListIterator<Tweet> iterator = mTweets.listIterator(mTweets.size());
					while(iterator.hasPrevious() && result.size() < lim) {
						Tweet item = iterator.previous();
						if(item.getTimeStamp() > timeStamp) {
							Log.d("XXX", "adding item with timestamp: " + item.getTimeStamp());
							result.add(item);
						}
					}

					mDelegate.handleResultNewest(result);
				}
			}
		}, 1000);

	}


}
