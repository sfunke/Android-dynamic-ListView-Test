package com.example.listdeletetest.recyclerview;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class SpacingItemDecoration extends RecyclerView.ItemDecoration {
	private final int mHorzSpacing;
	private final int mVertSpacing;

	public SpacingItemDecoration(int horzSpacing, int vertSpacing) {
		mHorzSpacing = horzSpacing;
		mVertSpacing = vertSpacing;
	}

	@Override
	public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
		outRect.left = mHorzSpacing / 2;
		outRect.right = mHorzSpacing / 2;
		outRect.top = mVertSpacing / 2;
		outRect.bottom = mVertSpacing / 2;
	}


}
