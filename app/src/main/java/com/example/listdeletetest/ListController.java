package com.example.listdeletetest;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.webservice.WebService;

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
	private ListAdapter mAdapter;
	private List<Tweet> mMasterList;
	private RefreshCompleteDelegate mRefreshCompleteDelegate;

	public void setRefreshCompleteDelegate(RefreshCompleteDelegate refreshCompleteDelegate) {
		mRefreshCompleteDelegate = refreshCompleteDelegate;
	}

	public ListController(WebService webService, ListAdapter adapter) {
		mAdapter = adapter;
		mMasterList = new ArrayList<Tweet>();
		mPrepareDeleteTweets = new ArrayList<Tweet>();

		mWebService = webService;
		mWebService.setDelegate(this);
	}


	public void fetchInitial() {
		if(mIsBusy)
			return;

		if(mMasterList.size() > 0) {
			mAdapter.replaceAll(mMasterList);
		}

		if(mMasterList.size() < INITIAL_COUNT) {
			mIsBusy = true;
			mWebService.fetchNext(INITIAL_COUNT);
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
			mMasterList.add(tweet);
		}
		mAdapter.replaceAll(mMasterList);
		mRefreshCompleteDelegate.handleRefreshComplete();
		mIsBusy = false;
	}


	@Override
	public void handleResultNewest(List<Tweet> tweets) {
		for(Tweet tweet : tweets) {
			mMasterList.add(0, tweet);
		}
		mAdapter.replaceAll(mMasterList);
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
				mMasterList.remove(tweet);
			}

			// update adapter
			mAdapter.makeAllVisibleAndNotify(false);
			mAdapter.replaceAll(mMasterList);

			// call to webservice
			mWebService.delete(mPrepareDeleteTweets);
		}
	}

	public void undoPrepareDelete() {
		mPrepareDeleteTweets.clear();
		mAdapter.makeAllVisibleAndNotify(true);
	}

}
