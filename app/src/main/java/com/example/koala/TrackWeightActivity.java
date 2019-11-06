package com.example.koala;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Activity for Track Weight Screen. It shows a graph of weight measurements and allows the user
 * to set their current and goal weights.
 */
public class TrackWeightActivity extends BaseActivity implements FitbitGetRequestTask.AsyncResponse,
        FitbitPostRequestTask.AsyncResponse{
    private Calendar startDate; // For the month currently displayed
    // number of months difference between this month and the month currently displayed
    private int offsetFromThisMonth = 0;
    private static final int MAX_OFFSET = 3;

    // Labels for data sets on the chart
    private static final String LABEL_GOAL = "Goal";
    private static final String LABEL_NEXT = "Next"; // for projected weight at end of month
    private static final String LABEL_PREV = "Prev"; // for projected weight at start of month
    private static final String LABEL_WEIGHT = "Weight measurements (kg)";

    // Current and goal weights which will be obtained using Fitbit requests
    private int weightGoal = 0;
    private float weightCurrent = 0;
    // Date when the current weight was set
    private Calendar dateWeightCurrent = Calendar.getInstance();

    // Variables to store last/first values from the previous/next month so lines can be drawn
    // on the graph showing the change between weight measurements in different months
    // -1 indicates that the data is yet to be retrieved from Fitbit, 0 is used for no data
    // and any other value is a valid weight measurement
    // Last weight recorded in the month previous to the one currently being displayed
    private float weightPrevMonth = -1;
    private int dayWeightPrev = -1; // day of month when weightPrevMonth was recorded
    // First weight recorded in the month after the one currently being displayed
    private float weightNextMonth = 0;
    private int dayWeightNext = -1; // day of month when weightNextMonth was recorded

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        final String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        // Show current month on create
        startDate = Calendar.getInstance();
        startDate.setTime(getTodayAtMidnight());
        startDate.set(Calendar.DAY_OF_MONTH, 1);

        // Prevent the user from editing their current or goal weight until we have
        // retrieved up to date data from Fitbit and set the EditText listeners
        EditText editTextWeightNew = findViewById(R.id.et_new_wgt_value);
        editTextWeightNew.setEnabled(false);

        EditText editTextWeightGoal = findViewById(R.id.et_goal_wgt_value);
        editTextWeightGoal.setEnabled(false);

        // Setup buttons and chart
        setPrevNextButtonsAppearance();
        setupMonthlyLineChart();

        // Call method to request data from Fitbit
        getWeightData(accessToken, true);
    }

    /**
     * Calculates the projected weight on the last day of month 1.
     * Months 1 and 2 must be consecutive.
     * @param weightMonth1    Last weight in month 1
     * @param weightMonth2    First weight in month 2
     * @param dayWeightMonth1 Day in month 1 when the weight was recorded
     * @param dayWeightMonth2 Day in month 2 when the weight was recorded
     * @param daysInMonth1    Number of days in month 1
     * @return The projected weight
     */
    private float calcProjectedEndWeight(float weightMonth1, float weightMonth2, int dayWeightMonth1,
                                      int dayWeightMonth2, int daysInMonth1) {
        float weightChange = weightMonth2 - weightMonth1;
        int daysBetween = dayWeightMonth2 + daysInMonth1 - dayWeightMonth1;
        if(daysBetween > 0) {
            return weightMonth2 - (weightChange * dayWeightMonth2 / daysBetween);
        } else {
            return 0;
        }
    }

    /**
     * Calculates the projected weight on the first day of month 2.
     * Months 1 and 2 must be consecutive.
     * @param weightMonth1    Last weight in month 1
     * @param weightMonth2    First weight in month 2
     * @param dayWeightMonth1 Day in month 1 when the weight was recorded
     * @param dayWeightMonth2 Day in month 2 when the weight was recorded
     * @param daysInMonth1    Number of days in month 1
     * @return The projected weight
     */
    private float calcProjectedStartWeight(float weightMonth1, float weightMonth2, int dayWeightMonth1,
                                         int dayWeightMonth2, int daysInMonth1) {
        float weightChange = weightMonth2 - weightMonth1;
        int daysBetween = dayWeightMonth2 + daysInMonth1 - dayWeightMonth1;
        if(daysBetween > 0) {
            return weightMonth2 - (weightChange * (dayWeightMonth2 - 1) / daysBetween);
        } else {
            return 0;
        }
    }

    /**
     * Creates a data set showing the user's weight goal as a horizontal line across the whole month.
     * @param daysInMonth Number of days in the current month
     * @return Data set object
     */
    private LineDataSet createDataSetGoal(int daysInMonth) {
        ArrayList<Entry> entriesGoal = new ArrayList<>();
        if(weightGoal > 0) {
            entriesGoal.add(new Entry(1f, (float) weightGoal));
            entriesGoal.add(new Entry((float) daysInMonth, weightGoal));
        }
        LineDataSet dataSetGoal = new LineDataSet(entriesGoal, LABEL_GOAL);
        dataSetGoal.setLineWidth(2f);
        dataSetGoal.setColor(getResources().getColor(R.color.colorPrimary));
        dataSetGoal.setDrawValues(false);
        return dataSetGoal;
    }

    /**
     * Gets the last day in the month which dateInMonth is in.
     * @param dateInMonth Any date in the month
     * @return Calendar object representing the last day
     */
    private Calendar getLastDayInMonth(Calendar dateInMonth) {
        Calendar endDate = (Calendar) dateInMonth.clone();
        endDate.set(Calendar.DAY_OF_MONTH, endDate.getActualMaximum(Calendar.DAY_OF_MONTH));
        return endDate;
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_track_weight;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * Reads weight data from Fitbit and displays it on the screen.
     * @param jsonObjects Data from Fitbit requests
     */
    @Override
    public void getProcessFinish(JSONObject[] jsonObjects) {
        ArrayList<Entry> entries = new ArrayList<>();
        boolean firstLoad = false;
        try {
            // Get data for month we want to display
            JSONArray weightArray = jsonObjects[0].getJSONArray("weight");
            for(int i = 0; i < weightArray.length(); i++) {
                float weight = Float.parseFloat(weightArray.getJSONObject(i)
                        .getString("weight"));

                String dateString = weightArray.getJSONObject(i)
                        .getString("date");
                int dayOfMonth = Integer.parseInt(dateString.substring(
                        dateString.lastIndexOf("-") + 1));

                entries.add(new Entry((float) dayOfMonth, weight));

            }

            // Get data from previous or next month, whichever was requested
            JSONArray weightArrayPrev = jsonObjects[1].getJSONArray("weight");
            if(weightPrevMonth == -1) { // previous month was requested
                if(weightArrayPrev.length() > 0) {
                    // Get last weight log from previous month
                    int lastIndex = weightArrayPrev.length() - 1;
                    weightPrevMonth = Float.parseFloat(weightArrayPrev.getJSONObject(lastIndex)
                            .getString("weight"));

                    String dateString = weightArrayPrev.getJSONObject(lastIndex)
                            .getString("date");
                    dayWeightPrev = Integer.parseInt(dateString.substring(
                            dateString.lastIndexOf("-") + 1));

                } else { // no weight set in the previous month
                    weightPrevMonth = 0;
                }
            } else if(weightNextMonth == -1) { // next month was requested
                if(weightArrayPrev.length() > 0) {
                    // Get first weight log from next month
                    weightNextMonth = Float.parseFloat(weightArrayPrev.getJSONObject(0)
                            .getString("weight"));

                    String dateString = weightArrayPrev.getJSONObject(0)
                            .getString("date");
                    dayWeightNext = Integer.parseInt(dateString.substring(
                            dateString.lastIndexOf("-") + 1));
                } else { // no weight set in the next month
                    weightNextMonth = 0;
                }
            }

            // If the weight goal was requested from Fitbit then the Activity was just created so
            // update the Edit weight goal field and display the current weight
            if(jsonObjects.length > 2) {
                firstLoad = true;

                // Display and store weight goal
                if(jsonObjects[2].getJSONObject("goal").has("weight")) {
                    weightGoal = jsonObjects[2].getJSONObject("goal").getInt("weight");
                    EditText editTextGoalWeight = findViewById(R.id.et_goal_wgt_value);
                    editTextGoalWeight.setText(weightGoal + "", TextView.BufferType.EDITABLE);
                }

                // Display and store last weight measurement in this month, i.e. the current weight,
                // and store the corresponding date
                int lastIndex = weightArray.length() - 1;
                weightCurrent = Float.parseFloat(weightArray.getJSONObject(lastIndex)
                        .getString("weight"));
                TextView textViewCurrentWeight = findViewById(R.id.tv_current_wgt_value);
                textViewCurrentWeight.setText(weightCurrent + "");

                String dateString = weightArray.getJSONObject(lastIndex).getString("date");
                SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
                dateWeightCurrent.setTime(dateFormat.parse(dateString));
            }
        } catch(JSONException | ParseException | NullPointerException e) {
            LineChart chart = findViewById(R.id.chart);
            chart.setNoDataText(getResources().getString(R.string.data_error));
            chart.invalidate();

            Button buttonUpdateWeightGoal = findViewById(R.id.btn_goal_wgt);
            buttonUpdateWeightGoal.setEnabled(false);
            Button buttonUpdateWeightNew = findViewById(R.id.btn_new_wgt);
            buttonUpdateWeightNew.setEnabled(false);

            Button btnPrev = findViewById(R.id.btn_prev);
            btnPrev.setVisibility(View.GONE);
            Button btnNext = findViewById(R.id.btn_next);
            btnNext.setVisibility(View.GONE);
            return;
        }


        // Add the line displaying weight measurements
        LineDataSet dataSetWeightValues = new LineDataSet(entries, LABEL_WEIGHT);
        dataSetWeightValues.setLineWidth(2f);
        dataSetWeightValues.setColor(getResources().getColor(R.color.colorMediumGrey));
        dataSetWeightValues.setCircleRadius(5f);
        dataSetWeightValues.setCircleColor(getResources().getColor(R.color.colorMediumGrey));
        dataSetWeightValues.setDrawValues(false);

        // Add the line displaying the user's goal
        int daysInMonth = getLastDayInMonth(startDate).get(Calendar.DAY_OF_MONTH);
        LineDataSet dataSetGoal = createDataSetGoal(daysInMonth);

        // Add lines showing the change between weight measurements in different months
        // These need to be in separate datasets so we can set drawCircles to false. Otherwise the
        // projected weights on the first and last days of the month will be marked with a circle

        // Calculate projected weight at day 1 of this month
        ArrayList<Entry> entriesPrev = new ArrayList<>();
        if(weightPrevMonth > 0 && entries.size() > 0) {
            // Get measurement and day of month for first recorded weight in current month
            int dayWeightFirst = (int) entries.get(0).getX();
            float weightFirst = entries.get(0).getY();

            // Calculate the projected weight at day 1 of current month
            Calendar startLastMonth = (Calendar) startDate.clone();
            startLastMonth.add(Calendar.MONTH, -1);
            int daysPrevMonth = getLastDayInMonth(startLastMonth).get(Calendar.DAY_OF_MONTH);
            float weightProjection = calcProjectedStartWeight(weightPrevMonth, weightFirst,
                    dayWeightPrev, dayWeightFirst, daysPrevMonth);

            // Add to list of entries
            if(weightProjection > 0) {
                entriesPrev.add(new Entry(1, weightProjection));
                entriesPrev.add(new Entry(dayWeightFirst, weightFirst));
            }

        }

        // Create dataset
        LineDataSet dataSetPrevChange = new LineDataSet(entriesPrev, LABEL_PREV);
        dataSetPrevChange.setLineWidth(2f);
        dataSetPrevChange.setColor(getResources().getColor(R.color.colorMediumGrey));
        dataSetPrevChange.setDrawCircles(false);
        dataSetPrevChange.setDrawValues(false);

        // Calculate projected weight on last day of this month
        ArrayList<Entry> entriesNext = new ArrayList<>();
        if(weightNextMonth > 0 && entries.size() > 0) {
            // Get measurement and day of month for last recorded weight in current omnth
            int lastIndex = entries.size() - 1;
            int dayWeightLast = (int) entries.get(lastIndex).getX();
            float weightLast = entries.get(lastIndex).getY();

            // Calculate the projected weight on the last day of the current month
            float weightProjection = calcProjectedEndWeight(weightLast, weightNextMonth,
                    dayWeightLast, dayWeightNext, daysInMonth);

            // Add to list of entries
            if(weightProjection > 0) {
                entriesNext.add(new Entry(dayWeightLast, weightLast));
                entriesNext.add(new Entry(daysInMonth, weightProjection));
            }
        }

        // Create dataset
        LineDataSet dataSetNextChange = new LineDataSet(entriesNext, LABEL_NEXT);
        dataSetNextChange.setLineWidth(2f);
        dataSetNextChange.setColor(getResources().getColor(R.color.colorMediumGrey));
        dataSetNextChange.setDrawCircles(false);
        dataSetNextChange.setDrawValues(false);

        // Finish setting up chart
        LineChart chart = findViewById(R.id.chart);
        LineData lineData = new LineData(dataSetWeightValues, dataSetGoal, dataSetPrevChange,
                dataSetNextChange);
        chart.setData(lineData);

        resetChartLegend();

        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(1f);
        xAxis.setAxisMaximum((float) daysInMonth);

        chart.invalidate();

        // Show previous and next buttons
        Button btnPrev = findViewById(R.id.btn_prev);
        if(offsetFromThisMonth <= MAX_OFFSET)
            btnPrev.setVisibility(View.VISIBLE);

        Button btnNext = findViewById(R.id.btn_next);
        if(!isThisMonthDisplayed())
            btnNext.setVisibility(View.VISIBLE);

        // Wait until we've finished setting up initial values to set listeners
        if(firstLoad)
            setupButtonListeners();

    }

    /**
     * Starts task to request weight data from the Fitbit API.
     * @param accessToken For making requests to the Fitbit API
     * @param firstLoad   True if the Activity has just been created, or False otherwise
     */
    private void getWeightData(String accessToken, boolean firstLoad) {
        // Hide previous and next buttons until new data is loaded
        Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setVisibility(View.GONE);
        Button btnNext = findViewById(R.id.btn_next);
        btnNext.setVisibility(View.GONE);

        Calendar endDate = getLastDayInMonth(startDate);

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedStartDate = dateFormat.format(startDate.getTime());
        String formattedEndDate = dateFormat.format(endDate.getTime());

        ArrayList<String> taskParamsList = new ArrayList<>();
        taskParamsList.add(accessToken);

        // Request for data in month to display on the chart
        taskParamsList.add("https://api.fitbit.com/1/user/-/body/log/weight/date/" +
                formattedStartDate + "/" + formattedEndDate + ".json");

        // Depending on which direction we are moving in (to previous or next month) or whether
        // the page has just been loaded (defaulting to displaying the current month)
        // request data for the previous or next month from Fitbit
        // If the user clicked the Previous button we can get weightNextMonth from the data
        // currently being displayed and vice versa if they clicked Next
        if(weightPrevMonth == -1) { // value of -1 indicates we need to get data from Fitbit
            // Start date is first date in month so we can subtract one day to get last day
            // in previous month
            Calendar endDatePrev = (Calendar) startDate.clone();
            endDatePrev.add(Calendar.DATE, -1);
            Calendar startDatePrev = (Calendar) endDatePrev.clone();
            startDatePrev.set(Calendar.DAY_OF_MONTH, 1);

            String formattedStartDatePrev = dateFormat.format(startDatePrev.getTime());
            String formattedEndDatePrev = dateFormat.format(endDatePrev.getTime());

            // Request for data from previous month
            taskParamsList.add("https://api.fitbit.com/1/user/-/body/log/weight/date/" +
                    formattedStartDatePrev + "/" + formattedEndDatePrev + ".json");
        } else if(weightNextMonth == -1) {
            Calendar startDateNext = (Calendar) endDate.clone();
            startDateNext.add(Calendar.DATE, 1);
            Calendar endDateNext = getLastDayInMonth(startDateNext);

            String formattedStartDateNext = dateFormat.format(startDateNext.getTime());
            String formattedEndDateNext = dateFormat.format(endDateNext.getTime());

            // Request for data from next month
            taskParamsList.add("https://api.fitbit.com/1/user/-/body/log/weight/date/" +
                    formattedStartDateNext + "/" + formattedEndDateNext + ".json");
        }

        // Request weight goal when the Activity is created
        if(firstLoad) {
            taskParamsList.add("https://api.fitbit.com/1/user/-/body/log/weight/goal.json");
        }

        String[] taskParams = new String[taskParamsList.size()];
        taskParams = taskParamsList.toArray(taskParams);

        updateRequestCount(taskParams.length - 1);

        new FitbitGetRequestTask(this).execute(taskParams);
    }

    /**
     * Checks if the start date is for this month.
     * @return True if the start date is in this month, False otherwise
     */
    private boolean isThisMonthDisplayed() {
        Calendar today = Calendar.getInstance();

        if((startDate.get(Calendar.MONTH) == today.get(Calendar.MONTH))
                && (startDate.get(Calendar.YEAR) == today.get(Calendar.YEAR))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if the start date is for the month previous to this month.
     * @return True if start date is for previous month, False otherwise
     */
    private boolean isPrevToThisMonthDisplayed() {
        // Subtract one month from today to get a date in the previous month
        Calendar dateInPrevMonth = Calendar.getInstance(); // today
        dateInPrevMonth.add(Calendar.MONTH, -1);

        if((startDate.get(Calendar.MONTH) == dateInPrevMonth.get(Calendar.MONTH))
                && (startDate.get(Calendar.YEAR) == dateInPrevMonth.get(Calendar.YEAR))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void postProcessFinish(JSONObject jsonObject) {

    }

    /**
     * Sets the chart legend to only show the data sets for weight measurements in the current
     * month and for their goal weight, hiding legend entries for projected weight data sets.
     */
    private void resetChartLegend() {
        LineChart chart = findViewById(R.id.chart);

        chart.getLegend().resetCustom();
        chart.getLineData().notifyDataChanged();
        chart.notifyDataSetChanged();
        LegendEntry[] legendEntries = chart.getLegend().getEntries();
        List<LegendEntry> legendEntriesList = new ArrayList<>();
        for(int i = 0; i < legendEntries.length; i++) {
            String label = legendEntries[i].label;
            if(label.equals(LABEL_WEIGHT) || label.equals(LABEL_GOAL)) {
                legendEntriesList.add(legendEntries[i]);
            }
        }
        chart.getLegend().setCustom(legendEntriesList);
    }

    /**
     * Starts a task to update their current weight using an HTTP POST request.
     * @param accessToken For making requests to the Fitbit API
     */
    private void setFitbitCurrentWeight(String accessToken) {
        String[] taskParams = new String[4];
        taskParams[0] = accessToken;
        taskParams[1] = "https://api.fitbit.com/1/user/-/body/log/weight.json";

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedToday = dateFormat.format(Calendar.getInstance().getTime());

        // Parameters for request
        taskParams[2] = "weight=" + weightCurrent;
        taskParams[3] = "date=" + formattedToday;

        updateRequestCount(1);

        new FitbitPostRequestTask(this).execute(taskParams);
    }

    /**
     * Starts a task to update their goal weight using an HTTP POST request.
     * @param accessToken For making requests to the Fitbit API
     */
    private void setFitbitWeightGoal(String accessToken) {
        String[] taskParams = new String[5];
        taskParams[0] = accessToken;
        taskParams[1] = "https://api.fitbit.com/1/user/-/body/log/weight/goal.json";

        SimpleDateFormat dateFormat = new SimpleDateFormat(getString(R.string.fitbit_date_format));
        String formattedDate = dateFormat.format(dateWeightCurrent.getTime());

        // Parameters for request
        taskParams[2] = "startDate=" + formattedDate;
        taskParams[3] = "startWeight=" + weightCurrent;
        taskParams[4] = "weight=" + weightGoal;

        updateRequestCount(1);

        new FitbitPostRequestTask(this).execute(taskParams);
    }

    /**
     * Sets up the chart for showing one month of data.
     */
    private void setupMonthlyLineChart() {
        LineChart chart = findViewById(R.id.chart);
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(0, 0, 0, 10);
        chart.setNoDataText(getString(R.string.loading_data));
        chart.getPaint(Chart.PAINT_INFO).setTextSize(Utils.convertDpToPixel(16f));

        Legend legend = chart.getLegend();
        legend.setTextSize(16f);
        legend.setYOffset(10f);
        legend.setXEntrySpace(40f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(16f);

        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setTextSize(16f);
        yAxisLeft.setGranularity(1.0f); // show integer values on y axis
        yAxisLeft.setGranularityEnabled(true);

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);
    }

    /**
     * Sets up the EditText fields and buttons.
     */
    private void setupButtonListeners() {
        final SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        final String accessToken = sharedPref.getString(getString(R.string.access_token_key), null);

        final EditText editTextWeightNew = findViewById(R.id.et_new_wgt_value);
        editTextWeightNew.setEnabled(true);

        final LineChart chart = findViewById(R.id.chart);

        final Button buttonUpdateWeightNew = findViewById(R.id.btn_new_wgt);
        buttonUpdateWeightNew.setOnClickListener(new View.OnClickListener() {
            // Updates the user's current weight
            public void onClick(View v) {
                float newWeightCurrent = Float.parseFloat(editTextWeightNew.getText().toString());
                // Check the value they entered
                if(newWeightCurrent <= 0) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_invalid_weight),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Hide keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editTextWeightNew.getWindowToken(), 0);

                // Show message if new weight meets goal
                if((weightGoal < weightCurrent) && (newWeightCurrent <= weightGoal)){
                    Toast.makeText(getApplicationContext(), getString(R.string.weight_goal_met),
                            Toast.LENGTH_LONG).show();
                }

                // Store new weight
                weightCurrent = newWeightCurrent;
                dateWeightCurrent = Calendar.getInstance();
                setFitbitCurrentWeight(accessToken);

                // Update current weight on screen
                TextView textViewCurrentWeight = findViewById(R.id.tv_current_wgt_value);
                textViewCurrentWeight.setText(weightCurrent + "");

                // Update chart data
                int dayOfMonthToday = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

                LineDataSet dataSetWeightValues = (LineDataSet) chart.getLineData()
                        .getDataSetByLabel(LABEL_WEIGHT, true);
                if(isThisMonthDisplayed()) {
                    // If there is already a value for today, remove it from the graph
                    if(dataSetWeightValues.getEntriesForXValue(dayOfMonthToday).size() > 0) {
                        dataSetWeightValues.removeEntryByXValue(dayOfMonthToday);
                    }

                    // Add the new weight
                    dataSetWeightValues.addEntry(new Entry((float) dayOfMonthToday, weightCurrent));

                    // If the current weight change affects the projected weight from the previous
                    // month to this one, update the projection
                    if(dataSetWeightValues.getIndexInEntries(dayOfMonthToday) == 0) {
                        LineDataSet dataSetPrevChange = (LineDataSet) chart.getLineData()
                                .getDataSetByLabel(LABEL_PREV, true);

                        dataSetPrevChange.clear();

                        Calendar startLastMonth = (Calendar) startDate.clone();
                        startLastMonth.add(Calendar.MONTH, -1);
                        int daysPrevMonth = getLastDayInMonth(startLastMonth)
                                .get(Calendar.DAY_OF_MONTH);
                        float weightProjection = calcProjectedStartWeight(weightPrevMonth,
                                weightCurrent, dayWeightPrev, dayOfMonthToday, daysPrevMonth);

                        if(weightProjection > 0) {
                            dataSetPrevChange.addEntry(new Entry(1, weightProjection));
                            dataSetPrevChange.addEntry(new Entry(dayOfMonthToday, weightCurrent));
                        }
                    }

                    chart.getLineData().notifyDataChanged();
                    chart.notifyDataSetChanged();
                    chart.invalidate();
                }else if (isPrevToThisMonthDisplayed() && dayWeightNext == dayOfMonthToday
                        && dataSetWeightValues.getEntryCount() > 0){
                    // If the current weight change affects the projected weight from the previous
                    // month to this one, update the projection
                    LineDataSet dataSetNextChange = (LineDataSet) chart.getLineData()
                            .getDataSetByLabel(LABEL_NEXT, true);

                    dataSetNextChange.clear();
                    int lastWeightIndex = dataSetWeightValues.getEntryCount() - 1;
                    int dayWeightLast = (int) dataSetWeightValues.getEntryForIndex(lastWeightIndex).getX();
                    float weightLast = dataSetWeightValues.getEntryForIndex(lastWeightIndex).getY();
                    int daysInMonth = getLastDayInMonth(startDate).get(Calendar.DAY_OF_MONTH);
                    float weightProjection = calcProjectedEndWeight(weightLast, weightNextMonth, dayWeightLast,
                            dayWeightNext, daysInMonth);

                    if(weightProjection > 0) {
                        dataSetNextChange.addEntry(new Entry(dayWeightLast, weightLast));
                        dataSetNextChange.addEntry(new Entry(daysInMonth, weightProjection));
                    }

                    chart.getLineData().notifyDataChanged();
                    chart.notifyDataSetChanged();
                    chart.invalidate();

                }
            }
        });

        final EditText editTextWeightGoal = findViewById(R.id.et_goal_wgt_value);
        editTextWeightGoal.setEnabled(true);

        final Button buttonUpdateWeightGoal = findViewById(R.id.btn_goal_wgt);
        buttonUpdateWeightGoal.setOnClickListener(new View.OnClickListener() {
            // Updates the user's weight goal
            public void onClick(View v) {
                int newWeightGoal = Integer.parseInt(editTextWeightGoal.getText().toString());
                if(newWeightGoal >= 10 && weightCurrent > 0) {
                    // Hide keyboard
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editTextWeightGoal.getWindowToken(), 0);

                    // Store value
                    weightGoal = newWeightGoal;
                    setFitbitWeightGoal(accessToken);

                    // Update chart data
                    chart.getLineData().removeDataSet(chart.getLineData()
                            .getDataSetByLabel(LABEL_GOAL, true));

                    int daysInMonth = getLastDayInMonth(startDate).get(Calendar.DAY_OF_MONTH);
                    LineDataSet dataSetGoal = createDataSetGoal(daysInMonth);
                    chart.getLineData().addDataSet(dataSetGoal);

                    resetChartLegend();

                    chart.getLineData().notifyDataChanged();
                    chart.notifyDataSetChanged();
                    chart.invalidate();

                } else if(weightCurrent == 0) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_missing_current),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_invalid_goal),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        final TextView textViewMonthHeading = findViewById(R.id.tv_current_heading);

        final Button btnNext = findViewById(R.id.btn_next);
        btnNext.setOnClickListener(new View.OnClickListener() {
            // Shows next month of data
            public void onClick(View v) {
                startDate.add(Calendar.MONTH, 1);
                offsetFromThisMonth--;

                // Store last measurement in old month which will be used to calculate the
                // projected weight at the start of the new month
                LineDataSet dataSetWeightValues = (LineDataSet) chart.getLineData()
                        .getDataSetByLabel(LABEL_WEIGHT, true);
                int lastIndex = dataSetWeightValues.getEntryCount() - 1;
                if(lastIndex >= 0) {
                    weightPrevMonth = dataSetWeightValues.getEntryForIndex(lastIndex).getY();
                    dayWeightPrev = (int) dataSetWeightValues.getEntryForIndex(lastIndex).getX();
                }
                else {
                    weightPrevMonth = 0;
                    dayWeightPrev = -1;
                }

                weightNextMonth = -1; // set to -1 so a Fitbit request is made for the data
                dayWeightNext = -1;

                // Clear old data
                chart.invalidate();
                chart.clear();

                // Get new data
                getWeightData(accessToken, false);

                if(isThisMonthDisplayed()) {
                    textViewMonthHeading.setText(getString(R.string.month_heading_this_month));
                } else {
                    textViewMonthHeading.setText(getString(R.string.month_heading_month_year,
                            new SimpleDateFormat("MMMM").format(startDate.getTime()),
                            startDate.get(Calendar.YEAR)));
                }
            }

        });

        final Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            // Shows previous month of data
            public void onClick(View v) {
                startDate.add(Calendar.MONTH, -1);
                offsetFromThisMonth++;
                
                weightPrevMonth = -1; // set to -1 so a Fitbit request is made for the data
                dayWeightPrev = -1;

                // Store first measurement in old month which will be used to calculate the
                // projected weight at the end of the new month
                LineDataSet dataSetWeightValues = (LineDataSet) chart.getLineData()
                        .getDataSetByLabel(LABEL_WEIGHT, true);
                if(dataSetWeightValues.getEntryCount() >= 0) {
                    weightNextMonth = dataSetWeightValues.getEntryForIndex(0).getY();
                    dayWeightNext = (int) dataSetWeightValues.getEntryForIndex(0).getX();
                } else {
                    weightNextMonth = 0;
                    dayWeightNext = -1;
                }

                // Clear old data
                chart.invalidate();
                chart.clear();

                // Get new data
                getWeightData(accessToken, false);

                textViewMonthHeading.setText(getString(R.string.month_heading_month_year,
                        new SimpleDateFormat("MMMM").format(startDate.getTime()),
                        startDate.get(Calendar.YEAR)));

            }
        });

    }

}
