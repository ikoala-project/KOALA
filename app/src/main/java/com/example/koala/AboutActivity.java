package com.example.koala;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

/**
 * Activity for the About screen.
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textViewAboutText = findViewById(R.id.tv_about_text);
        textViewAboutText.setMovementMethod(new ScrollingMovementMethod());
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_about;
    }

    protected int getNavItemId(){
        return R.id.nav_about;
    }

}
