package com.example.koala;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Activity for Track Symptoms screen.
 */
public class TrackSymptomsActivity extends BaseActivity {
    private AppDatabase db;

    // Start and end dates for the currently displayed week
    private Calendar endDate;
    private Calendar startDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getDatabase(this);

        // Show this week on create
        endDate = Calendar.getInstance();
        endDate.setTime(getTodayAtMidnight());
        startDate = getFirstMondayInWeek(endDate);

        // Setup widgets
        final SeekBar seekBarPain = findViewById(R.id.sb_pain_score);
        seekBarPain.setMax(getResources().getInteger(R.integer.pain_score_max));

        final SeekBar seekBarFunction = findViewById(R.id.sb_function_score);
        seekBarFunction.setMax(getResources().getInteger(R.integer.function_score_max));

        setPrevNextButtonsAppearance();
        setupWeeklyBarChart(30f);

        // Start task to get values from database
        new SetValuesOnLoadTask().execute();

        // Set listeners for previous and next buttons
        final BarChart chart = findViewById(R.id.chart);
        final TextView textViewWeekHeading = findViewById(R.id.tv_current_heading);
        final SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.week_heading_date_format));

        final Button btnNext = findViewById(R.id.btn_next);
        btnNext.setVisibility(View.GONE); // current week is displayed on create, so no next week
        btnNext.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Clear old data
                chart.invalidate();
                chart.clear();

                // Calculate new dates
                startDate = endDate;
                startDate.add(Calendar.DATE, 1);

                endDate = calcEndDateFromStart(startDate);

                // Start task to display new data
                new DisplaySymptomScoresTask().execute();

                // Set heading and next button visibility based on whether this week is displayed
                Calendar today = Calendar.getInstance();
                today.setTime(getTodayAtMidnight());
                if((endDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) &&
                        (endDate.get(Calendar.YEAR) == today.get(Calendar.YEAR))) { //endDate==today
                    btnNext.setVisibility(View.GONE);
                    textViewWeekHeading.setText(getString(R.string.week_heading_this_week));
                } else {
                    textViewWeekHeading.setText(getString(R.string.week_heading_with_dates,
                            dateFormat.format(startDate.getTime()),
                            dateFormat.format(endDate.getTime())));
                }

            }
        });

        final Button btnPrev = findViewById(R.id.btn_prev);
        btnPrev.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Clear old data
                chart.invalidate();
                chart.clear();

                // Calculate new dates
                endDate = calcPrevEndDate(endDate);
                startDate = getFirstMondayInWeek(endDate);

                // Start task to display new data
                new DisplaySymptomScoresTask().execute();

                // Set next button visibility and date heading
                // Note if the user clicked Previous then the new week must be before this week
                if(btnNext.getVisibility() == View.GONE) {
                    btnNext.setVisibility(View.VISIBLE);
                }

                textViewWeekHeading.setText(getString(R.string.week_heading_with_dates,
                        dateFormat.format(startDate.getTime()),
                        dateFormat.format(endDate.getTime())));
            }
        });

    }

    /**
     * Increments a date by one day.
     * @param date Date to increment
     * @return date + one day
     */
    private Date addOneDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, 1);
        return calendar.getTime();
    }

    /**
     * Unchecks the symptom score switch and disables the seek bar given by the parameters.
     * @param idSwitch  ID for the symptom score switch
     * @param idSeekBar ID for the symptom score seek bar
     */
    private void disableSymptomScore(int idSwitch, int idSeekBar) {
        Switch switchSymptom = findViewById(idSwitch);
        if(switchSymptom.isChecked())
            switchSymptom.setChecked(false);

        SeekBar seekBarSymptom = findViewById(idSeekBar);
        seekBarSymptom.setEnabled(false);
        seekBarSymptom.setProgress(0);
    }

    /**
     * Checks the symptom score switch and enables the seek bar with the value specified.
     * @param score     Score to set on the seek bar
     * @param idSwitch  ID for the symptom score switch
     * @param idSeekBar ID for the symptom score seek bar
     */
    private void enableSymptomScore(int score, int idSwitch, int idSeekBar) {
        Switch switchSymptom = findViewById(idSwitch);
        if(!switchSymptom.isChecked())
            switchSymptom.setChecked(true);

        SeekBar seekBarSymptom = findViewById(idSeekBar);
        seekBarSymptom.setEnabled(true);
        seekBarSymptom.setProgress(score);
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_track_symptoms;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * Gets the symptom score data for the new week to display and shows it on the chart.
     */
    private class DisplaySymptomScoresTask extends AsyncTask<Void, Void, Pair<List<SymptomScore>, Integer>> {
        /**
         * Gets the all-time maximum of function and pain scores and the symptom score data
         * for days between startDate and endDate inclusive.
         * @param params Not used
         * @return List of symptom scores ordered by date and the all-time maximum value
         */
        @Override
        protected Pair<List<SymptomScore>, Integer> doInBackground(Void... params) {
            // Find the highest pain or function score entered by the user
            // The maximum value on the y axis of every graph will be set to this value
            // so that the graphs are uniform in size for easier comparison
            int maxPainScore = db.symptomScoreDao().getMaxPainScore();
            int maxFunctionScore = db.symptomScoreDao().getMaxFunctionScore();
            int maxScore = Math.max(maxPainScore, maxFunctionScore);

            List<SymptomScore> symptomScores = db.symptomScoreDao()
                    .getScoresBetweenDates(startDate.getTime(), endDate.getTime());

            return Pair.create(symptomScores, maxScore);
        }

        /**
         * Displays the data on the chart.
         * @param symptomScoresAndMax Pair of list of symptom scores and integer maximum
         */
        @Override
        protected void onPostExecute(Pair<List<SymptomScore>, Integer> symptomScoresAndMax) {
            List<SymptomScore> symptomScores = symptomScoresAndMax.first;
            int maxScore = symptomScoresAndMax.second;

            ArrayList<BarEntry> entriesPain = new ArrayList<>();
            ArrayList<BarEntry> entriesFunction = new ArrayList<>();

            // Flags used in the value formatter to decide whether to show "?" for missing data
            boolean[] missingScoresPain = new boolean[7];
            boolean[] missingScoresFunction = new boolean[7];

            Date currentDate = startDate.getTime();
            Date today = getTodayAtMidnight();
            int scoreIndex = 0;
            int painScore, functionScore;

            // Create lists of data entries for the chart
            // If data is missing we set it as -1 temporarily to distinguish between a score of 0
            // and missing data, but change -1 to 0 before the chart is displayed
            for(int i = 0; i < 7; i++) {
                // If there are more scores to read in symptomScores
                if (scoreIndex < symptomScores.size()) {
                    SymptomScore symptomScore = symptomScores.get(scoreIndex);
                    // If date of next unused score in SymptomScores matches currentDate which
                    // we are creating the bars for
                    if (symptomScore.getDate().equals(currentDate)) { //scores exist for currentDate
                        painScore = symptomScore.getPainScore();
                        functionScore = symptomScore.getFunctionScore();
                        scoreIndex++; // on next iteration look at next scores (if there are any)
                    } else { // No data entered for this day
                        painScore = -1;
                        functionScore = -1;
                    }
                } else { // No data entered for this day
                    painScore = -1;
                    functionScore = -1;
                }

                // If there is no data and the date is not a date in the future,
                // set missingScores flag to true so a question mark is displayed in the graph
                // when the value formatter is applied.
                // Once we've set the flag we change the score to 0 before adding the bar entry
                missingScoresPain[i] = (painScore == -1 && !currentDate.after(today));
                if(painScore == -1)
                    painScore = 0;

                missingScoresFunction[i] = (functionScore == -1 && !currentDate.after(today));
                if(functionScore == -1)
                    functionScore = 0;

                entriesPain.add(new BarEntry((float) i, painScore));
                entriesFunction.add(new BarEntry((float) i, functionScore));

                currentDate = addOneDay(currentDate);
            }

            BarChart chart = findViewById(R.id.chart);

            // Create and format data sets for chart
            BarDataSet dataSetPain = new BarDataSet(entriesPain, "Pain");
            dataSetPain.setColor(getResources().getColor(R.color.colorGraph1));
            dataSetPain.setValueTextSize(20f);
            dataSetPain.setValueFormatter(new SymptomScoreValueFormatter(missingScoresPain));

            BarDataSet dataSetFunction = new BarDataSet(entriesFunction, "Function");
            dataSetFunction.setColor(getResources().getColor(R.color.colorGraph2));
            dataSetFunction.setValueTextSize(20f);
            dataSetFunction.setValueFormatter(new SymptomScoreValueFormatter(missingScoresFunction));

            // Show data on chart
            BarData barData = new BarData(dataSetPain, dataSetFunction);
            chart.setData(barData);
            chart.getBarData().setBarWidth(chart.getBarData().getBarWidth() / 2);
            chart.groupBars(chart.getXAxis().getAxisMinimum(), 0.15f, 0f);

            // Show all-time maximum as maximum on y-axis
            chart.getAxisLeft().setAxisMaximum((float) maxScore);

            chart.setDrawValueAboveBar(true); // so ? is shown for missing data

            chart.invalidate();
        }
    }

    /**
     * Gets and sets initial values on the score setting widgets when the Activity is created.
     * Then starts task to display scores on chart and sets listeners for the score widgets.
     */
    private class SetValuesOnLoadTask extends AsyncTask<Void, Void, SymptomScore> {
        /**
         * Queries the database for symptom scores for today's date. If none exist insert an
         * entry for today with -1 for each score representing missing data.
         * @param params Not used
         * @return Scores which are now in the database for today
         */
        @Override
        protected SymptomScore doInBackground(Void... params) {
            final Date today = getTodayAtMidnight();

            List<SymptomScore> todayList = db.symptomScoreDao().getTodayScores(today);
            SymptomScore scores;
            if (todayList.isEmpty()) {
                scores = new SymptomScore(today, -1, -1);
                db.symptomScoreDao().insert(scores);
            } else {
                scores = todayList.get(0);
            }

            return scores;
        }

        /**
         * Sets appearance of score setting widgets based on current symptom scores and
         * sets listeners for them. Also starts task to display data on chart.
         * @param scores Entry in the database for today
         */
        @Override
        protected void onPostExecute(SymptomScore scores) {
            final TextView textViewPainScore = findViewById(R.id.tv_pain_score);
            final TextView textViewFunctionScore = findViewById(R.id.tv_function_score);

            // Display values on widgets used to set scores
            int painScore = scores.getPainScore();
            if (painScore >= 0) {
                enableSymptomScore(painScore, R.id.sw_pain, R.id.sb_pain_score);
                textViewPainScore.setText(painScore + "");
            } else {
                disableSymptomScore(R.id.sw_pain, R.id.sb_pain_score);
            }

            int functionScore = scores.getFunctionScore();
            if (functionScore >= 0) {
                enableSymptomScore(functionScore, R.id.sw_function, R.id.sb_function_score);
                textViewFunctionScore.setText(functionScore + "");
            } else {
                disableSymptomScore(R.id.sw_function, R.id.sb_function_score);
            }

            // Start task to display this week's scores on the chart
            new DisplaySymptomScoresTask().execute();

            // Set listeners for widgets - these respond to changes on the seek bars and
            // switches so we do not set them until the initial values have been set,
            // otherwise it can trigger an override of values stored in the database

            // Set listeners for pain score widgets
            final SeekBar seekBarPain = findViewById(R.id.sb_pain_score);
            seekBarPain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                    // Show currently selected score
                    textViewPainScore.setText(progress + "");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Update score in database
                    new UpdatePainScoreTask().execute(seekBarPain.getProgress());
                }
            });

            final Switch switchPain = findViewById(R.id.sw_pain);
            switchPain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if(b) {
                        // Enable seek bar with value 0
                        new UpdatePainScoreTask().execute(0);
                        enableSymptomScore(0, R.id.sw_pain, R.id.sb_pain_score);
                        textViewPainScore.setText("0");
                    } else {
                        // Disable seek bar and remove score from database
                        new UpdatePainScoreTask().execute(-1);
                        disableSymptomScore(R.id.sw_pain, R.id.sb_pain_score);
                        textViewPainScore.setText("");
                    }
                }
            });

            // Set listeners for function score widgets
            final SeekBar seekBarFunction = findViewById(R.id.sb_function_score);
            seekBarFunction.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                    // Show currently selected score
                    textViewFunctionScore.setText(progress + "");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // Update score in database
                    new UpdateFunctionScoreTask().execute(seekBarFunction.getProgress());
                }
            });

            final Switch switchFunction = findViewById(R.id.sw_function);
            switchFunction.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if(b) {
                        // Enable seek bar with value 0
                        new UpdateFunctionScoreTask().execute(0);
                        enableSymptomScore(0, R.id.sw_function, R.id.sb_function_score);
                        textViewFunctionScore.setText("0");
                    } else {
                        // Disable seek bar and remove score from database
                        new UpdateFunctionScoreTask().execute(-1);
                        disableSymptomScore(R.id.sw_function, R.id.sb_function_score);
                        textViewFunctionScore.setText("");
                    }
                }
            });
        }
    }

    /**
     * Updates today's function score in database and the chart data.
     */
    private class UpdateFunctionScoreTask extends AsyncTask<Integer, Void, Void> {
        /**
         * Updates the function score for today in the database.
         * @param params First parameter is new function score
         * @return null
         */
        @Override
        protected Void doInBackground(Integer... params) {
            int functionScore = params[0];

            Date today = getTodayAtMidnight();
            List<SymptomScore> todayList = db.symptomScoreDao().getTodayScores(today);

            if(todayList.isEmpty()) {
                SymptomScore scores = new SymptomScore(today, -1, functionScore);
                db.symptomScoreDao().insert(scores);
            } else {
                db.symptomScoreDao().updateFunction(getTodayAtMidnight(), functionScore);
            }
            return null;

        }

        /**
         * Starts task to update chart.
         * @param v Not used
         */
        @Override
        protected void onPostExecute(Void v){
            new DisplaySymptomScoresTask().execute();
        }
    }

    /**
     * Updates today's pain score in database and the chart data.
     */
    private class UpdatePainScoreTask extends AsyncTask<Integer, Void, Void> {
        /**
         * Updates the pain score for today in the database.
         * @param params First parameter is new pain score
         * @return null
         */
        @Override
        protected Void doInBackground(Integer... params) {
            int painScore = params[0];

            Date today = getTodayAtMidnight();
            List<SymptomScore> todayList = db.symptomScoreDao().getTodayScores(today);

            if(todayList.isEmpty()) {
                SymptomScore scores = new SymptomScore(today, painScore, -1);
                db.symptomScoreDao().insert(scores);
            } else {
                db.symptomScoreDao().updatePain(getTodayAtMidnight(), painScore);
            }
            return null;
        }

        /**
         * Starts task to update chart.
         * @param v Not used
         */
        @Override
        protected void onPostExecute(Void v){
            new DisplaySymptomScoresTask().execute();
        }
    }

}
