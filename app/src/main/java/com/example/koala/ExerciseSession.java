package com.example.koala;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Entity for exercise_session_table.
 * Adapted from
 * https://codelabs.developers.google.com/codelabs/android-room-with-a-view/#3 by Google.
 */
@Entity(tableName = "exercise_session_table")
public class ExerciseSession {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "date")
    private Date mDate;

    public ExerciseSession(Date date) {
        this.mDate = date;
    }

    public Date getDate(){return this.mDate;}
}
