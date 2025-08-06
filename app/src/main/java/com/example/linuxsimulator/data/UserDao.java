package com.example.linuxsimulator.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert
    void insertUser(User user);

    @Query("SELECT * FROM User WHERE username = :username AND password = :password")
    User login(String username, String password);
}
