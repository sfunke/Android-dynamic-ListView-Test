package com.example.listdeletetest;


import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import com.jensdriller.libs.undobar.UndoBar;


/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment {
	private ListController mListController;
	private AbsListView mListView;
	private SwipeRefreshLayout mSwipeLayout;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		mListView = (AbsListView) view.findViewById(R.id.listView);
		mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
		mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				mListController.refreshNewest();
			}
		});
		mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);
		WebService webService = new FauxWebService(getActivity());
		MyAdapter adapter = MyAdapter.istantiate(getActivity());

		mListController = new ListController(webService, adapter);
		mListController.setRefreshCompleteDelegate(new ListController.RefreshCompleteDelegate() {
			@Override
			public void handleRefreshComplete() {
				mSwipeLayout.setRefreshing(false);
			}
		});

		mListView.setAdapter(adapter);
		mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
		mListView.setMultiChoiceModeListener(mMultiChoiceModeListener);
		mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {

			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				int visibleCount = firstVisibleItem + visibleItemCount;
				if(visibleCount > (totalItemCount - 5)) {
					mSwipeLayout.setRefreshing(true);
					mListController.fetchNext();
				}
			}
		});


		// start
		mListController.fetchInitial();
	}


	private AbsListView.MultiChoiceModeListener mMultiChoiceModeListener = new AbsListView.MultiChoiceModeListener() {
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
					long[] checkedItemIds = getListView().getCheckedItemIds();
//						for (int i = 0; i < checked.size(); i++) {
//							if(checked.valueAt(i) == true) {
//								Tweet tweet = (Tweet) getListView().getItemAtPosition(checked.keyAt(i));
//								Log.i("xxxx", i + " " + tweet);
//							}
//						}
//						mListController.prepareDelete(checked)
					new UndoBar.Builder(getActivity())
							.setMessage("Delete " + getListView().getCheckedItemCount() + " items")
							.setListener(new UndoBar.Listener() {
								@Override
								public void onHide() {

								}

								@Override
								public void onUndo(Parcelable parcelable) {

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


	public AbsListView getListView() {
		return mListView;
	}

}
