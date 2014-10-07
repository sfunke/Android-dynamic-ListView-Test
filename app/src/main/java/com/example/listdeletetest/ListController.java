package com.example.listdeletetest;

import com.example.listdeletetest.model.Tweet;

import java.util.ArrayList;
import java.util.List;

public class ListController implements WebService.Delegate {
	private final ArrayList<Tweet> mPrepareDeleteTweets;
	private boolean mIsBusy;

	public static interface RefreshCompleteDelegate {
		void handleRefreshComplete();
	}

	public static int INITIAL_COUNT = 10;
	public static int NEXT_REQUEST_LIMIT = 5;
	public static int NEWEST_REQUEST_LIMIT = 2;

	private WebService mWebService;
	private MyAdapter mAdapter;
	private List<Tweet> mTweets;
	private RefreshCompleteDelegate mRefreshCompleteDelegate = new RefreshCompleteDelegate() {
		@Override
		public void handleRefreshComplete() {}
	};

	public void setRefreshCompleteDelegate(RefreshCompleteDelegate refreshCompleteDelegate) {
		mRefreshCompleteDelegate = refreshCompleteDelegate;
	}

	public ListController(WebService webService, MyAdapter adapter) {
		mAdapter = adapter;
		mTweets = new ArrayList<Tweet>();
		mPrepareDeleteTweets = new ArrayList<Tweet>();

		mWebService = webService;
		mWebService.setDelegate(this);
	}



	public void fetchInitial() {
		if(mIsBusy)
			return;

		mAdapter.addAll(mTweets);

		if(mTweets.size() < INITIAL_COUNT) {
			fetchNext();
		}
	}

	public void fetchNext() {
		if(mIsBusy)
			return;

		mIsBusy = true;
		mWebService.fetchNext(NEXT_REQUEST_LIMIT);
	}

	public void refreshNewest() {
		if(mIsBusy)
			return;

		mIsBusy = true;
		mWebService.fetchNewest(NEWEST_REQUEST_LIMIT);
	}

	@Override
	public void handleResultNext(List<Tweet> tweets) {
		for(Tweet tweet : tweets) {
			mTweets.add(tweet);
		}
		mAdapter.replaceAll(mTweets);
//		mAdapter.addAll(mTweets);
		mRefreshCompleteDelegate.handleRefreshComplete();
		mIsBusy = false;
	}

	@Override
	public void handleResultNewest(List<Tweet> tweets) {
		for(Tweet tweet : tweets) {
			mTweets.add(0, tweet);
		}
		mAdapter.replaceAll(mTweets);
		mRefreshCompleteDelegate.handleRefreshComplete();
		mIsBusy = false;
	}

	public void prepareDelete(ArrayList<Tweet> selectedItems) {
		mPrepareDeleteTweets.addAll(selectedItems);
		mAdapter.makeInvisible(selectedItems);
	}

	public void doDelete() {
		if(mPrepareDeleteTweets.size() > 0) {
			// remove locally
			for(Tweet tweet : mPrepareDeleteTweets) {
				mTweets.remove(tweet);
			}

			// update adapter
			mAdapter.makeAllVisible(false);
			mAdapter.replaceAll(mTweets);

			// call to webservice
			mWebService.delete(mPrepareDeleteTweets);
		}
	}

	public void undoPrepareDelete() {
		mPrepareDeleteTweets.clear();
		mAdapter.makeAllVisible(true);
	}

}
