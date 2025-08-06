package com.example.linuxsimulator;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.linuxsimulator.data.*;

public class LoginActivity extends AppCompatActivity {

    EditText usernameEditText, passwordEditText;
    Button actionButton;
    TextView toggleText;
    boolean isLoginMode = true;

    AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        actionButton = findViewById(R.id.actionButton);
        toggleText = findViewById(R.id.toggleText);

        db = AppDatabase.getInstance(this);

        actionButton.setOnClickListener(v -> {
            String user = usernameEditText.getText().toString().trim();
            String pass = passwordEditText.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isLoginMode) {
                User loggedInUser = db.userDao().login(user, pass);
                if (loggedInUser != null) {
                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show();
                    // Navigate to HomeDashboard
                    Intent intent = new Intent(LoginActivity.this, HomeDashboard.class);
                    intent.putExtra("username", user);
                    startActivity(intent);
                    finish(); // Close login activity
                } else {
                    Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
                }
            } else {
                db.userDao().insertUser(new User(user, pass));
                Toast.makeText(this, "Signup Success", Toast.LENGTH_SHORT).show();
                toggleMode(); // switch to login after sign up
            }
        });

        toggleText.setOnClickListener(v -> toggleMode());
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        actionButton.setText(isLoginMode ? "Login" : "Signup");
        toggleText.setText(isLoginMode ? "Don't have an account? Signup" : "Already have an account? Login");
    }
}