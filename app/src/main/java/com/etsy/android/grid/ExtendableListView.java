/*
 * Copyright (c) 2013 Etsy
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.etsy.android.grid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * An extendable implementation of the Android {@link android.widget.ListView}
 * <p/>
 * This is partly inspired by the incomplete StaggeredGridView supplied in the
 * Android 4.2+ source & the {@link android.widget.AbsListView} & {@link android.widget.ListView} source;
 * however this is intended to have a smaller simplified
 * scope of functionality and hopefully therefore be a workable solution.
 * <p/>
 * Some things that this doesn't support (yet)
 * - Dividers (We don't use them in our Etsy grid)
 * - Edge effect
 * - Fading edge - yuck
 * - Item selection
 * - Focus
 * <p/>
 * Note: we only really extend {@link android.widget.AbsListView} so we can appear to be one of its direct subclasses.
 * However most of the code we need to modify is either 1. hidden or 2. package private
 * So a lot of it's code and some {@link android.widget.AdapterView} code is repeated here
 * Be careful with this - not everything may be how you expect if you assume this to be
 * a regular old {@link android.widget.ListView}
 */
public abstract class ExtendableListView extends AbsListView {

	private static final String TAG = "ExtendableListView";

	private static final boolean DBG = false;

	private static final int TOUCH_MODE_IDLE = 0;
	private static final int TOUCH_MODE_SCROLLING = 1;
	private static final int TOUCH_MODE_FLINGING = 2;
	private static final int TOUCH_MODE_DOWN = 3;
	private static final int TOUCH_MODE_TAP = 4;
	private static final int TOUCH_MODE_DONE_WAITING = 5;

	private static final int INVALID_POINTER = -1;

	// Layout using our default existing state
	private static final int LAYOUT_NORMAL = 0;
	// Layout from the first item down
	private static final int LAYOUT_FORCE_TOP = 1;
	// Layout from the saved instance state data
	private static final int LAYOUT_SYNC = 2;

	private int mLayoutMode;

	private int mTouchMode;
	private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

	// Rectangle used for hit testing children
	// private Rect mTouchFrame;
	// TODO : ItemClick support from AdapterView

	// For managing scrolling
	private VelocityTracker mVelocityTracker = null;

	private int mTouchSlop;
	private int mMaximumVelocity;
	private int mFlingVelocity;

	// TODO : Edge effect handling
	// private EdgeEffectCompat mEdgeGlowTop;
	// private EdgeEffectCompat mEdgeGlowBottom;

	// blocker for when we're in a layout pass
	private boolean mInLayout;

	ListAdapter mAdapter;

	private int mMotionY;
	private int mMotionX;
	private int mMotionCorrection;
	private int mMotionPosition;

	private int mLastY;

	private int mActivePointerId = INVALID_POINTER;

	protected int mFirstPosition;

	// are we attached to a window - we shouldn't handle any touch events if we're not!
	private boolean mIsAttached;

	/**
	 * When set to true, calls to requestLayout() will not propagate up the parent hierarchy.
	 * This is used to layout the children during a layout pass.
	 */
	private boolean mBlockLayoutRequests = false;

	// has our data changed - and should we react to it
	private boolean mDataChanged;
	private int mItemCount;
	private int mOldItemCount;

	final boolean[] mIsScrap = new boolean[1];

	private RecycleBin mRecycleBin;

	private AdapterDataSetObserver mObserver;
	private int mWidthMeasureSpec;
	private FlingRunnable mFlingRunnable;

	protected boolean mClipToPadding;
	private PerformClick mPerformClick;

	private CheckForLongPress mPendingCheckForLongPress;

	private class CheckForLongPress extends WindowRunnnable implements Runnable {
		public void run() {
			final int motionPosition = mMotionPosition;
			final View child = getChildAt(motionPosition);
			if (child != null) {
				final int longPressPosition = mMotionPosition;
				final long longPressId = mAdapter.getItemId(mMotionPosition + mFirstPosition);

				boolean handled = false;
				if (sameWindow() && !mDataChanged) {
					handled = performLongPress(child, longPressPosition + mFirstPosition, longPressId);
				}
				if (handled) {
					mTouchMode = TOUCH_MODE_IDLE;
					setPressed(false);
					child.setPressed(false);
				} else {
					mTouchMode = TOUCH_MODE_DONE_WAITING;
				}

			}
		}
	}

	/**
	 * A class that represents a fixed view in a list, for example a header at the top
	 * or a footer at the bottom.
	 */
	public class FixedViewInfo {
		/**
		 * The view to add to the list
		 */
		public View view;
		/**
		 * The data backing the view. This is returned from {@link android.widget.ListAdapter#getItem(int)}.
		 */
		public Object data;
		/**
		 * <code>true</code> if the fixed view should be selectable in the list
		 */
		public boolean isSelectable;
	}

	private ArrayList<FixedViewInfo> mHeaderViewInfos;
	private ArrayList<FixedViewInfo> mFooterViewInfos;


