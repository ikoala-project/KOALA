package com.example.koala;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Activity for Article List screen. Shows the list of all articles within a topic.
 * Adapted from https://stackoverflow.com/questions/40584424/simple-android-recyclerview-example
 * by Suragch.
 */
public class ArticleListActivity extends BaseActivity implements ArticleAdapter.ItemClickListener {
    // Intent Extra keys
    protected static final String ARTICLE_DATE = "com.example.koala.ARTICLE_DATE";
    protected static final String ARTICLE_FILE_NAME = "com.example.koala.ARTICLE_FILE_NAME";
    protected static final String ARTICLE_NAME = "com.example.koala.ARTICLE_NAME";
    protected static final String TOPIC_NAME = "com.example.koala.TOPIC_NAME";

    // These must correspond to topic_names in string resources and files in the Assets folder
    private static final String TOPIC_FILE_NAMES[] = {"koa.csv", "pa.csv", "ex.csv", "wgt.csv"};

    // File names and dates which will be read from the csv file
    private ArrayList<String> articleFileNames = new ArrayList<>();
    private ArrayList<String> articleDates = new ArrayList<>();

    // Topic for which articles are being displayed
    private String topicName;

    // Adapter for RecyclerView
    private ArticleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        topicName = intent.getStringExtra(InfoActivity.TOPIC_NAME);
        setTitle(topicName); // show topic name in app bar

        // Get the list of articles for this topic
        String fileName = TOPIC_FILE_NAMES[intent.getIntExtra(InfoActivity.TOPIC_CHOICE, 0)];
        ArrayList<String> articleNames = readArticleNames(fileName);

        // set up the RecyclerView for the list of articles
        RecyclerView recyclerView = findViewById(R.id.edu_articles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArticleAdapter(this, articleNames);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL));
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_article_list;
    }

    protected int getNavItemId(){
        return R.id.nav_info;
    }

    /**
     * Reads the article names, file names and dates from the specified file.
     * Adapted from https://stackoverflow.com/questions/9544737/read-file-from-assets by HpTerm
     * @param fileName Containing the article names and other information for this topic
     * @return List of article names read from the file
     */
    private ArrayList<String> readArticleNames(String fileName) {
        ArrayList<String> articleNames = new ArrayList<>();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open(fileName)));

            // Read article information
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                int firstCommaIndex = mLine.indexOf(",");
                int secondCommaIndex = mLine.indexOf(",", firstCommaIndex + 1);

                // If first comma is missing then either the article name or
                // article file name is missing, so do not display
                if (firstCommaIndex > 0 && secondCommaIndex > 0) {
                    articleNames.add(mLine.substring(0, firstCommaIndex));
                    articleFileNames.add(mLine.substring(firstCommaIndex+1, secondCommaIndex));
                    articleDates.add(mLine.substring(secondCommaIndex+1));
                }
            }
        } catch (IOException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.article_list_error)
                    .setPositiveButton(R.string.ok_btn, null).create().show();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        return articleNames;
    }

    /**
     * Starts the Activity to show the article the user selected.
     * @param view     Current view
     * @param position Of the article the user selected in the list
     */
    @Override
    public void onItemClick(View view, int position) {
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.putExtra(ARTICLE_NAME, adapter.getItem(position));
        intent.putExtra(TOPIC_NAME, topicName);
        intent.putExtra(ARTICLE_FILE_NAME, articleFileNames.get(position));
        intent.putExtra(ARTICLE_DATE, articleDates.get(position));
        startActivity(intent);
    }

}
