package com.example.listdeletetest;


import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Toast;

import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.webservice.FauxWebService;
import com.example.listdeletetest.webservice.WebService;
import com.jensdriller.libs.undobar.UndoBar;

import java.util.List;


public class MainFragment extends Fragment {
	private ListController mListController;
	private RecyclerView mListView;
	private SwipeRefreshLayout mSwipeLayout;
	private boolean mUserHasInitiallyScrolled;
	private ActionMode mActionMode;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		mListView = (RecyclerView) view.findViewById(R.id.listView);
		mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
		mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				mListController.fetchTop();
			}
		});
		mSwipeLayout.setColorSchemeResources(
				android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);


		WebService webService = new FauxWebService(getActivity());
		final ListAdapter adapter = ListAdapter.instantiate(getActivity());
		adapter.setListener(new ListAdapter.Listener() {
			@Override
			public void endReached() {
				Log.w("XXX", "endReached called");
				mListController.fetchBottom();
			}

			@Override
			public void itemClicked(ListAdapter.IAdapterItem item) {
				Toast.makeText(getActivity(), "Open Details", Toast.LENGTH_SHORT).show();
			}

			@Override
			public boolean isSelectionAllowed(long id) {
				if (id == 1608761649L) { // <= hardcoded value
					Toast.makeText(getActivity(), "This is your profile image, we don't allow selection", Toast.LENGTH_SHORT).show();
					return false;
				}
				return true;
			}

			@Override
			public void multiSelectionBegun() {
				mActionMode = getActivity().startActionMode(new ActionMode.Callback() {
					@Override
					public boolean onCreateActionMode(ActionMode mode, Menu menu) {
						MenuInflater inflater = getActivity().getMenuInflater();
						inflater.inflate(R.menu.list_select_menu, menu);
						mode.setTitle("Select Items");
						return true;
					}

					@Override
					public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
						return true;
					}

					@Override
					public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
						switch (item.getItemId()) {
							case R.id.action_delete:
								List<Tweet> selectedItems = adapter.getSelectedItems();
								mListController.prepareDelete(selectedItems);


								new UndoBar.Builder(getActivity())
										.setMessage("Delete " + selectedItems.size() + " items")
										.setListener(new UndoBar.Listener() {
											@Override
											public void onHide() {
												mListController.doDelete();
											}

											@Override
											public void onUndo(Parcelable parcelable) {
												mListController.undoPrepareDelete();
											}
										})
										.show();

								mode.finish();
								break;
						}
						return true;
					}

					;

					@Override
					public void onDestroyActionMode(ActionMode mode) {
						adapter.clearSelectionState();
					}
				});
			}

			@Override
			public void multiSelectionEnd() {
				if (mActionMode != null) {
					mActionMode.finish();
					mActionMode = null;
				}
			}

			@Override
			public void multiSelectionUpdate(int count) {
				switch (count) {
					case 0:
						mActionMode.setSubtitle(null);
						break;
					case 1:
						mActionMode.setSubtitle("1 item selected");
						break;
					default:
						mActionMode.setSubtitle("" + count + " items selected");
						break;
				}
			}
		});

		mListController = new ListController(webService, adapter);
		mListController.setRequestStateChangeDelegate(new ListController.RequestStateChangeDelegate() {
			@Override
			public void handleRequestStart() {
				mSwipeLayout.setRefreshing(true);
			}

			@Override
			public void handleRequestComplete() {
				mSwipeLayout.setRefreshing(false);
			}
		});

		mListView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
		mListView.setAdapter(adapter);
		mListView.setLongClickable(true);
		int spacing = getResources().getDimensionPixelSize(R.dimen.item_spacing);
//		mListView.addItemDecoration(new SpacingItemDecoration(spacing, spacing));
		mListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int scrollState) {
				if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
					mUserHasInitiallyScrolled = true; // <= prevents from initially onScroll being called when set as ScrollListener
				}
			}

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				if (!mUserHasInitiallyScrolled) return;

//				int visibleCount = firstVisibleItem + visibleItemCount;
//				if (visibleCount > (totalItemCount - 5)) {
//					mListController.fetchBottom();
//				}
			}
		});
/*
		mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
					mUserHasInitiallyScrolled = true; // <= prevents from initially onScroll being called when set as ScrollListener
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if (!mUserHasInitiallyScrolled) return;

				int visibleCount = firstVisibleItem + visibleItemCount;
				if (visibleCount > (totalItemCount - 5)) {
					mListController.fetchBottom();
				}
			}
		});
*/
/*
		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if(((ExtendableListView) mListView).getActionMode() != null) {

					// check value, if e.g. this is an **important** image which should not be deleted,
					// unset selection
					if(id == 1608761649L) { // <= hardcoded value
						boolean itemChecked = mListView.isItemChecked(position);
						if(itemChecked) {
							mListView.setItemChecked(position, false);
							Toast.makeText(getActivity(), "This is your profile image, we don't allow selection", Toast.LENGTH_SHORT).show();
						}
					}
				} else {
					Toast.makeText(getActivity(), "Open Details", Toast.LENGTH_SHORT).show();
				}

			}
		});
*/
/*
		mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				if(((ExtendableListView) mListView).getActionMode() == null) {
					// check value, if e.g. this is an **important** image which should not be deleted,
					// no item check
					if(id == 1608761649L) { // <= hardcoded value
						Toast.makeText(getActivity(), "This is your profile image, we don't allow selection", Toast.LENGTH_SHORT).show();
						return true;
					}

					// this implicitely starts an actionmode!
					mListView.setItemChecked(position, true);
					return true;
				}
				return false;
			}
		});
*/


		// start
		mListController.fetchInitial();
	}


/*
	private ExtendableListView.MultiChoiceModeListener mMultiChoiceModeListener = new ExtendableListView.MultiChoiceModeListener() {
		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
			final int checkedCount = getListView().getCheckedItemCount();
			switch (checkedCount) {
				case 0:
					mode.setSubtitle(null);
					break;
				case 1:
					mode.setSubtitle("1 item selected");
					break;
				default:
					mode.setSubtitle("" + checkedCount + " items selected");
					break;
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = getActivity().getMenuInflater();
			inflater.inflate(R.menu.list_select_menu, menu);
			mode.setTitle("Select Items");
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case R.id.action_delete:

					SparseBooleanArray checked = getListView().getCheckedItemPositions();

					ArrayList<Tweet> selectedItems = new ArrayList<Tweet>();
					for (int i = 0; i < checked.size(); i++) {
						int position = checked.keyAt(i);
						if (checked.valueAt(i))
							selectedItems.add((Tweet) getListView().getAdapter().getItem(position));
					}
					mListController.prepareDelete(selectedItems);


					new UndoBar.Builder(getActivity())
							.setMessage("Delete " + getListView().getCheckedItemCount() + " items")
							.setListener(new UndoBar.Listener() {
								@Override
								public void onHide() {
									mListController.doDelete();
								}

								@Override
								public void onUndo(Parcelable parcelable) {
									mListController.undoPrepareDelete();
								}
							})
							.show();

					mode.finish();
					break;
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
		}
	};
*/


/*
	public AbsListView getListView() {
		return new ListView(getActivity());
	}
*/

}
