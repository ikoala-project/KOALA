package com.example.koala;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Activity for Exercises Summary screen. Shows the days on which the user completed the exercises
 * this week.
 */
public class ExercisesSummaryActivity extends BaseActivity {
    // IDs for check boxes representing days of the week when exercises were completed
    private static final int[] CHECK_BOX_IDS = {R.id.cb_day_1, R.id.cb_day_2, R.id.cb_day_3,
            R.id.cb_day_4, R.id.cb_day_5, R.id.cb_day_6, R.id.cb_day_7};

    private AppDatabase db;

    // Start date for the currently displayed week
    private Calendar startDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = AppDatabase.getDatabase(this);

        // Show this week on create
        Calendar today = Calendar.getInstance();
        today.setTime(getTodayAtMidnight());
        startDate = getFirstMondayInWeek(today);

        // Do not show check boxes for dates in the future
        int dayOfWeekToday = today.get(Calendar.DAY_OF_WEEK);
        int lastCheckBoxIndex;
        if(dayOfWeekToday == Calendar.SUNDAY) {
            lastCheckBoxIndex = CHECK_BOX_IDS.length;
        } else {
            lastCheckBoxIndex = dayOfWeekToday - 2;
        }

        for(int i = 0; i < 7; i++) {
            if(i > lastCheckBoxIndex) {
                CheckBox checkBox = findViewById(CHECK_BOX_IDS[i]);
                checkBox.setVisibility(View.GONE);
            }
        }

        // Get Extra indicating whether to check the exercises as completed today.
        // Used when redirected from the last video back to the summary page to automatically
        // mark the exercises as complete.
        Intent intent = getIntent();
        boolean checkToday = intent.getBooleanExtra(ExerciseVideoActivity.CHECK_TODAY, false);

        // Start task to show days on which exercises have been marked completed
        new SetValuesOnLoadTask().execute(checkToday);

        // Setup View Exercises button
        Button btnStartExercises = findViewById(R.id.btn_start);
        btnStartExercises.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showExerciseVideos();
            }
        });

    }

    protected int getLayoutActivityId(){
        return R.layout.activity_exercises_summary;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

    /**
     * onClick method for check boxes, specified in check box attributes in styles.xml.
     * @param view Representing the checkbox they just checked or unchecked
     */
    public void onCheckBoxClick(View view) {
        CheckBox checkBox = (CheckBox) view;
        Calendar dateChecked = (Calendar) startDate.clone();

        // Calculate the date represented by the check box
        int idIndex = 0;
        while (checkBox.getId() != CHECK_BOX_IDS[idIndex]){
            dateChecked.add(Calendar.DATE, 1);
            idIndex++;
        }

        // Start task to update database value
        if(checkBox.isChecked()) {
            new AddExerciseSessionTask().execute(dateChecked.getTime());
        } else {
            new DeleteExerciseSessionTask().execute(dateChecked.getTime());
        }
    }

    private void showExerciseVideos() {
        Intent intent = new Intent(this, ExerciseVideoActivity.class);
        startActivity(intent);
    }

    /**
     * Records the exercises as completed on the date specified.
     */
    private class AddExerciseSessionTask extends AsyncTask<Date, Void, Void> {
        @Override
        protected Void doInBackground(Date... params) {
            Date date = params[0];
            db.exerciseSessionDao().insert(new ExerciseSession(date));
            return null;
        }
    }

    /**
     * Removes record of exercises being completed on the date specified.
     */
    private class DeleteExerciseSessionTask extends AsyncTask<Date, Void, Void> {
        @Override
        protected Void doInBackground(Date... params) {
            Date date = params[0];
            db.exerciseSessionDao().delete(date);
            return null;
        }
    }

    /**
     * Gets values for this week from the database and checks or unchecks each checkbox
     * accordingly.
     */
    private class SetValuesOnLoadTask extends AsyncTask<Boolean, Void, List<ExerciseSession>> {
        /**
         * Marks exercises as completed today if the parameter is True and gets data for this week.
         * @param params One Boolean value, True if today should be marked as complete, else False
         * @return List of ExerciseSession objects for dates completed this week
         */
        @Override
        protected List<ExerciseSession> doInBackground(Boolean... params) {
            boolean checkToday = params[0];

            Calendar today = Calendar.getInstance();
            today.setTime(getTodayAtMidnight());

            if(checkToday) {
                db.exerciseSessionDao().insert(new ExerciseSession(today.getTime()));
            }

            List<ExerciseSession> exerciseSessions = db.exerciseSessionDao()
                    .getSessionsBetweenDates(startDate.getTime(), today.getTime());
            return exerciseSessions;
        }

        /**
         * Sets checkbox values according to whether exercises were completed on each day
         * @param exerciseSessions List of ExerciseSession objects ordered by date
         */
        @Override
        protected void onPostExecute(List<ExerciseSession> exerciseSessions) {
            int exIndex = 0;
            Calendar currentDate = (Calendar) startDate.clone();
            // For each checkbox test whether it represents a date when exercises were completed,
            // and if so then check the box
            for(int idIndex = 0; idIndex < 7; idIndex++) {
                if(exIndex < exerciseSessions.size()) { // if there if still data
                    // If the date for the next data object matches the date represented by the
                    // checkbox, set the box as checked
                    if(exerciseSessions.get(exIndex).getDate().equals(currentDate.getTime())) {
                        CheckBox checkBox = findViewById(CHECK_BOX_IDS[idIndex]);
                        checkBox.setChecked(true);
                        exIndex++;
                    }
                } else {
                    idIndex = 7; // if no more data end the loop
                }
                currentDate.add(Calendar.DATE, 1);
            }
        }
    }

}
