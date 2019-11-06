package com.example.koala;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity for Information screen. Shows the list of information topics.
 * Adapted from https://stackoverflow.com/questions/40584424/simple-android-recyclerview-example
 * by Suragch.
 */
public class InfoActivity extends BaseActivity implements TopicAdapter.ItemClickListener {
    // Intent Extra keys
    protected static final String TOPIC_CHOICE = "com.example.koala.TOPIC_CHOICE";
    protected static final String TOPIC_NAME = "com.example.koala.TOPIC_NAME";

    private TopicAdapter adapter; // Adapter for RecyclerView
    private String[] topicNamesArr; // Display names for topics

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // data to populate the RecyclerView with
        Resources res = getResources();
        topicNamesArr = res.getStringArray(R.array.topic_names);
        ArrayList<String> topicNames = new ArrayList<>(Arrays.asList(topicNamesArr));

        // set up the RecyclerView for the list of topics
        RecyclerView recyclerView = findViewById(R.id.edu_topics);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TopicAdapter(this, topicNames);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_info;
    }

    protected int getNavItemId(){
        return R.id.nav_info;
    }

    /**
     * Starts the Activity to show the article list for the topic selected by the user.
     * @param view     Current view
     * @param position Of the topic the user selected in the list
     */
    @Override
    public void onItemClick(View view, int position) {
        Intent intent = new Intent(this, ArticleListActivity.class);
        intent.putExtra(TOPIC_CHOICE, position);
        intent.putExtra(TOPIC_NAME, topicNamesArr[position]);
        startActivity(intent);
    }

}
