package com.example.listdeletetest.webservice;

import com.example.listdeletetest.model.Tweet;

import java.util.List;

public interface WebService {

	static interface Delegate {
		void handleResultNext(List<Tweet> tweets);
		void handleResultNewest(List<Tweet> tweets);
	}

	public void setDelegate(Delegate delegate);

	void delete(List<Tweet> tweets);

	void fetchBefore(long timeStamp, int limit);

	void fetchSince(long timeStamp, int limit);

}
