package com.example.linuxsimulator.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {User.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "linux_sim_db")
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // for simplicity; avoid in production
                    .build();
        }
        return INSTANCE;
    }
}
