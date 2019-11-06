package com.example.koala;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Activity for Read Article screen. Shows the content of an article.
 */
public class ReadArticleActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        // Show topic name in app bar
        setTitle(intent.getStringExtra(ArticleListActivity.TOPIC_NAME));

        // Show article name, date updated on and content
        String articleName = intent.getStringExtra(ArticleListActivity.ARTICLE_NAME);
        String articleDate = intent.getStringExtra(ArticleListActivity.ARTICLE_DATE);
        String articleFileName = intent.getStringExtra(ArticleListActivity.ARTICLE_FILE_NAME);

        TextView textViewArticleName = findViewById(R.id.tv_article_name);
        TextView textViewArticleDate = findViewById(R.id.tv_article_date);
        TextView textViewArticleBody = findViewById(R.id.tv_article_body);

        textViewArticleName.setText(articleName);
        textViewArticleDate.setText(getString(R.string.last_updated_on, articleDate));
        textViewArticleBody.setText(readArticleBody(articleFileName + ".txt"));

        textViewArticleBody.setMovementMethod(new ScrollingMovementMethod());
        textViewArticleBody.setMovementMethod(new LinkMovementMethod());
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_read_article;
    }

    protected int getNavItemId(){
        return R.id.nav_info;
    }

    /**
     * Reads the article body from the specified file.
     * Adapted from https://stackoverflow.com/questions/9544737/read-file-from-assets by HpTerm
     * @param fileName Containing the article body including the list of sources
     * @return Article body and sources as a string
     */
    private String readArticleBody(String fileName) {
        String articleBody = "";

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getAssets().open(fileName)));

            // Read line from file and append to string
            String mLine;
            while ((mLine = reader.readLine()) != null) {
                articleBody += mLine + "\n";
            }
        } catch (IOException e) {
            articleBody = "";

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.read_article_error)
                    .setPositiveButton(R.string.ok_btn, null).create().show();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        return articleBody;
    }

}
