package com.example.koala;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.arch.persistence.room.migration.Migration;
import android.content.Context;

/**
 * Abstract class for the application database.
 * Adapted from https://codelabs.developers.google.com/codelabs/android-room-with-a-view/#6
 * and https://developer.android.com/training/data-storage/room/migrating-db-versions
 * by Google.
 */
@Database(entities = {SymptomScore.class, ExerciseSession.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract SymptomScoreDao symptomScoreDao();
    public abstract ExerciseSessionDao exerciseSessionDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Builds and returns the app database.
     * @param context Current state
     * @return AppDatabase object
     */
    static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class,"koala_database.db")
                            .addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Database migration from version 1 to 2 adding the exercises table.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE `exercise_session_table` (`date` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`date`))");
        }
    };



}
