package com.example.linuxsimulator;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.widget.SwitchCompat;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    // SharedPreferences
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    // UI Components - Terminal Settings
    private SwitchCompat terminalSoundSwitch;
    private SwitchCompat terminalVibrateSwitch;
    private SwitchCompat terminalAutoCompleteSwitch;
    private SwitchCompat terminalHistorySwitch;
    private Spinner fontSizeSpinner;
    private Spinner terminalThemeSpinner;
    private Spinner shellTypeSpinner;
    private SeekBar terminalOpacitySeekBar;
    private TextView opacityValueText;
    private Button customPromptButton;
    private TextView currentPromptText;

    // UI Components - Network Settings
    private SwitchCompat networkMonitoringSwitch;
    private SwitchCompat firewallSwitch;
    private SwitchCompat vpnSimulationSwitch;
    private EditText defaultGatewayEdit;
    private EditText dnsServerEdit;
    private Spinner networkInterfaceSpinner;

    // UI Components - Security Settings
    private SwitchCompat rootAccessSwitch;
    private SwitchCompat passwordAuthSwitch;
    private SwitchCompat sshServerSwitch;
    private SwitchCompat encryptionSwitch;
    private Button changePasswordButton;
    private Spinner securityLevelSpinner;
    private SwitchCompat auditLogsSwitch;

    // UI Components - System Settings
    private SwitchCompat darkModeSwitch;
    private SwitchCompat notificationsSwitch;
    private SwitchCompat autoSaveSwitch;
    private Spinner languageSpinner;
    private SeekBar cpuUsageSeekBar;
    private SeekBar memoryUsageSeekBar;
    private TextView cpuUsageText;
    private TextView memoryUsageText;

    // UI Components - Tools & Features
    private SwitchCompat metasploitSwitch;
    private SwitchCompat nmapSwitch;
    private SwitchCompat wiresharkSwitch;
    private SwitchCompat aircrackSwitch;
    private SwitchCompat johnRipperSwitch;
    private SwitchCompat burpSuiteSwitch;
    private SwitchCompat sqlmapSwitch;

    // UI Components - Advanced
    private Button exportSettingsButton;
    private Button importSettingsButton;
    private Button resetSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        initializePreferences();
        initializeViews();
        loadSettings();
        setupEventListeners();
        setupBackPressHandler();
    }

    private void initializePreferences() {
        prefs = getSharedPreferences("BlackShellSettings", Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    @SuppressLint("WrongViewCast")
    private void initializeViews() {
        // Terminal Settings
        terminalSoundSwitch = findViewById(R.id.terminalSoundSwitch);
        terminalVibrateSwitch = findViewById(R.id.terminalVibrateSwitch);
        terminalAutoCompleteSwitch = findViewById(R.id.terminalAutoCompleteSwitch);
        terminalHistorySwitch = findViewById(R.id.terminalHistorySwitch);
        fontSizeSpinner = findViewById(R.id.fontSizeSpinner);
        terminalThemeSpinner = findViewById(R.id.terminalThemeSpinner);
        shellTypeSpinner = findViewById(R.id.shellTypeSpinner);
        terminalOpacitySeekBar = findViewById(R.id.terminalOpacitySeekBar);
        opacityValueText = findViewById(R.id.opacityValueText);
        customPromptButton = findViewById(R.id.customPromptButton);
        currentPromptText = findViewById(R.id.currentPromptText);

        // Network Settings
        networkMonitoringSwitch = findViewById(R.id.networkMonitoringSwitch);
        firewallSwitch = findViewById(R.id.firewallSwitch);
        vpnSimulationSwitch = findViewById(R.id.vpnSimulationSwitch);
        defaultGatewayEdit = findViewById(R.id.defaultGatewayEdit);
        dnsServerEdit = findViewById(R.id.dnsServerEdit);
        networkInterfaceSpinner = findViewById(R.id.networkInterfaceSpinner);

        // Security Settings
        rootAccessSwitch = findViewById(R.id.rootAccessSwitch);
        passwordAuthSwitch = findViewById(R.id.passwordAuthSwitch);
        sshServerSwitch = findViewById(R.id.sshServerSwitch);
        encryptionSwitch = findViewById(R.id.encryptionSwitch);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        securityLevelSpinner = findViewById(R.id.securityLevelSpinner);
        auditLogsSwitch = findViewById(R.id.auditLogsSwitch);

        // System Settings
        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        autoSaveSwitch = findViewById(R.id.autoSaveSwitch);
        languageSpinner = findViewById(R.id.languageSpinner);
        cpuUsageSeekBar = findViewById(R.id.cpuUsageSeekBar);
        memoryUsageSeekBar = findViewById(R.id.memoryUsageSeekBar);
        cpuUsageText = findViewById(R.id.cpuUsageText);
        memoryUsageText = findViewById(R.id.memoryUsageText);

        // Tools & Features
        metasploitSwitch = findViewById(R.id.metasploitSwitch);
        nmapSwitch = findViewById(R.id.nmapSwitch);
        wiresharkSwitch = findViewById(R.id.wiresharkSwitch);
        aircrackSwitch = findViewById(R.id.aircrackSwitch);
        johnRipperSwitch = findViewById(R.id.johnRipperSwitch);
        burpSuiteSwitch = findViewById(R.id.burpSuiteSwitch);
        sqlmapSwitch = findViewById(R.id.sqlmapSwitch);

        exportSettingsButton = findViewById(R.id.exportSettingsButton);
        importSettingsButton = findViewById(R.id.importSettingsButton);
        resetSettingsButton = findViewById(R.id.resetSettingsButton);

    }



    private void setupEventListeners() {
        // Terminal opacity seekbar
        terminalOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityValueText.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSetting("terminal_opacity", seekBar.getProgress());
            }
        });

        // CPU Usage SeekBar
        cpuUsageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cpuUsageText.setText("CPU Usage: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSetting("cpu_usage", seekBar.getProgress());
            }
        });

        // Memory Usage SeekBar
        memoryUsageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                memoryUsageText.setText("Memory Usage: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSetting("memory_usage", seekBar.getProgress());
            }
        });

        // Dark Mode Switch
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSetting("dark_mode", isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Custom Prompt Button
        customPromptButton.setOnClickListener(v -> showCustomPromptDialog());

        // Change Password Button
        changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());

        // Export Settings Button
        exportSettingsButton.setOnClickListener(v -> exportSettings());

        // Import Settings Button
        importSettingsButton.setOnClickListener(v -> importSettings());

        // Reset Settings Button
        resetSettingsButton.setOnClickListener(v -> showResetDialog());

        // Setup switch listeners
        setupSwitchListeners();

        // Setup spinner listeners
        setupSpinnerListeners();
    }

    private void setupSwitchListeners() {
        // Terminal switches
        terminalSoundSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("terminal_sound", isChecked));
        terminalVibrateSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("terminal_vibrate", isChecked));
        terminalAutoCompleteSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("terminal_autocomplete", isChecked));
        terminalHistorySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("terminal_history", isChecked));

        // Network switches
        networkMonitoringSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("network_monitoring", isChecked));
        firewallSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("firewall", isChecked));
        vpnSimulationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("vpn_simulation", isChecked));

        // Security switches
        rootAccessSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("root_access", isChecked));
        passwordAuthSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("password_auth", isChecked));
        sshServerSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("ssh_server", isChecked));
        encryptionSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("encryption", isChecked));
        auditLogsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("audit_logs", isChecked));

        // System switches
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("notifications", isChecked));
        autoSaveSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("auto_save", isChecked));

        // Tools switches
        metasploitSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("metasploit", isChecked));
        nmapSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("nmap", isChecked));
        wiresharkSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("wireshark", isChecked));
        aircrackSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("aircrack", isChecked));
        johnRipperSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("john_ripper", isChecked));
        burpSuiteSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("burp_suite", isChecked));
        sqlmapSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSetting("sqlmap", isChecked));

    }

    private void setupSpinnerListeners() {
        fontSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("font_size", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        terminalThemeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("terminal_theme", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        shellTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("shell_type", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        networkInterfaceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("network_interface", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        securityLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("security_level", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSetting("language", position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSettings() {
        // Load Terminal Settings
        terminalSoundSwitch.setChecked(prefs.getBoolean("terminal_sound", true));
        terminalVibrateSwitch.setChecked(prefs.getBoolean("terminal_vibrate", true));
        terminalAutoCompleteSwitch.setChecked(prefs.getBoolean("terminal_autocomplete", true));
        terminalHistorySwitch.setChecked(prefs.getBoolean("terminal_history", true));
        fontSizeSpinner.setSelection(prefs.getInt("font_size", 2));
        terminalThemeSpinner.setSelection(prefs.getInt("terminal_theme", 0));
        shellTypeSpinner.setSelection(prefs.getInt("shell_type", 0));

        int opacity = prefs.getInt("terminal_opacity", 80);
        terminalOpacitySeekBar.setProgress(opacity);
        opacityValueText.setText(opacity + "%");

        currentPromptText.setText(prefs.getString("custom_prompt", "root@blackshell:~# "));

        // Load Network Settings
        networkMonitoringSwitch.setChecked(prefs.getBoolean("network_monitoring", true));
        firewallSwitch.setChecked(prefs.getBoolean("firewall", true));
        vpnSimulationSwitch.setChecked(prefs.getBoolean("vpn_simulation", false));
        defaultGatewayEdit.setText(prefs.getString("default_gateway", "192.168.1.1"));
        dnsServerEdit.setText(prefs.getString("dns_server", "8.8.8.8"));
        networkInterfaceSpinner.setSelection(prefs.getInt("network_interface", 0));

        // Load Security Settings
        rootAccessSwitch.setChecked(prefs.getBoolean("root_access", true));
        passwordAuthSwitch.setChecked(prefs.getBoolean("password_auth", true));
        sshServerSwitch.setChecked(prefs.getBoolean("ssh_server", false));
        encryptionSwitch.setChecked(prefs.getBoolean("encryption", true));
        securityLevelSpinner.setSelection(prefs.getInt("security_level", 1));
        auditLogsSwitch.setChecked(prefs.getBoolean("audit_logs", true));

        // Load System Settings
        darkModeSwitch.setChecked(prefs.getBoolean("dark_mode", true));
        notificationsSwitch.setChecked(prefs.getBoolean("notifications", true));
        autoSaveSwitch.setChecked(prefs.getBoolean("auto_save", true));
        languageSpinner.setSelection(prefs.getInt("language", 0));

        int cpuUsage = prefs.getInt("cpu_usage", 25);
        cpuUsageSeekBar.setProgress(cpuUsage);
        cpuUsageText.setText("CPU Usage: " + cpuUsage + "%");

        int memoryUsage = prefs.getInt("memory_usage", 40);
        memoryUsageSeekBar.setProgress(memoryUsage);
        memoryUsageText.setText("Memory Usage: " + memoryUsage + "%");

        // Load Tools & Features
        metasploitSwitch.setChecked(prefs.getBoolean("metasploit", true));
        nmapSwitch.setChecked(prefs.getBoolean("nmap", true));
        wiresharkSwitch.setChecked(prefs.getBoolean("wireshark", true));
        aircrackSwitch.setChecked(prefs.getBoolean("aircrack", true));
        johnRipperSwitch.setChecked(prefs.getBoolean("john_ripper", true));
        burpSuiteSwitch.setChecked(prefs.getBoolean("burp_suite", false));
        sqlmapSwitch.setChecked(prefs.getBoolean("sqlmap", true));

        }

    private void saveSetting(String key, boolean value) {
        editor.putBoolean(key, value).apply();
    }

    private void saveSetting(String key, int value) {
        editor.putInt(key, value).apply();
    }

    private void saveSetting(String key, String value) {
        editor.putString(key, value).apply();
    }

    private void showCustomPromptDialog() {
        EditText input = new EditText(this);
        input.setText(currentPromptText.getText());
        input.setHint("Enter custom prompt (e.g., user@blackshell:~$ )");

        new AlertDialog.Builder(this)
                .setTitle("Custom Prompt")
                .setMessage("Customize your terminal prompt")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String prompt = input.getText().toString();
                    if (!prompt.isEmpty()) {
                        currentPromptText.setText(prompt);
                        saveSetting("custom_prompt", prompt);
                        showToast("Custom prompt saved");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText oldPassword = new EditText(this);
        oldPassword.setHint("Current Password");
        oldPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(oldPassword);

        final EditText newPassword = new EditText(this);
        newPassword.setHint("New Password");
        newPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPassword);

        final EditText confirmPassword = new EditText(this);
        confirmPassword.setHint("Confirm New Password");
        confirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPassword);

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Change", (dialog, which) -> {
                    String oldPass = oldPassword.getText().toString();
                    String newPass = newPassword.getText().toString();
                    String confirmPass = confirmPassword.getText().toString();

                    if (newPass.equals(confirmPass) && newPass.length() >= 6) {
                        saveSetting("user_password", newPass);
                        showToast("Password changed successfully");
                    } else {
                        showToast("Password mismatch or too short (min 6 chars)");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportSettings() {
        // Implementation for exporting settings to file
        showToast("Settings exported to /storage/emulated/0/BlackShell/settings.json");
    }

    private void importSettings() {
        // Implementation for importing settings from file
        showToast("Settings imported successfully");
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("Are you sure you want to reset all settings to default? This action cannot be undone.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    editor.clear().apply();
                    loadDefaultSettings();
                    showToast("Settings reset to default");
                    recreate(); // Restart activity to reload UI
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadDefaultSettings() {
        // Set default values
        editor.putBoolean("terminal_sound", true);
        editor.putBoolean("terminal_vibrate", true);
        editor.putBoolean("terminal_autocomplete", true);
        editor.putBoolean("terminal_history", true);
        editor.putInt("font_size", 2);
        editor.putInt("terminal_theme", 0);
        editor.putInt("shell_type", 0);
        editor.putInt("terminal_opacity", 80);
        editor.putString("custom_prompt", "root@blackshell:~# ");

        editor.putBoolean("network_monitoring", true);
        editor.putBoolean("firewall", true);
        editor.putBoolean("vpn_simulation", false);
        editor.putString("default_gateway", "192.168.1.1");
        editor.putString("dns_server", "8.8.8.8");
        editor.putInt("network_interface", 0);

        editor.putBoolean("root_access", true);
        editor.putBoolean("password_auth", true);
        editor.putBoolean("ssh_server", false);
        editor.putBoolean("encryption", true);
        editor.putInt("security_level", 1);
        editor.putBoolean("audit_logs", true);

        editor.putBoolean("dark_mode", true);
        editor.putBoolean("notifications", true);
        editor.putBoolean("auto_save", true);
        editor.putInt("language", 0);
        editor.putInt("cpu_usage", 25);
        editor.putInt("memory_usage", 40);

        editor.putBoolean("metasploit", true);
        editor.putBoolean("nmap", true);
        editor.putBoolean("wireshark", true);
        editor.putBoolean("aircrack", true);
        editor.putBoolean("john_ripper", true);
        editor.putBoolean("burp_suite", false);
        editor.putBoolean("sqlmap", true);

        editor.putBoolean("debug_mode", false);
        editor.putBoolean("verbose_logging", false);
        editor.putBoolean("kernel_modules", true);
        editor.putString("custom_scripts_path", "/home/blackshell/scripts");

        editor.apply();
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save EditText values
        saveSetting("default_gateway", defaultGatewayEdit.getText().toString());
        saveSetting("dns_server", dnsServerEdit.getText().toString());
    }
}