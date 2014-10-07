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
import java.util.List;

public class FauxWebService implements WebService {
	private Context mContext;

	private List<Tweet> mNewTweets;
	private List<Tweet> mTweets;

	private Handler mHandler = new Handler(Looper.getMainLooper());

	private Delegate mDelegate;

	public FauxWebService(Context context) {
		mContext = context;
		loadFromResource(R.raw.tweets2);
	}

	private void loadFromResource(int resID) {
		if (mTweets != null) {
			return;
		}

		try {
			final JSONArray tweets = RawResource.getAsJSON(mContext, resID);
			mTweets = new ArrayList<Tweet>();
			mNewTweets = new ArrayList<Tweet>();

			final int count = tweets.length();
			for (int i = 0; i < 20; i++) {
				final JSONObject tweet = (JSONObject) tweets.get(i);
				mNewTweets.add(new Tweet(tweet));
			}
			for (int i = 20; i < count; i++) {
				final JSONObject tweet = (JSONObject) tweets.get(i);
				mTweets.add(new Tweet(tweet));
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
	public void fetchNext(final int limit) {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {

				synchronized (this) {
					List<Tweet> result = new ArrayList<Tweet>();
					for (int i = 0; i < limit; i++) {
						try {
							Tweet tweet = mTweets.remove(0);
							result.add(tweet);
						} catch (Exception e) {

						}
					}
					mDelegate.handleResultNext(result);
				}

			}
		}, 1000);
	}

	@Override
	public void delete(List<Tweet> tweets) {
		mTweets.removeAll(tweets);
	}

	@Override
	public void fetchNewest(final int limit) {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				synchronized (this) {
					List<Tweet> result = new ArrayList<Tweet>();
					int lim = (int) Math.floor(Math.random() * (limit))+1;
					Log.d("XXX", "lim " + lim);
					for (int i = 0; i < lim; i++) {
						try {
							Tweet tweet = mNewTweets.remove(mNewTweets.size() - 1);
							result.add(tweet);
						} catch (Exception e) {
						}
					}
					mDelegate.handleResultNewest(result);
				}
			}
		}, 1000);
	}


}
