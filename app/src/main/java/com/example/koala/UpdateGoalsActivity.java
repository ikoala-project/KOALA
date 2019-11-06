package com.example.koala;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Activity for Update Goals screen. Allows user to set activity goals and displays recommendations.
 */
public class UpdateGoalsActivity extends BaseActivity implements FitbitGetRequestTask.AsyncResponse,
        FitbitPostRequestTask.AsyncResponse{
    private AppDatabase db;

    // Number of days or weeks to request data for in order to calculate activity recommendations.
    // Unlike calorie burn and active time each non-sedentary day needs a separate request so
    // fewer days are used in the calculation
    private static final int NON_SED_DAYS = 3;
    private static final int CAL_BURN_DAYS = 7;
    private static final int ACTIVE_MINS_WEEKS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getDatabase(this);

        // Set temporary text for recommendation values while they are loading
        TextView textViewNonSedRecValue = findViewById(R.id.tv_non_sed_rec_value);
        textViewNonSedRecValue.setText("...");

        TextView textViewCalBurnRecValue = findViewById(R.id.tv_cal_burn_rec_value);
        textViewCalBurnRecValue.setText("...");

        TextView textViewActiveTimeRecValue = findViewById(R.id.tv_active_time_rec_value);
        textViewActiveTimeRecValue.setText("...");

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPref.edit();

        // Either display recommendations calculated earlier or recalculate
        Calendar calNow = Calendar.getInstance();
        Calendar calStored = Calendar.getInstance();
        calStored.setTime(new Date(sharedPref.getLong(getString(R.string.goal_rec_date_key), 0)));

        boolean calcRecommendations = true;
        if(inSameHour(calNow, calStored)) { // Already calculated in this hour
            calcRecommendations = false;
            displayRecommendedGoals(sharedPref.getInt(getString(R.string.calorie_rec_key), 0),
                    sharedPref.getInt(getString(R.string.active_rec_key), 0),
                    sharedPref.getInt(getString(R.string.non_sed_rec_key), 0));
        } else {
            editor.putLong(getString(R.string.goal_rec_date_key), calNow.getTime().getTime());
            editor.apply();
        }

        // Get data to calculate recommendations and current calorie burn goal
        // - performed in getProcessFinish method after return from FitbitGetRequestTask
        getFitbitData(calcRecommendations);

        // Non-sedentary goal - our version is different to the Fitbit one so the value
        // is stored in shared preferences
        final EditText editTextNonSedGoal = findViewById(R.id.et_non_sed_goal);

        // Display current goal
        editTextNonSedGoal.setText(getGoalDisplayValue(sharedPref.getInt(
                getString(R.string.non_sed_goal_key), 0), ""), TextView.BufferType.EDITABLE);

        // Update non-sedentary goal in preferences when text is changed
        editTextNonSedGoal.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable s) {
                String nonSedGoal = editTextNonSedGoal.getText().toString();
                if(!nonSedGoal.equals("")) {
                    editor.putInt(getString(R.string.non_sed_goal_key),
                            Integer.parseInt(nonSedGoal));
                    editor.apply();
                }
            }
        });

        // Calorie burn - we use the goal stored in the Fitbit account and
        // update the value stored there when they change their goal
        final EditText editTextCalBurnGoal = findViewById(R.id.et_cal_burn_goal);

        // Update calorie burn goal when text is changed
        editTextCalBurnGoal.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable s) {
                String calBurnGoal = editTextCalBurnGoal.getText().toString();
                if(!calBurnGoal.equals("")) {
                    editor.putInt(getString(R.string.home_calorie_goal_key),
                            Integer.parseInt(calBurnGoal));
                    editor.apply();
                    setFitbitCalorieGoal(calBurnGoal);
                }
            }
        });


        // Weekly active time goal - currently there is no equivalent in the Fitbit app
        // so the value is stored in shared preferences
        final EditText editTextActiveTimeGoal = findViewById(R.id.et_active_time_goal);

        // Display current goal
        editTextActiveTimeGoal.setText(getGoalDisplayValue(sharedPref.getInt(
                getString(R.string.active_goal_key), 0), ""), TextView.BufferType.EDITABLE);

        // Update active time goal when text is changed
        editTextActiveTimeGoal.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable s) {
                String activeGoal = editTextActiveTimeGoal.getText().toString();
                if(!activeGoal.equals("")) {
                    editor.putInt(getString(R.string.active_goal_key),
                            Integer.parseInt(activeGoal));
                    editor.apply();
                }
            }
        });

    }

    /**
     * Calculates the average of the given pain scores.
     * @param symptomScores List of scores to average
     * @return Average value or 0 if there are no pain scores
     */
    private double calcAveragePainScore(List<SymptomScore> symptomScores) {
        int countDays = 0;
        int totalPainScore = 0;
        double averagePainScore = 0f;
        for(int i = 0; i < symptomScores.size(); i++) {
            int painScore = symptomScores.get(i).getPainScore();
            if(painScore >= 0) {
                totalPainScore += painScore;
                countDays++;
            }
        }

        if(countDays > 0) {
            averagePainScore = (double) totalPainScore / (double) countDays;
        }

        return averagePainScore;
    }

    /**
     * Displays goal recommendations on the screen, or a placeholder if the recommendation could
     * not be calculated.
     * @param caloriesOutGoalRec Calorie burn goal recommendation
     * @param activeMinsGoalRec  Active time goal recommendation
     * @param nonSedHoursGoalRec Non-sedentary time goal recommendation
     */
    private void displayRecommendedGoals(int caloriesOutGoalRec, int activeMinsGoalRec,
                                         int nonSedHoursGoalRec) {
        final TextView textViewCalBurnRecValue = findViewById(R.id.tv_cal_burn_rec_value);
        textViewCalBurnRecValue.setText(getGoalDisplayValue(caloriesOutGoalRec, "–"));

        TextView textViewActiveTimeRecValue = findViewById(R.id.tv_active_time_rec_value);
        textViewActiveTimeRecValue.setText(getGoalDisplayValue(activeMinsGoalRec, "–"));

        TextView textViewNonSedRecValue = findViewById(R.id.tv_non_sed_rec_value);
        textViewNonSedRecValue.setText(getGoalDisplayValue(nonSedHoursGoalRec, "–"));

    }

    /**
     * Starts a task to request the calorie burn goal and optionally the data needed to calculate
     * recommendations from Fitbit.
     * @param calcRecommendations True if requests used to calculate recommendations should be made,
     *                            False otherwise
     */
    private void getFitbitData(boolean calcRecommendations) {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        ArrayList<String> taskParamsList = new ArrayList<>();
        taskParamsList.add(accessToken);

        // Request for calories out goal
        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/goals/daily.json");

        if(calcRecommendations) {
            // Get data from the previous full days, i.e. "starting" yesterday and going back for
            // the number of days given by CAL_BURN_DAYS
            Calendar endDateCalories = Calendar.getInstance();
            endDateCalories.add(Calendar.DATE, -1);
            Calendar startDateCalories = (Calendar) endDateCalories.clone();
            startDateCalories.add(Calendar.DATE, -(CAL_BURN_DAYS -1));

            SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
            String formattedStartDateCalories = dateFormat.format(startDateCalories.getTime());
            String formattedEndDateCalories = dateFormat.format(endDateCalories.getTime());

            // Request used to calculate recommended calories out goal
            taskParamsList.add("https://api.fitbit.com/1/user/-/activities/calories/date/" +
                    formattedStartDateCalories + "/" + formattedEndDateCalories + ".json");


            // For active minutes consider the data across previous full weeks (Monday - Sunday).
            // The number of weeks to get data for is given by ACTIVE_MINS_WEEKS.
            // We look at full weeks because the active minutes goal is for the week from
            // Monday to Sunday so the user may do lots of activity at the start or end of the week.
            // If this is not consistent across weeks the previous x days may not be representative
            // of their activity.
            Calendar endDateActiveMins = calcPrevEndDate(Calendar.getInstance());
            Calendar startDateActiveMins = (Calendar) endDateActiveMins.clone();
            startDateActiveMins.add(Calendar.DATE, -(ACTIVE_MINS_WEEKS*7 - 1));

            String formattedStartDateActiveMins = dateFormat.format(startDateActiveMins.getTime());
            String formattedEndDateActiveMins = dateFormat.format(endDateActiveMins.getTime());

            // Requests used to calculate recommended active minutes goal
            taskParamsList.add("https://api.fitbit.com/1/user/-/activities/minutesFairlyActive/date/" +
                    formattedStartDateActiveMins + "/" + formattedEndDateActiveMins + ".json");

            taskParamsList.add("https://api.fitbit.com/1/user/-/activities/minutesVeryActive/date/" +
                    formattedStartDateActiveMins + "/" + formattedEndDateActiveMins + ".json");


            // For the intraday steps data we are only allowed to request data from within one
            // 24 hour period at a time. To reduce the number of API requests we pick a few recent
            // days and base our recommendation on that.
            Calendar dateNonSed = endDateCalories;
            for (int i = 0; i < NON_SED_DAYS; i++) {
                String formattedDate = dateFormat.format(dateNonSed.getTime());
                // Request used to calculate recommended non-sedentary hours goal
                taskParamsList.add("https://api.fitbit.com/1/user/-/activities/steps/date/"
                        + formattedDate + "/1d/15min.json");
                dateNonSed.add(Calendar.DATE, -1);
            }
        }

        String[] taskParams = new String[taskParamsList.size()];
        taskParams = taskParamsList.toArray(taskParams);

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_update_goals;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * Displays the calorie burn goal and optionally calculates the average activity values
     * needed for the recommendations, then starts the task to calculate recommendations.
     * Called from FitbitGetRequestTask once data has been retrieved.
     * @param jsonObjects Data from Fitbit request/s
     */
    @Override
    public void getProcessFinish(JSONObject[] jsonObjects){
        Integer[] activityValues = new Integer[3];

        try {
            boolean calcRecommendations = false;
            if(jsonObjects.length > 1) {
                calcRecommendations = true;
            }

            // Display current calorie burn goal
            JSONObject jsonObject = jsonObjects[0];
            int caloriesOutGoal = Integer.parseInt(jsonObject.getJSONObject("goals")
                    .getString("caloriesOut"));

            final EditText editTextCalBurnGoal = findViewById(R.id.et_cal_burn_goal);
            editTextCalBurnGoal.setText(getGoalDisplayValue(caloriesOutGoal, ""),
                    TextView.BufferType.EDITABLE);

            if(!calcRecommendations)
                return;

            // Calculate average calorie burn
            JSONArray caloriesArray = jsonObjects[1].getJSONArray("activities-calories");

            int totalCaloriesOut = 0, countDays = 0;
            for(int i = 0; i < caloriesArray.length(); i++) {
                int dailyCaloriesOut = Integer.parseInt(caloriesArray.getJSONObject(i)
                        .getString("value"));
                totalCaloriesOut += dailyCaloriesOut;
                if(dailyCaloriesOut > 0) {
                    countDays++;
                }
            }
            int averageCaloriesOut = totalCaloriesOut / countDays;
            activityValues[0] = averageCaloriesOut;

            // Calculate total active minutes across the two weeks requested
            int totalActiveMins = calcActiveMins(
                    jsonObjects[2].getJSONArray("activities-minutesFairlyActive"),
                    jsonObjects[3].getJSONArray("activities-minutesVeryActive"));

            // Active minutes are considered on a weekly basis rather than daily so we divide by
            // the number of weeks to get a weekly average
            int averageActiveMins = totalActiveMins / ACTIVE_MINS_WEEKS;
            activityValues[1] = averageActiveMins;

            // Calculate average non-sedentary hours
            int totalNonSedHours = 0;
            for(int i = 0; i < NON_SED_DAYS; i++) {
                int index = 4 + i;
                totalNonSedHours += calcNonSedentaryHours(jsonObjects[index]
                        .getJSONObject("activities-steps-intraday").getJSONArray("dataset"));
            }
            int averageNonSedHours = totalNonSedHours / NON_SED_DAYS; // rounds down to nearest int
            activityValues[2] = averageNonSedHours;

            new CalcRecommendedGoals().execute(activityValues);

        } catch(JSONException | NullPointerException e) {
            // The calorie burn goal is stored through their Fitbit account so can't be updated
            // if we can't connect to Fitbit
            final EditText editTextCalBurnGoal = findViewById(R.id.et_cal_burn_goal);
            editTextCalBurnGoal.setEnabled(false);

            displayRecommendedGoals(0, 0, 0);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.fitbit_error_update_goals)
                    .setPositiveButton(R.string.ok_btn, null).create().show();

            return;
        }

    }

    @Override
    public void postProcessFinish(JSONObject jsonObject) {

    }

    /**
     * Starts the HTTP POST request task to update the calorie burn goal.
     * @param calBurnGoal
     */
    private void setFitbitCalorieGoal(String calBurnGoal) {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        String[] taskParams = new String[3];
        taskParams[0] = accessToken;
        taskParams[1] = "https://api.fitbit.com/1/user/-/activities/goals/daily.json";
        taskParams[2] = "caloriesOut=" + calBurnGoal;

        updateRequestCount(1);

        new FitbitPostRequestTask(this).execute(taskParams);

    }

    /**
     * Calculates the goal recommendations then displays and stores them.
     */
    private class CalcRecommendedGoals extends AsyncTask<Integer, Void, Integer[]> {
        /**
         * Calculates recommendations using the recent average activity values and pain scores.
         * @param params Recent average activity values
         * @return Goal recommendations
         */
        @Override
        protected Integer[] doInBackground(Integer... params) {
            // We look at symptom score data from the previous 7 days to work out an average of
            // recent scores, and look at the previous 4 weeks to calculate what is typical
            // for the user. This way we can identify whether they seem to be experiencing a
            // flare up in symptoms.
            Calendar endDate = Calendar.getInstance();
            endDate.setTime(getTodayAtMidnight());
            endDate.add(Calendar.DATE, -1);
            Calendar startDateWeek = (Calendar) endDate.clone();
            startDateWeek.add(Calendar.DATE, -6);
            Calendar startDate4Weeks = (Calendar) endDate.clone();
            startDate4Weeks.add(Calendar.DATE, -27);

            List<SymptomScore> lastWeekScores = db.symptomScoreDao()
                    .getScoresBetweenDates(startDateWeek.getTime(), endDate.getTime());
            List<SymptomScore> last4WeeksScores = db.symptomScoreDao()
                    .getScoresBetweenDates(startDate4Weeks.getTime(), endDate.getTime());

            double averageWeekScore = calcAveragePainScore(lastWeekScores);
            double average4WeeksScore = calcAveragePainScore(last4WeeksScores);

            // Calculate whether the user's symptoms are worse than normal to determine how
            // ambitious the suggested goal should be. Note that the average score will be
            // greater than 0 if they have entered any pain scores above 0 in the last week,
            // and in that case the average score over 4 weeks is also greater than 0
            boolean painFlare = false;
            if(averageWeekScore != 0) {
                double averageDifference = averageWeekScore - average4WeeksScore;
                if(averageDifference >= 2.0) {
                    painFlare = true;
                }
            }

            // Baseline recommendations are recent activity averages
            int caloriesOutGoalRec = params[0];
            int activeMinsGoalRec  = params[1];
            int nonSedHoursGoalRec = params[2];

            // If their pain is not worse than normal recommend they challenge themselves
            // slightly more by adding the increment
            if(!painFlare) {
                if(caloriesOutGoalRec > 0) {
                    caloriesOutGoalRec += getResources().getInteger(R.integer.cal_burn_goal_inc);
                }
                if(activeMinsGoalRec > 0) {
                    activeMinsGoalRec += getResources().getInteger(R.integer.active_mins_goal_inc);
                }
                if(nonSedHoursGoalRec > 0) {
                    nonSedHoursGoalRec += getResources().getInteger(R.integer.non_sed_goal_inc);
                }
            }

            Integer[] goalRecommendations = new Integer[3];
            goalRecommendations[0] = caloriesOutGoalRec;
            goalRecommendations[1] = activeMinsGoalRec;
            goalRecommendations[2] = nonSedHoursGoalRec;

            return goalRecommendations;
        }

        /**
         * Calls a method to display the recommendations and stores the calculated values.
         * @param goalRecommendations Recommended goals
         */
        @Override
        protected void onPostExecute(Integer[] goalRecommendations) {
            int caloriesOutGoalRec = goalRecommendations[0];
            int activeMinsGoalRec = goalRecommendations[1];
            int nonSedHoursGoalRec = goalRecommendations[2];

            // Call method to display recommendations
            displayRecommendedGoals(caloriesOutGoalRec, activeMinsGoalRec, nonSedHoursGoalRec);

            // Store recommendations
            SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();

            editor.putInt(getString(R.string.calorie_rec_key), caloriesOutGoalRec);
            editor.putInt(getString(R.string.active_rec_key), activeMinsGoalRec);
            editor.putInt(getString(R.string.non_sed_rec_key), nonSedHoursGoalRec);

            editor.apply();
        }
    }
}
