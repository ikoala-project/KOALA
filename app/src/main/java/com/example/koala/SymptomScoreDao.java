package com.example.koala;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.Date;
import java.util.List;

/**
 * Data access object for SymptomScores.
 * Adapted from
 * https://codelabs.developers.google.com/codelabs/android-room-with-a-view/#4 by Google.
 */
@Dao
public interface SymptomScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SymptomScore symptomScore);

    @Query("SELECT MAX(pain_score) from symptom_score_table")
    int getMaxPainScore();

    @Query("SELECT MAX(function_score) from symptom_score_table")
    int getMaxFunctionScore();

    @Query("SELECT * FROM symptom_score_table WHERE date =:date")
    List<SymptomScore> getTodayScores(Date date);

    @Query("SELECT * FROM symptom_score_table WHERE date >=:startDate AND date <=:endDate"
            + " ORDER BY date ASC")
    List<SymptomScore> getScoresBetweenDates(Date startDate, Date endDate);

    @Query("UPDATE symptom_score_table SET function_score =:functionScore WHERE date=:date")
    void updateFunction(Date date, int functionScore);

    @Query("UPDATE symptom_score_table SET pain_score =:painScore WHERE date=:date")
    void updatePain(Date date, int painScore);


}
