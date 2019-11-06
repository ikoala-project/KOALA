package com.example.koala;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

/**
* Base Activity for the application. All other Activities extend the BaseActivity.
*/
public abstract class BaseActivity extends AppCompatActivity  {
    // Intent Extra key
    protected static final String SHOW_TUTORIAL = "com.example.koala.SHOW_TUTORIAL";

    private DrawerLayout mDrawerLayout;
    boolean homeShouldOpenDrawer; // flag for onOptionsItemSelected

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutActivityId());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        // Listener for drawer menu
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        // close drawer when item is tapped
                        mDrawerLayout.closeDrawers();

                        // Open correct screen based on menu item selected
                        switch (menuItem.getItemId()) {
                            case android.R.id.home:
                                mDrawerLayout.openDrawer(GravityCompat.START);
                                return true;
                            case R.id.nav_home:
                                showHome(false);
                                return false;
                            case R.id.nav_about:
                                showAbout();
                                return false;
                            case R.id.nav_info:
                                showInfo();
                                return false;
                            case R.id.nav_fitbit:
                                showFitbit();
                                return false;
                            case R.id.nav_help:
                                showHome(true);
                                return false;
                        }

                        return true;
                    }
                });

        // Setup app bar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // If the Activity is for a top-level screen (linked from the drawer menu), then show
        // the menu icon, otherwise show the up button which opens the parent screen
        // Adapted from
        // https://stackoverflow.com/questions/36579799/android-switch-actionbar-back-button-to-navigation-button
        // by David Refaeli
        if(this instanceof MainActivity || this instanceof InfoActivity
        || this instanceof AboutActivity || this instanceof ManageFitbitActivity) {
            // Enables burger icon
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
            homeShouldOpenDrawer = true;

            navigationView.getMenu().findItem(getNavItemId()).setChecked(true);
        } else {
            // Enables back button icon
            actionBar.setHomeAsUpIndicator(null);
            homeShouldOpenDrawer = false;
        }

        // Set status bar colour (on API 21 and above)
        // Adapted from
        // https://stackoverflow.com/questions/26702000/change-status-bar-color-with-appcompat-actionbaractivity
        // by matiash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
        }
    }

    /**
     * Calculates total active minutes across several days.
     * @param fairlyActiveArray Fairly active minutes for different days
     * @param veryActiveArray   Very active minutes for different days
     * @return Total active minutes
     */
    protected int calcActiveMins(JSONArray fairlyActiveArray, JSONArray veryActiveArray) {
        int totalFairlyActiveMins = 0, totalVeryActiveMins = 0;

        try {
            for (int i = 0; i < fairlyActiveArray.length(); i++) {
                totalFairlyActiveMins += Integer.parseInt(fairlyActiveArray.getJSONObject(i)
                        .getString("value"));
                totalVeryActiveMins += Integer.parseInt(veryActiveArray.getJSONObject(i)
                        .getString("value"));
            }
        } catch(JSONException | NullPointerException e) {
            return 0;
        }

        return totalFairlyActiveMins + totalVeryActiveMins;
    }

    /**
     * Calculates the end date for the week which starts on startDate.
     * If the startDate is for this week then the endDate is today, otherwise it is the last day
     * of the week.
     * @param startDate
     * @return
     */
    protected Calendar calcEndDateFromStart(Calendar startDate) {
        Calendar endDate = (Calendar) startDate.clone();
        endDate.add(Calendar.DATE, 6);
        Calendar today = Calendar.getInstance();
        today.setTime(getTodayAtMidnight());
        if(endDate.after(today)) {
            endDate = today;
        }
        return endDate;
    }

    /**
     * Calculates the number of non-sedentary hours in a day.
     * @param stepsArray Steps data in 15-minute intervals
     * @return Number of non-sedentary hours
     */
    protected int calcNonSedentaryHours(JSONArray stepsArray) {
        int steps = 0, interval = 0, nonSedentaryHours = 0;
        int stepThreshold = getResources().getInteger(R.integer.steps_threshold);
        try {
            for (int i = 0; i < stepsArray.length(); i++) {
                // Step data is requested in 15 minute intervals so we add up the steps
                // in every four values to get the total for each hour
                steps += Integer.parseInt(stepsArray.getJSONObject(i).getString("value"));
                interval++;
                if(interval == 4) { // we have reached the end of the hour
                    if(steps >= stepThreshold) {
                        nonSedentaryHours++;
                    }
                    interval = 0;
                    steps = 0;
                }
            }
        } catch(JSONException | NullPointerException e) {
            return 0;
        }

        return nonSedentaryHours;
    }

    /**
     * Calculates the endDate for the previous week (assuming Monday as the first day of the week)
     * @param endDate Current end date
     * @return Previous end date
     */
    protected Calendar calcPrevEndDate(Calendar endDate) {
        // Loop backwards until we reach the previous Sunday
        do {
            endDate.add(Calendar.DATE, -1); // get the previous day
        } while(endDate.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY);
        return endDate;
    }

    /**
     * Gets the button colour drawable ID according to how close the user is to meeting their goal
     * (green, yellow, amber, red), or grey if no goal is set.
     * @param achieved    Value achieved by the user e.g. calories burned
     * @param goal        Goal value, or 0 if no goal set
     * @param amberIsHalf True if the amber threshold should be 50%, False otherwise.
     *                    Used for the exercises summary box for which the goal is always 2
     * @return Drawable resource ID
     */
    protected int getButtonColourBackground(int achieved, int goal, boolean amberIsHalf) {
        if(goal == 0) {
            return R.drawable.btn_bg_grey;
        }

        double a = (double) achieved;
        double g = (double) goal;
        double percentAchieved = a / g * 100;

        int amberThreshold;
        if(amberIsHalf) {
            amberThreshold = 50;
        } else {
            amberThreshold = getResources().getInteger(R.integer.amber_threshold);
        }

        int yellowThreshold = getResources().getInteger(R.integer.yellow_threshold);

        // The second conditions are for non-sedentary hours if they are one or two hours short
        if(percentAchieved >= 100) {
            return R.drawable.btn_bg_green;
        } else if ((percentAchieved >= yellowThreshold) || (achieved >= 5 && achieved == (goal-1))) {
            return R.drawable.btn_bg_yellow;
        } else if (percentAchieved >= amberThreshold || (achieved >= 4 && achieved == (goal-2))) {
            return R.drawable.btn_bg_amber;
        } else {
            return R.drawable.btn_bg_red;
        }
    }

    /**
     * Gets the first Monday in the week specified by endDate.
     * @param endDate Date in the week to find the first Monday for
     * @return Calendar object for the first Monday
     */
    protected Calendar getFirstMondayInWeek(Calendar endDate) {
        Calendar startDate = (Calendar) endDate.clone();

        // Loop backwards, starting from endDate
        while(startDate.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            startDate.add(Calendar.DATE, -1); // get the previous day
        }

        return startDate;
    }

    /**
     * Gets the value to display for a goal. If a goal has been set then the value is returned,
     * otherwise a placeholder is used.
     * @param goal Goal value, or 0 if no goal is set
     * @param placeholder To use if no goal has been set
     * @return Display value as a string
     */
    protected String getGoalDisplayValue(int goal, String placeholder) {
        if(goal > 0) {
            return goal + "";
        } else {
            return placeholder; // e.g. blank or a dash
        }
    }

    /**
     * Gets the ID for the current Activity's layout file.
     * @return Layout ID
     */
    protected abstract int getLayoutActivityId();

    /**
     * Gets the navigation item to highlight for the screen which is currently displayed.
     * @return Navigation item ID
     */
    protected abstract int getNavItemId();

    /**
     * Gets the colour resource ID according to the user's progress towards their goal.
     * @param achieved Value achieved by the user e.g. calories burned
     * @param goal Goal value, or 0 if no goal set
     * @return Colour resource ID
     */
    protected int getProgressColour(int achieved, int goal) {
        if(goal == 0) {
            return R.color.colorProgressGrey;
        }

        double a = (double) achieved;
        double g = (double) goal;

        double percentAchieved = a / g * 100;

        if(percentAchieved >= 100) {
            return R.color.colorProgressGreen;
        } else if (percentAchieved >= getResources().getInteger(R.integer.yellow_threshold)) {
            return R.color.colorProgressYellow;
        } else if (percentAchieved >= getResources().getInteger(R.integer.amber_threshold)) {
            return R.color.colorProgressAmber;
        } else {
            return R.color.colorProgressRed;
        }

    }

    /**
     * Gets an object with today's date at midnight.
     * @return Date object
     */
    protected Date getTodayAtMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Compares two Calendar objects to see if they have the same date and times within the same
     * hour of the day e.g. 1:00pm and 1:59pm are in the same hour but 1:59pm and 2:00pm are not.
     * Used to decide whether to reset the Fitbit request count, because the limit is reset by
     * Fitbit on the hour.
     * @param cal1 First Calendar object to compare
     * @param cal2 Second Calendar object to compare
     * @return True if the dates are the same and the times are in the same hour, else False
     */
    protected boolean inSameHour(Calendar cal1, Calendar cal2) {
        if ((cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR))
                && (cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR))
                && (cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY))) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Determines behaviour when a menu item is selected.
     * Adapted from
     * https://stackoverflow.com/questions/36579799/android-switch-actionbar-back-button-to-navigation-button
     * by David Refaeli
     * @param item MenuItem selected
     * @return True to consume event, False otherwise
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(homeShouldOpenDrawer) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    mDrawerLayout.openDrawer(GravityCompat.START);
                    return true;
            }
            return super.onOptionsItemSelected(item);
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Sets the appearance of the previous and next buttons. On lower API devices we are unable
     * to use drawableLeft and Right so use text instead.
     */
    protected void setPrevNextButtonsAppearance() {
        Button btnPrev = findViewById(R.id.btn_prev);
        Button btnNext = findViewById(R.id.btn_next);
        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Use arrow icons
            btnPrev.setCompoundDrawablesWithIntrinsicBounds
                    (R.drawable.ic_keyboard_arrow_left_black_24dp, 0, 0, 0);
            btnNext.setCompoundDrawablesWithIntrinsicBounds
                    (0, 0, R.drawable.ic_keyboard_arrow_right_black_24dp, 0);
        } else {
            // Use text
            btnPrev.setText(getString(R.string.previous));
            btnNext.setText(getString(R.string.next));
        }
    }

    /**
     * Sets up a bar chart for one week from Monday-Sunday.
     * @param legendEntrySpace Space to leave between entries in the legend
     */
    protected void setupWeeklyBarChart(float legendEntrySpace) {
        BarChart chart = findViewById(R.id.chart);

        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(0, 0, 0, 10);
        chart.setNoDataText(getResources().getString(R.string.loading_graph));
        chart.getPaint(Chart.PAINT_INFO).setTextSize(Utils.convertDpToPixel(16f));

        final ArrayList<String> daysOfWeek = new ArrayList<>(
                Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return daysOfWeek.get((int) value);
            }
        });
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(16f);

        YAxis yAxisLeft = chart.getAxisLeft();
        yAxisLeft.setAxisMinimum(0f);
        yAxisLeft.setTextSize(16f);
        yAxisLeft.setGranularity(1.0f); // show integer values on y axis
        yAxisLeft.setGranularityEnabled(true);

        YAxis yAxisRight = chart.getAxisRight();
        yAxisRight.setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextSize(16f);
        legend.setYOffset(10f);
        legend.setXEntrySpace(legendEntrySpace);
    }

    protected void showAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    protected void showExercisesSummary() {
        Intent intent = new Intent(this, ExercisesSummaryActivity.class);
        startActivity(intent);
    }

    protected void showFitbit() {
        Intent intent = new Intent(this, ManageFitbitActivity.class);
        startActivity(intent);
    }

    protected void showHome(boolean showTutorial) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(SHOW_TUTORIAL, showTutorial);
        startActivity(intent);
    }

    protected void showInfo() {
        Intent intent = new Intent(this, InfoActivity.class);
        startActivity(intent);
    }

    protected void showUpdateGoals() {
        Intent intent = new Intent(this, UpdateGoalsActivity.class);
        startActivity(intent);
    }

    /**
     * Updates the number of Fitbit API requests remaining for this hour. When a new hour begins
     * the value is reset.
     * @param numberOfRequests Number of requests to deduct from the total remaining
     */
    protected void updateRequestCount(int numberOfRequests) {
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        Calendar calNow = Calendar.getInstance(); // current date/time
        // Date/time when the requests remaining count was last reset
        Calendar calStored = Calendar.getInstance();
        calStored.setTime(new Date(sharedPref.getLong(getString(R.string.api_limit_date_key), 0)));

        int maxRequests = getResources().getInteger(R.integer.fitbit_api_limit);
        int requestsRemaining;
        if(inSameHour(calNow, calStored)) {
            // Get requests remaining from preferences
            requestsRemaining = sharedPref.getInt(getString(R.string.api_limit_count_key), 0);
        } else { // hour has lapsed
            // Reset requests count
            requestsRemaining = maxRequests;
            // Update stored date/time
            editor.putLong(getString(R.string.api_limit_date_key), calNow.getTime().getTime());
            editor.apply();
        }

        requestsRemaining = requestsRemaining - numberOfRequests;
        editor.putInt(getString(R.string.api_limit_count_key), requestsRemaining);
        editor.apply();
    }
}
