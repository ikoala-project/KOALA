package com.example.koala;

import android.arch.persistence.room.TypeConverter;

import java.util.Date;

/**
 * Type Converters for the App Database.
 * From https://developer.android.com/training/data-storage/room/referencing-data by Google.
 */
public class Converters {
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
