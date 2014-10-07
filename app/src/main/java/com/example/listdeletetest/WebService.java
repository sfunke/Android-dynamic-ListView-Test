package com.example.listdeletetest;

import com.example.listdeletetest.model.Tweet;

import java.util.List;

public interface WebService {

	static interface Delegate {
		void handleResultNext(List<Tweet> tweets);
		void handleResultNewest(List<Tweet> tweets);
	}

	public void setDelegate(Delegate delegate);

	void fetchNewest(int limit);

	void fetchNext(int limit);

	void delete(List<Tweet> tweets);

}
