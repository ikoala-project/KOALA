package com.example.koala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


/**
 * Activity for main screen. Shows an activity summary and buttons to go to other screens.
 */
public class MainActivity extends BaseActivity implements FitbitGetRequestTask.AsyncResponse {
    // Fitbit access token lifetime in seconds (31536000 is one year)
    private static final int TOKEN_EXPIRES_IN = 31536000;

    // Used to pass data type (CALORIE, ACTIVE OR NON-SED) to ActivityDataActivity
    protected static final String DATA_TYPE = "com.example.koala.DATA_TYPE";

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getDatabase(this);

        // Display loading messages
        final Button btnNonSedTime = findViewById(R.id.btn_non_sed);
        btnNonSedTime.setText(getString(R.string.non_sed_time_loading, getString(R.string.loading_data)));

        final Button btnCalBurn = findViewById(R.id.btn_cal_burn);
        btnCalBurn.setText(getString(R.string.cal_burn_loading, getString(R.string.loading_data)));

        final Button btnActiveTime = findViewById(R.id.btn_active_time);
        btnActiveTime.setText(getString(R.string.active_time_loading, getString(R.string.loading_data)));

        final Button btnStrengthEx = findViewById(R.id.btn_strength_ex);
        btnStrengthEx.setText(getString(R.string.strength_ex_loading, getString(R.string.loading_data)));

        // IF the user has not yet connected their Fitbit account, or if their access token has
        // expired, direct them to Fitbit to authorise access. Otherwise display data either by
        // requesting up to date values or loading saved values from shared preferences.
        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        boolean showWelcome = sharedPref.getBoolean(getString(R.string.show_welcome_key), true);

        Date expiresOn = new Date(sharedPref.getLong(getString(R.string.expires_on_key), 0));
        long lastRefreshLong = sharedPref.getLong(getString(R.string.home_refresh_key), 0);

        Date today = Calendar.getInstance().getTime();
        long timeSinceRefresh = 0;
        int minsToRefresh = 0;

        boolean storedDataExist;
        if(lastRefreshLong != 0) {
            storedDataExist = true;
            Date lastRefresh = new Date(lastRefreshLong);

            // Calculate time since homepage data was displayed in minutes
            timeSinceRefresh = (today.getTime() - lastRefresh.getTime()) / (1000 * 60);
            minsToRefresh = getResources().getInteger(R.integer.mins_to_refresh);
        } else {
            storedDataExist = false;
        }

        if(showWelcome || today.after(expiresOn)) {
            // Prompt the user to authorise access
            String dialogMessage;
            if(showWelcome) {
                dialogMessage = getString(R.string.dialog_welcome);
            } else {
                dialogMessage = getString(R.string.dialog_token_expired);
            }

            // Show welcome or re-authorise dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(dialogMessage);
            builder.setPositiveButton(R.string.continue_btn, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    loginToFitbit();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

        } else if(((timeSinceRefresh >= minsToRefresh) && !requestsLow()) || !storedDataExist){
            // Request up to date data
            String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);
            getHomepageData(accessToken);
        } else {
            // Use stored data
            Integer[] params = {
                sharedPref.getInt(getString(R.string.home_calorie_value_key), 0),
                sharedPref.getInt(getString(R.string.home_calorie_goal_key), 0),
                sharedPref.getInt(getString(R.string.home_active_value_key), 0),
                sharedPref.getInt(getString(R.string.home_non_sed_value_key), 0)
            };
            new DisplayHomepageDataTask(this).execute(params);

        }

        // If applicable, show tutorial
        Intent intent = getIntent();
        boolean showTutorial = intent.getBooleanExtra(BaseActivity.SHOW_TUTORIAL, false);
        if(showTutorial) {
            showTutorial();
        }

