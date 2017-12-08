/*
 * Copyright (C) 2012 OpenIntents.org
 * Copyright (C) 2017 George Venios
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

package com.veniosg.dir.android.fragment;

import android.arch.lifecycle.Observer;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.espresso.IdlingRegistry;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.veniosg.dir.R;
import com.veniosg.dir.android.activity.FileManagerActivity;
import com.veniosg.dir.android.adapter.FileListViewHolder.OnItemClickListener;
import com.veniosg.dir.android.adapter.SearchListAdapter;
import com.veniosg.dir.android.view.widget.WaitingViewFlipper;
import com.veniosg.dir.mvvm.model.FileHolder;
import com.veniosg.dir.mvvm.model.search.BooleanIdlingResource;
import com.veniosg.dir.mvvm.model.search.SearchState;
import com.veniosg.dir.mvvm.viewmodel.SearchViewModel;

import java.io.File;
import java.util.List;

import static android.arch.lifecycle.ViewModelProviders.of;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;
import static com.veniosg.dir.BuildConfig.DEBUG;
import static com.veniosg.dir.android.util.FileUtils.openFile;
import static com.veniosg.dir.android.view.Themer.getThemedResourceId;
import static com.veniosg.dir.android.view.widget.WaitingViewFlipper.PAGE_INDEX_CONTENT;

public class SearchListFragment extends Fragment {
    public static final int PAGE_INDEX_EMPTY = 1;

//    private static final String STATE_POS = "pos";
//    private static final String STATE_TOP = "top";

    private WaitingViewFlipper mFlipper;
    private RecyclerView mRecyclerView;
    private EditText mQueryView;
    private SearchViewModel viewModel;
    private String hintText;
    private final OnItemClickListener onItemClickListener = (itemView, item) -> {
        if (item.getFile().isDirectory()) {
            browse(Uri.parse(item.getFile().getAbsolutePath()));
        } else {
            openFile(item, getActivity());
        }
    };
    private final SearchListAdapter adapter = new SearchListAdapter(onItemClickListener);
    private final Observer<SearchState> resultObserver = searchState -> {
        if (searchState == null) {
            showLoading(false);
        } else {
            List<String> results = searchState.results();
            boolean isLoading = !searchState.isFinished();
            boolean finishedAndEmpty = !isLoading && results.isEmpty();

            adapter.notifyDataAppended(results);
            mFlipper.setDisplayedChild(finishedAndEmpty ? PAGE_INDEX_EMPTY : PAGE_INDEX_CONTENT);
            showLoading(isLoading);
        }
    };
    @NonNull
    private final BooleanIdlingResource searchIdlingResource = new BooleanIdlingResource(
            "SearchIdlingResource", DEBUG);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IdlingRegistry.getInstance().register(searchIdlingResource);

        viewModel = of(this).get(SearchViewModel.class);
        handleIntent();
    }

    @Override
    public void onDestroy() {
        IdlingRegistry.getInstance().unregister(searchIdlingResource);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int backgroundColorRes = getThemedResourceId(getActivity(), android.R.attr.colorBackground);
        view.setBackgroundResource(backgroundColorRes);

        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        mFlipper = (WaitingViewFlipper) view.findViewById(R.id.flipper);
        mQueryView = (EditText) view.findViewById(R.id.searchQuery);

        setupStaticViews();
        setupList();
        restoreScroll(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO still needed?
//        int top = 0;
//        if (mRecyclerView.getChildCount() != 0) {
//            top = mRecyclerView.getChildAt(0).getTop();
//        }
//
//        LinearLayoutManager linearLayoutMgr = (LinearLayoutManager) mRecyclerView.getLayoutManager();
//        outState.putInt(STATE_POS, linearLayoutMgr.findFirstVisibleItemPosition());
//        outState.putInt(STATE_TOP, top);
    }

    private void restoreScroll(Bundle savedInstanceState) {
        // TODO still needed?
//        if (savedInstanceState != null) {
//            int index = savedInstanceState.getInt(STATE_POS);
//            int top = savedInstanceState.getInt(STATE_TOP);
//            ScrollPosition scrollPosition = new ScrollPosition(index, top);
//            scrollToPosition(mRecyclerView, scrollPosition, true);
//        }
    }

    private void setupStaticViews() {
        mQueryView.setHint(hintText);
        mQueryView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean pressedDpadDownOrEnter = false;
                if (event != null) {
                    int eventCode = event.getKeyCode();
                    boolean dpadCenterOrEnter = eventCode == KEYCODE_DPAD_CENTER || eventCode == KEYCODE_ENTER;
                    pressedDpadDownOrEnter = event.getAction() == ACTION_DOWN && dpadCenterOrEnter;
                }

                if (actionId == IME_ACTION_SEARCH || pressedDpadDownOrEnter) {
                    boolean startedNewSearch = viewModel.updateQuery(v.getText().toString());
                    if (startedNewSearch) searchIdlingResource.setBusy();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupList() {
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    void showLoading(boolean loading) {
        if (!loading) searchIdlingResource.setIdle();
        // TODO GV handle (progressbar/hint visibility)
    }

    private void handleIntent() {
        Intent intent = getActivity().getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction()) && intent.getData() != null) {
            // Get the current path
            String path = intent.getData().getPath();
            File root = new File(path);

            // Init search
            viewModel.init(root);
            viewModel.getLiveResults().observe(this, resultObserver);

            hintText = getResources().getString(R.string.search_hint, root.getName());
        } else {
            getActivity().finish();
        }
    }

    private void browse(FileHolder file) {
        if (file.getFile().isDirectory()) {
            Intent intent = new Intent(getActivity(), FileManagerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setData(Uri.parse(file.getFile().getAbsolutePath()));

            startActivity(intent);
        } else {
            openFile(file, getContext());
        }
    }

    private void browse(Uri path) {
        Intent intent = new Intent(getActivity(), FileManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setData(path);

        startActivity(intent);
    }
}
