package com.example.koala;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Activity for the Manage Fitbit Account screen.
 */
public class ManageFitbitActivity extends BaseActivity implements FitbitGetRequestTask.AsyncResponse {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFullName();
    }

    /**
     * Removes and resets preferences related to the Fitbit account so the user can login
     * with a different Fitbit account. Called when the Logout button is clicked.
     * @param view View for the Logout button
     */
    public void logOut(View view) {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.prompt_login_key), true);
        editor.putBoolean(getString(R.string.show_welcome_key), true);

        editor.remove(getString(R.string.expires_on_key));
        editor.remove(getString(R.string.access_token_key));
        editor.remove(getString(R.string.home_refresh_key));
        editor.remove(getString(R.string.home_calorie_goal_key));
        editor.remove(getString(R.string.home_calorie_value_key));
        editor.remove(getString(R.string.home_active_value_key));
        editor.remove(getString(R.string.home_non_sed_value_key));
        editor.remove(getString(R.string.goal_rec_date_key));
        editor.remove(getString(R.string.non_sed_rec_key));
        editor.remove(getString(R.string.calorie_rec_key));
        editor.remove(getString(R.string.active_rec_key));
        editor.remove(getString(R.string.active_goal_key));
        editor.remove(getString(R.string.non_sed_goal_key));

        editor.apply();

        showHome(false);
    }

    /**
     * Requests the full name of the currently logged in user from Fitbit.
     */
    private void getFullName() {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        String[] taskParams = new String[2];
        taskParams[0] = accessToken;
        taskParams[1] = "https://api.fitbit.com/1/user/-/profile.json";

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Displays the user's full name. Called from FitbitGetRequestTask once data has been retrieved.
     * @param jsonObjects Data from Fitbit request
     */
    @Override
    public void getProcessFinish(JSONObject[] jsonObjects) {
        final Button btnLogOut = findViewById(R.id.btn_logout);
        btnLogOut.setVisibility(View.VISIBLE);

        TextView textViewLoggedInAs = findViewById(R.id.tv_logged_in_as);

        // Get their full name
        String name = "";
        try {
            JSONObject jsonObject = jsonObjects[0];
            name = jsonObject.getJSONObject("user").getString("fullName");
        }
        catch(JSONException | NullPointerException e) {
            textViewLoggedInAs.setText(getString(R.string.fitbit_error_get_name));
            return;
        }

        // Display their name
        textViewLoggedInAs.setText(getString(R.string.logged_in_as, name));
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_manage_fitbit;
    }

    protected int getNavItemId(){
        return R.id.nav_fitbit;
    }
}
