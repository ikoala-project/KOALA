package com.example.koala;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.Date;
import java.util.List;

/**
 * Data access object for ExerciseSessions.
 * Adapted from
 * https://codelabs.developers.google.com/codelabs/android-room-with-a-view/#4 by Google.
 */
@Dao
public interface ExerciseSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ExerciseSession exerciseSession);

    @Query("DELETE FROM exercise_session_table WHERE date =:date")
    void delete(Date date);

    @Query("SELECT * FROM exercise_session_table WHERE date >=:startDate AND date <=:endDate"
            + " ORDER BY date ASC")
    List<ExerciseSession> getSessionsBetweenDates(Date startDate, Date endDate);
}