        // Setup button listeners
        btnNonSedTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNonSedTime();
            }
        });

        btnCalBurn.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showCalBurn();
            }
        });

        btnActiveTime.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showActiveTime();
            }
        });

        btnStrengthEx.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showExercisesSummary();
            }
        });

        final Button btnUpdateGoals = findViewById(R.id.btn_goals);
        btnUpdateGoals.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showUpdateGoals();
            }
        });

        final Button btnWeight = findViewById(R.id.btn_weight);
        btnWeight.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showTrackWeight();
            }
        });

        final Button btnSymptoms = findViewById(R.id.btn_symptoms);
        btnSymptoms.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showTrackSymptoms();
            }
        });

    }

    /**
     * Displays activity and exercises summary data on the homepage.
     */
    private class DisplayHomepageDataTask extends AsyncTask<Integer, Void, List<Integer>> {
        private Context mContext;

        protected DisplayHomepageDataTask (Context context){
            mContext = context;
        }

        /**
         * Gets number of exercise sessions marked complete this week.
         * @param params Activity data: calorie value, calorie goal, active time value,
         *               non-sedentary time value
         * @return List of parameters, comprises params above and no. exercise sessions completed
         */
        @Override
        protected List<Integer> doInBackground(Integer... params) {
            Calendar today = Calendar.getInstance();
            today.setTime(getTodayAtMidnight());
            Calendar startDate = getFirstMondayInWeek(today);
            List<ExerciseSession> exerciseSessions = db.exerciseSessionDao()
                    .getSessionsBetweenDates(startDate.getTime(), today.getTime());
            int exSessions = exerciseSessions.size();

            List<Integer> paramsList = new ArrayList<>(Arrays.asList(params));
            paramsList.add(exSessions);
            return paramsList;
        }

        /**
         * Displays activity and exercises summary data.
         * @param paramsList Activity and exercises data
         */
        @Override
        protected void onPostExecute(List<Integer> paramsList) {
            SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                    Context.MODE_PRIVATE);
            int exSessionsGoal = getResources().getInteger(R.integer.strength_ex_goal);

            int exSessions;
            Date lastRefresh;
            if(paramsList.size() > 1) { // Activity data was passed in the parameters
                int caloriesOut = paramsList.get(0);
                int caloriesOutGoal = paramsList.get(1);
                int activeMins = paramsList.get(2);
                int nonSedentaryHours = paramsList.get(3);
                exSessions = paramsList.get(4);

                int activeMinsGoal = sharedPref.getInt(getString(R.string.active_goal_key), 0);
                int nonSedentaryGoal = sharedPref.getInt(getString(R.string.non_sed_goal_key), 0);

                // Update non-sedentary button
                Button btnNonSedTime = findViewById(R.id.btn_non_sed);
                btnNonSedTime.setText(getString(R.string.non_sed_time_values, nonSedentaryHours,
                        getGoalDisplayValue(nonSedentaryGoal, "–")));
                btnNonSedTime.setBackground(ContextCompat.getDrawable(mContext,
                        getButtonColourBackground(nonSedentaryHours, nonSedentaryGoal, false)));

                // Update calorie burn button
                Button btnCalBurn = findViewById(R.id.btn_cal_burn);
                btnCalBurn.setText(getString(R.string.cal_burn_values, caloriesOut,
                        getGoalDisplayValue(caloriesOutGoal, "–")));
                btnCalBurn.setBackground(ContextCompat.getDrawable(mContext,
                        getButtonColourBackground(caloriesOut, caloriesOutGoal, false)));

                // Update active time button
                Button btnActiveTime = findViewById(R.id.btn_active_time);
                btnActiveTime.setText(getString(R.string.active_time_values, activeMins,
                        getGoalDisplayValue(activeMinsGoal, "–")));
                btnActiveTime.setBackground(ContextCompat.getDrawable(mContext,
                        getButtonColourBackground(activeMins, activeMinsGoal, false)));

                lastRefresh = new Date(sharedPref.getLong(getString(R.string.home_refresh_key), 0));
            } else { // No activity data was passed to the task because an error occurred
                exSessions = paramsList.get(0);
                lastRefresh = Calendar.getInstance().getTime();
            }

            // Update exercises button
            Button btnStrengthEx = findViewById(R.id.btn_strength_ex);
            btnStrengthEx.setText(getString(R.string.strength_ex_values, exSessions,
                    getGoalDisplayValue(exSessionsGoal, "-")));
            btnStrengthEx.setBackground(ContextCompat.getDrawable(mContext,
                    getButtonColourBackground(exSessions, exSessionsGoal, true)));

            // Update "last updated at" text
            TextView textViewLastUpdated = findViewById(R.id.tv_last_updated);
            DateFormat dateFormat = new SimpleDateFormat(getString(R.string.time_format));
            String time = dateFormat.format(lastRefresh);
            textViewLastUpdated.setText(getString(R.string.last_updated_at, time));
        }
    }

    /**
     * Requests activity data from Fitbit.
     * @param accessToken For making requests to the Fitbit API
     */
    private void getHomepageData(String accessToken) {
        ArrayList<String> taskParamsList = new ArrayList<>();
        taskParamsList.add(accessToken);

        Calendar endDate = Calendar.getInstance(); // today
        Calendar startDate = getFirstMondayInWeek(endDate);

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedStartDate = dateFormat.format(startDate.getTime());
        String formattedEndDate = dateFormat.format(endDate.getTime());

        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/date/" +
                formattedEndDate + ".json");

        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/minutesFairlyActive/date/"
                + formattedStartDate + "/" + formattedEndDate + ".json");

        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/minutesVeryActive/date/"
                + formattedStartDate + "/" + formattedEndDate + ".json");

        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/steps/date/"
                + formattedEndDate + "/1d/15min.json");

        String[] taskParams = new String[taskParamsList.size()];
        taskParams = taskParamsList.toArray(taskParams);

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Gets activity summary values from data returned by Fitbit.
     * Called from FitbitGetRequestTask once data has been retrieved.
     * @param jsonObjects Data from Fitbit requests
     */
    @Override
    public void getProcessFinish(JSONObject[] jsonObjects){
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        int totalActiveMins = 0, caloriesOut = 0, caloriesOutGoal = 0, nonSedentaryHours = 0;
        try {
            // Update calorie burn button
            JSONObject jsonObject = jsonObjects[0];
            caloriesOut = Integer.parseInt(jsonObject.getJSONObject("summary")
                    .getString("caloriesOut"));
            caloriesOutGoal = Integer.parseInt(jsonObject.getJSONObject("goals")
                    .getString("caloriesOut"));

            // Calculate total active minutes across week so far
            totalActiveMins = calcActiveMins(
                    jsonObjects[1].getJSONArray("activities-minutesFairlyActive"),
                    jsonObjects[2].getJSONArray("activities-minutesVeryActive"));

            // Calculate number of hours with a certain number of steps
            nonSedentaryHours = calcNonSedentaryHours(jsonObjects[3]
                    .getJSONObject("activities-steps-intraday").getJSONArray("dataset"));

        }
        catch(JSONException | NullPointerException e) {
            // Show error messages
            Button btn = (Button) findViewById(R.id.btn_non_sed);
            btn.setText(getString(R.string.non_sed_time_loading, getString(R.string.data_error)));

            btn = (Button) findViewById(R.id.btn_cal_burn);
            btn.setText(getString(R.string.cal_burn_loading, getString(R.string.data_error)));

            btn = (Button) findViewById(R.id.btn_active_time);
            btn.setText(getString(R.string.active_time_loading, getString(R.string.data_error)));

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.fitbit_error_home)
                    .setPositiveButton(R.string.ok_btn, null).create().show();

            // Call task to display exercises data
            new DisplayHomepageDataTask(this).execute();

            return;
        }

        // Store values in shared preferences so data does not need to be requested every time
        // the homepage is loaded, if there has only been a short interval since the last request
        editor.putInt(getString(R.string.home_calorie_value_key), caloriesOut);
        editor.putInt(getString(R.string.home_calorie_goal_key), caloriesOutGoal);
        editor.putInt(getString(R.string.home_active_value_key), totalActiveMins);
        editor.putInt(getString(R.string.home_non_sed_value_key), nonSedentaryHours);

        // Update date/time when homepage last refreshed
        Date lastRefresh = Calendar.getInstance().getTime();
        editor.putLong(getString(R.string.home_refresh_key), lastRefresh.getTime());
        editor.apply();

        // Call task to display activity and exercises data
        Integer[] params = {caloriesOut, caloriesOutGoal, totalActiveMins, nonSedentaryHours};
        new DisplayHomepageDataTask(this).execute(params);
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_main;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * Launches Custom Tabs to get user's authorisation to access their Fitbit data.
     * Adapted from
     * https://stackoverflow.com/questions/33814946/android-chrome-custom-tabs-fitbit-web-api-wont-redirect-if-app-is-already-aut
     * by Buruiană Cătălin
     */
    private void loginToFitbit() {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        boolean promptLogin = sharedPref.getBoolean(getString(R.string.prompt_login_key), true);

        String url = "https://www.fitbit.com/oauth2/authorize?" +
                "response_type=token" +
                "&client_id=22DJFZ" +
                "&scope=activity%20weight%20profile" +
                "&expires_in=" + TOKEN_EXPIRES_IN +
                "&redirect_uri=koala://logincallback";

        if(promptLogin) {
            url += "&prompt=login";
        }

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        builder.setToolbarColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimary));
        customTabsIntent.launchUrl(MainActivity.this, Uri.parse(url));
    }

    /**
     * Calculates whether lots of requests have been used up so far this hour.
     * Used to decide whether to request up to date activity summary data from Fitbit.
     * @return True if a lot of requests have been used, False otherwise
     */
    private boolean requestsLow() {
        // Call method to reset request limit if a new hour has begun since the last request
        updateRequestCount(0);

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        int requestsRemaining = sharedPref.getInt(getString(R.string.api_limit_count_key), 0);
        int apiLimit = getResources().getInteger(R.integer.fitbit_api_limit);

        int spare = (int) (apiLimit * 0.2); // no. of requests we want to keep spare
        // Average rate of requests which would use all those not kept as spare
        double averagePerMin = (apiLimit - spare) / 60f;

        Calendar cal = Calendar.getInstance(); // current time
        int minsRemaining = 60 - cal.get(Calendar.MINUTE); // the limit resets on the hour
        int lowThreshold = spare + ((int) averagePerMin * minsRemaining);

        boolean requestsLow = false;
        if(requestsRemaining <= lowThreshold)
            requestsLow = true;

        return requestsLow;
    }

    private void showActiveTime() {
        Intent intent = new Intent(this, ActivityDataActivity.class);
        intent.putExtra(DATA_TYPE, "ACTIVE");
        startActivity(intent);
    }

    private void showCalBurn() {
        Intent intent = new Intent(this, ActivityDataActivity.class);
        intent.putExtra(DATA_TYPE, "CALORIE");
        startActivity(intent);
    }

    private void showNonSedTime() {
        Intent intent = new Intent(this, ActivityDataActivity.class);
        intent.putExtra(DATA_TYPE, "NON-SED");
        startActivity(intent);
    }

    private void showTrackSymptoms() {
        Intent intent = new Intent(this, TrackSymptomsActivity.class);
        startActivity(intent);
    }

    private void showTrackWeight() {
        Intent intent = new Intent(this, TrackWeightActivity.class);
        startActivity(intent);
    }

    /**
     * Displays a dialog as part of the tutorial.
     * @param msgIndex Of the tutorial message to show
     */
    private void showTutorialDialog(final int msgIndex) {
        final String[] helpMessages = getResources().getStringArray(R.array.home_help_texts);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(helpMessages[msgIndex]).setTitle(getString(R.string.home_help_title));

        int textId;
        if(msgIndex == helpMessages.length-1) { // this is the last dialog
            textId = R.string.ok_btn;
        } else { // more dialogs to view
            textId = R.string.next_btn;
        }

        // Once the user has finished viewing this message show the next dialog, if there is one
        builder.setPositiveButton(textId, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if(msgIndex+1 < helpMessages.length){ // there are more dialogs to show
                    showTutorialDialog(msgIndex+1); // show next dialog
                }
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    /**
     * Shows the first tutorial dialog. Subsequent dialogs are displayed recursively.
     */
    private void showTutorial() {
        showTutorialDialog(0);
    }

}
