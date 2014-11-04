package com.example.listdeletetest;


import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
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
import android.widget.Button;
import android.widget.Toast;

import com.example.listdeletetest.adapter.IAdapterItem;
import com.example.listdeletetest.adapter.MultiSelectionAdapter;
import com.example.listdeletetest.adapter.TweetItemPresenter;
import com.example.listdeletetest.model.Tweet;
import com.example.listdeletetest.webservice.FauxWebService;
import com.example.listdeletetest.webservice.WebService;
import com.jensdriller.libs.undobar.UndoBar;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainFragment extends Fragment {
	private ListController mListController;
	private RecyclerView mListView;
	private SwipeRefreshLayout mSwipeLayout;
	private ActionMode mActionMode;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		ButterKnife.inject(this, view);
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
		final MultiSelectionAdapter adapter = new MultiSelectionAdapter(getActivity(), new TweetItemPresenter());
		adapter.setListener(new MultiSelectionAdapter.Listener() {
			@Override
			public void endReached() {
				Log.w("XXX", "endReached called");
				mListController.fetchBottom();
			}

			@Override
			public void itemClicked(IAdapterItem item) {
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
//		mListView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
		mListView.setAdapter(adapter);
		mListView.setLongClickable(true);
		int spacing = getResources().getDimensionPixelSize(R.dimen.item_spacing);
//		mListView.addItemDecoration(new SpacingItemDecoration(spacing, spacing));


		// start
		mListController.fetchInitial();
	}

	@OnClick({R.id.buttonLinear, R.id.buttonStaggered, R.id.buttonGrid})
	public void buttonClick(Button button) {
		switch (button.getId()) {
			case R.id.buttonLinear:
				mListView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
				break;
			case R.id.buttonStaggered:
				mListView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
				break;
			case R.id.buttonGrid:
				mListView.setLayoutManager(new GridLayoutManager(getActivity(), 1));
				break;
		}
	}


}
