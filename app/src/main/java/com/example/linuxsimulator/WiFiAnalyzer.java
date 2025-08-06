package com.example.linuxsimulator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import androidx.core.app.ActivityCompat;

import java.util.Collections;
import java.util.List;

public class WiFiAnalyzer {
    public final WifiManager wifiManager;
    public final Context context;

    public WiFiAnalyzer(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public String scanWifiNetworks() {
        if (!wifiManager.isWifiEnabled()) {
            return "Error: WiFi is disabled. Please enable WiFi first.";
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission not granted to scan WiFi networks.";
        }

        List<ScanResult> scanResults = wifiManager.getScanResults();

        if (scanResults.isEmpty()) {
            return "No networks found. Try scanning again.";
        }

        Collections.sort(scanResults, (a, b) -> Integer.compare(b.level, a.level));

        StringBuilder result = new StringBuilder();
        result.append(String.format("%-20s %-8s %-10s %-8s %-15s\n",
                "SSID", "CHANNEL", "FREQ", "SIGNAL", "SECURITY"));
        result.append("------------------------------------------------\n");

        for (ScanResult network : scanResults) {
            String ssid = network.SSID.length() > 20 ? network.SSID.substring(0, 17) + "..." : network.SSID;
            String security = getSecurityType(network);
            int channel = getChannel(network.frequency);
            String freq = network.frequency >= 5000 ? "5 GHz" : "2.4 GHz";

            result.append(String.format("%-20s %-8d %-10s %-8d %-15s\n",
                    ssid, channel, freq, network.level, security));
        }

        return result.toString();
    }

    private String getSecurityType(ScanResult result) {
        if (result.capabilities.contains("WPA2")) return "WPA2";
        if (result.capabilities.contains("WPA")) return "WPA";
        if (result.capabilities.contains("WEP")) return "WEP";
        return "Open";
    }

    private int getChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) {
            return (frequency - 2412) / 5 + 1;
        } else if (frequency >= 5170 && frequency <= 5825) {
            return (frequency - 5170) / 5 + 34;
        }
        return -1;
    }
}
