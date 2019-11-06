package com.example.koala;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * ValueFormatter for symptom score values on the TrackSymptoms bar chart.
 * Used to display a question mark for missing data.
 * A separate instance is needed for each data set.
 */
public class SymptomScoreValueFormatter implements IValueFormatter {
    private boolean[] mMissingScores;

    /**
     * Constructor for the ValueFormatter with array of boolean flags passed in.
     * @param missingScores Value at position i is true if the value on day i is missing
     */
    public SymptomScoreValueFormatter(boolean[] missingScores) {
        mMissingScores = missingScores;
    }

    /**
     * Gets value to display above a bar on the chart: a question mark ? for missing data or
     * blank otherwise.
     * @param value Symptom score value on the chart
     * @param entry For this value in the bar data set
     * @param dataSetIndex Index of the data set
     * @param viewPortHandler Handles view-port of chart
     * @return "?" for a missing value or "" otherwise
     */
    public String getFormattedValue(float value, Entry entry, int dataSetIndex,
                                    ViewPortHandler viewPortHandler) {
        // The days of the week are represented by numbers on the X axis: Mon = 0, Tues = 1 etc.
        // The X value is not an integer because they are off centre so that two bars can be shown.
        // So we round to the nearest integer to work out which day it is
        int scoreIndex = Math.round(entry.getX());

        if(mMissingScores[scoreIndex]) {
            return "?";
        } else {
            return "";
        }
    }

}
