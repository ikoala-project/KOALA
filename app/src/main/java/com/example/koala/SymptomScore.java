package com.example.koala;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Entity for symptom_score_table.
 * Adapted from
 * https://codelabs.developers.google.com/codelabs/android-room-with-a-view/#3 by Google.
 */
@Entity(tableName = "symptom_score_table")
public class SymptomScore {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "date")
    private Date mDate;

    @ColumnInfo(name = "pain_score")
    private int mPainScore;

    @ColumnInfo(name = "function_score")
    private int mFunctionScore;

    public SymptomScore(Date date, int painScore, int functionScore) {
        this.mDate = date;
        this.mPainScore = painScore;
        this.mFunctionScore = functionScore;
    }

    public Date getDate(){return this.mDate;}
    public int getPainScore(){return this.mPainScore;}
    public int getFunctionScore(){return this.mFunctionScore;}
}