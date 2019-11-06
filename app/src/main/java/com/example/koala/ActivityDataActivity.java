package com.example.koala;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Activity for the Activity Data screen which displays non-sedentary time, calorie burn or
 * active time data in a bar chart. The chart for non-sedentary time shows one day's data at a time
 * but for calorie burn and active time one week's worth of data is shown.
 */
public class ActivityDataActivity extends BaseActivity implements FitbitGetRequestTask.AsyncResponse {
    // dataType is either NON-SED, CALORIE, or ACTIVE
    private String dataType;

    // used if displaying calorie burn data
    private int caloriesOutGoal;

    // end date of current week being displayed, or the current day being displayed
    private Calendar endDate;
    // number of days/weeks difference from today and the day/week currently displayed
    private int offsetFromToday = 0;
    private static final int MAX_OFFSET = 4;

    // if the activity has just been loaded, used to determine whether to setup button listeners
    private boolean firstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        dataType = intent.getStringExtra(MainActivity.DATA_TYPE);

        endDate = Calendar.getInstance();
        endDate.setTime(getTodayAtMidnight());

        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        final String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        // Set headings and chart format according to type of data
        TextView textViewHeading = findViewById(R.id.tv_heading);
        TextView textViewSubheading = findViewById(R.id.tv_subheading);
        TextView textViewDateHeading = findViewById(R.id.tv_current_heading);
        switch(dataType) {
            case "NON-SED":
                setupDailyBarChart();
                textViewHeading.setText(getResources().getString(R.string.non_sed_heading));
                textViewSubheading.setText(getResources().getString(R.string.non_sed_unit_hour));
                textViewDateHeading.setText(getString(R.string.day_heading_today));
                break;
            case "CALORIE":
                setupWeeklyBarChart(0f);
                textViewHeading.setText(getResources().getString(R.string.cal_burn_heading));
                textViewSubheading.setText(getResources().getString(R.string.cal_burn_unit_day));
                textViewDateHeading.setText(getString(R.string.week_heading_this_week));
                break;
            case "ACTIVE":
                setupWeeklyBarChart(50f);
                textViewHeading.setText(getResources().getString(R.string.active_heading));
                textViewSubheading.setText(getResources().getString(R.string.active_unit_day));
                textViewDateHeading.setText(getString(R.string.week_heading_this_week));
                break;
        }

        // Get data for the chart and goal to display at the bottom of the screen
        getActivityData(accessToken, true);

