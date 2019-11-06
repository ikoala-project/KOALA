package com.example.koala;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.Calendar;

/**
 * Activity for the login callback after the user authorises access via Fitbit.
 */
public class LoginActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String redirectParameters = getIntent().getDataString();

        // Access token needed to get data from the Fitbit API
        String accessToken = getParameter(redirectParameters, "access_token");

        // Time left until token expires in seconds
        String expiresIn = getParameter(redirectParameters, "expires_in");

        Calendar calendar = Calendar.getInstance(); // gets a calendar with the current date/time
        calendar.add(Calendar.SECOND, Integer.parseInt(expiresIn)); // calculate date/time when token expires
        long expiresOn = calendar.getTime().getTime();

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        // Store login information
        editor.putBoolean(getString(R.string.prompt_login_key), false);
        editor.putBoolean(getString(R.string.show_welcome_key), false);
        editor.putString(getString(R.string.access_token_key), accessToken);
        editor.putLong(getString(R.string.expires_on_key), expiresOn);
        editor.apply();

        showHome(true);

    }

    /**
     * Gets the parameter specified by parameterName from the redirectParameters string.
     * @param redirectParameters String of all parameters
     * @param parameterName Parameter key to find the value for
     * @return Parameter value
     */
    private String getParameter(String redirectParameters, String parameterName) {
        String parameterValue;

        // Substring redirectParameters to remove all text before the parameter value
        parameterValue = redirectParameters.substring(redirectParameters.indexOf(parameterName + "=")
                +parameterName.length()+1);

        // If this parameter is not the last parameter, remove all text after the parameter value
        if(parameterValue.contains("&")) {
            parameterValue = parameterValue.substring(0, parameterValue.indexOf("&"));
        }

        return parameterValue;
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_login;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

}
