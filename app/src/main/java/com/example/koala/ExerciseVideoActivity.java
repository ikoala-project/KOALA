package com.example.koala;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Activity for the Exercise Video screen.
 * Adapted from https://examples.javacodegeeks.com/android/android-videoview-example/
 * by Chryssa Aliferi.
 */
public class ExerciseVideoActivity extends BaseActivity {
    // View object for the video and progress dialog for when the video is loading
    private VideoView mVideoView;
    private ProgressDialog progressDialog;

    // Exercise instructions text to show to the user, from exercise_instructions in strings.xml
    private static String[] EXERCISE_INSTRUCTIONS;

    // The three arrays below must all correspond to one another
    // Indicates which instruction from EXERCISE_INSTRUCTIONS to use for each video
    private static final int[] INSTRUCTION_INDEXES = {0,0,1,1,2,3,4,4,0,0,5,4,4,4,2,6,7};

    // Name for each exercise video, from exercise_names in strings.xml
    private static String[] EXERCISE_NAMES;

    // ID for each video in the res/raw folder
    private static final int[] EXERCISE_RES_IDS = {R.raw.e0_knee_mobility, R.raw.e1_leg_slide,
            R.raw.e2_thigh_stretch, R.raw.e3_hamstring_stretch, R.raw.e4_sit_stand,
            R.raw.e5_step_ups, R.raw.e6_mini_squat, R.raw.e7_leg_cross, R.raw.e8_thigh_squeeze,
            R.raw.e9_leg_extension, R.raw.e10_leg_raise, R.raw.e11_calf_raise,
            R.raw.e12_hamstring_curl, R.raw.e13_wall_squat, R.raw.e14_step_downs,
            R.raw.e15_stand_one_leg, R.raw.e16_foot_alphabet};

    // Intent extra key
    protected static final String CHECK_TODAY = "com.example.koala.CHECK_TODAY";

    // Index for currently displayed exercise
    private int exerciseIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EXERCISE_NAMES = getResources().getStringArray(R.array.exercise_names);
        EXERCISE_INSTRUCTIONS = getResources().getStringArray(R.array.exercise_instructions);

        //initialize the VideoView
        mVideoView = (VideoView) findViewById(R.id.video_view);

        // create a progress bar while the video file is loading
        progressDialog = new ProgressDialog(ExerciseVideoActivity.this);
        // set a title for the progress bar
        progressDialog.setTitle("Exercise Video");
        // set a message for the progress bar
        progressDialog.setMessage("Loading...");
        //set the progress bar not cancelable on users' touch
        progressDialog.setCancelable(false);

        progressDialog.show();

        final MediaController mediaController = new MediaController(this) {
            // Override hide method to prevent the media controls from disappearing
            @Override
            public void hide() { }
        };

        mVideoView.setMediaController(mediaController);

        showCurrentExercise();

        mVideoView.requestFocus();
        //we also set an setOnPreparedListener in order to know when the video file is ready for playback
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            public void onPrepared(MediaPlayer mediaPlayer) {
                // close the progress bar and play the video
                progressDialog.dismiss();
                mVideoView.start();
                mediaController.show(0);
            }
        });

        // Setup previous and next buttons
        // When viewing the first video, the previous button goes Back to the Exercises Summary
        // When viewing the last video, the next button goes to the Exercises Summary - "Finish"
        final Button btnPrev = findViewById(R.id.btn_prev);
        final Button btnNext = findViewById(R.id.btn_next);

        btnPrev.setText(getString(R.string.back));
        btnNext.setText(getString(R.string.next));

        btnPrev.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                exerciseIndex--;

                int lastIndex = EXERCISE_NAMES.length - 1;
                if(exerciseIndex < 0) { // if we were viewing the first video, go back to Summary
                    showExercisesSummary(false);
                    return;
                } else if(exerciseIndex == 0) { // if the new video is the first video
                    btnPrev.setText(getString(R.string.back));
                } else if(exerciseIndex == lastIndex - 1) { // if the old video was the last video
                    // Change the button text from Finish to Next
                    btnNext.setText(getString(R.string.next));
                }

                showCurrentExercise();
            }
        });


        btnNext.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                exerciseIndex++;

                int lastIndex = EXERCISE_NAMES.length - 1;
                if(exerciseIndex > lastIndex) { // if we were viewing the last video
                    showExercisesSummary(true);
                    return;
                } else if(exerciseIndex == lastIndex) { // if the new video is the last video
                    btnNext.setText(getString(R.string.finish));
                } else if(exerciseIndex == 1) { // if the old video was the first video
                    // Change the button text from Back to Previous
                    btnPrev.setText(getString(R.string.previous));
                }

                showCurrentExercise();
            }
        });

    }

    /**
     * Sets the video URI for the video indicated by exerciseIndex and sets headings and
     * instruction text.
     */
    private void showCurrentExercise() {
        // Set the video URI
        try {
            mVideoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/"
                    + EXERCISE_RES_IDS[exerciseIndex]));
        } catch (Exception e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.video_error)
                    .setPositiveButton(R.string.ok_btn, null).create().show();
        }

        // Show headings and instructions
        TextView textViewExName = findViewById(R.id.tv_exercise_name);
        textViewExName.setText(EXERCISE_NAMES[exerciseIndex]);

        TextView textViewExNumber = findViewById(R.id.tv_current_heading);
        textViewExNumber.setText(getString(R.string.exercise_x_of_y, exerciseIndex+1,
                EXERCISE_NAMES.length));

        TextView textViewInstructions = findViewById(R.id.tv_instructions);
        textViewInstructions.setText(EXERCISE_INSTRUCTIONS[INSTRUCTION_INDEXES[exerciseIndex]]);
    }

    protected void showExercisesSummary(boolean checkToday) {
        Intent intent = new Intent(this, ExercisesSummaryActivity.class);
        intent.putExtra(CHECK_TODAY, checkToday);
        startActivity(intent);
    }

    protected int getLayoutActivityId(){
        return R.layout.activity_exercise_video;
    }

    protected int getNavItemId(){
        return R.id.nav_home;
    }

}
