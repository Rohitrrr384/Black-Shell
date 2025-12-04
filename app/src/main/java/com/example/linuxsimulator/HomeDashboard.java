package com.example.linuxsimulator;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeDashboard extends AppCompatActivity {

    // System Status Views
    TextView welcomeText, timeText, batteryText, networkSpeedText;
    ProgressBar batteryProgress;
    ImageView batteryIcon, networkIcon;
    Button logoutButton;

    // App Cards
    ImageView terminalCard, textEditorCard, calculatorCard, browserCard;
    ImageView settingsCard, fileManagerCard, networkToolsCard, systemInfoCard;
    ImageView cryptoToolsCard, passwordGenCard, wifiAnalyzerCard, portScannerCard;

    // System monitoring
    Handler systemHandler;
    Runnable systemRunnable;
    long lastTxBytes = 0, lastRxBytes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        initializeViews();
        setupSystemMonitoring();
        setupAppClickListeners();

        // Get username from intent
        String username = getIntent().getStringExtra("username");
        if (username != null) {
            welcomeText.setText("root@kali-" + username + ":~#");
        }
    }

    private void initializeViews() {
        // System Status
        welcomeText = findViewById(R.id.welcomeText);
        timeText = findViewById(R.id.timeText);
        batteryText = findViewById(R.id.batteryText);
        networkSpeedText = findViewById(R.id.networkSpeedText);
        batteryProgress = findViewById(R.id.batteryProgress);
        batteryIcon = findViewById(R.id.batteryIcon);
        networkIcon = findViewById(R.id.networkIcon);
        logoutButton = findViewById(R.id.logoutButton);

        // App Cards
        terminalCard = findViewById(R.id.terminalIcon);
        textEditorCard = findViewById(R.id.textEditorIcon);
        calculatorCard = findViewById(R.id.calculatorIcon);
        browserCard = findViewById(R.id.browserIcon);
        settingsCard = findViewById(R.id.settingsIcon);
        fileManagerCard = findViewById(R.id.fileManagerIcon);
        networkToolsCard = findViewById(R.id.networkToolsIcon);
        systemInfoCard = findViewById(R.id.systemInfoIcon);
        cryptoToolsCard = findViewById(R.id.cryptoToolsIcon);
        passwordGenCard = findViewById(R.id.passwordGenIcon);
        wifiAnalyzerCard = findViewById(R.id.wifiAnalyzerIcon);
        portScannerCard = findViewById(R.id.portScannerIcon);
    }

    private void setupSystemMonitoring() {
        systemHandler = new Handler();
        systemRunnable = new Runnable() {
            @Override
            public void run() {
                updateSystemStatus();
                systemHandler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        systemHandler.post(systemRunnable);
    }

    private void updateSystemStatus() {
        // Update time
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        timeText.setText(sdf.format(new Date()));

        // Update battery
        updateBatteryStatus();

        // Update network speed
        updateNetworkSpeed();
    }

    private void updateBatteryStatus() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (level * 100) / scale;

            batteryProgress.setProgress(batteryPct);
            batteryText.setText(batteryPct + "%");

            // Change battery icon color based on level
            if (batteryPct < 20) {
                batteryIcon.setColorFilter(getColor(R.color.battery_low));
            } else if (batteryPct < 50) {
                batteryIcon.setColorFilter(getColor(R.color.battery_medium));
            } else {
                batteryIcon.setColorFilter(getColor(R.color.kali_green));
            }
        }
    }

    private void updateNetworkSpeed() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            networkIcon.setColorFilter(getColor(R.color.kali_green));

            long currentTxBytes = TrafficStats.getTotalTxBytes();
            long currentRxBytes = TrafficStats.getTotalRxBytes();

            if (lastTxBytes != 0 && lastRxBytes != 0) {
                long txSpeed = (currentTxBytes - lastTxBytes) / 2; // bytes per second
                long rxSpeed = (currentRxBytes - lastRxBytes) / 2;

                String speedText = formatBytes(rxSpeed) + "/s ↓ " + formatBytes(txSpeed) + "/s ↑";
                networkSpeedText.setText(speedText);
            }

            lastTxBytes = currentTxBytes;
            lastRxBytes = currentRxBytes;
        } else {
            networkIcon.setColorFilter(getColor(R.color.terminal_gray));
            networkSpeedText.setText("No Connection");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private void setupAppClickListeners() {
        logoutButton.setOnClickListener(v -> logout());

        terminalCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, TerminalActivity.class);
            startActivity(intent);
        });

        textEditorCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, TextEditorActivity.class);
            startActivity(intent);
        });

        calculatorCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, CalculatorActivity.class);
            startActivity(intent);
        });

        browserCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, BrowserActivity.class);
            startActivity(intent);
        });

        settingsCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, SettingsActivity.class);
            startActivity(intent);
        });

        fileManagerCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, FileManagerActivity.class);
            startActivity(intent);
        });

        networkToolsCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, NetworkToolsActivity.class);
            startActivity(intent);
        });

        systemInfoCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, SystemInfoActivity.class);
            startActivity(intent);
        });

        cryptoToolsCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, CryptoToolsActivity.class);
            startActivity(intent);
        });

        passwordGenCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, PasswordGenActivity.class);
            startActivity(intent);
        });

        wifiAnalyzerCard.setOnClickListener(v -> {
            String[] possiblePackages = {"com.termux", "com.termux.app"};
            Intent launchIntent = null;

            for (String pkg : possiblePackages) {
                launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) break;
            }

            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(this, "Termux not installed", Toast.LENGTH_SHORT).show();
            }
        });

        portScannerCard.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboard.this, PortScannerActivity.class);
            startActivity(intent);
        });
    }


    private void openApp(String appName, String className) {
        Toast.makeText(this, "Opening " + appName + "...", Toast.LENGTH_SHORT).show();
        // You can implement actual app launching here
        // Intent intent = new Intent(this, className);
        // startActivity(intent);
    }

    private void logout() {
        Intent intent = new Intent(HomeDashboard.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (systemHandler != null && systemRunnable != null) {
            systemHandler.removeCallbacks(systemRunnable);
        }
    }
}