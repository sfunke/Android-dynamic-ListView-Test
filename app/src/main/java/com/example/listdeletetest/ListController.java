package com.example.listdeletetest;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.webservice.WebService;

import java.util.ArrayList;
import java.util.List;

public class ListController implements WebService.Delegate {
	private final ArrayList<Tweet> mPrepareDeleteTweets;
	private boolean mIsBusy;

	public static interface RequestStateChangeDelegate {
		void handleRequestStart();

		void handleRequestComplete();
	}

	public static int INITIAL_COUNT = 20;
	public static int NEXT_REQUEST_LIMIT = 10;
	public static int NEWEST_REQUEST_LIMIT = 2;

	private WebService mWebService;
	private ListAdapter mAdapter;
	private List<Tweet> mMasterList;
	private RequestStateChangeDelegate mRequestStateChangeDelegate;

	public void setRequestStateChangeDelegate(RequestStateChangeDelegate requestStateChangeDelegate) {
		mRequestStateChangeDelegate = requestStateChangeDelegate;
	}

	public ListController(WebService webService, ListAdapter adapter) {
		mAdapter = adapter;
		mMasterList = new ArrayList<Tweet>();
		mPrepareDeleteTweets = new ArrayList<Tweet>();

		mWebService = webService;
		mWebService.setDelegate(this);
	}


	public void fetchInitial() {
		if (mIsBusy)
			return;

		if (mMasterList.size() > 0) {
			mAdapter.replaceAll(mMasterList);
		}

		if (mMasterList.size() < INITIAL_COUNT) {
			mIsBusy = true;
			mWebService.fetchBefore(81, INITIAL_COUNT); // <= dummy timestamp, 20 before end of master list
		}
	}

	public void fetchBottom() {
		if (mIsBusy)
			return;

		Tweet lastItem = mMasterList.get(mMasterList.size() - 1);
		if(lastItem != null) {
			mIsBusy = true;
			mRequestStateChangeDelegate.handleRequestStart();
			mWebService.fetchBefore(lastItem.getTimeStamp(), NEXT_REQUEST_LIMIT); // <= fetch all older before existing last item
		}
	}

	public void fetchTop() {
		if (mIsBusy)
			return;

		Tweet firstItem = mMasterList.get(0);
		if(firstItem != null) {
			mIsBusy = true;
			mRequestStateChangeDelegate.handleRequestStart();
			mWebService.fetchSince(firstItem.getTimeStamp(), NEWEST_REQUEST_LIMIT); // <= fetch all new since existing first item
		}
	}


	@Override
	public void handleResultNext(List<Tweet> tweets) {
		if (tweets != null && tweets.size() > 0) { // <= prevent adapter notifychanged when there are no new items
			for (Tweet tweet : tweets) {
				mMasterList.add(tweet);
			}
			mAdapter.replaceAll(mMasterList);
		}
		mRequestStateChangeDelegate.handleRequestComplete();
		mIsBusy = false;
	}


	@Override
	public void handleResultNewest(List<Tweet> tweets) {
		for (Tweet tweet : tweets) {
			mMasterList.add(0, tweet);
		}
		mAdapter.replaceAll(mMasterList);
		mRequestStateChangeDelegate.handleRequestComplete();
		mIsBusy = false;
	}

	public void prepareDelete(ArrayList<Tweet> selectedItems) {
		mPrepareDeleteTweets.addAll(selectedItems);
		mAdapter.makeInvisible(selectedItems);
	}

	public void doDelete() {
		if (mPrepareDeleteTweets.size() > 0) {
			// remove locally
			for (Tweet tweet : mPrepareDeleteTweets) {
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
