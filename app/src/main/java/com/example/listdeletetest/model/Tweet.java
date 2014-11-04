/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.listdeletetest.model;

import com.example.listdeletetest.adapter.IAdapterItem;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet implements IAdapterItem {
    private static final String JSON_ID = "id";
    private static final String JSON_AUTHOR_NAME = "authorName";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_PROFILE_IMAGE_URL = "profileImageUrl";
    private static final String JSON_POST_IMAGE_URL = "postImageUrl";

    private final String mId;
    private final String mAuthorName;
    private final String mMessage;
    private final String mProfileImageUrl;
    private final String mPostImageUrl;
	private final long mTimeStamp;
	private int mHashCode;
//	private boolean mIsSelected;

	public Tweet(JSONObject jsonTweet, long timeStamp) throws JSONException {
        mId = jsonTweet.getString(JSON_ID);
        mMessage = jsonTweet.getString(JSON_MESSAGE);
        mAuthorName = jsonTweet.getString(JSON_AUTHOR_NAME);
        mProfileImageUrl = jsonTweet.getString(JSON_PROFILE_IMAGE_URL);
        mPostImageUrl = jsonTweet.isNull(JSON_POST_IMAGE_URL) ? null : jsonTweet.optString(JSON_POST_IMAGE_URL, null);

		mTimeStamp = timeStamp;
    }

	public long getTimeStamp() {
		return mTimeStamp;
	}

	public String getId() {
        return mId;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getAuthorName() {
        return mAuthorName;
    }

    public String getProfileImageUrl() {
        return mProfileImageUrl;
    }

    public String getPostImageUrl() {
        return mPostImageUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Tweet)) {
            return false;
        }

        Tweet other = (Tweet) o;
        return (mId == other.mId);
    }

	@Override
	public int hashCode() {
		return mId.hashCode();
	}

	@Override
    public String toString() {
        return "Tweet@" + mId;
    }

	//----------------------------------
	//  IAdapterItem
	//----------------------------------
	@Override
	public long getItemId() {
		return hashCode();
	}
}
