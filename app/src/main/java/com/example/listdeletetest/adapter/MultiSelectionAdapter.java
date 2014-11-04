package com.example.listdeletetest.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiSelectionAdapter<T extends IAdapterItem> extends RecyclerView.Adapter<MultiSelectionAdapter.ItemViewHolder> {
	private final static int VIEW_TYPE_INVISIBLE = 0;
	private static int VIEW_TYPE_DEFAULT = 1;

	private LinkedList<T> mList = new LinkedList<T>();
	private Set<Long> mInvisibleItems = new HashSet<Long>();
	private List<Long> mSelectedItemIds = new LinkedList<Long>();
	private Map<Long, IAdapterItem> mIdItemMap = new HashMap<Long, IAdapterItem>();
	private Context mContext;
	private IPresenter mItemPresenter;
	private AdapterState mState;
	private final Object mLock = new Object();
	private Listener mListener;
	private int mLastPosition;

	public static interface Listener {
		void itemClicked(IAdapterItem item);

		void multiSelectionBegun();

		void multiSelectionEnd();

		boolean isSelectionAllowed(long id);

		void multiSelectionUpdate(int count);

		void endReached();
	}

	public MultiSelectionAdapter(Context context, IPresenter itemPresenter) {
		super();
		mContext = context;
		mItemPresenter = itemPresenter;

		setHasStableIds(true);
		setState(new StateNormal());
	}

	public void setListener(Listener listener) {
		mListener = listener;
	}

	public void setState(AdapterState state) {
		mState = state;
		mState.execute();
	}

	@Override
	public int getItemViewType(int position) {
		T item = mList.get(position);
		if (mInvisibleItems.contains(item.getItemId())) {
			return VIEW_TYPE_INVISIBLE;
		}
		return VIEW_TYPE_DEFAULT;
	}

	@Override
	public long getItemId(int position) {
		IAdapterItem item = mList.get(position);
		return item.getItemId();
	}


	public List<IAdapterItem> getSelectedItems() {
		// can be made better, just quick & dirty
		List<IAdapterItem> list = new ArrayList<IAdapterItem>();
		for (long itemid : mSelectedItemIds) {
			IAdapterItem adapterItem = mIdItemMap.get(itemid);
			if (!list.contains(adapterItem)) {
				list.add(adapterItem);
			}
		}
		return list;
	}


	//----------------------------------
	//  RecyclerView
	//----------------------------------
	@Override
	public ItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
		switch (viewType) {
			case VIEW_TYPE_INVISIBLE:
				View view = new NullView(mContext);
				view.requestLayout();
				view.setVisibility(View.GONE);
				return new ItemViewHolder(view);

			default:
				View itemView = mItemPresenter.getView(mContext);
				itemView.setVisibility(View.VISIBLE);
				return new ItemViewHolder(itemView);
		}
	}

	@Override
	public void onBindViewHolder(ItemViewHolder viewHolder, final int position) {
		if (viewHolder.isNotNullView()) {
			View itemView = viewHolder.itemView;
			final T item = mList.get(position);
			final long itemId = item.getItemId();
			mIdItemMap.put(itemId, item);

			mItemPresenter.present(itemView, item);

			itemView.setSelected(mSelectedItemIds.contains(itemId));

			itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mState.handleClick(new Subject(item, position, itemId));
				}
			});
			itemView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					mState.handleLongClick(new Subject(item, position, itemId));
					return false;
				}
			});
		}
		int size = mList.size();

		if (position > mLastPosition) {
			// we scroll down
			if (position > (size - 5)) {
				mListener.endReached();
			}
		}
		mLastPosition = position;
	}


	@Override
	public int getItemCount() {
		return mList.size();
	}

	public void clearSelectionState() {
		setState(new StateNormal());

	}

	//----------------------------------
	//  Custom
	//----------------------------------
	public void replaceAll(List<T> items) {
		if (items == null || items.size() == 0)
			return;

		synchronized (this) {
			mList.clear();
			mList.addAll(items);
			notifyDataSetChanged();
		}
	}

	public void makeInvisible(List<T> items) {
		for (T item : items) {
			mInvisibleItems.add(item.getItemId());
		}
		notifyDataSetChanged();
	}

	public void makeAllVisibleAndNotify(boolean notifyChanged) {
		mInvisibleItems.clear();
		if (notifyChanged) {
			notifyDataSetChanged();
		}
	}


	//----------------------------------
	//  ViewHolder
	//----------------------------------
	public static class ItemViewHolder extends RecyclerView.ViewHolder {
		public boolean isNotNullView() {
			return !(itemView instanceof NullView);
		}
		public ItemViewHolder(View itemView) {
			super(itemView);
		}
	}

	//----------------------------------
	//  NullViewHelper
	//----------------------------------
	public static class NullView extends View {
		public NullView(Context context) {
			super(context);
		}
	}


	//--------------------------------------------------------------------------
	//
	//  States
	//
	//--------------------------------------------------------------------------
	abstract class AdapterState {
		abstract void execute();

		protected void handleClick(Subject subject) {
		}

		protected void handleLongClick(Subject subject) {
		}
	}

	//----------------------------------
	//  Initial State, nothing selected
	//----------------------------------
	class StateNormal extends AdapterState {
		@Override
		void execute() {
			mSelectedItemIds.clear();
			notifyDataSetChanged();
		}

		@Override
		protected void handleClick(Subject subject) {
			mListener.itemClicked(subject.adapterItem);
		}

		@Override
		protected void handleLongClick(Subject subject) {
			setState(new StateTransitionToSelection(subject));
		}
	}

	//----------------------------------
	//  transition state, because after long click also comes implicit single click, just to maintain that.
	//  implicit single click in this state handles how we proceed (selection state, or back to normal)
	//  notifyItemChanged has a weird effect: if it is called, the implicit pending click doesn't come.
	//----------------------------------
	class StateTransitionToSelection extends AdapterState {
		private Subject mSubject;
		private boolean mSelectionAllowed;

		StateTransitionToSelection(Subject subject) {
			mSubject = subject;
		}

		@Override
		void execute() {
			Log.i("XXX", "StateTransitionToSelection::execute");
			mSelectionAllowed = mListener.isSelectionAllowed(mSubject.id);
			if (mSelectionAllowed) {
				mListener.multiSelectionBegun();
				toggleSelection(mSubject);
				// click does not come, bc of notifyitemchanged in toggleSelection, so change state here
				setState(new StateSelection());
			}
		}

		@Override
		protected void handleClick(Subject subject) {
			Log.i("XXX", "StateTransitionToSelection::handleClick");
			if (!mSelectionAllowed) {
				// wait for click event and change state here
				setState(new StateTransitionToNormal());
			}
		}
	}

	//----------------------------------
	//  State "we are in multiselection"
	//----------------------------------
	class StateSelection extends AdapterState {
		@Override
		void execute() {
			Log.d("XXX", "StateSelection::execute");

		}

		@Override
		protected void handleClick(Subject subject) {
			Log.d("XXX", "StateSelection::handleClick");
			toggleSelection(subject);
		}
	}

	//----------------------------------
	//  Transitioning state, clear after multiselection
	//----------------------------------
	class StateTransitionToNormal extends AdapterState {
		@Override
		void execute() {
			mListener.multiSelectionEnd();
			setState(new StateNormal());
		}
	}


	private void toggleSelection(Subject subject) {
		synchronized (mLock) {
			if (!mSelectedItemIds.contains(subject.id)) {
				boolean selectionAllowed = mListener.isSelectionAllowed(subject.id);
				if (selectionAllowed) {
					mSelectedItemIds.add(subject.id);
				}
			} else {
				mSelectedItemIds.remove(subject.id);
			}
			mListener.multiSelectionUpdate(mSelectedItemIds.size());
			notifyItemChanged(subject.position);

			// state check
			if (mSelectedItemIds.size() == 0) {
				setState(new StateTransitionToNormal());
			}
		}
	}

	//----------------------------------
	//  Transfer class between states
	//----------------------------------
	private static class Subject {
		private Subject(IAdapterItem adapterItem, int position, long id) {
			this.adapterItem = adapterItem;
			this.position = position;
			this.id = id;
		}

		public IAdapterItem adapterItem;
		//		public View view;
		public int position;
		public long id;
	}
}