        final Button btnUpdateGoals = findViewById(R.id.btn_goals);
        btnUpdateGoals.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showUpdateGoals();
            }
        });

    }

    /**
     * Creates a LegendEntry for the chart
     * @param colourId Colour id for entry form
     * @param label    Text for entry label
     * @return         LegendEntry object
     */
    private LegendEntry createLegendEntry(int colourId, String label) {
        LegendEntry legendEntry = new LegendEntry();
        legendEntry.formColor = getResources().getColor(colourId);
        legendEntry.label = label;
        return legendEntry;

    }

    /**
     * Displays chart and goal for active time data.
     * @param jsonObjects Data from Fitbit requests
     */
    private void displayActiveData(JSONObject[] jsonObjects) {
        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);

        int activeGoal = sharedPref.getInt(getResources().getString(R.string.active_goal_key), 0);

        int[] activeTime = {};
        try {
            // Populate array of active time for each day
            JSONArray fairlyActiveArray = jsonObjects[0]
                    .getJSONArray("activities-minutesFairlyActive");
            JSONArray veryActiveArray = jsonObjects[1]
                    .getJSONArray("activities-minutesVeryActive");

            activeTime = new int[fairlyActiveArray.length()];

            for(int i = 0; i < fairlyActiveArray.length(); i++) {
                int fairlyActiveMins = Integer.parseInt(fairlyActiveArray.getJSONObject(i)
                        .getString("value"));

                int veryActiveMins = Integer.parseInt(veryActiveArray.getJSONObject(i)
                        .getString("value"));

                activeTime[i] = fairlyActiveMins + veryActiveMins;
            }

        } catch(JSONException | NullPointerException e) {
            fitbitRequestError();
            return;
        }

        BarChart chart = findViewById(R.id.chart);

        ArrayList<BarEntry> entriesDay = new ArrayList<>();
        ArrayList<BarEntry> entriesSum = new ArrayList<>();
        int[] sumColours = new int[7];

        // For each day in the week create two bars, one showing the daily active time and one
        // for the running total.
        // If there is no data create a bar with value 0 so the day is still shown on the graph.
        float dayValue, sumValue = 0f;
        for (int i = 0; i < 7; i++) {
            if (i < activeTime.length) {
                dayValue = (float) activeTime[i];
                sumValue += dayValue;
            } else {
                dayValue = 0f;
                sumValue = 0f;
            }

            entriesDay.add(new BarEntry((float) i, dayValue));
            entriesSum.add(new BarEntry((float) i, sumValue));

            sumColours[i] = getResources().getColor(getProgressColour((int) sumValue,
                    activeGoal));
        }

        // Show data on chart
        BarDataSet dataSetDay = new BarDataSet(entriesDay, "1");
        dataSetDay.setColor(getResources().getColor(R.color.colorGraph2));
        dataSetDay.setDrawValues(false);

        BarDataSet dataSetSum = new BarDataSet(entriesSum, "2");
        dataSetSum.setColors(sumColours);
        dataSetSum.setDrawValues(false);

        BarData barData = new BarData(dataSetDay, dataSetSum);

        chart.setData(barData);
        chart.getBarData().setBarWidth(chart.getBarData().getBarWidth() / 2);
        chart.groupBars(chart.getXAxis().getAxisMinimum(), 0.15f, 0f);

        // Create legend for the two datasets
        List<LegendEntry> legendEntries = new ArrayList<>();
        legendEntries.add(createLegendEntry(R.color.colorGraph2,
                getString(R.string.active_legend_daily)));
        if(activeGoal > 0) { // if the user has set an active time goal
            legendEntries.add(createLegendEntry(R.color.colorProgressRed, null));
            legendEntries.add(createLegendEntry(R.color.colorProgressAmber, null));
            legendEntries.add(createLegendEntry(R.color.colorProgressYellow, null));
            legendEntries.add(createLegendEntry(R.color.colorProgressGreen,
                    getString(R.string.active_legend_total)));
        } else { // if they have not set a goal
            legendEntries.add(createLegendEntry(R.color.colorProgressGrey,
                    getString(R.string.active_legend_total)));
        }

        chart.getLegend().setCustom(legendEntries);
        chart.invalidate();

        // Show active time goal
        String unit = getResources().getString(R.string.active_unit);
        TextView textViewSummary = findViewById(R.id.tv_goal_summary);
        if(activeGoal > 0) {
            textViewSummary.setText(getResources().getString(R.string.goal_value_unit,
                    activeGoal, unit));
        } else {
            textViewSummary.setText(getResources().getString(R.string.no_goal_set));
        }
    }

    /**
     * Displays chart and goal for calorie burn data.
     * @param jsonObjects Data from Fitbit requests
     */
    private void displayCalorieData(JSONObject[] jsonObjects) {
        int[] caloriesOut = {};
        boolean displayGoal = false;

        try {
            // Get calories burned for each day
            JSONArray caloriesArray = jsonObjects[0].getJSONArray("activities-calories");
            caloriesOut = new int[caloriesArray.length()];

            for(int i = 0; i < caloriesArray.length(); i++) {
                int calsOut = Integer.parseInt(caloriesArray.getJSONObject(i)
                        .getString("value"));
                caloriesOut[i] = calsOut;
            }

            // Get calorie burn goal, if it was requested
            if(jsonObjects.length > 1) {
                displayGoal = true;
                JSONObject jsonObject = jsonObjects[1];
                caloriesOutGoal = Integer.parseInt(jsonObject.getJSONObject("goals")
                        .getString("caloriesOut"));
            }

        } catch(JSONException | NullPointerException e) {
            fitbitRequestError();
            return;
        }

        BarChart chart = findViewById(R.id.chart);

        ArrayList<BarEntry> entries = new ArrayList<>();
        int[] barColours = new int[7];

        // For each day in the week create a bar showing the data
        // If there is no data create a bar with value 0 so the day is still shown on the graph
        for (int i = 0; i < 7; i++) {
            float yValue = 0f;
            if (i < caloriesOut.length) {
                yValue = (float) caloriesOut[i];
            }

            entries.add(new BarEntry((float) i, yValue));

            barColours[i] = getResources().getColor(getProgressColour((int) yValue,
                    caloriesOutGoal));
        }

        // Show data on chart
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(barColours);
        dataSet.setDrawValues(false);
        BarData barData = new BarData(dataSet);

        chart.setData(barData);
        chart.getLegend().setEnabled(false);

        chart.invalidate();

        if(displayGoal) {
            // Show calorie burn goal
            String unit = getResources().getString(R.string.cal_burn_unit);
            TextView textViewSummary = findViewById(R.id.tv_goal_summary);
            if(caloriesOutGoal > 0) {
                textViewSummary.setText(getResources().getString(R.string.goal_value_unit,
                        caloriesOutGoal, unit));
            } else {
                textViewSummary.setText(getResources().getString(R.string.no_goal_set));
            }
        }
    }

    /**
     * Displays chart and goal for non-sedentary time data.
     * @param jsonObjects Data from Fitbit requests
     */
    private void displayNonSedData(JSONObject[] jsonObjects) {
        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);

        int[] stepsPerHour = new int[24];
        try {
            // Calculate steps per hour
            JSONArray jsonArray = jsonObjects[0].getJSONObject("activities-steps-intraday")
                    .getJSONArray("dataset");

            int steps = 0, interval = 0, hour = 0;
            for(int i = 0; i < jsonArray.length(); i++) {
                // Step data is requested in 15 minute intervals so we add up the steps
                // in every four values to get the total for each hour
                steps += Integer.parseInt(jsonArray.getJSONObject(i).getString("value"));
                interval++;
                if(interval == 4) {// we have reached the end of the hour
                    stepsPerHour[hour] = steps;

                    interval = 0;
                    steps = 0;
                    hour++;
                }
            }
        } catch(JSONException | NullPointerException e) {
            fitbitRequestError();
            return;
        }

        BarChart chart = findViewById(R.id.chart);

        ArrayList<BarEntry> entries = new ArrayList<>();
        int[] barColours = new int[24];

        // Get non-sedentary hours goal and step threshold for an hour to be non-sedentary
        int stepThreshold;
        int nonSedHoursGoal = sharedPref.getInt(getString(R.string.non_sed_goal_key), 0);
        if(nonSedHoursGoal == 0) {
            stepThreshold = 0;
        } else {
            stepThreshold = getResources().getInteger(R.integer.steps_threshold);
        }

        // For each hour in the day create a bar showing the data
        // If there is no data create a bar with value 0 so the hour is still shown on the graph
        for (int i = 0; i < 24; i++) {
            float yValue = (float) stepsPerHour[i];

            entries.add(new BarEntry((float) i, yValue));

            barColours[i] = getResources().getColor(getProgressColour((int) yValue, stepThreshold));
        }

        // Show data on chart
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(barColours);
        dataSet.setDrawValues(false);
        BarData barData = new BarData(dataSet);

        chart.setData(barData);
        chart.invalidate();

        // Show non-sedentary goal
        TextView textViewSummary = findViewById(R.id.tv_goal_summary);
        if(nonSedHoursGoal > 0) {
            textViewSummary.setText(getResources().getString(R.string.goal_hours_steps,
                    nonSedHoursGoal, stepThreshold));
        } else {
            textViewSummary.setText(getResources().getString(R.string.no_goal_set));
        }
    }

    /**
     * Shows error message and hides previous and next buttons when unable to get Fitbit data.
     */
    private void fitbitRequestError() {
        BarChart chart = findViewById(R.id.chart);
        chart.setNoDataText(getResources().getString(R.string.data_error));
        chart.invalidate();

        Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setVisibility(View.GONE);
        Button btnNext = findViewById(R.id.btn_next);
        btnNext.setVisibility(View.GONE);
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_data_activity;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * Calls method to display data and enables previous and next buttons.
     * Called from FitbitGetRequestTask once data has been retrieved.
     * @param jsonObjects Data from Fitbit requests
     */
    @Override
    public void getProcessFinish(JSONObject[] jsonObjects) {
        // Call the appropriate method to display the data according to the data type
        switch(dataType) {
            case "NON-SED":
                displayNonSedData(jsonObjects);
                break;
            case "CALORIE":
                displayCalorieData(jsonObjects);
                break;
            case "ACTIVE":
                displayActiveData(jsonObjects);
                break;
        }

        // Show previous and next buttons
        Button btnPrev = findViewById(R.id.btn_prev);
        if(offsetFromToday <= MAX_OFFSET)
            btnPrev.setVisibility(View.VISIBLE);

        Button btnNext = findViewById(R.id.btn_next);
        if(!getTodayAtMidnight().equals(endDate.getTime()))
            btnNext.setVisibility(View.VISIBLE);

        // If the screen has just been opened setup the previous and next buttons
        if(firstLoad) {
            firstLoad = false;
            setPrevNextButtonsAppearance();
            setupButtonListeners();
        }
    }

    /**
     * Calls method to get data from Fitbit and hides previous and next buttons.
     * @param accessToken Access token for making requests to the Fitbit API
     * @param getGoal Whether or not to request the goal from Fitbit
     */
    private void getActivityData(String accessToken, boolean getGoal) {
        // Hide previous and next buttons until new data is loaded
        Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setVisibility(View.GONE);
        Button btnNext = findViewById(R.id.btn_next);
        btnNext.setVisibility(View.GONE);

        // Call method to get data based on data type
        switch(dataType) {
            case "NON-SED":
                getDayNonSedTimeData(accessToken);
                break;
            case "CALORIE":
                getWeekCalorieData(accessToken, getGoal);
                break;
            case "ACTIVE":
                getWeekActiveTimeData(accessToken);
                break;
        }
    }

    /**
     * Requests non-sedentary time data for the date specified by endDate.
     * @param accessToken Access token for making requests to the Fitbit API
     */
    private void getDayNonSedTimeData(String accessToken) {
        String[] taskParams = new String[2];
        taskParams[0] = accessToken;

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedEndDate = dateFormat.format(endDate.getTime());

        taskParams[1] = "https://api.fitbit.com/1/user/-/activities/steps/date/"
                + formattedEndDate + "/1d/15min.json";

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Requests active time data for the week ending on endDate.
     * @param accessToken Access token for making requests to the Fitbit API
     */
    private void getWeekActiveTimeData(String accessToken) {
        String[] taskParams = new String[3];

        taskParams[0] = accessToken;

        Calendar startDate = getFirstMondayInWeek(endDate);

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedStartDate = dateFormat.format(startDate.getTime());
        String formattedEndDate = dateFormat.format(endDate.getTime());

        // Fairly active and very active minutes are added to get active minutes total
        taskParams[1] = "https://api.fitbit.com/1/user/-/activities/minutesFairlyActive/date/" +
                formattedStartDate + "/" + formattedEndDate + ".json";

        taskParams[2] = "https://api.fitbit.com/1/user/-/activities/minutesVeryActive/date/" +
                formattedStartDate + "/" + formattedEndDate + ".json";

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Requests calorie burn data for the week ending on endDate.
     * @param accessToken Access token for making requests to the Fitbit API
     * @param getGoal Whether or not to request the goal from Fitbit
     */
    private void getWeekCalorieData(String accessToken, boolean getGoal) {
        ArrayList<String> taskParamsList = new ArrayList<>();
        taskParamsList.add(accessToken);

        Calendar startDate = getFirstMondayInWeek(endDate);

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedStartDate = dateFormat.format(startDate.getTime());
        String formattedEndDate = dateFormat.format(endDate.getTime());

        taskParamsList.add("https://api.fitbit.com/1/user/-/activities/calories/date/" +
                formattedStartDate + "/" + formattedEndDate + ".json");

        if(getGoal) {
            taskParamsList.add("https://api.fitbit.com/1/user/-/activities/date/" +
                    formattedEndDate + ".json");
        }

        String[] taskParams = new String[taskParamsList.size()];
        taskParams = taskParamsList.toArray(taskParams);

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Sets up listeners for the buttons used to show previous or next data on the chart.
     */
    private void setupButtonListeners() {
        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        final String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        final BarChart chart = findViewById(R.id.chart);

        final Button btnNext = findViewById(R.id.btn_next);
        btnNext.setVisibility(View.GONE); // current week is displayed on create, so no next week
        btnNext.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Clear old data
                chart.invalidate();
                chart.clear();

                offsetFromToday--;

                // Calculate new endDate used to keep track of which day or week is displayed
                // and update the date heading
                if(dataType.equals("NON-SED")) {
                    endDate.add(Calendar.DATE, 1);
                    updateDateHeading(null);
                } else {
                    Calendar startDate = endDate;
                    startDate.add(Calendar.DATE, 1);
                    endDate = calcEndDateFromStart(startDate);
                    updateDateHeading(startDate);
                }

                // Request new data
                getActivityData(accessToken, false);
            }
        });

        final Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Clear old data
                chart.invalidate();
                chart.clear();

                offsetFromToday++;

                // Calculate new endDate used to keep track of which day or week is displayed
                // and update the date heading
                if(dataType.equals("NON-SED")) {
                    endDate.add(Calendar.DATE, -1);
                    updateDateHeading(null);
                } else {
                    endDate = calcPrevEndDate(endDate);
                    Calendar startDate = getFirstMondayInWeek(endDate);
                    updateDateHeading(startDate);
                }

                // Request new data
                getActivityData(accessToken, false);
            }
        });
    }

    /**
     * Sets up the bar chart for data over a 24-hour period. Used for non-sedentary time.
     */
    private void setupDailyBarChart() {
        BarChart chart = findViewById(R.id.chart);
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(0, 0, 0, 10);
        chart.setNoDataText(getResources().getString(R.string.loading_graph));
        chart.getPaint(Chart.PAINT_INFO).setTextSize(Utils.convertDpToPixel(16f));
        chart.getLegend().setEnabled(false);

        // Show time on x-axis at three hour intervals
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                int valueInt = (int) value;
                if(valueInt == 0) {
                    return "12am";
                } else if(valueInt > 0 && valueInt < 12) {
                    return valueInt + "am";
                } else if(valueInt == 12) {
                    return "12pm";
                } else {
                    return (valueInt - 12) + "pm";
                }
            }
        });

        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(14f);
        // show label for every three hours
        xAxis.setGranularity(3.0f);
        xAxis.setLabelCount(8);
        xAxis.setGranularityEnabled(true);

        // Show non-sedentary hours on left y-axis
        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setAxisMinimum(0f);
        yAxisLeft.setTextSize(14f);
        yAxisLeft.setGranularity(1.0f); // show integer values on y axis
        yAxisLeft.setGranularityEnabled(true);

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);
    }

    /**
     * Displays the date heading for the data currently shown on the chart.
     * @param startDate For current week if data is displayed on a weekly basis
     */
    private void updateDateHeading(Calendar startDate) {
        TextView textViewDateHeading = findViewById(R.id.tv_current_heading);

        String dateHeading = "";
        SimpleDateFormat dateFormat;

        // Check if endDate is today, so either today or this week is displayed
        Calendar today = Calendar.getInstance();
        if((endDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR))
                && (endDate.get(Calendar.YEAR) == today.get(Calendar.YEAR))) {
            if(dataType.equals("NON-SED")) {
                dateHeading = getString(R.string.day_heading_today);
            } else {
                dateHeading = getString(R.string.week_heading_this_week);
            }
        } else { // not today or this week
            if(dataType.equals("NON-SED")) {
                dateFormat = new SimpleDateFormat(getString(R.string.day_heading_date_format));
                dateHeading = dateFormat.format(endDate.getTime());
            } else {
                dateFormat = new SimpleDateFormat(getString(R.string.week_heading_date_format));
                dateHeading = getString(R.string.week_heading_with_dates,
                        dateFormat.format(startDate.getTime()),
                        dateFormat.format(endDate.getTime()));
            }
        }

        textViewDateHeading.setText(dateHeading);
    }

}
