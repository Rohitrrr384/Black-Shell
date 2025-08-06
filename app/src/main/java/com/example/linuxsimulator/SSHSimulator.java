package com.example.linuxsimulator;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SSHSimulator {
    private static SSHSimulator instance;
    private Map<String, Device> availableDevices;
    private Map<String, SSHSession> activeSessions;
    private String currentDeviceId;

    // Device class to represent different devices
    public static class Device {
        public String deviceId;
        public String hostname;
        public String ipAddress;
        public String username;
        public String password;
        public boolean isOnline;
        public Map<String, String> fileSystem;
        public String currentDirectory;

        public Device(String deviceId, String hostname, String ipAddress, String username, String password) {
            this.deviceId = deviceId;
            this.hostname = hostname;
            this.ipAddress = ipAddress;
            this.username = username;
            this.password = password;
            this.isOnline = true;
            this.currentDirectory = "/home/" + username;
            initializeFileSystem();
        }

        private void initializeFileSystem() {
            fileSystem = new HashMap<>();
            fileSystem.put("/", "drwxr-xr-x root root");
            fileSystem.put("/home", "drwxr-xr-x root root");
            fileSystem.put("/home/" + username, "drwxr-xr-x " + username + " " + username);
            fileSystem.put("/home/" + username + "/documents", "drwxr-xr-x " + username + " " + username);
            fileSystem.put("/home/" + username + "/documents/readme.txt", "-rw-r--r-- " + username + " " + username + " Welcome to " + hostname);
            fileSystem.put("/etc", "drwxr-xr-x root root");
            fileSystem.put("/etc/passwd", "-rw-r--r-- root root users:x:0:0:root:/root:/bin/bash");
        }
    }

    // SSH Session class to manage connections
    public static class SSHSession {
        public String sessionId;
        public Device targetDevice;
        public String connectedUser;
        public boolean isAuthenticated;
        public long connectionTime;

        public SSHSession(String sessionId, Device targetDevice) {
            this.sessionId = sessionId;
            this.targetDevice = targetDevice;
            this.isAuthenticated = false;
            this.connectionTime = System.currentTimeMillis();
        }
    }

    private SSHSimulator() {
        availableDevices = new ConcurrentHashMap<>();
        activeSessions = new ConcurrentHashMap<>();
        currentDeviceId = "local_device";
        initializeDefaultDevices();
    }

    public static SSHSimulator getInstance() {
        if (instance == null) {
            instance = new SSHSimulator();
        }
        return instance;
    }

    private void initializeDefaultDevices() {
        // Local device
        addDevice("local_device", "localhost", "127.0.0.1", "user", "password");

        // Remote devices for simulation
        addDevice("server1", "ubuntu-server", "192.168.1.100", "admin", "admin123");
        addDevice("server2", "centos-box", "192.168.1.101", "root", "root123");
        addDevice("workstation", "dev-machine", "192.168.1.102", "developer", "dev456");
    }

    public void addDevice(String deviceId, String hostname, String ipAddress, String username, String password) {
        Device device = new Device(deviceId, hostname, ipAddress, username, password);
        availableDevices.put(deviceId, device);
    }

    public String processSSHCommand(String command) {
        String[] parts = command.trim().split("\\s+");

        if (parts.length < 2) {
            return "ssh: usage: ssh [user@]hostname [command]";
        }

        String target = parts[1];
        String username = null;
        String hostname = null;

        // Parse user@hostname format
        if (target.contains("@")) {
            String[] userHost = target.split("@");
            username = userHost[0];
            hostname = userHost[1];
        } else {
            hostname = target;
        }

        // Find target device
        Device targetDevice = findDeviceByHostnameOrIP(hostname);
        if (targetDevice == null) {
            return "ssh: Could not resolve hostname " + hostname + ": Name or service not known";
        }

        if (!targetDevice.isOnline) {
            return "ssh: connect to host " + hostname + " port 22: Connection refused";
        }

        // If username not specified, use device's default username
        if (username == null) {
            username = targetDevice.username;
        }

        // Create new SSH session
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        SSHSession session = new SSHSession(sessionId, targetDevice);

        // Simulate password authentication
        return initiateSSHConnection(session, username);
    }

    private Device findDeviceByHostnameOrIP(String identifier) {
        for (Device device : availableDevices.values()) {
            if (device.hostname.equals(identifier) || device.ipAddress.equals(identifier)) {
                return device;
            }
        }
        return null;
    }

    private String initiateSSHConnection(SSHSession session, String username) {
        Device device = session.targetDevice;

        // Simulate authentication
        if (username.equals(device.username)) {
            session.isAuthenticated = true;
            session.connectedUser = username;
            activeSessions.put(session.sessionId, session);

            return "Connected to " + device.hostname + " (" + device.ipAddress + ")\n" +
                    "Welcome to " + device.hostname + "\n" +
                    "Last login: " + new Date() + "\n" +
                    "[SSH-" + session.sessionId + "] " + username + "@" + device.hostname + ":~$ ";
        } else {
            return "Permission denied (publickey,password).";
        }
    }

    public String processRemoteCommand(String sessionId, String command) {
        SSHSession session = activeSessions.get(sessionId);
        if (session == null || !session.isAuthenticated) {
            return "No active SSH session found.";
        }

        Device device = session.targetDevice;
        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                return processLsCommand(device, parts);
            case "pwd":
                return device.currentDirectory;
            case "cd":
                return processCdCommand(device, parts);
            case "cat":
                return processCatCommand(device, parts);
            case "whoami":
                return session.connectedUser;
            case "hostname":
                return device.hostname;
            case "exit":
                return disconnectSSH(sessionId);
            case "uname":
                return "Linux " + device.hostname + " 5.4.0-generic #47-Ubuntu SMP";
            case "ps":
                return "PID TTY TIME CMD\n1234 pts/0 00:00:01 bash\n5678 pts/0 00:00:00 ps";
            case "df":
                return "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sda1 20971520 5242880 15728640 25% /";
            case "uptime":
                long uptime = (System.currentTimeMillis() - session.connectionTime) / 1000;
                return "up " + uptime + " seconds, 1 user, load average: 0.15, 0.10, 0.05";
            default:
                return "bash: " + cmd + ": command not found";
        }
    }

    private String processLsCommand(Device device, String[] parts) {
        String targetDir = device.currentDirectory;
        if (parts.length > 1 && !parts[1].startsWith("-")) {
            targetDir = parts[1];
            if (!targetDir.startsWith("/")) {
                targetDir = device.currentDirectory + "/" + targetDir;
            }
        }

        StringBuilder result = new StringBuilder();
        for (String path : device.fileSystem.keySet()) {
            if (path.startsWith(targetDir + "/") && !path.equals(targetDir)) {
                String relativePath = path.substring(targetDir.length() + 1);
                if (!relativePath.contains("/")) {
                    result.append(relativePath).append("\n");
                }
            }
        }

        return result.length() > 0 ? result.toString() : "";
    }

    private String processCdCommand(Device device, String[] parts) {
        if (parts.length < 2) {
            device.currentDirectory = "/home/" + device.username;
            return "";
        }

        String targetDir = parts[1];
        if (targetDir.equals("..")) {
            if (!device.currentDirectory.equals("/")) {
                int lastSlash = device.currentDirectory.lastIndexOf("/");
                device.currentDirectory = lastSlash > 0 ? device.currentDirectory.substring(0, lastSlash) : "/";
            }
        } else if (targetDir.equals("~")) {
            device.currentDirectory = "/home/" + device.username;
        } else if (targetDir.startsWith("/")) {
            if (device.fileSystem.containsKey(targetDir)) {
                device.currentDirectory = targetDir;
            } else {
                return "cd: " + targetDir + ": No such file or directory";
            }
        } else {
            String fullPath = device.currentDirectory + "/" + targetDir;
            if (device.fileSystem.containsKey(fullPath)) {
                device.currentDirectory = fullPath;
            } else {
                return "cd: " + targetDir + ": No such file or directory";
            }
        }

        return "";
    }

    private String processCatCommand(Device device, String[] parts) {
        if (parts.length < 2) {
            return "cat: missing file operand";
        }

        String filename = parts[1];
        String fullPath = filename.startsWith("/") ? filename : device.currentDirectory + "/" + filename;

        String fileInfo = device.fileSystem.get(fullPath);
        if (fileInfo == null) {
            return "cat: " + filename + ": No such file or directory";
        }

        if (fileInfo.startsWith("d")) {
            return "cat: " + filename + ": Is a directory";
        }

        // Return simulated file content
        if (fullPath.endsWith("readme.txt")) {
            return "Welcome to " + device.hostname + "!\nThis is a simulated Linux environment.";
        } else if (fullPath.endsWith("passwd")) {
            return device.username + ":x:1000:1000:" + device.username + ":/home/" + device.username + ":/bin/bash";
        }

        return "Sample file content for " + filename;
    }

    public String disconnectSSH(String sessionId) {
        SSHSession session = activeSessions.remove(sessionId);
        if (session != null) {
            return "Connection to " + session.targetDevice.hostname + " closed.";
        }
        return "No active session to disconnect.";
    }

    public String listAvailableDevices() {
        StringBuilder result = new StringBuilder("Available devices:\n");
        for (Device device : availableDevices.values()) {
            String status = device.isOnline ? "online" : "offline";
            result.append(String.format("%-15s %-20s %-15s [%s]\n",
                    device.deviceId, device.hostname, device.ipAddress, status));
        }
        return result.toString();
    }

    public String listActiveSessions() {
        if (activeSessions.isEmpty()) {
            return "No active SSH sessions.";
        }

        StringBuilder result = new StringBuilder("Active SSH sessions:\n");
        for (SSHSession session : activeSessions.values()) {
            result.append(String.format("Session: %s -> %s@%s\n",
                    session.sessionId, session.connectedUser, session.targetDevice.hostname));
        }
        return result.toString();
    }

    public void setDeviceStatus(String deviceId, boolean online) {
        Device device = availableDevices.get(deviceId);
        if (device != null) {
            device.isOnline = online;
        }
    }

    public boolean hasActiveSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    public SSHSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
}