	public ExtendableListView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);

		// setting up to be a scrollable view group
		setWillNotDraw(false);
		setClipToPadding(false);
		setFocusableInTouchMode(false);

		final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
		mTouchSlop = viewConfiguration.getScaledTouchSlop();
		mMaximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
		mFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();

		mRecycleBin = new RecycleBin();
		mObserver = new AdapterDataSetObserver();

		mHeaderViewInfos = new ArrayList<FixedViewInfo>();
		mFooterViewInfos = new ArrayList<FixedViewInfo>();

		// start our layout mode drawing from the top
		mLayoutMode = LAYOUT_NORMAL;
	}


	// //////////////////////////////////////////////////////////////////////////////////////////
	// MAINTAINING SOME STATE
	//

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (mAdapter != null) {
			// Data may have changed while we were detached. Refresh.
			mDataChanged = true;
			mOldItemCount = mItemCount;
			mItemCount = mAdapter.getCount();
		}
		mIsAttached = true;
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		recycleVelocityTracker();

		// Detach any view left in the scrap heap
		mRecycleBin.clear();

		if (mFlingRunnable != null) {
			removeCallbacks(mFlingRunnable);
		}
		if (mPendingCheckForTap != null) {
			removeCallbacks(mPendingCheckForTap);
		}

		if (mPendingCheckForLongPress != null) {
			removeCallbacks(mPendingCheckForLongPress);
		}

		if (checkAndPreformClick != null) {
			removeCallbacks(checkAndPreformClick);
		}

		if (mPerformClick != null) {
			removeCallbacks(mPerformClick);
		}

		Handler handler = getHandler();
		if (handler != null) {
			handler.removeCallbacks(mFlingRunnable);
			handler.removeCallbacks(mPendingCheckForTap);
			handler.removeCallbacks(mPendingCheckForLongPress);
			handler.removeCallbacks(checkAndPreformClick);
			handler.removeCallbacks(mPerformClick);
		}

		mIsAttached = false;
	}

	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
		// TODO : handle focus and its impact on selection - if we add item selection support
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		// TODO : handle focus and its impact on selection - if we add item selection support
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (getChildCount() > 0) {
			stopFlingRunnable();
			mRecycleBin.clear();
			mDataChanged = true;
			rememberSyncState();
		}

	}


	// //////////////////////////////////////////////////////////////////////////////////////////
	// ADAPTER
	//

	@Override
	public ListAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	public void setAdapter(final ListAdapter adapter) {
		if (mAdapter != null) {
			mAdapter.unregisterDataSetObserver(mObserver);
		}

		if (adapter != null || mHeaderViewInfos.size() > 0 || mFooterViewInfos.size() > 0) {
			mAdapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, adapter);
		} else {
			mAdapter = null;
		}


		mDataChanged = true;
		mItemCount = mAdapter != null ? mAdapter.getCount() : 0;

		if (mAdapter != null) {
			mAdapter.registerDataSetObserver(mObserver);
			mRecycleBin.setViewTypeCount(mAdapter.getViewTypeCount());
		}

		requestLayout();
	}

	@Override
	public int getCount() {
		return mItemCount;
	}

	public View getItemAt(int position) {

		position += getHeaderViewsCount();
		int firstVisibleChild = super.getFirstVisiblePosition();
		if (position < firstVisibleChild || position > getLastVisiblePosition()) {
			return null;
		}

		return getChildAt(position - firstVisibleChild);
	}

	protected void setFirstPosition(int position) {
		if (position >= 0) {
			mLayoutMode = LAYOUT_NORMAL;
			mSpecificTop = getListPaddingTop();
			mFirstPosition = position;
			if (mNeedSync) {
				mSyncPosition = position;
				mSyncRowId = mAdapter.getItemId(position);
			}

			requestLayout();
		}
	}

	protected ContextMenu.ContextMenuInfo mContextMenuInfo = null;

	@Override
	protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
		return mContextMenuInfo;
	}

	@Override
	public boolean showContextMenuForChild(View originalView) {
		final int longPressPosition = getPositionForView(originalView);
		if (longPressPosition >= 0) {
			final long longPressId = mAdapter.getItemId(longPressPosition);
			boolean handled = false;

			if (getOnItemLongClickListener() != null) {
				handled = getOnItemLongClickListener().onItemLongClick(this, originalView,
						longPressPosition, longPressId);
			}
			if (!handled) {
				mContextMenuInfo = createContextMenuInfo(
						getChildAt(longPressPosition - mFirstPosition),
						longPressPosition, longPressId);
				handled = getParent() != null && getParent().showContextMenuForChild(originalView);
			}

			return handled;
		}
		return false;
	}

	protected ContextMenu.ContextMenuInfo createContextMenuInfo(View view, int position, long id) {
		return new AdapterContextMenuInfo(view, position, id);
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// ADAPTER VIEW - UNSUPPORTED
	//

	@Override
	public View getSelectedView() {
		if (DBG) Log.e(TAG, "getSelectedView() is not supported in ExtendableListView yet");
		return null;
	}

	@Override
	public void setSelection(final int position) {
		if (position >= 0) {
			mLayoutMode = LAYOUT_SYNC;
			mSpecificTop = getListPaddingTop();

			mFirstPosition = 0;
			if (mNeedSync) {
				mSyncPosition = position;
				mSyncRowId = mAdapter.getItemId(position);
			}

			requestLayout();
		}

	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// HEADER & FOOTER
	//

	/**
	 * Add a fixed view to appear at the top of the list. If addHeaderView is
	 * called more than once, the views will appear in the order they were
	 * added. Views added using this call can take focus if they want.
	 * <p/>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap
	 * the supplied cursor with one that will also account for header and footer
	 * views.
	 *
	 * @param v            The view to add.
	 * @param data         Data to associate with this view
	 * @param isSelectable whether the item is selectable
	 */

	public void addHeaderView(View v, Object data, boolean isSelectable) {

		if (mAdapter != null && !(mAdapter instanceof HeaderViewListAdapter)) {
			throw new IllegalStateException(
					"Cannot add header view to list -- setAdapter has already been called.");
		}

		LayoutParams params = generateHeaderFooterLayoutParams(v);
		v.setLayoutParams(params);

		FixedViewInfo info = new FixedViewInfo();
		info.view = v;
		info.data = data;
		info.isSelectable = isSelectable;
		mHeaderViewInfos.add(info);

		// in the case of re-adding a header view, or adding one later on,
		// we need to notify the observer
		if (mAdapter != null && mObserver != null) {
			mObserver.onChanged();
		}
	}

	/**
	 * Add a fixed view to appear at the top of the list. If addHeaderView is
	 * called more than once, the views will appear in the order they were
	 * added. Views added using this call can take focus if they want.
	 * <p/>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap
	 * the supplied cursor with one that will also account for header and footer
	 * views.
	 *
	 * @param v The view to add.
	 */
	public void addHeaderView(View v) {
		addHeaderView(v, null, true);
	}

	public int getHeaderViewsCount() {
		return mHeaderViewInfos.size();
	}

	/**
	 * Removes a previously-added header view.
	 *
	 * @param v The view to remove
	 * @return true if the view was removed, false if the view was not a header
	 * view
	 */
	public boolean removeHeaderView(View v) {
		if (mHeaderViewInfos.size() > 0) {
			boolean result = false;
			if (mAdapter != null && ((HeaderViewListAdapter) mAdapter).removeHeader(v)) {
				if (mObserver != null) {
					mObserver.onChanged();
				}
				result = true;
			}
			removeFixedViewInfo(v, mHeaderViewInfos);
			return result;
		}
		return false;
	}

	private void removeFixedViewInfo(View v, ArrayList<FixedViewInfo> where) {
		int len = where.size();
		for (int i = 0; i < len; ++i) {
			FixedViewInfo info = where.get(i);
			if (info.view == v) {
				where.remove(i);
				break;
			}
		}
	}

	/**
	 * Add a fixed view to appear at the bottom of the list. If addFooterView is
	 * called more than once, the views will appear in the order they were
	 * added. Views added using this call can take focus if they want.
	 * <p/>
	 * NOTE: Call this before calling setAdapter. This is so ListView can wrap
	 * the supplied cursor with one that will also account for header and footer
	 * views.
	 *
	 * @param v            The view to add.
	 * @param data         Data to associate with this view
	 * @param isSelectable true if the footer view can be selected
	 */
	public void addFooterView(View v, Object data, boolean isSelectable) {

		// NOTE: do not enforce the adapter being null here, since unlike in
		// addHeaderView, it was never enforced here, and so existing apps are
		// relying on being able to add a footer and then calling setAdapter to
		// force creation of the HeaderViewListAdapter wrapper

		FixedViewInfo info = new FixedViewInfo();
		info.view = v;
		info.data = data;
		info.isSelectable = isSelectable;
		mFooterViewInfos.add(info);

		// in the case of re-adding a footer view, or adding one later on,
		// we need to notify the observer
		if (mAdapter != null && mObserver != null) {
			mObserver.onChanged();
		}
	}

	/**
	 * Add a fixed view to appear at the bottom of the list. If addFooterView is called more
	 * than once, the views will appear in the order they were added. Views added using
	 * this call can take focus if they want.
	 * <p>NOTE: Call this before calling setAdapter. This is so ListView can wrap the supplied
	 * cursor with one that will also account for header and footer views.
	 *
	 * @param v The view to add.
	 */
	public void addFooterView(View v) {
		addFooterView(v, null, true);
	}

	public int getFooterViewsCount() {
		return mFooterViewInfos.size();
	}

	/**
	 * Removes a previously-added footer view.
	 *
	 * @param v The view to remove
	 * @return true if the view was removed, false if the view was not a footer view
	 */
	public boolean removeFooterView(View v) {
		if (mFooterViewInfos.size() > 0) {
			boolean result = false;
			if (mAdapter != null && ((HeaderViewListAdapter) mAdapter).removeFooter(v)) {
				if (mObserver != null) {
					mObserver.onChanged();
				}
				result = true;
			}
			removeFixedViewInfo(v, mFooterViewInfos);
			return result;
		}
		return false;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// Property Overrides
	//

	@Override
	public void setClipToPadding(final boolean clipToPadding) {
		super.setClipToPadding(clipToPadding);
		mClipToPadding = clipToPadding;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// LAYOUT
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void requestLayout() {
		if (!mBlockLayoutRequests && !mInLayout) {
			super.requestLayout();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
		// super.onLayout(changed, l, t, r, b); - skipping base AbsListView implementation on purpose
		// haven't set an adapter yet? get to it
		if (mAdapter == null) {
			clearState();
			return;
		}

		if (changed) {
			int childCount = getChildCount();
			for (int i = 0; i < childCount; i++) {
				getChildAt(i).forceLayout();
			}
			mRecycleBin.markChildrenDirty();
		}

		// TODO get the height of the view??
		mInLayout = true;
		layoutChildren();
		mInLayout = false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void layoutChildren() {
		if (mBlockLayoutRequests) return;
		mBlockLayoutRequests = true;

		try {
			super.layoutChildren();
			invalidate();

			if (mAdapter == null) {
				clearState();
				invokeOnItemScrollListener();
				return;
			}

			int childrenTop = getListPaddingTop();

			int childCount = getChildCount();
			View oldFirst = null;

			// our last state so we keep our position
			if (mLayoutMode == LAYOUT_NORMAL) {
				oldFirst = getChildAt(0);
			}

			boolean dataChanged = mDataChanged;
			if (dataChanged) {
				handleDataChanged();
			}

			// safety check!
			// Handle the empty set by removing all views that are visible
			// and calling it a day
			if (mItemCount == 0) {
				clearState();
				invokeOnItemScrollListener();
				return;
			} else if (mItemCount != mAdapter.getCount()) {
				throw new IllegalStateException("The content of the adapter has changed but "
						+ "ExtendableListView did not receive a notification. Make sure the content of "
						+ "your adapter is not modified from a background thread, but only "
						+ "from the UI thread. [in ExtendableListView(" + getId() + ", " + getClass()
						+ ") with Adapter(" + mAdapter.getClass() + ")]");
			}

			// Pull all children into the RecycleBin.
			// These views will be reused if possible
			final int firstPosition = mFirstPosition;
			final RecycleBin recycleBin = mRecycleBin;

			if (dataChanged) {
				for (int i = 0; i < childCount; i++) {
					recycleBin.addScrapView(getChildAt(i), firstPosition + i);
				}
			} else {
				recycleBin.fillActiveViews(childCount, firstPosition);
			}

			// Clear out old views
			detachAllViewsFromParent();
			recycleBin.removeSkippedScrap();

			switch (mLayoutMode) {
				case LAYOUT_FORCE_TOP: {
					mFirstPosition = 0;
					resetToTop();
					adjustViewsUpOrDown();
					fillFromTop(childrenTop);
					adjustViewsUpOrDown();
					break;
				}
				case LAYOUT_SYNC: {
					fillSpecific(mSyncPosition, mSpecificTop);
					break;
				}
				case LAYOUT_NORMAL:
				default: {
					if (childCount == 0) {
						fillSpecific(mFirstPosition, getListPaddingTop());
					} else if (mFirstPosition < mItemCount) {
						fillSpecific(mFirstPosition,
								oldFirst == null ? childrenTop : oldFirst.getTop());
					} else {
						fillSpecific(0, childrenTop);
					}
					break;
				}
			}

			// Flush any cached views that did not get reused above
			recycleBin.scrapActiveViews();
			mDataChanged = false;
			mNeedSync = false;
			mLayoutMode = LAYOUT_NORMAL;
			invokeOnItemScrollListener();
		} finally {
			mBlockLayoutRequests = false;
		}
	}


	@Override
	protected void handleDataChanged() {
		super.handleDataChanged();

		final int count = mItemCount;

		if (count > 0 && mNeedSync) {
			mNeedSync = false;
			mSyncState = null;

			mLayoutMode = LAYOUT_SYNC;
			mSyncPosition = Math.min(Math.max(0, mSyncPosition), count - 1);
			return;
		}

		mLayoutMode = LAYOUT_FORCE_TOP;
		mNeedSync = false;
		mSyncState = null;

		// TODO : add selection handling here
	}

	public void resetToTop() {
		// TO override
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// MEASUREMENT
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		setMeasuredDimension(widthSize, heightSize);
		mWidthMeasureSpec = widthMeasureSpec;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// ON TOUCH
	//

	private boolean cancelTap;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// we're not passing this down as
		// all the touch handling is right here
		// super.onTouchEvent(event);

		if (!isEnabled()) {
			// A disabled view that is clickable still consumes the touch
			// events, it just doesn't respond to them.
			return isClickable() || isLongClickable();
		}

		initVelocityTrackerIfNotExists();
		mVelocityTracker.addMovement(event);

		if (!hasChildren()) return false;

		boolean handled;
		final int action = event.getAction() & MotionEvent.ACTION_MASK;
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				handled = onTouchDown(event);
				break;

			case MotionEvent.ACTION_MOVE:
				handled = onTouchMove(event);
				break;

			case MotionEvent.ACTION_CANCEL:
				handled = onTouchCancel(event);
				break;

			case MotionEvent.ACTION_POINTER_UP:
				handled = onTouchPointerUp(event);
				break;

			case MotionEvent.ACTION_UP:
				handled = onTouchUp(event);
				break;
			default:
				handled = false;
				break;
		}

		notifyTouchMode();

		return handled;
	}


	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		int action = ev.getAction();

		if (!mIsAttached) {
			// Something isn't right.
			// Since we rely on being attached to get data set change notifications,
			// don't risk doing anything where we might try to resync and find things
			// in a bogus state.
			return false;
		}


		switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN: {
				int touchMode = mTouchMode;

				// TODO : overscroll
//                if (touchMode == TOUCH_MODE_OVERFLING || touchMode == TOUCH_MODE_OVERSCROLL) {
//                    mMotionCorrection = 0;
//                    return true;
//                }

				final int x = (int) ev.getX();
				final int y = (int) ev.getY();
				mActivePointerId = ev.getPointerId(0);

				int motionPosition = findMotionRow(y);
				if (touchMode != TOUCH_MODE_FLINGING && motionPosition >= 0) {
					// User clicked on an actual view (and was not stopping a fling).
					// Remember where the motion event started
					mMotionX = x;
					mMotionY = y;
					mMotionPosition = motionPosition;
					mTouchMode = TOUCH_MODE_DOWN;
				}
				mLastY = Integer.MIN_VALUE;
				initOrResetVelocityTracker();
				mVelocityTracker.addMovement(ev);
				if (touchMode == TOUCH_MODE_FLINGING) {
					return true;
				}
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				switch (mTouchMode) {
					case TOUCH_MODE_DOWN:
						int pointerIndex = ev.findPointerIndex(mActivePointerId);
						if (pointerIndex == -1) {
							pointerIndex = 0;
							mActivePointerId = ev.getPointerId(pointerIndex);
						}
						final int y = (int) ev.getY(pointerIndex);
						final int x = (int) ev.getX(pointerIndex);
						initVelocityTrackerIfNotExists();
						mVelocityTracker.addMovement(ev);
						if (startScrollIfNeeded(x, y)) {
							return true;
						}
						break;
				}
				break;
			}

			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				mTouchMode = TOUCH_MODE_IDLE;
				mActivePointerId = INVALID_POINTER;
				recycleVelocityTracker();
				reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				onSecondaryPointerUp(ev);
				break;
			}
		}

		return false;
	}

	@Override
	public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
		if (disallowIntercept) {
			recycleVelocityTracker();
		}
		super.requestDisallowInterceptTouchEvent(disallowIntercept);
	}

	private Runnable mPendingCheckForTap;

	final class CheckForTap implements Runnable {
		public void run() {
			if (mTouchMode == TOUCH_MODE_DOWN) {
				mTouchMode = TOUCH_MODE_TAP;
				final View child = getChildAt(mMotionPosition);
				if (child != null && !child.hasFocusable()) {
					mLayoutMode = LAYOUT_NORMAL;

					if (!mDataChanged) {
						layoutChildren();
						child.setPressed(true);
						setPressed(true);

						final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
						final boolean longClickable = isLongClickable();

						if (longClickable) {
							if (mPendingCheckForLongPress == null) {
								mPendingCheckForLongPress = new CheckForLongPress();
							}
							mPendingCheckForLongPress.rememberWindowAttachCount();
							postDelayed(mPendingCheckForLongPress, longPressTimeout);
						} else {
							mTouchMode = TOUCH_MODE_DONE_WAITING;
						}
					} else {
						mTouchMode = TOUCH_MODE_DONE_WAITING;
					}
				}
			}
		}
	}


	private boolean onTouchDown(final MotionEvent event) {
		final int x = (int) event.getX();
		final int y = (int) event.getY();
		int motionPosition = pointToPosition(x, y);

		mVelocityTracker.clear();
		mActivePointerId = event.getPointerId(0);

		// TODO : use the motion position for fling support
		// TODO : support long press!
		// startLongPressCheck();

		if ((mTouchMode != TOUCH_MODE_FLINGING) &&
				!mDataChanged &&
				motionPosition >= 0 &&
				getAdapter().isEnabled(motionPosition)) {
			// is it a tap or a scroll .. we don't know yet!
			mTouchMode = TOUCH_MODE_DOWN;

			if (mPendingCheckForTap == null) {
				mPendingCheckForTap = new CheckForTap();
			}

			postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout() / 2);

			if (event.getEdgeFlags() != 0 && motionPosition < 0) {
				// If we couldn't find a view to click on, but the down event was touching
				// the edge, we will bail out and try again. This allows the edge correcting
				// code in ViewRoot to try to find a nearby view to select
				return false;
			}
		} else if (mTouchMode == TOUCH_MODE_FLINGING) {
			mTouchMode = TOUCH_MODE_SCROLLING;
			mMotionCorrection = 0;
			motionPosition = findMotionRow(y);
		}
		cancelTap = false;
		mMotionX = x;
		mMotionY = y;
		mMotionPosition = motionPosition;
		mLastY = Integer.MIN_VALUE;

		return true;
	}

	private boolean onTouchMove(final MotionEvent event) {
		final int index = event.findPointerIndex(mActivePointerId);;
		if (index < 0) {
			Log.e(TAG, "onTouchMove could not find pointer with id " +
					mActivePointerId + " - did ExtendableListView receive an inconsistent " +
					"event stream?");
			return false;
		}
		final int y = (int) event.getY(index);
		final int x = (int) event.getX(index);

		// our data's changed so we need to do a layout before moving any further
		if (mDataChanged) {
			layoutChildren();
		}

		switch (mTouchMode) {
			case TOUCH_MODE_DOWN:
			case TOUCH_MODE_TAP:
			case TOUCH_MODE_DONE_WAITING:
				// Check if we have moved far enough that it looks more like a
				// scroll than a tap
				if (startScrollIfNeeded(x, y)) {
					break;
				}

				break;
			case TOUCH_MODE_SCROLLING:
//            case TOUCH_MODE_OVERSCROLL:
				scrollIfNeeded(y);
				break;
		}

		return true;
	}

	private void cancelTapOnView() {
		cancelTap = true;
		stopTapOnView();
	}

	private void stopTapOnView() {
		final Handler handler = getHandler();

		if (handler != null) {
			handler.removeCallbacks(mPendingCheckForLongPress);
			handler.removeCallbacks(mPendingCheckForTap);
		}
		setPressed(false);
		removeCallbacks(mPendingCheckForTap);
		removeCallbacks(mPendingCheckForLongPress);
		View motionView = getChildAt(mMotionPosition);
		if (motionView != null) {
			motionView.setPressed(false);
		}
	}


	private boolean onTouchCancel(final MotionEvent event) {

		cancelTapOnView();
		mTouchMode = TOUCH_MODE_IDLE;

		invalidate(); // redraw selector

		recycleVelocityTracker();
		mActivePointerId = INVALID_POINTER;
		return true;
	}

	private boolean onTouchUp(final MotionEvent event) {
		switch (mTouchMode) {
			case TOUCH_MODE_DOWN:
			case TOUCH_MODE_TAP:
			case TOUCH_MODE_DONE_WAITING:
				stopTapOnView();
				return onTouchUpTap(event);

			case TOUCH_MODE_SCROLLING:
				return onTouchUpScrolling(event);
		}

		invalidate(); // redraw selector
		recycleVelocityTracker();
		stopTapOnView();
		mActivePointerId = INVALID_POINTER;
		return true;
	}

	private boolean onTouchUpScrolling(final MotionEvent event) {
		if (hasChildren()) {
			// 2 - Are we at the top or bottom?
			int top = getFirstChildTop();
			int bottom = getLastChildBottom();
			final boolean atEdge = (mFirstPosition == 0 &&
					top >= getListPaddingTop()) ||
					(mFirstPosition + getChildCount() < mItemCount &&
							bottom <= getHeight() - getListPaddingBottom());

			if (!atEdge) {
				mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				final float velocity = mVelocityTracker.getYVelocity(mActivePointerId);

				if (Math.abs(velocity) > mFlingVelocity) {
					fling((int) velocity);
					return true;
				}
			}
		}

		mActivePointerId = INVALID_POINTER;
		stopFlingRunnable();
		recycleVelocityTracker();
		mTouchMode = TOUCH_MODE_IDLE;
		return true;
	}

	private Runnable checkAndPreformClick;


	private boolean onTouchUpTap(final MotionEvent event) {
		if (cancelTap) {
			mTouchMode = TOUCH_MODE_IDLE;
			invalidate();
			cancelTap = false;
			return true;
		}
		final int motionPosition = mMotionPosition;
		if (motionPosition >= 0) {
			final View child = getChildAt(motionPosition);
			if (child != null && !child.hasFocusable()) {
				if (mTouchMode != TOUCH_MODE_DOWN) {
					child.setPressed(false);
				}

				if (mPerformClick == null) {
					invalidate();
					mPerformClick = new PerformClick();
				}

				final PerformClick performClick = mPerformClick;
				performClick.mClickMotionPosition = motionPosition;
				performClick.rememberWindowAttachCount();

				// mResurrectToPosition = motionPosition;

				if (mTouchMode == TOUCH_MODE_DOWN || mTouchMode == TOUCH_MODE_TAP) {
					final Handler handler = getHandler();
					if (handler != null) {
						handler.removeCallbacks(mTouchMode == TOUCH_MODE_DOWN ?
								mPendingCheckForTap : mPendingCheckForLongPress);
					}
					mLayoutMode = LAYOUT_NORMAL;
					if (!mDataChanged && motionPosition >= 0 && mAdapter.isEnabled(motionPosition)) {
						mTouchMode = TOUCH_MODE_TAP;
						layoutChildren();
						child.setPressed(true);
						setPressed(true);
						checkAndPreformClick = new Runnable() {
							public void run() {
								child.setPressed(false);
								setPressed(false);
								if (!mDataChanged) {
									post(performClick);
								}
								mTouchMode = TOUCH_MODE_IDLE;
							}
						};
						postDelayed(checkAndPreformClick, ViewConfiguration.getPressedStateDuration());
					} else {
						mTouchMode = TOUCH_MODE_IDLE;
					}
					return true;
				} else if (!mDataChanged && motionPosition >= 0 && mAdapter.isEnabled(motionPosition)) {
					post(performClick);
				}
			}
		}
		mTouchMode = TOUCH_MODE_IDLE;
		return true;
	}

	private boolean onTouchPointerUp(final MotionEvent event) {
		onSecondaryPointerUp(event);
		final int x = mMotionX;
		final int y = mMotionY;
		final int motionPosition = pointToPosition(x, y);
		if (motionPosition >= 0) {
			mMotionPosition = motionPosition;
		}
		mLastY = y;
		return true;
	}

	private void onSecondaryPointerUp(MotionEvent event) {
		final int pointerIndex = (event.getAction() &
				MotionEvent.ACTION_POINTER_INDEX_MASK) >>
				MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		final int pointerId = event.getPointerId(pointerIndex);
		if (pointerId == mActivePointerId) {
			// This was our active pointer going up. Choose a new
			// active pointer and adjust accordingly.
			// TODO: Make this decision more intelligent.
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mMotionX = (int) event.getX(newPointerIndex);
			mMotionY = (int) event.getY(newPointerIndex);
			mActivePointerId = event.getPointerId(newPointerIndex);
			recycleVelocityTracker();
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// SCROLL HELPERS
	//

	public void stopAndFly(int velocity) {
		recycleVelocityTracker();
		stopTapOnView();
		mActivePointerId = INVALID_POINTER;
		fling(velocity);
	}

	public void stopDrag() {
		recycleVelocityTracker();
		mActivePointerId = INVALID_POINTER;
		mTouchMode = TOUCH_MODE_IDLE;
	}


	public void fling(int velocity) {
		stopFlingRunnable();
		startFlingRunnable(velocity);
		mTouchMode = TOUCH_MODE_FLINGING;
		mMotionY = 0;
		mMotionX = 0;
		invalidate();
	}

	protected VelocityTracker snatchVelocityTracker() {
		VelocityTracker snatchTracker = mVelocityTracker;
		mVelocityTracker = null;
		return snatchTracker;
	}

	public void startScroll(VelocityTracker velocityTracker, MotionEvent prevEvent, MotionEvent event) {

		if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
			velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
			final float velocity = velocityTracker.getYVelocity(mActivePointerId);
			velocityTracker.recycle();
			if (Math.abs(velocity) > mFlingVelocity) {
				stopAndFly((int) velocity);
			}
			return;
		}

		if (velocityTracker != null) {
			recycleVelocityTracker();
			mVelocityTracker = velocityTracker;
		} else {
			initOrResetVelocityTracker();
		}
		mLastY = Integer.MIN_VALUE;
		mMotionX = (int) prevEvent.getX();
		mMotionY = (int) prevEvent.getY();
		mMotionPosition = pointToPosition(mMotionX, mMotionY);

		mMotionCorrection = 0;
		mActivePointerId = prevEvent.getPointerId(0);

		final int index = event.findPointerIndex( mActivePointerId);
		final int y = (int) event.getY(index);
		final int deltaY = y - mMotionY;
		mTouchMode = TOUCH_MODE_SCROLLING;
		mMotionCorrection = deltaY > 0 ? mTouchSlop : -mTouchSlop;
		scrollIfNeeded(y);
	}


	private boolean startScrollIfNeeded(final int x, final int y) {
		final int deltaY = y - mMotionY;
		final int distanceY = Math.abs(deltaY);

		final int deltaX = x - mMotionX;
		final int distanceX = Math.abs(deltaX);
		// TODO : Overscroll?
		// final boolean overscroll = mScrollY != 0;
		final boolean overscroll = false;
		if (overscroll || distanceY > mTouchSlop) {
			if (overscroll) {
				mMotionCorrection = 0;
			} else {
				mTouchMode = TOUCH_MODE_SCROLLING;
				notifyTouchMode();
				mMotionCorrection = deltaY > 0 ? mTouchSlop : -mTouchSlop;
			}

			stopTapOnView();
			final ViewParent parent = getParent();
			if (parent != null) {
				parent.requestDisallowInterceptTouchEvent(true);
			}
			scrollIfNeeded(y);
			return true;
		}

		if (distanceX > mTouchSlop) {
			removeCallbacks(mPendingCheckForTap);
			cancelTapOnView();

		}
		return false;
	}

	private void scrollIfNeeded(final int y) {
		if (DBG) Log.d(TAG, "scrollIfNeeded y: " + y);
		final int rawDeltaY = y - mMotionY;
		final int deltaY = rawDeltaY - mMotionCorrection;
		int incrementalDeltaY = mLastY != Integer.MIN_VALUE ? y - mLastY : deltaY;

		if (mTouchMode == TOUCH_MODE_SCROLLING) {
			if (DBG) Log.d(TAG, "scrollIfNeeded TOUCH_MODE_SCROLLING");
			if (y != mLastY) {
				// stop our parent
				if (Math.abs(rawDeltaY) > mTouchSlop) {
					final ViewParent parent = getParent();
					if (parent != null) {
						parent.requestDisallowInterceptTouchEvent(true);
					}
				}

				final int motionIndex;
				if (mMotionPosition >= 0) {
					motionIndex = mMotionPosition - mFirstPosition;
				} else {
					// If we don't have a motion position that we can reliably track,
					// pick something in the middle to make a best guess at things below.
					motionIndex = getChildCount() / 2;
				}

				// No need to do all this work if we're not going to move anyway
				boolean atEdge = false;
				if (incrementalDeltaY != 0) {
					atEdge = moveTheChildren(deltaY, incrementalDeltaY);
				}

				// Check to see if we have bumped into the scroll limit
				View motionView = this.getChildAt(motionIndex);
				if (motionView != null) {
					if (atEdge) {
						// TODO : edge effect & overscroll
					}
					mMotionY = y;
				}
				mLastY = y;
			}

		}
		// TODO : ELSE SUPPORT OVERSCROLL!
	}

	private int findMotionRow(int y) {
		int childCount = getChildCount();
		if (childCount > 0) {
			// always from the top
			for (int i = 0; i < childCount; i++) {
				View v = getChildAt(i);
				if (y <= v.getBottom()) {
					return mFirstPosition + i;
				}
			}
		}
		return INVALID_POSITION;
	}

	@Override
	protected int computeVerticalScrollExtent() {
		final int childCount = getChildCount();
		if (childCount > 0) {
			if (isSmoothScrollbarEnabled()) {
				int extent = childCount * 100;

				View view = getChildAt(0);
				final int top = view.getTop();
				int height = view.getHeight();
				if (height > 0) {
					extent += (top * 100) / height;
				}

				view = getChildAt(childCount - 1);
				final int bottom = view.getBottom();
				height = view.getHeight();
				if (height > 0) {
					extent -= ((bottom - getHeight()) * 100) / height;
				}

				return extent;
			} else {
				return 1;
			}
		}
		return 0;
	}

	@Override
	protected int computeVerticalScrollOffset() {
		final int firstPosition = mFirstPosition;
		final int childCount = getChildCount();
		if (firstPosition >= 0 && childCount > 0) {
			final int count = getCount();
			if (isSmoothScrollbarEnabled()) {
				final View view = getChildAt(0);
				final int top = view.getTop();
				final int height = view.getHeight();
				if (height > 0) {
					return Math.max(firstPosition * 100 - (top * 100) / height +
							(int) ((float) getScrollY() / getHeight() * count * 100), 0);
				}
			} else {
				int index;
				if (firstPosition == 0) {
					index = 0;
				} else if (firstPosition + childCount == count) {
					index = count;
				} else {
					index = firstPosition + childCount / 2;
				}
				return (int) (firstPosition + childCount * (index / (float) count));
			}
		}
		return 0;
	}

	@Override
	protected int computeVerticalScrollRange() {
		int result;
		if (isSmoothScrollbarEnabled()) {
			result = Math.max(mItemCount * 100, 0);
			final int scrollY = getScrollY();
			if (scrollY != 0) {
				// Compensate for overscroll
				result += Math.abs((int) ((float) scrollY / getHeight() * mItemCount * 100));
			}
		} else {
			result = mItemCount;
		}
		return result;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// MOVING STUFF!
	//
	// It's not scrolling - we're just moving views!
	// Move our views and implement view recycling to show new views if necessary

	// move our views by deltaY - what's the incrementalDeltaY?
	private boolean moveTheChildren(int deltaY, int incrementalDeltaY) {
		if (DBG)
			Log.d(TAG, "moveTheChildren deltaY: " + deltaY + "incrementalDeltaY: " + incrementalDeltaY);
		// there's nothing to move!
		if (!hasChildren()) return true;

		final int firstTop = getHighestChildTop();
		final int lastBottom = getLowestChildBottom();

		// "effective padding" In this case is the amount of padding that affects
		// how much space should not be filled by items. If we don't clip to padding
		// there is no effective padding.
		int effectivePaddingTop = 0;
		int effectivePaddingBottom = 0;
		if (mClipToPadding) {
			effectivePaddingTop = getListPaddingTop();
			effectivePaddingBottom = getListPaddingBottom();
		}

		final int gridHeight = getHeight();
		final int spaceAbove = effectivePaddingTop - getFirstChildTop();
		final int end = gridHeight - effectivePaddingBottom;
		final int spaceBelow = getLastChildBottom() - end;

		final int height = gridHeight - getListPaddingBottom() - getListPaddingTop();

		if (incrementalDeltaY < 0) {
			incrementalDeltaY = Math.max(-(height - 1), incrementalDeltaY);
		} else {
			incrementalDeltaY = Math.min(height - 1, incrementalDeltaY);
		}

		final int firstPosition = mFirstPosition;

		int maxTop = getListPaddingTop();
		int maxBottom = gridHeight - getListPaddingBottom();
		int childCount = getChildCount();

		final boolean cannotScrollDown = (firstPosition == 0 &&
				firstTop >= maxTop && incrementalDeltaY >= 0);
		final boolean cannotScrollUp = (firstPosition + childCount == mItemCount &&
				lastBottom <= maxBottom && incrementalDeltaY <= 0);

		if (DBG) {
			Log.d(TAG, "moveTheChildren " +
					" firstTop " + firstTop +
					" maxTop " + maxTop +
					" incrementalDeltaY " + incrementalDeltaY);
			Log.d(TAG, "moveTheChildren " +
					" lastBottom " + lastBottom +
					" maxBottom " + maxBottom +
					" incrementalDeltaY " + incrementalDeltaY);
		}

		if (cannotScrollDown) {
			if (DBG) Log.d(TAG, "moveTheChildren cannotScrollDown " + cannotScrollDown);
			return incrementalDeltaY != 0;
		}

		if (cannotScrollUp) {
			if (DBG) Log.d(TAG, "moveTheChildren cannotScrollUp " + cannotScrollUp);
			return incrementalDeltaY != 0;
		}

		final boolean isDown = incrementalDeltaY < 0;

		final int headerViewsCount = getHeaderViewsCount();
		final int footerViewsStart = mItemCount - getFooterViewsCount();

		int start = 0;
		int count = 0;

		if (isDown) {
			int top = -incrementalDeltaY;
			if (mClipToPadding) {
				top += getListPaddingTop();
			}
			for (int i = 0; i < childCount; i++) {
				final View child = getChildAt(i);
				if (child.getBottom() >= top) {
					break;
				} else {
					count++;
					int position = firstPosition + i;
					if (position >= headerViewsCount && position < footerViewsStart) {
						mRecycleBin.addScrapView(child, position);
					}
				}
			}
		} else {
			int bottom = gridHeight - incrementalDeltaY;
			if (mClipToPadding) {
				bottom -= getListPaddingBottom();
			}
			for (int i = childCount - 1; i >= 0; i--) {
				final View child = getChildAt(i);
				if (child.getTop() <= bottom) {
					break;
				} else {
					start = i;
					count++;
					int position = firstPosition + i;
					if (position >= headerViewsCount && position < footerViewsStart) {
						mRecycleBin.addScrapView(child, position);
					}
				}
			}
		}

		mBlockLayoutRequests = true;

		if (count > 0) {
			if (DBG) Log.d(TAG, "scrap - detachViewsFromParent start:" + start + " count:" + count);
			detachViewsFromParent(start, count);
			mRecycleBin.removeSkippedScrap();
			onChildrenDetached(start, count);
		}

		// invalidate before moving the children to avoid unnecessary invalidate
		// calls to bubble up from the children all the way to the top
		if (!awakenScrollBars()) {
			invalidate();
		}

		offsetChildrenTopAndBottom(incrementalDeltaY);

		if (isDown) {
			mFirstPosition += count;
		}

		final int absIncrementalDeltaY = Math.abs(incrementalDeltaY);
		if (spaceAbove < absIncrementalDeltaY || spaceBelow < absIncrementalDeltaY) {
			fillGap(isDown);
		}

		// TODO : touch mode selector handling
		mBlockLayoutRequests = false;
		invokeOnItemScrollListener();

		return false;
	}

	protected void onChildrenDetached(final int start, final int count) {

	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// FILLING THE GRID!
	//

	/**
	 * As we move and scroll and recycle views we want to fill the gap created with new views
	 */
	protected void fillGap(boolean down) {
		final int count = getChildCount();
		if (down) {
			// fill down from the top of the position below our last
			int position = mFirstPosition + count;
			final int startOffset = getChildTop(position);
			fillDown(position, startOffset);
		} else {
			// fill up from the bottom of the position above our first.
			int position = mFirstPosition - 1;
			final int startOffset = getChildBottom(position);
			fillUp(position, startOffset);
		}
		adjustViewsAfterFillGap(down);
	}

	protected void adjustViewsAfterFillGap(boolean down) {
		if (down) {
			correctTooHigh(getChildCount());
		} else {
			correctTooLow(getChildCount());
		}
	}

	private View fillDown(int pos, int nextTop) {
		if (DBG) Log.d(TAG, "fillDown - pos:" + pos + " nextTop:" + nextTop);

		View selectedView = null;

		int end = getHeight();
		if (mClipToPadding) {
			end -= getListPaddingBottom();
		}

		while ((nextTop < end || hasSpaceDown()) && pos < mItemCount) {
			// TODO : add selection support
			makeAndAddView(pos, nextTop, true, false);
			pos++;
			nextTop = getNextChildDownsTop(pos); // = child.getBottom();
		}

		return selectedView;
	}

	/**
	 * Override to tell filling flow to continue to fill up as we have space.
	 */
	protected boolean hasSpaceDown() {
		return false;
	}

	private View fillUp(int pos, int nextBottom) {
		if (DBG) Log.d(TAG, "fillUp - position:" + pos + " nextBottom:" + nextBottom);
		View selectedView = null;

		int end = mClipToPadding ? getListPaddingTop() : 0;

		while ((nextBottom > end || hasSpaceUp()) && pos >= 0) {
			// TODO : add selection support
			makeAndAddView(pos, nextBottom, false, false);
			pos--;
			nextBottom = getNextChildUpsBottom(pos);
			if (DBG) Log.d(TAG, "fillUp next - position:" + pos + " nextBottom:" + nextBottom);
		}

		mFirstPosition = pos + 1;
		return selectedView;
	}

	/**
	 * Override to tell filling flow to continue to fill up as we have space.
	 */
	protected boolean hasSpaceUp() {
		return false;
	}

	/**
	 * Fills the list from top to bottom, starting with mFirstPosition
	 */
	private View fillFromTop(int nextTop) {
		mFirstPosition = Math.min(mFirstPosition, mItemCount - 1);
		if (mFirstPosition < 0) {
			mFirstPosition = 0;
		}
		return fillDown(mFirstPosition, nextTop);
	}

	/**
	 * Put a specific item at a specific location on the screen and then build
	 * up and down from there.
	 *
	 * @param position The reference view to use as the starting point
	 * @param top      Pixel offset from the top of this view to the top of the
	 *                 reference view.
	 * @return The selected view, or null if the selected view is outside the
	 * visible area.
	 */
	private View fillSpecific(int position, int top) {
		boolean tempIsSelected = false; // ain't no body got time for that @ Etsy
		View temp = makeAndAddView(position, top, true, tempIsSelected);
		// Possibly changed again in fillUp if we add rows above this one.
		mFirstPosition = position;

		View above;
		View below;

		int nextBottom = getNextChildUpsBottom(position - 1);
		int nextTop = getNextChildDownsTop(position + 1);

		above = fillUp(position - 1, nextBottom);
		// This will correct for the top of the first view not touching the top of the list
		adjustViewsUpOrDown();
		below = fillDown(position + 1, nextTop);
		int childCount = getChildCount();
		if (childCount > 0) {
			correctTooHigh(childCount);
		}

		if (tempIsSelected) {
			return temp;
		} else if (above != null) {
			return above;
		} else {
			return below;
		}
	}

	/**
	 * Gets a view either a new view an unused view?? or a recycled view and adds it to our children
	 */
	private View makeAndAddView(int position, int y, boolean flowDown, boolean selected) {
		View child;

		onChildCreated(position, flowDown);

		if (!mDataChanged) {
			// Try to use an existing view for this position
			child = mRecycleBin.getActiveView(position);
			if (child != null) {

				// Found it -- we're using an existing child
				// This just needs to be positioned
				setupChild(child, position, y, flowDown, selected, true);
				return child;
			}
		}

		// Make a new view for this position, or convert an unused view if possible
		child = obtainView(position, mIsScrap);
		// This needs to be positioned and measured
		setupChild(child, position, y, flowDown, selected, mIsScrap[0]);

		return child;
	}

	/**
	 * Add a view as a child and make sure it is measured (if necessary) and
	 * positioned properly.
	 *
	 * @param child    The view to add
	 * @param position The position of this child
	 * @param y        The y position relative to which this view will be positioned
	 * @param flowDown If true, align top edge to y. If false, align bottom
	 *                 edge to y.
	 * @param selected Is this position selected?
	 * @param recycled Has this view been pulled from the recycle bin? If so it
	 *                 does not need to be remeasured.
	 */
	private void setupChild(View child, int position, int y, boolean flowDown,
	                        boolean selected, boolean recycled) {
//		final boolean isSelected = false; // TODO : selected && shouldShowSelector();
//		final boolean updateChildSelected = isSelected != child.isSelected();
		final int mode = mTouchMode;
		final boolean isPressed = mode > TOUCH_MODE_DOWN && mode < TOUCH_MODE_SCROLLING &&
				mMotionPosition == position;
		final boolean updateChildPressed = isPressed != child.isPressed();
		final boolean needToMeasure = !recycled || child.isLayoutRequested();

		int itemViewType = mAdapter.getItemViewType(position);

		LayoutParams layoutParams;
		if (itemViewType == ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
			layoutParams = generateWrapperLayoutParams(child);
		} else {
			layoutParams = generateChildLayoutParams(child);
		}

		layoutParams.viewType = itemViewType;
		layoutParams.position = position;

		if (recycled || (layoutParams.recycledHeaderFooter &&
				layoutParams.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER)) {
			if (DBG) Log.d(TAG, "setupChild attachViewToParent position:" + position);
			attachViewToParent(child, flowDown ? -1 : 0, layoutParams);
		} else {
			if (DBG) Log.d(TAG, "setupChild addViewInLayout position:" + position);
			if (layoutParams.viewType == AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
				layoutParams.recycledHeaderFooter = true;
			}
			addViewInLayout(child, flowDown ? -1 : 0, layoutParams, true);
		}

//		if (updateChildSelected) {
//			child.setSelected(isSelected);
//		}

		if (updateChildPressed) {
			child.setPressed(isPressed);
		}

		if (needToMeasure) {
			if (DBG) Log.d(TAG, "setupChild onMeasureChild position:" + position);
			onMeasureChild(child, layoutParams);
		} else {
			if (DBG) Log.d(TAG, "setupChild cleanupLayoutState position:" + position);
			cleanupLayoutState(child);
		}

		final int w = child.getMeasuredWidth();
		final int h = child.getMeasuredHeight();
		final int childTop = flowDown ? y : y - h;

		if (DBG) {
			Log.d(TAG, "setupChild position:" + position + " h:" + h + " w:" + w);
		}

		final int childrenLeft = getChildLeft(position);

		if (needToMeasure) {
			final int childRight = childrenLeft + w;
			final int childBottom = childTop + h;
			onLayoutChild(child, position, flowDown, childrenLeft, childTop, childRight, childBottom);
		} else {
			onOffsetChild(child, position, flowDown, childrenLeft, childTop);
		}

	}

	protected LayoutParams generateChildLayoutParams(final View child) {
		return generateWrapperLayoutParams(child);
	}

	protected LayoutParams generateWrapperLayoutParams(final View child) {
		LayoutParams layoutParams = null;

		final ViewGroup.LayoutParams childParams = child.getLayoutParams();
		if (childParams != null) {
			if (childParams instanceof LayoutParams) {
				layoutParams = (LayoutParams) childParams;
			} else {
				layoutParams = new LayoutParams(childParams);
			}
		}
		if (layoutParams == null) {
			layoutParams = generateDefaultLayoutParams();
		}

		return layoutParams;
	}


	/**
	 * Measures a child view in the list. Should call
	 */
	protected void onMeasureChild(final View child, final LayoutParams layoutParams) {
		int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
				getListPaddingLeft() + getListPaddingRight(), layoutParams.width);
		int lpHeight = layoutParams.height;
		int childHeightSpec;
		if (lpHeight > 0) {
			childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
		} else {
			childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		}
		child.measure(childWidthSpec, childHeightSpec);
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT, 0);
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}


	@Override
	public AbsListView.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	protected LayoutParams generateHeaderFooterLayoutParams(final View child) {
		LayoutParams layoutParams = null;

		final ViewGroup.LayoutParams childParams = child.getLayoutParams();
		if (childParams != null) {
			if (childParams instanceof LayoutParams) {
				layoutParams = (LayoutParams) childParams;
			} else {
				layoutParams = new LayoutParams(childParams);
			}
		}
		if (layoutParams == null) {
			layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT, AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER);
		} else {
			layoutParams.viewType = AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
		}

		return layoutParams;

	}

	/**
	 * Get a view and have it show the data associated with the specified
	 * position. This is called when we have already discovered that the view is
	 * not available for reuse in the recycle bin. The only choices left are
	 * converting an old view or making a new one.
	 *
	 * @param position The position to display
	 * @param isScrap  Array of at least 1 boolean, the first entry will become true if
	 *                 the returned view was taken from the scrap heap, false if otherwise.
	 * @return A view displaying the data associated with the specified position
	 */
	private View obtainView(int position, boolean[] isScrap) {
		isScrap[0] = false;
		View scrapView;

		scrapView = mRecycleBin.getScrapView(position);

		View child;
		if (scrapView != null) {
			if (DBG) Log.d(TAG, "getView from scrap position:" + position);
			child = mAdapter.getView(position, scrapView, this);

			if (child != scrapView) {
				mRecycleBin.addScrapView(scrapView, position);
			} else {
				isScrap[0] = true;
			}
		} else {
			if (DBG) Log.d(TAG, "getView position:" + position);
			child = mAdapter.getView(position, null, this);
		}

		return child;
	}


	/**
	 * Check if we have dragged the bottom of the list too high (we have pushed the
	 * top element off the top of the screen when we did not need to). Correct by sliding
	 * everything back down.
	 *
	 * @param childCount Number of children
	 */
	private void correctTooHigh(int childCount) {
		// First see if the last item is visible. If it is not, it is OK for the
		// top of the list to be pushed up.
		int lastPosition = mFirstPosition + childCount - 1;
		if (lastPosition == mItemCount - 1 && childCount > 0) {

			// ... and its bottom edge
			final int lastBottom = getLowestChildBottom();

			// This is bottom of our drawable area
			final int end = (getBottom() - getTop()) - getListPaddingBottom();

			// This is how far the bottom edge of the last view is from the bottom of the
			// drawable area
			int bottomOffset = end - lastBottom;

			final int firstTop = getHighestChildTop();

			// Make sure we are 1) Too high, and 2) Either there are more rows above the
			// first row or the first row is scrolled off the top of the drawable area
			if (bottomOffset > 0 && (mFirstPosition > 0 || firstTop < getListPaddingTop())) {
				if (mFirstPosition == 0) {
					// Don't pull the top too far down
					bottomOffset = Math.min(bottomOffset, getListPaddingTop() - firstTop);
				}
				// Move everything down
				offsetChildrenTopAndBottom(bottomOffset);
				if (mFirstPosition > 0) {
					// Fill the gap that was opened above mFirstPosition with more rows, if
					// possible
					int previousPosition = mFirstPosition - 1;
					fillUp(previousPosition, getNextChildUpsBottom(previousPosition));
					// Close up the remaining gap
					adjustViewsUpOrDown();
				}

			}
		}
	}

	/**
	 * Check if we have dragged the bottom of the list too low (we have pushed the
	 * bottom element off the bottom of the screen when we did not need to). Correct by sliding
	 * everything back up.
	 *
	 * @param childCount Number of children
	 */
	private void correctTooLow(int childCount) {
		// First see if the first item is visible. If it is not, it is OK for the
		// bottom of the list to be pushed down.
		if (mFirstPosition == 0 && childCount > 0) {

			// ... and its top edge
			final int firstTop = getHighestChildTop();

			// This is top of our drawable area
			final int start = getListPaddingTop();

			// This is bottom of our drawable area
			final int end = (getTop() - getBottom()) - getListPaddingBottom();

			// This is how far the top edge of the first view is from the top of the
			// drawable area
			int topOffset = firstTop - start;
			final int lastBottom = getLowestChildBottom();

			int lastPosition = mFirstPosition + childCount - 1;

			// Make sure we are 1) Too low, and 2) Either there are more rows below the
			// last row or the last row is scrolled off the bottom of the drawable area
			if (topOffset > 0) {
				if (lastPosition < mItemCount - 1 || lastBottom > end) {
					if (lastPosition == mItemCount - 1) {
						// Don't pull the bottom too far up
						topOffset = Math.min(topOffset, lastBottom - end);
					}
					// Move everything up
					offsetChildrenTopAndBottom(-topOffset);
					if (lastPosition < mItemCount - 1) {
						// Fill the gap that was opened below the last position with more rows, if
						// possible
						int nextPosition = lastPosition + 1;
						fillDown(nextPosition, getNextChildDownsTop(nextPosition));
						// Close up the remaining gap
						adjustViewsUpOrDown();
					}
				} else if (lastPosition == mItemCount - 1) {
					adjustViewsUpOrDown();
				}
			}
		}
	}

	/**
	 * Make sure views are touching the top or bottom edge, as appropriate for
	 * our gravity
	 */
	private void adjustViewsUpOrDown() {
		final int childCount = getChildCount();
		int delta;

		if (childCount > 0) {
			// Uh-oh -- we came up short. Slide all views up to make them
			// align with the top
			delta = getHighestChildTop() - getListPaddingTop();
			if (delta < 0) {
				// We only are looking to see if we are too low, not too high
				delta = 0;
			}

			if (delta != 0) {
				offsetChildrenTopAndBottom(-delta);
			}
		}
	}

	/**
	 * Controls if/how the user may choose/check items in the list
	 */
	int mChoiceMode = CHOICE_MODE_NONE;

	/**
	 * Controls CHOICE_MODE_MULTIPLE_MODAL. null when inactive.
	 */
	ActionMode mChoiceActionMode;

	/**
	 * Wrapper for the multiple choice mode callback; AbsListView needs to perform
	 * a few extra actions around what application code does.
	 */
	MultiChoiceModeWrapper mMultiChoiceModeCallback;

	/**
	 * Running count of how many items are currently checked
	 */
	int mCheckedItemCount;
	public int getCheckedItemCount() {
		return mCheckedItemCount;
	}

	/**
	 * Running state of which positions are currently checked
	 */
	SparseBooleanArray mCheckStates;

	/**
	 * Running state of which IDs are currently checked.
	 * If there is a value for a given key, the checked state for that ID is true
	 * and the value holds the last known position in the adapter for that id.
	 */
	LongSparseArray<Integer> mCheckedIdStates;

	/**
	 * Returns the checked state of the specified position. The result is only
	 * valid if the choice mode has been set to {@link #CHOICE_MODE_SINGLE}
	 * or {@link #CHOICE_MODE_MULTIPLE}.
	 *
	 * @param position The item whose checked state to return
	 * @return The item's checked state or <code>false</code> if choice mode
	 *         is invalid
	 *
	 * @see #setChoiceMode(int)
	 */
	public boolean isItemChecked(int position) {
		if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
			return mCheckStates.get(position);
		}

		return false;
	}

	/**
	 * Returns the currently checked item. The result is only valid if the choice
	 * mode has been set to {@link #CHOICE_MODE_SINGLE}.
	 *
	 * @return The position of the currently checked item or
	 *         {@link #INVALID_POSITION} if nothing is selected
	 *
	 * @see #setChoiceMode(int)
	 */
	public int getCheckedItemPosition() {
		if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
			return mCheckStates.keyAt(0);
		}

		return INVALID_POSITION;
	}

	/**
	 * Returns the set of checked items in the list. The result is only valid if
	 * the choice mode has not been set to {@link #CHOICE_MODE_NONE}.
	 *
	 * @return  A SparseBooleanArray which will return true for each call to
	 *          get(int position) where position is a checked position in the
	 *          list and false otherwise, or <code>null</code> if the choice
	 *          mode is set to {@link #CHOICE_MODE_NONE}.
	 */
	public SparseBooleanArray getCheckedItemPositions() {
		if (mChoiceMode != CHOICE_MODE_NONE) {
			return mCheckStates;
		}
		return null;
	}

	/**
	 * Returns the set of checked items ids. The result is only valid if the
	 * choice mode has not been set to {@link #CHOICE_MODE_NONE} and the adapter
	 * has stable IDs. ({@link ListAdapter#hasStableIds()} == {@code true})
	 *
	 * @return A new array which contains the id of each checked item in the
	 *         list.
	 */
	public long[] getCheckedItemIds() {
		if (mChoiceMode == CHOICE_MODE_NONE || mCheckedIdStates == null || mAdapter == null) {
			return new long[0];
		}

		final LongSparseArray<Integer> idStates = mCheckedIdStates;
		final int count = idStates.size();
		final long[] ids = new long[count];

		for (int i = 0; i < count; i++) {
			ids[i] = idStates.keyAt(i);
		}

		return ids;
	}

	/**
	 * Clear any choices previously set
	 */
	public void clearChoices() {
		if (mCheckStates != null) {
			mCheckStates.clear();
		}
		if (mCheckedIdStates != null) {
			mCheckedIdStates.clear();
		}
		mCheckedItemCount = 0;
	}


	/**
	 * Sets the checked state of the specified position. The is only valid if
	 * the choice mode has been set to {@link #CHOICE_MODE_SINGLE} or
	 * {@link #CHOICE_MODE_MULTIPLE}.
	 *
	 * @param position The item whose checked state is to be checked
	 * @param value The new checked state for the item
	 */
	public void setItemChecked(int position, boolean value) {
		if (mChoiceMode == CHOICE_MODE_NONE) {
			return;
		}

		// Start selection mode if needed. We don't need to if we're unchecking something.
		if (value && mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL && mChoiceActionMode == null) {
			if (mMultiChoiceModeCallback == null ||
					!mMultiChoiceModeCallback.hasWrappedCallback()) {
				throw new IllegalStateException("AbsListView: attempted to start selection mode " +
						"for CHOICE_MODE_MULTIPLE_MODAL but no choice mode callback was " +
						"supplied. Call setMultiChoiceModeListener to set a callback.");
			}
			mChoiceActionMode = startActionMode(mMultiChoiceModeCallback);
		}

		if (mChoiceMode == CHOICE_MODE_MULTIPLE || mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
			boolean oldValue = mCheckStates.get(position);
			mCheckStates.put(position, value);
			if (mCheckedIdStates != null && mAdapter.hasStableIds()) {
				if (value) {
					mCheckedIdStates.put(mAdapter.getItemId(position), position);
				} else {
					mCheckedIdStates.delete(mAdapter.getItemId(position));
				}
			}
			if (oldValue != value) {
				if (value) {
					mCheckedItemCount++;
				} else {
					mCheckedItemCount--;
				}
			}
			if (mChoiceActionMode != null) {
				final long id = mAdapter.getItemId(position);
				mMultiChoiceModeCallback.onItemCheckedStateChanged(mChoiceActionMode,
						position, id, value);
			}
		} else {
			boolean updateIds = mCheckedIdStates != null && mAdapter.hasStableIds();
			// Clear all values if we're checking something, or unchecking the currently
			// selected item
			if (value || isItemChecked(position)) {
				mCheckStates.clear();
				if (updateIds) {
					mCheckedIdStates.clear();
				}
			}
			// this may end up selecting the value we just cleared but this way
			// we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
			if (value) {
				mCheckStates.put(position, true);
				if (updateIds) {
					mCheckedIdStates.put(mAdapter.getItemId(position), position);
				}
				mCheckedItemCount = 1;
			} else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
				mCheckedItemCount = 0;
			}
		}

		// Do not generate a data change while we are in the layout phase
		if (!mInLayout && !mBlockLayoutRequests) {
			mDataChanged = true;
			rememberSyncState();
			requestLayout();
		}
	}
	/**
	 * @see #setChoiceMode(int)
	 *
	 * @return The current choice mode
	 */
	public int getChoiceMode() {
		return mChoiceMode;
	}

	/**
	 * Defines the choice behavior for the List. By default, Lists do not have any choice behavior
	 * ({@link #CHOICE_MODE_NONE}). By setting the choiceMode to {@link #CHOICE_MODE_SINGLE}, the
	 * List allows up to one item to  be in a chosen state. By setting the choiceMode to
	 * {@link #CHOICE_MODE_MULTIPLE}, the list allows any number of items to be chosen.
	 *
	 * @param choiceMode One of {@link #CHOICE_MODE_NONE}, {@link #CHOICE_MODE_SINGLE}, or
	 * {@link #CHOICE_MODE_MULTIPLE}
	 */
	public void setChoiceMode(int choiceMode) {
		mChoiceMode = choiceMode;
		if (mChoiceActionMode != null) {
			mChoiceActionMode.finish();
			mChoiceActionMode = null;
		}
		if (mChoiceMode != CHOICE_MODE_NONE) {
			if (mCheckStates == null) {
				mCheckStates = new SparseBooleanArray(0);
			}
			if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
				mCheckedIdStates = new LongSparseArray<Integer>(0);
			}
			// Modal multi-choice mode only has choices when the mode is active. Clear them.
			if (mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
				clearChoices();
				setLongClickable(true);
			}
		}
	}

	/**
	 * Set a {@link MultiChoiceModeListener} that will manage the lifecycle of the
	 * selection {@link ActionMode}. Only used when the choice mode is set to
	 * {@link #CHOICE_MODE_MULTIPLE_MODAL}.
	 *
	 * @param listener Listener that will manage the selection mode
	 *
	 * @see #setChoiceMode(int)
	 */
	public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
		if (mMultiChoiceModeCallback == null) {
			mMultiChoiceModeCallback = new MultiChoiceModeWrapper();
		}
		mMultiChoiceModeCallback.setWrapped(listener);
	}


	/**
	 * A MultiChoiceModeListener receives events for {@link AbsListView#CHOICE_MODE_MULTIPLE_MODAL}.
	 * It acts as the {@link ActionMode.Callback} for the selection mode and also receives
	 * {@link #onItemCheckedStateChanged(ActionMode, int, long, boolean)} events when the user
	 * selects and deselects list items.
	 */
	public interface MultiChoiceModeListener extends ActionMode.Callback {
		/**
		 * Called when an item is checked or unchecked during selection mode.
		 *
		 * @param mode The {@link ActionMode} providing the selection mode
		 * @param position Adapter position of the item that was checked or unchecked
		 * @param id Adapter ID of the item that was checked or unchecked
		 * @param checked <code>true</code> if the item is now checked, <code>false</code>
		 *                if the item is now unchecked.
		 */
		public void onItemCheckedStateChanged(ActionMode mode,
		                                      int position, long id, boolean checked);
	}

	class MultiChoiceModeWrapper implements MultiChoiceModeListener {
		private MultiChoiceModeListener mWrapped;

		public void setWrapped(MultiChoiceModeListener wrapped) {
			mWrapped = wrapped;
		}

		public boolean hasWrappedCallback() {
			return mWrapped != null;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			if (mWrapped.onCreateActionMode(mode, menu)) {
				// Initialize checked graphic state?
				setLongClickable(false);
				return true;
			}
			return false;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return mWrapped.onPrepareActionMode(mode, menu);
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			return mWrapped.onActionItemClicked(mode, item);
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mWrapped.onDestroyActionMode(mode);
			mChoiceActionMode = null;

			// Ending selection mode means deselecting everything.
			clearChoices();

			mDataChanged = true;
			rememberSyncState();
			requestLayout();

			setLongClickable(true);
		}

		@Override
		public void onItemCheckedStateChanged(ActionMode mode,
		                                      int position, long id, boolean checked) {
			mWrapped.onItemCheckedStateChanged(mode, position, id, checked);

			// If there are no items selected we no longer need the selection mode.
			if (getCheckedItemCount() == 0) {
				mode.finish();
			}
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// PROTECTED POSITIONING EXTENSABLES
	//

	/**
	 * Override
	 */
	protected void onChildCreated(final int position, final boolean flowDown) {

	}

	/**
	 * Override to position the child as you so wish
	 */
	protected void onLayoutChild(final View child, final int position,
	                             final boolean flowDown, final int childrenLeft, final int childTop,
	                             final int childRight, final int childBottom) {
		child.layout(childrenLeft, childTop, childRight, childBottom);
	}

	/**
	 * Override to offset the child as you so wish
	 */
	protected void onOffsetChild(final View child, final int position,
	                             final boolean flowDown, final int childrenLeft, final int childTop) {
		child.offsetLeftAndRight(childrenLeft - child.getLeft());
		child.offsetTopAndBottom(childTop - child.getTop());
	}

	/**
	 * Override to set you custom listviews child to a specific left location
	 *
	 * @return the left location to layout the child for the given position
	 */
	protected int getChildLeft(final int position) {
		return getListPaddingLeft();
	}

	/**
	 * Override to set you custom listviews child to a specific top location
	 *
	 * @return the top location to layout the child for the given position
	 */
	protected int getChildTop(final int position) {
		int count = getChildCount();
		int paddingTop = 0;
		if (mClipToPadding) {
			paddingTop = getListPaddingTop();
		}
		return count > 0 ? getChildAt(count - 1).getBottom() : paddingTop;
	}

	/**
	 * Override to set you custom listviews child to a bottom top location
	 *
	 * @return the bottom location to layout the child for the given position
	 */
	protected int getChildBottom(final int position) {
		int count = getChildCount();
		int paddingBottom = 0;
		if (mClipToPadding) {
			paddingBottom = getListPaddingBottom();
		}
		return count > 0 ? getChildAt(0).getTop() : getHeight() - paddingBottom;
	}

	protected int getNextChildDownsTop(final int position) {
		final int count = getChildCount();
		return count > 0 ? getChildAt(count - 1).getBottom() : 0;
	}

	protected int getNextChildUpsBottom(final int position) {
		final int count = getChildCount();
		if (count == 0) {
			return 0;
		}
		return count > 0 ? getChildAt(0).getTop() : 0;
	}

	protected int getFirstChildTop() {
		return hasChildren() ? getChildAt(0).getTop() : 0;
	}

	protected int getHighestChildTop() {
		return hasChildren() ? getChildAt(0).getTop() : 0;
	}

	protected int getLastChildBottom() {
		return hasChildren() ? getChildAt(getChildCount() - 1).getBottom() : 0;
	}

	protected int getLowestChildBottom() {
		return hasChildren() ? getChildAt(getChildCount() - 1).getBottom() : 0;
	}

	protected boolean hasChildren() {
		return getChildCount() > 0;
	}

	protected void offsetChildrenTopAndBottom(int offset) {
		if (DBG) Log.d(TAG, "offsetChildrenTopAndBottom: " + offset);
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			final View v = getChildAt(i);
			v.offsetTopAndBottom(offset);
		}
	}

	public int getFirstPosition() {
		return mFirstPosition;
	}

	@Override
	public int getFirstVisiblePosition() {
		return Math.max(0, mFirstPosition - getHeaderViewsCount());
	}

	@Override
	public int getLastVisiblePosition() {
		return Math.min(mFirstPosition + getChildCount() - 1, mAdapter != null ? mAdapter.getCount() - 1 : 0);
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// FLING
	//

	private void initOrResetVelocityTracker() {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		} else {
			mVelocityTracker.clear();
		}
	}

	private void initVelocityTrackerIfNotExists() {
		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
	}

	private void recycleVelocityTracker() {
		if (mVelocityTracker != null) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
	}

	private void startFlingRunnable(final float velocity) {
		if (mFlingRunnable == null) {
			mFlingRunnable = new FlingRunnable();
		}
		mFlingRunnable.start((int) -velocity);
	}

	protected void stopFlingRunnable() {
		if (mFlingRunnable != null) {
			mFlingRunnable.endFling();
			mFlingRunnable = null;
		}
	}

	@Override
	public boolean canScrollVertically(int direction) {
		return canScrollList(direction);
	}

	private Rect listPaddingsRect = new Rect();

	private Rect getListPaddingRect() {
		listPaddingsRect.set(getListPaddingLeft(), getListPaddingTop(), getListPaddingRight(),
				getListPaddingBottom());
		return listPaddingsRect;
	}

	public boolean canScrollList(int direction) {
		final int childCount = getChildCount();
		if (childCount == 0) {
			return false;
		}

		final int firstPosition = mFirstPosition;
		final Rect listPadding = getListPaddingRect();
		if (direction > 0) {
			final int lastBottom = getChildAt(childCount - 1).getBottom();
			final int lastPosition = firstPosition + childCount;
			return lastPosition < mItemCount || lastBottom > getHeight() - listPadding.bottom;
		} else {
			final int firstTop = getChildAt(0).getTop();
			return firstPosition > 0 || firstTop < listPadding.top;
		}
	}


	@Override
	public void smoothScrollBy(int distance, int duration) {
		if (mFlingRunnable == null) {
			mFlingRunnable = new FlingRunnable();
		}

		// No sense starting to scroll if we're not going anywhere
		final int firstPos = mFirstPosition;
		final int childCount = getChildCount();
		final int lastPos = firstPos + childCount;
		final int topLimit = getPaddingTop();
		final int bottomLimit = getHeight() - getPaddingBottom();

		if (distance == 0 || mItemCount == 0 || childCount == 0 ||
				(firstPos == 0 && getChildAt(0).getTop() == topLimit && distance < 0) ||
				(lastPos == mItemCount &&
						getChildAt(childCount - 1).getBottom() == bottomLimit && distance > 0)) {
			mFlingRunnable.endFling();
		} else {
			reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
			mFlingRunnable.startScroll(distance, duration);
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// FLING RUNNABLE
	//

	/**
	 * Responsible for fling behavior. Use {@link #start(int)} to
	 * initiate a fling. Each frame of the fling is handled in {@link #run()}.
	 * A FlingRunnable will keep re-posting itself until the fling is done.
	 */
	private class FlingRunnable implements Runnable {
		/**
		 * Tracks the decay of a fling scroll
		 */
		private final OverScroller mScroller;
//	    private final DecelerateInterpolator decelerateInterpolator;

		/**
		 * Y value reported by mScroller on the previous fling
		 */
		private int mLastFlingY;

		FlingRunnable() {
			mScroller = new OverScroller(getContext());
		}

		void start(int initialVelocity) {
			int initialY = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
			mLastFlingY = initialY;
			mScroller.forceFinished(true);
			mScroller.fling(0, initialY, 0, initialVelocity, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
			mTouchMode = TOUCH_MODE_FLINGING;
			postOnAnimate(this);
		}

		void startScroll(int distance, int duration) {
			int initialY = distance < 0 ? Integer.MAX_VALUE : 0;
			mLastFlingY = initialY;
			mScroller.startScroll(0, initialY, 0, distance, duration);
			mTouchMode = TOUCH_MODE_FLINGING;
			postOnAnimate(this);
		}

		private void endFling() {
			mLastFlingY = 0;
			mTouchMode = TOUCH_MODE_IDLE;

			reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
			removeCallbacks(this);

			mScroller.forceFinished(true);
		}

		public void run() {
			switch (mTouchMode) {
				default:
					return;

				case TOUCH_MODE_FLINGING: {
					if (mItemCount == 0 || getChildCount() == 0) {
						endFling();
						return;
					}

					final OverScroller scroller = mScroller;
					boolean more = scroller.computeScrollOffset();
					final int y = scroller.getCurrY();

					// Flip sign to convert finger direction to list items direction
					// (e.g. finger moving down means list is moving towards the top)
					int delta = mLastFlingY - y;

					// Pretend that each frame of a fling scroll is a touch scroll
					if (delta > 0) {
						// List is moving towards the top. Use first view as mMotionPosition
						mMotionPosition = mFirstPosition;
						// Don't fling more than 1 screen
						delta = Math.min(getHeight() - getPaddingBottom() - getPaddingTop() - 1, delta);
					} else {
						// List is moving towards the bottom. Use last view as mMotionPosition
						int offsetToLast = getChildCount() - 1;
						mMotionPosition = mFirstPosition + offsetToLast;

						// Don't fling more than 1 screen
						delta = Math.max(-(getHeight() - getPaddingBottom() - getPaddingTop() - 1), delta);
					}

					final boolean atEnd = moveTheChildren(delta, delta);

					if (more && !atEnd) {
						invalidate();
						mLastFlingY = y;
						postOnAnimate(this);
					} else {
						endFling();
					}
					break;
				}
			}
		}

	}

	private void postOnAnimate(Runnable runnable) {
		this.postOnAnimation(runnable);
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// SCROLL LISTENER
	//

	/**
	 * Notify any scroll listeners of our current touch mode
	 */
	public void notifyTouchMode() {
		// only tell the scroll listener about some things we want it to know
		switch (mTouchMode) {
			case TOUCH_MODE_SCROLLING:
				reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
				break;
			case TOUCH_MODE_FLINGING:
				reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
				break;
			case TOUCH_MODE_IDLE:
				reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
				break;
		}
	}

	private OnScrollListener mOnScrollListener;

	public void setOnScrollListener(OnScrollListener scrollListener) {
		super.setOnScrollListener(scrollListener);
		mOnScrollListener = scrollListener;
	}

	protected void reportScrollStateChange(int newState) {
		if (newState != mScrollState) {
			mScrollState = newState;
			if (mOnScrollListener != null) {
				mOnScrollListener.onScrollStateChanged(this, newState);
			}
		}
	}

	public int getScrollState() {
		return mScrollState;
	}

	public float getCurrentVelocity() {
		if (mFlingRunnable != null) {
			return mFlingRunnable.mScroller.getCurrVelocity();
		}
		return 0;
	}

	protected void invokeOnItemScrollListener() {
		if (mOnScrollListener != null) {
			mOnScrollListener.onScroll(this, mFirstPosition, getChildCount(), mItemCount);
		}
	}

	/**
	 * Update the status of the list based on the empty parameter.  If empty is true and
	 * we have an empty view, display it.  In all the other cases, make sure that the listview
	 * is VISIBLE and that the empty view is GONE (if it's not null).
	 */
	@SuppressLint("WrongCall")
	private void updateEmptyStatus() {
		boolean empty = getAdapter() == null || getAdapter().isEmpty();
		if (isInFilterMode()) {
			empty = false;
		}

		View emptyView = getEmptyView();
		if (empty) {
			if (emptyView != null) {
				emptyView.setVisibility(View.VISIBLE);
				setVisibility(View.GONE);
			} else {
				// If the caller just removed our empty view, make sure the list view is visible
				setVisibility(View.VISIBLE);
			}

			// We are now GONE, so pending layouts will not be dispatched.
			// Force one here to make sure that the state of the list matches
			// the state of the adapter.
			if (mDataChanged) {
				this.onLayout(false, getLeft(), getTop(), getRight(), getBottom());
			}
		} else {
			if (emptyView != null) {
				emptyView.setVisibility(View.GONE);
			}
			setVisibility(View.VISIBLE);
		}
	}

	public void stableView() {
		stopFlingRunnable();
		recycleVelocityTracker();
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// ADAPTER OBSERVER
	//


	class AdapterDataSetObserver extends DataSetObserver {

		private Parcelable mInstanceState = null;

		@Override
		public void onChanged() {
			stableView();
			mDataChanged = true;
			mOldItemCount = mItemCount;
			mItemCount = getAdapter().getCount();

			mRecycleBin.clearTransientStateViews();

			// Detect the case where a cursor that was previously invalidated has
			// been repopulated with new data.
			if (ExtendableListView.this.getAdapter().hasStableIds() && mInstanceState != null
					&& mOldItemCount == 0 && mItemCount > 0) {
				ExtendableListView.this.onRestoreInstanceState(mInstanceState);
				mInstanceState = null;
			} else {
				rememberSyncState();
			}

			updateEmptyStatus();
			requestLayout();
		}

		@Override
		public void onInvalidated() {
			mDataChanged = true;

			if (ExtendableListView.this.getAdapter().hasStableIds()) {
				// Remember the current state for the case where our hosting activity is being
				// stopped and later restarted
				mInstanceState = ExtendableListView.this.onSaveInstanceState();
			}

			// Data is invalid so we should reset our state
			mOldItemCount = mItemCount;
			mItemCount = 0;
			mNeedSync = false;

			updateEmptyStatus();
			requestLayout();
		}

		public void clearSavedState() {
			mInstanceState = null;
		}
	}


	// //////////////////////////////////////////////////////////////////////////////////////////
	// LAYOUT PARAMS
	//

	/**
	 * Re-implementing some properties in {@link android.view.ViewGroup.LayoutParams} since they're package
	 * private but we want to appear to be an extension of the existing class.
	 */
	public static class LayoutParams extends AbsListView.LayoutParams {

		boolean recycledHeaderFooter;

		// Position of the view in the data
		int position;

		// adapter ID the view represents fetched from the adapter if it's stable
		long itemId = -1;

		// adapter view type
		int viewType;

		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
		}

		public LayoutParams(int w, int h) {
			super(w, h);
		}

		public LayoutParams(int w, int h, int viewType) {
			super(w, h);
			this.viewType = viewType;
		}

		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}

	}

	public void reclaimViews(List<View> views) {
		int childCount = getChildCount();
		RecyclerListener listener = mRecycleBin.recyclerListener;

		// Reclaim views on screen
		for (int i = 0; i < childCount; i++) {
			View child = getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			// Don't reclaim header or footer views, or views that should be ignored
			if (lp != null && mRecycleBin.shouldRecycleViewType(lp.viewType)) {
				views.add(child);
				if (listener != null) {
					// Pretend they went through the scrap heap
					listener.onMovedToScrapHeap(child);
				}
			}
		}
		mRecycleBin.reclaimScrapViews(views);
		removeAllViewsInLayout();
	}

	@Override
	public void setRecyclerListener(RecyclerListener listener) {
		mRecycleBin.recyclerListener = listener;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// RecycleBin
	//

	/**
	 * Note there's no RecyclerListener. The caller shouldn't have a need and we can add it later.
	 */
	class RecycleBin {

		private RecyclerListener recyclerListener;
		/**
		 * The position of the first view stored in mActiveViews.
		 */
		private int mFirstActivePosition;

		/**
		 * Views that were on screen at the start of layout. This array is populated at the start of
		 * layout, and at the end of layout all view in mActiveViews are moved to mScrapViews.
		 * Views in mActiveViews represent a contiguous range of Views, with position of the first
		 * view store in mFirstActivePosition.
		 */
		private View[] mActiveViews = new View[0];

		/**
		 * Unsorted views that can be used by the adapter as a convert view.
		 */
		private ArrayList<View>[] mScrapViews;

		private int mViewTypeCount;

		private ArrayList<View> mCurrentScrap;

		private ArrayList<View> mSkippedScrap;

		private SparseArray<View> mTransientStateViews;

		public void setViewTypeCount(int viewTypeCount) {
			if (viewTypeCount < 1) {
				throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
			}
			//noinspection unchecked
			ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
			for (int i = 0; i < viewTypeCount; i++) {
				scrapViews[i] = new ArrayList<View>();
			}
			mViewTypeCount = viewTypeCount;
			mCurrentScrap = scrapViews[0];
			mScrapViews = scrapViews;
		}

		public void markChildrenDirty() {
			if (mViewTypeCount == 1) {
				final ArrayList<View> scrap = mCurrentScrap;
				final int scrapCount = scrap.size();
				for (int i = 0; i < scrapCount; i++) {
					scrap.get(i).forceLayout();
				}
			} else {
				final int typeCount = mViewTypeCount;
				for (int i = 0; i < typeCount; i++) {
					final ArrayList<View> scrap = mScrapViews[i];
					final int scrapCount = scrap.size();
					for (int j = 0; j < scrapCount; j++) {
						scrap.get(j).forceLayout();
					}
				}
			}
			if (mTransientStateViews != null) {
				final int count = mTransientStateViews.size();
				for (int i = 0; i < count; i++) {
					mTransientStateViews.valueAt(i).forceLayout();
				}
			}
		}

		public boolean shouldRecycleViewType(int viewType) {
			return viewType >= 0;
		}

		/**
		 * Clears the scrap heap.
		 */
		void clear() {
			if (mViewTypeCount == 1) {
				final ArrayList<View> scrap = mCurrentScrap;
				final int scrapCount = scrap.size();
				for (int i = 0; i < scrapCount; i++) {
					removeDetachedView(scrap.remove(scrapCount - 1 - i), false);
				}
			} else {
				final int typeCount = mViewTypeCount;
				for (int i = 0; i < typeCount; i++) {
					final ArrayList<View> scrap = mScrapViews[i];
					final int scrapCount = scrap.size();
					for (int j = 0; j < scrapCount; j++) {
						removeDetachedView(scrap.remove(scrapCount - 1 - j), false);
					}
				}
			}
			if (mTransientStateViews != null) {
				mTransientStateViews.clear();
			}
		}

		/**
		 * Fill ActiveViews with all of the children of the AbsListView.
		 *
		 * @param childCount          The minimum number of views mActiveViews should hold
		 * @param firstActivePosition The position of the first view that will be stored in
		 *                            mActiveViews
		 */
		void fillActiveViews(int childCount, int firstActivePosition) {
			if (mActiveViews.length < childCount) {
				mActiveViews = new View[childCount];
			}
			mFirstActivePosition = firstActivePosition;

			final View[] activeViews = mActiveViews;
			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				LayoutParams lp = (LayoutParams) child.getLayoutParams();
				// Don't put header or footer views into the scrap heap
				if (lp != null && lp.viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER) {
					// Note:  We do place AdapterView.ITEM_VIEW_TYPE_IGNORE in active views.
					//        However, we will NOT place them into scrap views.
					activeViews[i] = child;
				}
			}
		}

		/**
		 * Get the view corresponding to the specified position. The view will be removed from
		 * mActiveViews if it is found.
		 *
		 * @param position The position to look up in mActiveViews
		 * @return The view if it is found, null otherwise
		 */
		View getActiveView(int position) {
			int index = position - mFirstActivePosition;
			final View[] activeViews = mActiveViews;
			if (index >= 0 && index < activeViews.length) {
				final View match = activeViews[index];
				activeViews[index] = null;
				return match;
			}
			return null;
		}

		View getTransientStateView(int position) {
			if (mTransientStateViews == null) {
				return null;
			}
			final int index = mTransientStateViews.indexOfKey(position);
			if (index < 0) {
				return null;
			}
			final View result = mTransientStateViews.valueAt(index);
			mTransientStateViews.removeAt(index);
			return result;
		}

		void reclaimScrapViews(List<View> views) {
			if (mViewTypeCount == 1) {
				views.addAll(mCurrentScrap);
			} else {
				final int viewTypeCount = mViewTypeCount;
				final ArrayList<View>[] scrapViews = mScrapViews;
				for (int i = 0; i < viewTypeCount; ++i) {
					final ArrayList<View> scrapPile = scrapViews[i];
					views.addAll(scrapPile);
				}
			}
		}

		/**
		 * Dump any currently saved views with transient state.
		 */
		void clearTransientStateViews() {
			if (mTransientStateViews != null) {
				mTransientStateViews.clear();
			}
		}

		/**
		 * @return A view from the ScrapViews collection. These are unordered.
		 */
		View getScrapView(int position) {
			if (mViewTypeCount == 1) {
				return retrieveFromScrap(mCurrentScrap, position);
			} else {
				int whichScrap = mAdapter.getItemViewType(position);
				if (whichScrap >= 0 && whichScrap < mScrapViews.length) {
					return retrieveFromScrap(mScrapViews[whichScrap], position);
				}
			}
			return null;
		}

		/**
		 * Put a view into the ScrapViews list. These views are unordered.
		 *
		 * @param scrap The view to add
		 */
		void addScrapView(View scrap, int position) {
			if (DBG) Log.d(TAG, "addScrapView position = " + position);

			LayoutParams lp = (LayoutParams) scrap.getLayoutParams();
			if (lp == null) {
				return;
			}

			lp.position = position;

			// Don't put header or footer views or views that should be ignored
			// into the scrap heap
			int viewType = lp.viewType;
			final boolean scrapHasTransientState = scrap.hasTransientState();
			if (!shouldRecycleViewType(viewType) || scrapHasTransientState) {
				if (viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER || scrapHasTransientState) {
					if (mSkippedScrap == null) {
						mSkippedScrap = new ArrayList<View>();
					}
					mSkippedScrap.add(scrap);
				}
				if (scrapHasTransientState) {
					if (mTransientStateViews == null) {
						mTransientStateViews = new SparseArray<View>();
					}
					mTransientStateViews.put(position, scrap);
				}
				return;
			}

			if (mViewTypeCount == 1) {
				mCurrentScrap.add(scrap);
			} else {
				mScrapViews[viewType].add(scrap);
			}

			if (recyclerListener != null) {
				recyclerListener.onMovedToScrapHeap(scrap);
			}
		}

		/**
		 * Finish the removal of any views that skipped the scrap heap.
		 */
		void removeSkippedScrap() {
			if (mSkippedScrap == null) {
				return;
			}
			final int count = mSkippedScrap.size();
			for (int i = 0; i < count; i++) {
				removeDetachedView(mSkippedScrap.get(i), false);
			}
			mSkippedScrap.clear();
		}

		/**
		 * Move all views remaining in mActiveViews to mScrapViews.
		 */
		void scrapActiveViews() {
			final View[] activeViews = mActiveViews;
			final boolean multipleScraps = mViewTypeCount > 1;
			final boolean hasListener = recyclerListener != null;

			ArrayList<View> scrapViews = mCurrentScrap;
			final int count = activeViews.length;
			for (int i = count - 1; i >= 0; i--) {
				final View victim = activeViews[i];
				if (victim != null) {
					final LayoutParams lp = (LayoutParams) victim.getLayoutParams();
					activeViews[i] = null;

					final boolean scrapHasTransientState = victim.hasTransientState();
					int viewType = lp.viewType;

					if (!shouldRecycleViewType(viewType) || scrapHasTransientState) {
						// Do not move views that should be ignored
						if (viewType != ITEM_VIEW_TYPE_HEADER_OR_FOOTER || scrapHasTransientState) {
							removeDetachedView(victim, false);
						}
						if (scrapHasTransientState) {
							if (mTransientStateViews == null) {
								mTransientStateViews = new SparseArray<View>();
							}
							mTransientStateViews.put(mFirstActivePosition + i, victim);
						}
						continue;
					}

					if (multipleScraps) {
						scrapViews = mScrapViews[viewType];
					}
					lp.position = mFirstActivePosition + i;
					scrapViews.add(victim);

					if (hasListener) {
						recyclerListener.onMovedToScrapHeap(victim);
					}
				}
			}

			pruneScrapViews();
		}

		/**
		 * Makes sure that the size of mScrapViews does not exceed the size of mActiveViews.
		 * (This can happen if an adapter does not recycle its views).
		 */
		private void pruneScrapViews() {
			final int maxViews = mActiveViews.length;
			final int viewTypeCount = mViewTypeCount;
			final ArrayList<View>[] scrapViews = mScrapViews;
			for (int i = 0; i < viewTypeCount; ++i) {
				final ArrayList<View> scrapPile = scrapViews[i];
				int size = scrapPile.size();
				final int extras = size - maxViews;
				size--;
				for (int j = 0; j < extras; j++) {
					removeDetachedView(scrapPile.remove(size--), false);
				}
			}

			if (mTransientStateViews != null) {
				for (int i = 0; i < mTransientStateViews.size(); i++) {
					final View v = mTransientStateViews.valueAt(i);
					if (!v.hasTransientState()) {
						mTransientStateViews.removeAt(i);
						i--;
					}
				}
			}
		}

		/**
		 * Updates the cache color hint of all known views.
		 *
		 * @param color The new cache color hint.
		 */
		void setCacheColorHint(int color) {
			if (mViewTypeCount == 1) {
				final ArrayList<View> scrap = mCurrentScrap;
				final int scrapCount = scrap.size();
				for (int i = 0; i < scrapCount; i++) {
					scrap.get(i).setDrawingCacheBackgroundColor(color);
				}
			} else {
				final int typeCount = mViewTypeCount;
				for (int i = 0; i < typeCount; i++) {
					final ArrayList<View> scrap = mScrapViews[i];
					final int scrapCount = scrap.size();
					for (int j = 0; j < scrapCount; j++) {
						scrap.get(j).setDrawingCacheBackgroundColor(color);
					}
				}
			}
			// Just in case this is called during a layout pass
			final View[] activeViews = mActiveViews;
			final int count = activeViews.length;
			for (int i = 0; i < count; ++i) {
				final View victim = activeViews[i];
				if (victim != null) {
					victim.setDrawingCacheBackgroundColor(color);
				}
			}
		}
	}

	static View retrieveFromScrap(ArrayList<View> scrapViews, int position) {
		int size = scrapViews.size();
		if (size > 0) {
			// See if we still have a view for this position.
			for (int i = 0; i < size; i++) {
				View view = scrapViews.get(i);
				if (((LayoutParams) view.getLayoutParams()).position == position) {
					scrapViews.remove(i);
					return view;
				}
			}
			return scrapViews.remove(size - 1);
		} else {
			return null;
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////
	// OUR STATE
	//

	/**
	 * Position from which to start looking for mSyncRowId
	 */
	protected int mSyncPosition;

	/**
	 * The offset in pixels from the top of the AdapterView to the top
	 * of the view to select during the next layout.
	 */
	protected int mSpecificTop;

	/**
	 * Row id to look for when data has changed
	 */
	long mSyncRowId = INVALID_ROW_ID;

	/**
	 * Height of the view when mSyncPosition and mSyncRowId where set
	 */
	long mSyncHeight;

	/**
	 * True if we need to sync to mSyncRowId
	 */
	boolean mNeedSync = false;


	private ListSavedState mSyncState;


	/**
	 * Remember enough information to restore the screen state when the data has
	 * changed.
	 */
	void rememberSyncState() {
		if (getChildCount() > 0) {
			mNeedSync = true;
			mSyncHeight = getHeight();
			// Sync the based on the offset of the first view
			View v = getChildAt(0);
			ListAdapter adapter = getAdapter();
			if (mFirstPosition >= 0 && mFirstPosition < adapter.getCount()) {
				mSyncRowId = adapter.getItemId(mFirstPosition);
			} else {
				mSyncRowId = NO_ID;
			}
			if (v != null) {
				mSpecificTop = v.getTop();
			}
			mSyncPosition = mFirstPosition;
		}
	}

	private void clearState() {
		// cleanup headers and footers before removing the views
		clearRecycledState(mHeaderViewInfos);
		clearRecycledState(mFooterViewInfos);

		removeAllViewsInLayout();
		mFirstPosition = 0;
		mDataChanged = false;
		mRecycleBin.clear();
		mNeedSync = false;
		mSyncPosition = 0;
		mSpecificTop = 0;
		mSyncRowId = 0;
		mSyncState = null;
		mLayoutMode = LAYOUT_NORMAL;
		invalidate();
	}

	private void clearRecycledState(ArrayList<FixedViewInfo> infos) {
		if (infos == null) return;
		for (FixedViewInfo info : infos) {
			final View child = info.view;
			final ViewGroup.LayoutParams p = child.getLayoutParams();
			if (p instanceof LayoutParams) {
				((LayoutParams) p).recycledHeaderFooter = false;
			}
		}
	}

	public static class ListSavedState extends ClassLoaderSavedState {
		protected long selectedId;
		protected long firstId;
		protected int viewTop;
		protected int position;
		protected int height;

		/**
		 * Constructor called from {@link android.widget.AbsListView#onSaveInstanceState()}
		 */
		public ListSavedState(Parcelable superState) {
			super(superState, AbsListView.class.getClassLoader());
		}

		/**
		 * Constructor called from {@link #CREATOR}
		 */
		public ListSavedState(Parcel in) {
			super(in);
			selectedId = in.readLong();
			firstId = in.readLong();
			viewTop = in.readInt();
			position = in.readInt();
			height = in.readInt();
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeLong(selectedId);
			out.writeLong(firstId);
			out.writeInt(viewTop);
			out.writeInt(position);
			out.writeInt(height);
		}

		@Override
		public String toString() {
			return "ExtendableListView.ListSavedState{"
					+ Integer.toHexString(System.identityHashCode(this))
					+ " selectedId=" + selectedId
					+ " firstId=" + firstId
					+ " viewTop=" + viewTop
					+ " position=" + position
					+ " height=" + height + "}";
		}

		public static final Creator<ListSavedState> CREATOR
				= new Creator<ListSavedState>() {
			public ListSavedState createFromParcel(Parcel in) {
				return new ListSavedState(in);
			}

			public ListSavedState[] newArray(int size) {
				return new ListSavedState[size];
			}
		};
	}


	@Override
	public Parcelable onSaveInstanceState() {
		stableView();
		Parcelable superState = super.onSaveInstanceState();
		ListSavedState ss = new ListSavedState(superState);

		if (mSyncState != null) {
			// Just keep what we last restored.
			ss.selectedId = mSyncState.selectedId;
			ss.firstId = mSyncState.firstId;
			ss.viewTop = mSyncState.viewTop;
			ss.position = mSyncState.position;
			ss.height = mSyncState.height;
			return ss;
		}

		boolean haveChildren = getChildCount() > 0 && mItemCount > 0;
		ss.selectedId = getSelectedItemId();
		ss.height = getHeight();

		// TODO : sync selection when we handle it
		if (haveChildren && mFirstPosition > 0) {
			// Remember the position of the first child.
			// We only do this if we are not currently at the top of
			// the list, for two reasons:
			// (1) The list may be in the process of becoming empty, in
			// which case mItemCount may not be 0, but if we try to
			// ask for any information about position 0 we will crash.
			// (2) Being "at the top" seems like a special case, anyway,
			// and the user wouldn't expect to end up somewhere else when
			// they revisit the list even if its content has changed.
			View v = getChildAt(0);
			ss.viewTop = v.getTop();
			int firstPos = mFirstPosition;
			if (firstPos >= mItemCount) {
				firstPos = mItemCount - 1;
			}
			ss.position = firstPos;
			ss.firstId = mAdapter.getItemId(firstPos);
		} else {
			ss.viewTop = 0;
			ss.firstId = INVALID_POSITION;
			ss.position = 0;
		}

		return ss;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		ListSavedState ss = (ListSavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		mDataChanged = true;

		mSyncHeight = ss.height;

		if (ss.firstId >= 0) {
			mNeedSync = true;
			mSyncState = ss;
			mSyncRowId = ss.firstId;
			mSyncPosition = ss.position;
			mSpecificTop = ss.viewTop;
		}
		requestLayout();
	}

	private boolean performLongPress(final View child,
	                                 final int longPressPosition, final long longPressId) {
		boolean handled = false;

		OnItemLongClickListener onItemLongClickListener = getOnItemLongClickListener();
		if (onItemLongClickListener != null) {
			handled = onItemLongClickListener.onItemLongClick(ExtendableListView.this, child,
					longPressPosition, longPressId);
		}
		if (!handled) {
			mContextMenuInfo = createContextMenuInfo(child, longPressPosition, longPressId);
			handled = getParent() != null && getParent().showContextMenuForChild(this);
		}
		if (handled) {
			performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
		}
		return handled;
	}

	private class PerformClick extends WindowRunnnable implements Runnable {
		int mClickMotionPosition;

		public void run() {
			if (mDataChanged) return;

			final ListAdapter adapter = mAdapter;
			final int motionPosition = mClickMotionPosition;
			if (adapter != null && mItemCount > 0 &&
					motionPosition != INVALID_POSITION &&
					motionPosition < adapter.getCount() && sameWindow()) {
				final View view = getChildAt(motionPosition); // a fix by @pboos

				if (view != null) {
					final int clickPosition = motionPosition + mFirstPosition;
					int headersCount = getHeaderViewsCount();
					if (clickPosition >= headersCount && clickPosition < adapter.getCount() - getFooterViewsCount()) {
						performItemClick(view, clickPosition - headersCount,
								adapter.getItemId(clickPosition));
					}
				}
			}
		}
	}

	/**
	 * A base class for Runnables that will check that their view is still attached to
	 * the original window as when the Runnable was created.
	 */
	private class WindowRunnnable {
		private int mOriginalAttachCount;

		public void rememberWindowAttachCount() {
			mOriginalAttachCount = getWindowAttachCount();
		}

		public boolean sameWindow() {
			return hasWindowFocus() && getWindowAttachCount() == mOriginalAttachCount;
		}
	}
}