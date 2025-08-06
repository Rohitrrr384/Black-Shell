package com.example.linuxsimulator.terminal;


import com.example.linuxsimulator.SSHSimulator;

public class SSHCommandProcessor {
    private SSHSimulator sshSimulator;
    private String currentSSHSession = null;
    private boolean inSSHMode = false;

    public SSHCommandProcessor() {
        sshSimulator = SSHSimulator.getInstance();
    }

    /**
     * Add these cases to your existing switch statement in your command processor
     */
    public String processCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "";
        }

        String trimmedCommand = command.trim();
        String[] parts = trimmedCommand.split("\\s+");
        String baseCommand = parts[0].toLowerCase();

        // If we're in SSH mode, process commands remotely
        if (inSSHMode && currentSSHSession != null) {
            String result = processSSHModeCommand(trimmedCommand);
            return result;
        }

        // Regular command processing
        switch (baseCommand) {
            case "ssh":
                return handleSSHCommand(trimmedCommand);

            case "ssh-list":
            case "sshlist":
                return sshSimulator.listAvailableDevices();

            case "ssh-sessions":
            case "sshsessions":
                return sshSimulator.listActiveSessions();

            case "ssh-disconnect":
            case "sshdisconnect":
                return handleSSHDisconnect(parts);

            case "ssh-add-device":
            case "sshadddevice":
                return handleAddDevice(parts);

            case "ssh-toggle-device":
            case "sshtoggledevice":
                return handleToggleDevice(parts);

            default:
                return "Unknown command: " + baseCommand;
        }
    }

    private String processSSHModeCommand(String command) {
        if (command.equals("exit") || command.equals("logout")) {
            String result = sshSimulator.disconnectSSH(currentSSHSession);
            currentSSHSession = null;
            inSSHMode = false;
            return result + "\nReturned to local terminal.";
        }

        return sshSimulator.processRemoteCommand(currentSSHSession, command);
    }

    private String handleSSHCommand(String command) {
        String result = sshSimulator.processSSHCommand(command);

        // Check if connection was successful (look for session ID in result)
        if (result.contains("[SSH-") && result.contains("]")) {
            // Extract session ID from the result
            int startIndex = result.indexOf("[SSH-") + 5;
            int endIndex = result.indexOf("]", startIndex);
            if (endIndex > startIndex) {
                currentSSHSession = result.substring(startIndex, endIndex);
                inSSHMode = true;
            }
        }

        return result;
    }

    private String handleSSHDisconnect(String[] parts) {
        if (parts.length < 2) {
            if (currentSSHSession != null) {
                String result = sshSimulator.disconnectSSH(currentSSHSession);
                currentSSHSession = null;
                inSSHMode = false;
                return result;
            } else {
                return "No active SSH session to disconnect.";
            }
        } else {
            String sessionId = parts[1];
            String result = sshSimulator.disconnectSSH(sessionId);
            if (sessionId.equals(currentSSHSession)) {
                currentSSHSession = null;
                inSSHMode = false;
            }
            return result;
        }
    }

    private String handleAddDevice(String[] parts) {
        if (parts.length < 6) {
            return "Usage: ssh-add-device <deviceId> <hostname> <ipAddress> <username> <password>";
        }

        String deviceId = parts[1];
        String hostname = parts[2];
        String ipAddress = parts[3];
        String username = parts[4];
        String password = parts[5];

        sshSimulator.addDevice(deviceId, hostname, ipAddress, username, password);
        return "Device added successfully: " + deviceId + " (" + hostname + ")";
    }

    private String handleToggleDevice(String[] parts) {
        if (parts.length < 3) {
            return "Usage: ssh-toggle-device <deviceId> <online|offline>";
        }

        String deviceId = parts[1];
        boolean online = parts[2].equalsIgnoreCase("online");

        sshSimulator.setDeviceStatus(deviceId, online);
        return "Device " + deviceId + " is now " + (online ? "online" : "offline");
    }

    /**
     * Call this method to get the current prompt for your terminal
     */
    public String getCurrentPrompt() {
        if (inSSHMode && currentSSHSession != null) {
            SSHSimulator.SSHSession session = sshSimulator.getSession(currentSSHSession);
            if (session != null) {
                return "[SSH-" + currentSSHSession + "] " +
                        session.connectedUser + "@" + session.targetDevice.hostname +
                        ":" + session.targetDevice.currentDirectory.replace("/home/" + session.connectedUser, "~") + "$ ";
            }
        }
        return "user@localhost:~$ "; // Default local prompt
    }

    /**
     * Check if currently in SSH mode
     */
    public boolean isInSSHMode() {
        return inSSHMode;
    }

    /**
     * Get current SSH session ID
     */
    public String getCurrentSSHSession() {
        return currentSSHSession;
    }

    /**
     * Force disconnect current SSH session (useful for error handling)
     */
    public void forceDisconnectCurrentSession() {
        if (currentSSHSession != null) {
            sshSimulator.disconnectSSH(currentSSHSession);
            currentSSHSession = null;
            inSSHMode = false;
        }
    }
}