package com.example.linuxsimulator.terminal;

import android.content.Context;

import com.example.linuxsimulator.GitSimulator;
import com.example.linuxsimulator.TextEditorActivity;
import com.example.linuxsimulator.WiFiAnalyzer;
import com.example.linuxsimulator.data.FileSystemManager;
import com.example.linuxsimulator.data.FileItem;
import com.example.linuxsimulator.TerminalActivity;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import android.content.Context;
import android.content.Intent;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommandProcessor {
    private WiFiAnalyzer wifiAnalyzer;
    private TerminalActivity terminal;
    private FileSystemManager fsManager;
    private Context context;
    private Map<String, Process> runningProcesses;
    private int nextProcessId = 1;
    private GitSimulator gitSimulator;
    private String currentDirectory; // Or your current directory

    // SSH-related fields
    private Map<String, SSHDevice> availableDevices;
    private Map<String, SSHSession> activeSessions;
    private String currentSSHSession = null;
    private boolean inSSHMode = false;

    public interface CommandCallback {
        void onSuccess(String output);
        void onError(String error);
        void onDirectoryChanged();
    }

    // SSH Device class
    public static class SSHDevice {
        public String deviceId;
        public String hostname;
        public String ipAddress;
        public String username;
        public String password;
        public boolean isOnline;
        public Map<String, String> fileSystem;
        public String currentDirectory;

        public SSHDevice(String deviceId, String hostname, String ipAddress, String username, String password) {
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
            fileSystem.put("/var", "drwxr-xr-x root root");
            fileSystem.put("/var/log", "drwxr-xr-x root root");
            fileSystem.put("/var/log/syslog", "-rw-r--r-- root root System log file");
        }
    }

    // SSH Session class
    public static class SSHSession {
        public String sessionId;
        public SSHDevice targetDevice;
        public String connectedUser;
        public boolean isAuthenticated;
        public long connectionTime;

        public SSHSession(String sessionId, SSHDevice targetDevice) {
            this.sessionId = sessionId;
            this.targetDevice = targetDevice;
            this.isAuthenticated = false;
            this.connectionTime = System.currentTimeMillis();
        }
    }

    public CommandProcessor(TerminalActivity terminal, FileSystemManager fsManager) {
        this.terminal = terminal;
        this.fsManager = fsManager;
        this.context = terminal;
        this.wifiAnalyzer = new WiFiAnalyzer(terminal);
        this.runningProcesses = new HashMap<>();

        // Initialize SSH components
        this.availableDevices = new HashMap<>();
        this.activeSessions = new HashMap<>();
        initializeSSHDevices();
    }

    private void initializeSSHDevices() {
        // Add default SSH devices for simulation
        addSSHDevice("server1", "ubuntu-server", "192.168.1.100", "admin", "admin123");
        addSSHDevice("server2", "centos-box", "192.168.1.101", "root", "root123");
        addSSHDevice("workstation", "dev-machine", "192.168.1.102", "developer", "dev456");
        addSSHDevice("webserver", "nginx-server", "192.168.1.103", "www-data", "webpass");
        addSSHDevice("database", "mysql-db", "192.168.1.104", "dbadmin", "dbpass123");
    }

    private void addSSHDevice(String deviceId, String hostname, String ipAddress, String username, String password) {
        SSHDevice device = new SSHDevice(deviceId, hostname, ipAddress, username, password);
        availableDevices.put(deviceId, device);
    }

    private String getRepoNameFromUrl(String url) {
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public void processCommand(String input, CommandCallback callback) {
        if (input.trim().isEmpty()) {
            callback.onSuccess("");
            return;
        }

        // If we're in SSH mode, process commands remotely
        if (inSSHMode && currentSSHSession != null) {
            processSSHModeCommand(input.trim(), callback);
            return;
        }

        // Parse command and arguments
        String[] parts = parseCommand(input);
        String command = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        try {
            switch (command) {
                case "ls":
                    handleLs(args, callback);
                    break;
                case "cd":
                    handleCd(args, callback);
                    break;
                case "pwd":
                    handlePwd(callback);
                    break;
                case "mkdir":
                    handleMkdir(args, callback);
                    break;
                case "touch":
                    handleTouch(args, callback);
                    break;
                case "rm":
                    handleRm(args, callback);
                    break;
                case "cp":
                    handleCp(args, callback);
                    break;
                case "mv":
                    handleMv(args, callback);
                    break;
                case "cat":
                    handleCat(args, callback);
                    break;
                case "nano":
                case "vim":
                case "edit":
                    handleEdit(args, callback);
                    break;
                case "chmod":
                    handleChmod(args, callback);
                    break;
                case "find":
                    handleFind(args, callback);
                    break;
                case "grep":
                    handleGrep(args, callback);
                    break;
                case "ps":
                    handlePs(callback);
                    break;
                case "kill":
                    handleKill(args, callback);
                    break;
                case "clear":
                    handleClear(callback);
                    break;
                case "help":
                    handleHelp(callback);
                    break;
                case "whoami":
                    handleWhoami(callback);
                    break;
                case "date":
                    handleDate(callback);
                    break;
                case "echo":
                    handleEcho(args, callback);
                    break;
                case "history":
                    handleHistory(callback);
                    break;
                case "su":
                    handleSu(args, callback);
                    break;
                case "sudo":
                    handleSudo(Arrays.copyOfRange(parts, 1, parts.length), callback);
                    break;
                case "env":
                    handleEnv(callback);
                    break;
                case "which":
                    handleWhich(args, callback);
                    break;
                case "man":
                    handleMan(args, callback);
                    break;
                case "tree":
                    handleTree(args, callback);
                    break;
                case "head":
                    handleHead(args, callback);
                    break;
                case "tail":
                    handleTail(args, callback);
                    break;
                case "wc":
                    handleWc(args, callback);
                    break;
                case "df":
                    handleDf(callback);
                    break;
                case "free":
                    handleFree(callback);
                    break;
                case "uname":
                    handleUname(args, callback);
                    break;
                case "exit":
                    terminal.finish();
                    break;
                case "wifi-scan":
                    String scanResult = wifiAnalyzer.scanWifiNetworks();
                    callback.onSuccess(scanResult);
                    break;
                case "bpad.txt":
                    handleBpad(args, callback);
                    break;
                // SSH Commands
                case "ssh":
                    handleSSH(input.trim(), callback);
                    break;
                //case "ssh-list":
                case "ssh-list":
                    handleSSHList(callback);
                    break;
                case "ssh-sessions":
                case "sshsessions":
                    handleSSHSessions(callback);
                    break;
                case "ssh-disconnect":
                case "sshdisconnect":
                    handleSSHDisconnect(args, callback);
                    break;
                case "ssh-add":
                case "sshadd":
                    handleSSHAdd(args, callback);
                    break;
                case "ssh-toggle":
                case "sshtoggle":
                    handleSSHToggle(args, callback);
                    break;
                case "git":
                    if (args.length == 0 || args[0].equals("help")) {
                        handleGitHelp(callback);
                    } else {
                        handleGit(args, callback);  // handles specific git operations like clone, init, etc.
                    }
                    break;

                default:
                    callback.onError("Command not found: " + command + "\nType 'help' for available commands");
                    break;
            }
        } catch (Exception e) {
            callback.onError("Error executing command: " + e.getMessage());
        }
    }

    private void processSSHModeCommand(String command, CommandCallback callback) {
        if (command.equals("exit") || command.equals("logout")) {
            String result = disconnectSSH(currentSSHSession);
            currentSSHSession = null;
            inSSHMode = false;
            callback.onSuccess(result + "\nReturned to local terminal.");
            callback.onDirectoryChanged();
            return;
        }

        String result = processRemoteCommand(currentSSHSession, command);
        callback.onSuccess(result);
    }

    private void handleSSH(String fullCommand, CommandCallback callback) {
        String[] parts = fullCommand.trim().split("\\s+");

        if (parts.length < 2) {
            callback.onError("ssh: usage: ssh [user@]hostname [command]");
            return;
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
        SSHDevice targetDevice = findDeviceByHostnameOrIP(hostname);
        if (targetDevice == null) {
            callback.onError("ssh: Could not resolve hostname " + hostname + ": Name or service not known");
            return;
        }

        if (!targetDevice.isOnline) {
            callback.onError("ssh: connect to host " + hostname + " port 22: Connection refused");
            return;
        }

        // If username not specified, use device's default username
        if (username == null) {
            username = targetDevice.username;
        }

        // Create new SSH session
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        SSHSession session = new SSHSession(sessionId, targetDevice);

        // Simulate password authentication
        String result = initiateSSHConnection(session, username);
        callback.onSuccess(result);

        if (session.isAuthenticated) {
            currentSSHSession = sessionId;
            inSSHMode = true;
            callback.onDirectoryChanged();
        }
    }

    private SSHDevice findDeviceByHostnameOrIP(String identifier) {
        for (SSHDevice device : availableDevices.values()) {
            if (device.hostname.equals(identifier) || device.ipAddress.equals(identifier)) {
                return device;
            }
        }
        return null;
    }

    private String initiateSSHConnection(SSHSession session, String username) {
        SSHDevice device = session.targetDevice;

        // Simulate authentication
        if (username.equals(device.username)) {
            session.isAuthenticated = true;
            session.connectedUser = username;
            activeSessions.put(session.sessionId, session);

            return "Connected to " + device.hostname + " (" + device.ipAddress + ")\n" +
                    "Welcome to " + device.hostname + "\n" +
                    "Last login: " + new Date() + "\n" +
                    "SSH session established [" + session.sessionId + "]";
        } else {
            return "Permission denied (publickey,password).";
        }
    }

    private String processRemoteCommand(String sessionId, String command) {
        SSHSession session = activeSessions.get(sessionId);
        if (session == null || !session.isAuthenticated) {
            return "No active SSH session found.";
        }

        SSHDevice device = session.targetDevice;
        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                return processRemoteLs(device, parts);
            case "pwd":
                return device.currentDirectory;
            case "cd":
                return processRemoteCd(device, parts);
            case "cat":
                return processRemoteCat(device, parts);
            case "whoami":
                return session.connectedUser;
            case "hostname":
                return device.hostname;
            case "uname":
                return "Linux " + device.hostname + " 5.4.0-generic #47-Ubuntu SMP";
            case "ps":
                return "PID TTY TIME CMD\n1234 pts/0 00:00:01 bash\n5678 pts/0 00:00:00 ps";
            case "df":
                return "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sda1 20971520 5242880 15728640 25% /";
            case "uptime":
                long uptime = (System.currentTimeMillis() - session.connectionTime) / 1000;
                return "up " + uptime + " seconds, 1 user, load average: 0.15, 0.10, 0.05";
            case "free":
                return "total used free shared buff/cache available\nMem: 2048000 512000 1536000 0 0 1536000";
            case "date":
                return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault()).format(new Date());
            case "echo":
                return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            case "mkdir":
                return processRemoteMkdir(device, parts);
            case "touch":
                return processRemoteTouch(device, parts);
            case "rm":
                return processRemoteRm(device, parts);
            default:
                return "bash: " + cmd + ": command not found";
        }
    }

    private String processRemoteLs(SSHDevice device, String[] parts) {
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

    private String processRemoteCd(SSHDevice device, String[] parts) {
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

    private String processRemoteCat(SSHDevice device, String[] parts) {
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
            return "Welcome to " + device.hostname + "!\nThis is a simulated SSH environment.\nYou are connected remotely to this device.";
        } else if (fullPath.endsWith("passwd")) {
            return device.username + ":x:1000:1000:" + device.username + ":/home/" + device.username + ":/bin/bash";
        } else if (fullPath.endsWith("syslog")) {
            return "Aug  3 10:30:15 " + device.hostname + " kernel: [    0.000000] Linux version 5.4.0\n" +
                    "Aug  3 10:30:15 " + device.hostname + " systemd[1]: Starting SSH daemon...";
        }

        return "Sample file content for " + filename + " on " + device.hostname;
    }

    private String processRemoteMkdir(SSHDevice device, String[] parts) {
        if (parts.length < 2) {
            return "mkdir: missing operand";
        }

        String dirName = parts[1];
        String fullPath = dirName.startsWith("/") ? dirName : device.currentDirectory + "/" + dirName;
        device.fileSystem.put(fullPath, "drwxr-xr-x " + device.username + " " + device.username);
        return "";
    }

    private String processRemoteTouch(SSHDevice device, String[] parts) {
        if (parts.length < 2) {
            return "touch: missing file operand";
        }

        String fileName = parts[1];
        String fullPath = fileName.startsWith("/") ? fileName : device.currentDirectory + "/" + fileName;
        device.fileSystem.put(fullPath, "-rw-r--r-- " + device.username + " " + device.username + " Empty file");
        return "";
    }

    private String processRemoteRm(SSHDevice device, String[] parts) {
        if (parts.length < 2) {
            return "rm: missing operand";
        }

        String fileName = parts[1];
        String fullPath = fileName.startsWith("/") ? fileName : device.currentDirectory + "/" + fileName;
        if (device.fileSystem.remove(fullPath) != null) {
            return "";
        } else {
            return "rm: cannot remove '" + fileName + "': No such file or directory";
        }
    }

    private String disconnectSSH(String sessionId) {
        SSHSession session = activeSessions.remove(sessionId);
        if (session != null) {
            return "Connection to " + session.targetDevice.hostname + " closed.";
        }
        return "No active session to disconnect.";
    }

    private void handleSSHList(CommandCallback callback) {
        StringBuilder result = new StringBuilder("Available SSH devices:\n");
        result.append(String.format("%-15s %-20s %-15s %-10s %s\n", "ID", "HOSTNAME", "IP ADDRESS", "USER", "STATUS"));
        result.append("─".repeat(75)).append("\n");

        for (SSHDevice device : availableDevices.values()) {
            String status = device.isOnline ? "online" : "offline";
            result.append(String.format("%-15s %-20s %-15s %-10s %s\n",
                    device.deviceId, device.hostname, device.ipAddress, device.username, status));
        }
        callback.onSuccess(result.toString());
    }

    private void handleSSHSessions(CommandCallback callback) {
        if (activeSessions.isEmpty()) {
            callback.onSuccess("No active SSH sessions.");
            return;
        }

        StringBuilder result = new StringBuilder("Active SSH sessions:\n");
        result.append(String.format("%-10s %-15s %-20s %s\n", "SESSION", "USER", "HOSTNAME", "CONNECTED"));
        result.append("─".repeat(60)).append("\n");

        for (SSHSession session : activeSessions.values()) {
            String current = session.sessionId.equals(currentSSHSession) ? " [CURRENT]" : "";
            result.append(String.format("%-10s %-15s %-20s %s%s\n",
                    session.sessionId, session.connectedUser, session.targetDevice.hostname,
                    new SimpleDateFormat("HH:mm:ss").format(new Date(session.connectionTime)), current));
        }
        callback.onSuccess(result.toString());
    }

    private void handleSSHDisconnect(String[] args, CommandCallback callback) {
        if (args.length < 1) {
            if (currentSSHSession != null) {
                String result = disconnectSSH(currentSSHSession);
                currentSSHSession = null;
                inSSHMode = false;
                callback.onSuccess(result);
                callback.onDirectoryChanged();
            } else {
                callback.onError("No active SSH session to disconnect.");
            }
        } else {
            String sessionId = args[0];
            String result = disconnectSSH(sessionId);
            if (sessionId.equals(currentSSHSession)) {
                currentSSHSession = null;
                inSSHMode = false;
                callback.onDirectoryChanged();
            }
            callback.onSuccess(result);
        }
    }

    private void handleSSHAdd(String[] args, CommandCallback callback) {
        if (args.length < 5) {
            callback.onError("Usage: ssh-add <deviceId> <hostname> <ipAddress> <username> <password>");
            return;
        }

        String deviceId = args[0];
        String hostname = args[1];
        String ipAddress = args[2];
        String username = args[3];
        String password = args[4];

        addSSHDevice(deviceId, hostname, ipAddress, username, password);
        callback.onSuccess("✓ SSH device added: " + deviceId + " (" + hostname + ")");
    }

    private void handleSSHToggle(String[] args, CommandCallback callback) {
        if (args.length < 2) {
            callback.onError("Usage: ssh-toggle <deviceId> <online|offline>");
            return;
        }

        String deviceId = args[0];
        boolean online = args[1].equalsIgnoreCase("online");

        SSHDevice device = availableDevices.get(deviceId);
        if (device != null) {
            device.isOnline = online;
            callback.onSuccess("✓ Device " + deviceId + " is now " + (online ? "online" : "offline"));
        } else {
            callback.onError("Device not found: " + deviceId);
        }
    }

    // Method to get current prompt (call this from TerminalActivity)
    public String getCurrentPrompt() {
        if (inSSHMode && currentSSHSession != null) {
            SSHSession session = activeSessions.get(currentSSHSession);
            if (session != null) {
                String shortPath = session.targetDevice.currentDirectory.replace("/home/" + session.connectedUser, "~");
                return session.connectedUser + "@" + session.targetDevice.hostname + ":" + shortPath + "$ ";
            }
        }
        return terminal.getCurrentUser() + "@localhost:" + fsManager.getCurrentDirectory().replace("/storage/emulated/0", "~") + "$ ";
    }

    // Check if currently in SSH mode
    public boolean isInSSHMode() {
        return inSSHMode;
    }

    private void handleGitHelp(CommandCallback callback) {
        String helpText =
                "usage: git [--version] [--help] <command> [<args>]\n\n" +
                        "These are common Git commands used in your terminal:\n\n" +
                        "init       Create an empty Git repository\n" +
                        "clone      Clone a repository into a new directory\n" +
                        "status     Show the working tree status\n" +
                        "log        Show commit logs\n" +
                        "add        Add file contents to the index\n" +
                        "commit     Record changes to the repository\n" +
                        "push       Update remote refs along with associated objects\n" +
                        "pull       Fetch from and integrate with another repository\n" +
                        "remote     Manage set of tracked repositories\n" +
                        "branch     List, create, or delete branches\n" +
                        "checkout   Switch branches or restore working tree files\n" +
                        "\nUse 'git <command> --help' for more information on a specific command.";

        callback.onSuccess(helpText);
    }

    private void handleBpad(String[] args, CommandCallback callback){
        if (args.length == 0) {
            callback.onError("bpad: missing operand");
        }
        //create a new intent to launch the BpadActivity
        Intent intent = new Intent(context, TextEditorActivity.class);
        //start the activity
        context.startActivity(intent);

    }

    private void handleGit(String[] args, CommandCallback callback) {
        if (args.length < 1) {
            callback.onError("git: missing subcommand (init, clone, status, log)");
            return;
        }

        String subCommand = args[0]; // user says: "git init" → ["init"]
        switch (subCommand) {
            case "init":
                gitInit(callback);
                break;
            case "clone":
                if (args.length < 2) {
                    callback.onError("Usage: git clone <repo-url>");
                } else {
                    gitClone(args[1], callback);
                }
                break;
            case "status":
                gitStatus(callback);
                break;
            case "log":
                gitLog(callback);
                break;
            default:
                callback.onError("Unknown git subcommand: " + subCommand);
        }
    }


    private void gitInit(CommandCallback callback) {
        try {
            Git.init()
                    .setDirectory(new File(fsManager.getAbsoluteCurrentDirectory()))
                    .call();
            callback.onSuccess("✓ Git repository initialized.");
        } catch (Exception e) {
            callback.onError("✗ Failed to init repo: " + e.getMessage());
        }
    }



    private void gitClone(String repoUrl, CommandCallback callback) {
        try {
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(fsManager.getAbsoluteCurrentDirectory()))
                    .call();
            callback.onSuccess("✓ Repository cloned successfully.");
        } catch (Exception e) {
            callback.onError("✗ Clone failed: " + e.getMessage());
        }
    }


    private void gitStatus(CommandCallback callback) {
        try {
            Git git = Git.open(new File(fsManager.getAbsoluteCurrentDirectory()));
            Status status = git.status().call();

            StringBuilder sb = new StringBuilder();
            if (!status.getUntracked().isEmpty()) {
                sb.append("Untracked files:\n");
                for (String file : status.getUntracked()) {
                    sb.append("- ").append(file).append("\n");
                }
            }

            if (!status.getModified().isEmpty()) {
                sb.append("Modified files:\n");
                for (String file : status.getModified()) {
                    sb.append("- ").append(file).append("\n");
                }
            }

            if (sb.length() == 0) {
                sb.append("✓ Working directory clean.");
            }

            callback.onSuccess(sb.toString());

        } catch (Exception e) {
            callback.onError("✗ Failed to get git status: " + e.getMessage());
        }
    }


    private void gitLog(CommandCallback callback) {
        try {
            Git git = Git.open(new File(fsManager.getAbsoluteCurrentDirectory()));
            Iterable<RevCommit> logs = git.log().call();

            StringBuilder logOutput = new StringBuilder();
            for (RevCommit commit : logs) {
                logOutput.append("Commit: ").append(commit.getName()).append("\n");
                logOutput.append("Author: ").append(commit.getAuthorIdent().getName()).append("\n");
                logOutput.append("Message: ").append(commit.getShortMessage()).append("\n");
                logOutput.append("------\n");
            }

            if (logOutput.length() == 0) {
                logOutput.append("No commits yet.");
            }

            callback.onSuccess(logOutput.toString());

        } catch (Exception e) {
            callback.onError("✗ Failed to get git log: " + e.getMessage());
        }
    }



    private String[] parseCommand(String input) {
        // Simple command parsing - could be enhanced for complex shell features
        return input.trim().split("\\s+");
    }

    private void handleLs(String[] args, CommandCallback callback) {
        boolean longFormat = false;
        boolean showAll = false;
        boolean showHidden = false;
        String targetDir = null;

        // Parse arguments
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("l")) longFormat = true;
                if (arg.contains("a")) showAll = true;
                if (arg.contains("h")) showHidden = true;
            } else {
                targetDir = arg;
            }
        }

        try {
            List<FileItem> files;
            if (targetDir != null) {
                String currentDir = fsManager.getCurrentDirectory();
                fsManager.changeDirectory(targetDir);
                files = fsManager.listFiles();
                fsManager.changeDirectory(currentDir);
            } else {
                files = fsManager.listFiles();
            }

            if (files.isEmpty()) {
                callback.onSuccess("");
                return;
            }

            StringBuilder output = new StringBuilder();

            if (longFormat) {
                // Long format output
                for (FileItem file : files) {
                    if (!showAll && file.getName().startsWith(".")) continue;

                    String permissions = file.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                    String size = file.isDirectory() ? "4096" : String.valueOf(file.getSize());
                    String date = new SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                            .format(new Date(file.getLastModified()));

                    output.append(String.format("%s  1 %s %s %8s %s %s\n",
                            permissions,
                            terminal.getCurrentUser(),
                            terminal.getCurrentUser(),
                            size,
                            date,
                            file.getName()));
                }
            } else {
                // Simple format
                List<String> fileNames = new ArrayList<>();
                for (FileItem file : files) {
                    if (!showAll && file.getName().startsWith(".")) continue;
                    fileNames.add(file.getName());
                }

                // Format in columns (simplified)
                int columns = 3;
                for (int i = 0; i < fileNames.size(); i++) {
                    output.append(String.format("%-20s", fileNames.get(i)));
                    if ((i + 1) % columns == 0) {
                        output.append("\n");
                    }
                }
                if (fileNames.size() % columns != 0) {
                    output.append("\n");
                }
            }

            callback.onSuccess(output.toString());
        } catch (Exception e) {
            callback.onError("ls: cannot access '" + (targetDir != null ? targetDir : ".") + "': " + e.getMessage());
        }
    }

    private void handleCd(String[] args, CommandCallback callback) {
        String targetDir = args.length > 0 ? args[0] : "~";

        try {
            fsManager.changeDirectory(targetDir);
            callback.onDirectoryChanged();
            callback.onSuccess("");
        } catch (Exception e) {
            callback.onError("cd: " + targetDir + ": No such file or directory");
        }
    }

    private void handlePwd(CommandCallback callback) {
        callback.onSuccess(fsManager.getAbsoluteCurrentDirectory());
    }

    private void handleMkdir(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("mkdir: missing operand");
            return;
        }

        boolean success = true;
        StringBuilder output = new StringBuilder();

        for (String dir : args) {
            if (fsManager.createDirectory(dir)) {
                output.append("✓ Created directory: ").append(dir).append("\n");
            } else {
                output.append("✗ Failed to create directory: ").append(dir).append("\n");
                success = false;
            }
        }

        if (success) {
            callback.onSuccess(output.toString());
        } else {
            callback.onError(output.toString());
        }
    }

    private void handleTouch(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("touch: missing file operand");
            return;
        }

        boolean success = true;
        StringBuilder output = new StringBuilder();

        for (String file : args) {
            if (fsManager.createFile(file)) {
                output.append("✓ Created file: ").append(file).append("\n");
            } else {
                output.append("✗ Failed to create file: ").append(file).append("\n");
                success = false;
            }
        }

        if (success) {
            callback.onSuccess(output.toString());
        } else {
            callback.onError(output.toString());
        }
    }

    private void handleRm(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("rm: missing operand");
            return;
        }

        boolean recursive = false;
        boolean force = false;
        List<String> filesToDelete = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.contains("r") || arg.contains("R")) recursive = true;
                if (arg.contains("f")) force = true;
            } else {
                filesToDelete.add(arg);
            }
        }

        boolean success = true;
        StringBuilder output = new StringBuilder();

        for (String file : filesToDelete) {
            if (fsManager.deleteFile(file)) {
                output.append("✓ Deleted: ").append(file).append("\n");
            } else {
                output.append("✗ Failed to delete: ").append(file).append("\n");
                success = false;
            }
        }

        if (success) {
            callback.onSuccess(output.toString());
        } else {
            callback.onError(output.toString());
        }
    }

    private void handleCp(String[] args, CommandCallback callback) {
        if (args.length < 2) {
            callback.onError("cp: missing destination file operand");
            return;
        }

        String source = args[0];
        String dest = args[1];

        try {
            String sourcePath = fsManager.getAbsoluteCurrentDirectory() + "/" + source;
            String destPath = fsManager.getAbsoluteCurrentDirectory() + "/" + dest;

            if (fsManager.copyFile(sourcePath, destPath)) {
                callback.onSuccess("✓ Copied '" + source + "' to '" + dest + "'");
            } else {
                callback.onError("cp: cannot copy '" + source + "' to '" + dest + "'");
            }
        } catch (Exception e) {
            callback.onError("cp: " + e.getMessage());
        }
    }

    private void handleMv(String[] args, CommandCallback callback) {
        if (args.length < 2) {
            callback.onError("mv: missing destination file operand");
            return;
        }

        String source = args[0];
        String dest = args[1];

        try {
            if (fsManager.renameFile(source, dest)) {
                callback.onSuccess("✓ Moved '" + source + "' to '" + dest + "'");
            } else {
                callback.onError("mv: cannot move '" + source + "' to '" + dest + "'");
            }
        } catch (Exception e) {
            callback.onError("mv: " + e.getMessage());
        }
    }

    private void handleCat(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("cat: missing file operand");
            return;
        }

        StringBuilder output = new StringBuilder();

        for (String filename : args) {
            try {
                String content = fsManager.readFile(filename);
                if (content != null) {
                    output.append(content);
                    if (args.length > 1) {
                        output.append("\n");
                    }
                } else {
                    callback.onError("cat: " + filename + ": No such file or directory");
                    return;
                }
            } catch (Exception e) {
                callback.onError("cat: " + filename + ": " + e.getMessage());
                return;
            }
        }

        callback.onSuccess(output.toString());
    }

    private void handleEdit(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("nano: missing filename");
            return;
        }

        String filename = args[0];
        callback.onSuccess("Opening '" + filename + "' in text editor...");

        // Launch text editor activity
        android.content.Intent intent = new android.content.Intent(context,
                com.example.linuxsimulator.TextEditorActivity.class);
        intent.putExtra("filePath", fsManager.getAbsoluteCurrentDirectory() + "/" + filename);
        intent.putExtra("fileName", filename);
        context.startActivity(intent);
    }

    private void handleChmod(String[] args, CommandCallback callback) {
        if (args.length < 2) {
            callback.onError("chmod: missing operand");
            return;
        }

        String permissions = args[0];
        String filename = args[1];

        // Simulate chmod (actual file permissions might not be changeable on Android)
        callback.onSuccess("✓ Changed permissions of '" + filename + "' to " + permissions);
    }

    private void handleFind(String[] args, CommandCallback callback) {
        String searchPath = ".";
        String searchName = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-name") && i + 1 < args.length) {
                searchName = args[i + 1];
                i++; // Skip next argument
            } else if (!args[i].startsWith("-")) {
                searchPath = args[i];
            }
        }

        if (searchName == null) {
            callback.onError("find: missing search criteria");
            return;
        }

        try {
            StringBuilder output = new StringBuilder();
            findFiles(searchPath, searchName, output);
            callback.onSuccess(output.toString());
        } catch (Exception e) {
            callback.onError("find: " + e.getMessage());
        }
    }

    private void findFiles(String path, String pattern, StringBuilder output) {
        try {
            String currentDir = fsManager.getCurrentDirectory();
            if (!path.equals(".")) {
                fsManager.changeDirectory(path);
            }

            List<FileItem> files = fsManager.listFiles();
            for (FileItem file : files) {
                if (file.getName().contains(pattern) || file.getName().matches(pattern.replace("*", ".*"))) {
                    output.append(fsManager.getCurrentDirectory()).append("/").append(file.getName()).append("\n");
                }
            }

            if (!path.equals(".")) {
                fsManager.changeDirectory(currentDir);
            }
        } catch (Exception e) {
            // Ignore errors in recursive search
        }
    }

    private void handleGrep(String[] args, CommandCallback callback) {
        if (args.length < 2) {
            callback.onError("grep: missing pattern or file");
            return;
        }

        String pattern = args[0];
        String filename = args[1];

        try {
            String content = fsManager.readFile(filename);
            if (content != null) {
                StringBuilder output = new StringBuilder();
                String[] lines = content.split("\n");
                int lineNumber = 1;

                for (String line : lines) {
                    if (line.toLowerCase().contains(pattern.toLowerCase())) {
                        output.append(lineNumber).append(": ").append(line).append("\n");
                    }
                    lineNumber++;
                }

                callback.onSuccess(output.toString());
            } else {
                callback.onError("grep: " + filename + ": No such file or directory");
            }
        } catch (Exception e) {
            callback.onError("grep: " + e.getMessage());
        }
    }

    private void handlePs(CommandCallback callback) {
        StringBuilder output = new StringBuilder();
        output.append("  PID TTY          TIME CMD\n");
        output.append(String.format("%5d pts/0    00:00:00 bash\n", 1000));
        output.append(String.format("%5d pts/0    00:00:00 terminal\n", 1001));

        for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
            output.append(String.format("%5s pts/0    00:00:00 %s\n",
                    entry.getKey(), entry.getValue().toString()));
        }

        callback.onSuccess(output.toString());
    }

    private void handleKill(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("kill: missing process ID");
            return;
        }

        String pid = args[0];
        if (runningProcesses.containsKey(pid)) {
            runningProcesses.remove(pid);
            callback.onSuccess("✓ Process " + pid + " terminated");
        } else {
            callback.onError("kill: no such process: " + pid);
        }
    }

    private void handleClear(CommandCallback callback) {
        // Clear is handled by the terminal activity
        callback.onSuccess(" ");
    }

    private void handleHelp(CommandCallback callback) {
        StringBuilder help = new StringBuilder();
        help.append("🐧 Kali Linux Terminal - Available Commands:\n\n");
        help.append("📁 File Operations:\n");
        help.append("  ls [-la]           - List directory contents\n");
        help.append("  cd <dir>          - Change directory\n");
        help.append("  pwd               - Print working directory\n");
        help.append("  mkdir <dir>       - Create directory\n");
        help.append("  touch <file>      - Create empty file\n");
        help.append("  rm [-rf] <file>   - Remove files/directories\n");
        help.append("  cp <src> <dest>   - Copy files\n");
        help.append("  mv <src> <dest>   - Move/rename files\n");
        help.append("  find <path> -name <pattern> - Find files\n\n");
        help.append("📄 Text Operations:\n");
        help.append("  cat <file>        - Display file contents\n");
        help.append("  nano <file>       - Edit file in text editor\n");
        help.append("  grep <pattern> <file> - Search text in files\n");
        help.append("  head <file>       - Show first lines of file\n");
        help.append("  tail <file>       - Show last lines of file\n");
        help.append("  wc <file>         - Count lines, words, characters\n\n");
        help.append("🔐 SSH Commands:\n");
        help.append("  ssh [user@]host   - Connect to remote device\n");
        help.append("  ssh-list          - List available SSH devices\n");
        help.append("  ssh-sessions      - Show active SSH sessions\n");
        help.append("  ssh-disconnect    - Disconnect SSH session\n");
        help.append("  ssh-add           - Add new SSH device\n");
        help.append("  ssh-toggle        - Toggle device online/offline\n");
        help.append("  exit/logout       - Exit SSH session\n\n");
        help.append("⚙️ System Commands:\n");
        help.append("  ps                - List running processes\n");
        help.append("  kill <pid>        - Terminate process\n");
        help.append("  whoami            - Current user\n");
        help.append("  date              - Current date and time\n");
        help.append("  uname [-a]        - System information\n");
        help.append("  df                - Disk space usage\n");
        help.append("  free              - Memory usage\n");
        help.append("  env               - Environment variables\n\n");
        help.append("🔧 Utilities:\n");
        help.append("  echo <text>       - Display text\n");
        help.append("  history           - Command history\n");
        help.append("  clear             - Clear terminal\n");
        help.append("  help              - Show this help\n");
        help.append("  exit              - Exit terminal\n\n");
        help.append("📡 Network:\n");
        help.append("  wifi-scan         - Scan for WiFi networks\n\n");
        help.append("💡 SSH Tips:\n");
        help.append("  • Connect: ssh admin@ubuntu-server\n");
        help.append("  • List devices: ssh-list\n");
        help.append("  • Multiple sessions supported\n");
        help.append("  • Use exit to return to local terminal\n");

        callback.onSuccess(help.toString());
    }

    private void handleWhoami(CommandCallback callback) {
        callback.onSuccess(terminal.getCurrentUser());
    }

    private void handleDate(CommandCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault());
        callback.onSuccess(sdf.format(new Date()));
    }

    private void handleEcho(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onSuccess("");
            return;
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            output.append(args[i]);
            if (i < args.length - 1) {
                output.append(" ");
            }
        }
        callback.onSuccess(output.toString());
    }

    private void handleHistory(CommandCallback callback) {
        // This would need to be implemented with actual history tracking
        callback.onSuccess("Command history not implemented yet");
    }

    private void handleSu(String[] args, CommandCallback callback) {
        String user = args.length > 0 ? args[0] : "root";
        if (user.equals("root")) {
            terminal.setRoot(true);
            callback.onSuccess("Switched to root user");
            callback.onDirectoryChanged();
        } else {
            callback.onError("su: user " + user + " does not exist");
        }
    }

    private void handleSudo(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("sudo: missing command");
            return;
        }

        // Simulate sudo by temporarily becoming root
        boolean wasRoot = terminal.isRoot();
        terminal.setRoot(true);

        // Execute the command as root
        processCommand(String.join(" ", args), new CommandCallback() {
            @Override
            public void onSuccess(String output) {
                terminal.setRoot(wasRoot); // Restore original state
                callback.onSuccess(output);
            }

            @Override
            public void onError(String error) {
                terminal.setRoot(wasRoot); // Restore original state
                callback.onError(error);
            }

            @Override
            public void onDirectoryChanged() {
                callback.onDirectoryChanged();
            }
        });
    }

    private void handleEnv(CommandCallback callback) {
        StringBuilder output = new StringBuilder();
        Map<String, String> env = terminal.getEnvironmentVariables();

        for (Map.Entry<String, String> entry : env.entrySet()) {
            output.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        callback.onSuccess(output.toString());
    }

    private void handleWhich(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("which: missing command");
            return;
        }

        String command = args[0];
        String[] knownCommands = {"ls", "cd", "pwd", "mkdir", "touch", "rm", "cp", "mv", "cat", "nano",
                "chmod", "find", "grep", "ps", "kill", "clear", "help", "exit", "whoami", "ssh"};

        for (String cmd : knownCommands) {
            if (cmd.equals(command)) {
                callback.onSuccess("/usr/bin/" + command);
                return;
            }
        }

        callback.onError("which: no " + command + " in PATH");
    }

    private void handleMan(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("man: missing command");
            return;
        }

        String command = args[0];
        callback.onSuccess("Manual page for " + command + " - Use 'help' for available commands");
    }

    private void handleTree(String[] args, CommandCallback callback) {
        callback.onSuccess("📁 Directory tree:\n" + fsManager.getCurrentDirectory() + "\n└── Use 'ls' to see contents");
    }

    private void handleHead(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("head: missing file operand");
            return;
        }

        String filename = args[0];
        int lines = 10;

        // Check for -n option
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-n")) {
                try {
                    lines = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    callback.onError("head: invalid number of lines");
                    return;
                }
                break;
            }
        }

        try {
            String content = fsManager.readFile(filename);
            if (content != null) {
                String[] fileLines = content.split("\n");
                StringBuilder output = new StringBuilder();

                for (int i = 0; i < Math.min(lines, fileLines.length); i++) {
                    output.append(fileLines[i]).append("\n");
                }

                callback.onSuccess(output.toString());
            } else {
                callback.onError("head: " + filename + ": No such file or directory");
            }
        } catch (Exception e) {
            callback.onError("head: " + e.getMessage());
        }
    }

    private void handleTail(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("tail: missing file operand");
            return;
        }

        String filename = args[0];
        int lines = 10;

        try {
            String content = fsManager.readFile(filename);
            if (content != null) {
                String[] fileLines = content.split("\n");
                StringBuilder output = new StringBuilder();

                int start = Math.max(0, fileLines.length - lines);
                for (int i = start; i < fileLines.length; i++) {
                    output.append(fileLines[i]).append("\n");
                }

                callback.onSuccess(output.toString());
            } else {
                callback.onError("tail: " + filename + ": No such file or directory");
            }
        } catch (Exception e) {
            callback.onError("tail: " + e.getMessage());
        }
    }

    private void handleWc(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("wc: missing file operand");
            return;
        }

        String filename = args[0];

        try {
            String content = fsManager.readFile(filename);
            if (content != null) {
                String[] lines = content.split("\n");
                String[] words = content.trim().split("\\s+");
                int characters = content.length();

                callback.onSuccess(String.format("%d %d %d %s", lines.length, words.length, characters, filename));
            } else {
                callback.onError("wc: " + filename + ": No such file or directory");
            }
        } catch (Exception e) {
            callback.onError("wc: " + e.getMessage());
        }
    }

    private void handleDf(CommandCallback callback) {
        StringBuilder output = new StringBuilder();
        output.append("Filesystem     1K-blocks    Used Available Use% Mounted on\n");
        output.append("/dev/root       15728640 8123456   7605184  52% /\n");
        output.append("tmpfs            1048576    1024   1047552   1% /tmp\n");
        output.append("tmpfs            2097152   81920   2015232   4% /dev/shm\n");

        callback.onSuccess(output.toString());
    }

    private void handleFree(CommandCallback callback) {
        StringBuilder output = new StringBuilder();
        output.append("              total        used        free      shared  buff/cache   available\n");
        output.append("Mem:        4194304     1572864     2097152       81920      524288     2359296\n");
        output.append("Swap:       2097152      262144     1835008\n");

        callback.onSuccess(output.toString());
    }

    private void handleUname(String[] args, CommandCallback callback) {
        boolean all = args.length > 0 && args[0].equals("-a");

        if (all) {
            callback.onSuccess("Linux kali 5.15.0-kali3-amd64 #1 SMP Debian 5.15.15-2kali1 (2022-01-31) x86_64 GNU/Linux");
        } else {
            callback.onSuccess("Linux");
        }
    }
}