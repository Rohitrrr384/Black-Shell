package com.example.linuxsimulator.terminal;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.example.linuxsimulator.GitSimulator;
import com.example.linuxsimulator.NetworkToolsSimulator;
import com.example.linuxsimulator.TextEditorActivity;
import com.example.linuxsimulator.WiFiAnalyzer;
import com.example.linuxsimulator.data.FileSystemManager;
import com.example.linuxsimulator.data.FileItem;
import com.example.linuxsimulator.TerminalActivity;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.jcraft.jsch.*;

public class CommandProcessor {
    private WiFiAnalyzer wifiAnalyzer;
    private TerminalActivity terminal;
    private FileSystemManager fsManager;
    private Context context;
    private Map<String, Process> runningProcesses;
    private int nextProcessId = 1;
    private GitSimulator gitSimulator;
    private String currentDirectory;

    // SSH-related fields (Real SSH)
    private Map<String, Session> realSSHSessions;
    private String currentSSHSessionId = null;
    private boolean inSSHMode = false;
    private ChannelExec currentExecChannel = null;

    // Simulated SSH for demo
    private Map<String, SSHDevice> availableDevices;
    private Map<String, SSHSession> activeSessions;
    private String currentSimulatedSSHSession = null;

    public interface CommandCallback {
        void onSuccess(String output);
        void onError(String error);
        void onDirectoryChanged();
    }

    // SSH Device class (for simulation)
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

    // SSH Session class (for simulation)
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
        this.realSSHSessions = new HashMap<>();

        // Initialize simulated SSH components
        this.availableDevices = new HashMap<>();
        this.activeSessions = new HashMap<>();
        initializeSSHDevices();
    }

    private void initializeSSHDevices() {
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

    public void processCommand(String input, CommandCallback callback) {
        if (input.trim().isEmpty()) {
            callback.onSuccess("");
            return;
        }

        // If in SSH mode, handle differently
        if (inSSHMode && currentSSHSessionId != null) {
            processRealSSHCommand(input.trim(), callback);
            return;
        } else if (inSSHMode && currentSimulatedSSHSession != null) {
            processSSHModeCommand(input.trim(), callback);
            return;
        }

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
                    handleRealWifiScan(callback);
                    break;
                case "bpad.txt":
                    handleBpad(args, callback);
                    break;
                // Real SSH Commands
                case "ssh":
                    handleRealSSH(input.trim(), callback);
                    break;
                case "ssh-sim":
                    handleSSH(input.trim(), callback);
                    break;
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
                // Real Network Commands
                case "ping":
                    handleRealPing(args, callback);
                    break;
                case "netstat":
                    handleRealNetstat(args, callback);
                    break;
                case "ifconfig":
                case "ip":
                    handleRealIfconfig(callback);
                    break;
                case "nslookup":
                case "dig":
                    handleRealNslookup(args, callback);
                    break;
                case "traceroute":
                    handleRealTraceroute(args, callback);
                    break;
                case "curl":
                    handleRealCurl(args, callback);
                    break;
                case "wget":
                    handleRealWget(args, callback);
                    break;
                case "hostname":
                    handleRealHostname(callback);
                    break;
                // Real Git Commands
                case "git":
                    handleRealGit(args, callback);
                    break;
                // System Info Commands
                case "uptime":
                    handleRealUptime(callback);
                    break;
                case "top":
                    handleRealTop(callback);
                    break;
                case "lsof":
                    handleRealLsof(callback);
                    break;
                case "dmesg":
                    handleRealDmesg(callback);
                    break;
                default:
                    callback.onError("Command not found: " + command + "\nType 'help' for available commands");
                    break;
            }
        } catch (Exception e) {
            callback.onError("Error executing command: " + e.getMessage());
        }
    }

    // ==================== REAL GIT COMMANDS ====================
    private void handleRealGit(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            showGitHelp(callback);
            return;
        }

        String subCommand = args[0].toLowerCase();
        File currentDir = new File(fsManager.getAbsoluteCurrentDirectory());

        new Thread(() -> {
            try {
                String result = "";
                switch (subCommand) {
                    case "init":
                        result = realGitInit(currentDir);
                        break;
                    case "clone":
                        if (args.length < 2) {
                            result = "Error: git clone requires a repository URL";
                        } else {
                            result = realGitClone(args[1], currentDir);
                        }
                        break;
                    case "status":
                        result = realGitStatus(currentDir);
                        break;
                    case "add":
                        if (args.length < 2) {
                            result = "Error: git add requires a file path";
                        } else {
                            result = realGitAdd(currentDir, args[1]);
                        }
                        break;
                    case "commit":
                        if (args.length < 3 || !args[1].equals("-m")) {
                            result = "Error: git commit requires -m \"message\"";
                        } else {
                            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                            result = realGitCommit(currentDir, message.replaceAll("^\"|\"$", ""));
                        }
                        break;
                    case "log":
                        result = realGitLog(currentDir);
                        break;
                    case "branch":
                        result = realGitBranch(currentDir, args);
                        break;
                    case "checkout":
                        if (args.length < 2) {
                            result = "Error: git checkout requires a branch name";
                        } else {
                            result = realGitCheckout(currentDir, args[1]);
                        }
                        break;
                    case "pull":
                        result = realGitPull(currentDir);
                        break;
                    case "push":
                        result = realGitPush(currentDir);
                        break;
                    case "remote":
                        result = realGitRemote(currentDir, args);
                        break;
                    case "diff":
                        result = realGitDiff(currentDir);
                        break;
                    default:
                        result = "Unknown git command: " + subCommand + "\nUse 'git help' for available commands";
                }
                final String finalResult = result;
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));
            } catch (Exception e) {
                terminal.runOnUiThread(() -> callback.onError("Git error: " + e.getMessage()));
            }
        }).start();
    }

    private String realGitInit(File dir) throws GitAPIException {
        // Use app's internal storage for Git operations
        File gitDir = new File(context.getFilesDir(), "git_repos/" + dir.getName());
        if (!gitDir.exists()) {
            gitDir.mkdirs();
        }

        Git.init().setDirectory(gitDir).call().close();

        // Create a symlink reference in the current directory
        File gitMarker = new File(dir, ".git_ref");
        try {
            FileWriter writer = new FileWriter(gitMarker);
            writer.write(gitDir.getAbsolutePath());
            writer.close();
        } catch (IOException e) {
            // Ignore
        }

        return "✓ Initialized empty Git repository in " + gitDir.getAbsolutePath() + "/.git/";
    }

    private String realGitClone(String url, File dir) throws GitAPIException {
        String repoName = getRepoNameFromUrl(url);

        // Use app's internal storage for Git operations
        File gitDir = new File(context.getFilesDir(), "git_repos/" + repoName);
        if (gitDir.exists()) {
            deleteRecursive(gitDir);
        }
        gitDir.mkdirs();

        Git git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(gitDir)
                .call();
        git.close();

        // Create a symlink reference in the current directory
        File targetDir = new File(dir, repoName);
        targetDir.mkdirs();
        File gitMarker = new File(targetDir, ".git_ref");
        try {
            FileWriter writer = new FileWriter(gitMarker);
            writer.write(gitDir.getAbsolutePath());
            writer.close();
        } catch (IOException e) {
            // Ignore
        }

        return "✓ Repository cloned to " + gitDir.getAbsolutePath();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    private File getActualGitDirectory(File currentDir) {
        // Check if there's a .git_ref file pointing to actual git repo
        File gitRefFile = new File(currentDir, ".git_ref");
        if (gitRefFile.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(gitRefFile));
                String gitPath = reader.readLine();
                reader.close();
                File gitDir = new File(gitPath);
                if (gitDir.exists()) {
                    return gitDir;
                }
            } catch (IOException e) {
                // Fall through
            }
        }

        // Try to find in app's internal storage
        String dirName = currentDir.getName();
        File gitDir = new File(context.getFilesDir(), "git_repos/" + dirName);
        if (new File(gitDir, ".git").exists()) {
            return gitDir;
        }

        // Default to current directory
        return currentDir;
    }

    private String realGitStatus(File dir) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);
        Status status = git.status().call();

        StringBuilder sb = new StringBuilder();
        sb.append("On branch ").append(repo.getBranch()).append("\n\n");

        if (status.isClean()) {
            sb.append("nothing to commit, working tree clean");
        } else {
            if (!status.getUntracked().isEmpty()) {
                sb.append("Untracked files:\n");
                for (String file : status.getUntracked()) {
                    sb.append("  ").append(file).append("\n");
                }
            }
            if (!status.getModified().isEmpty()) {
                sb.append("\nModified files:\n");
                for (String file : status.getModified()) {
                    sb.append("  ").append(file).append("\n");
                }
            }
            if (!status.getAdded().isEmpty()) {
                sb.append("\nChanges to be committed:\n");
                for (String file : status.getAdded()) {
                    sb.append("  ").append(file).append("\n");
                }
            }
        }

        git.close();
        return sb.toString();
    }

    private String realGitAdd(File dir, String pattern) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);
        git.add().addFilepattern(pattern).call();
        git.close();
        return "✓ Added " + pattern + " to staging area";
    }

    private String realGitCommit(File dir, String message) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);

        // Set default identity if not configured
        try {
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor("Terminal User", "user@localhost")
                    .setCommitter("Terminal User", "user@localhost")
                    .call();
            git.close();
            return "✓ [" + repo.getBranch() + " " + commit.getName().substring(0, 7) + "] " + message;
        } catch (Exception e) {
            git.close();
            throw new Exception("Commit failed. Make sure you have added files using 'git add'");
        }
    }

    private String realGitLog(File dir) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);

        Iterable<RevCommit> logs = git.log().setMaxCount(10).call();

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (RevCommit commit : logs) {
            count++;
            sb.append("commit ").append(commit.getName()).append("\n");
            sb.append("Author: ").append(commit.getAuthorIdent().getName())
                    .append(" <").append(commit.getAuthorIdent().getEmailAddress()).append(">\n");
            sb.append("Date:   ").append(new Date(commit.getCommitTime() * 1000L)).append("\n\n");
            sb.append("    ").append(commit.getFullMessage()).append("\n\n");
        }

        if (count == 0) {
            sb.append("No commits yet");
        }

        git.close();
        return sb.toString();
    }

    private String realGitBranch(File dir, String[] args) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);

        if (args.length == 1) {
            // List branches
            StringBuilder sb = new StringBuilder();
            String currentBranch = repo.getBranch();
            for (var ref : git.branchList().call()) {
                String branchName = ref.getName().replace("refs/heads/", "");
                if (branchName.equals(currentBranch)) {
                    sb.append("* ").append(branchName).append("\n");
                } else {
                    sb.append("  ").append(branchName).append("\n");
                }
            }
            git.close();
            return sb.toString();
        } else {
            // Create branch
            git.branchCreate().setName(args[1]).call();
            git.close();
            return "✓ Created branch: " + args[1];
        }
    }

    private String realGitCheckout(File dir, String branch) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);
        git.checkout().setName(branch).call();
        git.close();
        return "✓ Switched to branch '" + branch + "'";
    }

    private String realGitPull(File dir) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);
        git.pull().call();
        git.close();
        return "✓ Successfully pulled from remote";
    }

    private String realGitPush(File dir) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);
        git.push().call();
        git.close();
        return "✓ Successfully pushed to remote";
    }

    private String realGitRemote(File dir, String[] args) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);

        if (args.length == 1) {
            // List remotes
            StringBuilder sb = new StringBuilder();
            for (var remote : git.remoteList().call()) {
                sb.append(remote.getName()).append("\n");
            }
            git.close();
            return sb.toString();
        } else if (args.length >= 4 && args[1].equals("add")) {
            git.remoteAdd().setName(args[2]).setUri(new org.eclipse.jgit.transport.URIish(args[3])).call();
            git.close();
            return "✓ Added remote: " + args[2];
        }

        git.close();
        return "Usage: git remote [add <name> <url>]";
    }

    private String realGitDiff(File dir) throws Exception {
        File actualGitDir = getActualGitDirectory(dir);

        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(actualGitDir, ".git"))
                .build();
        Git git = new Git(repo);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        git.diff().setOutputStream(out).call();
        git.close();

        String diff = out.toString();
        return diff.isEmpty() ? "No changes" : diff;
    }

    private void showGitHelp(CommandCallback callback) {
        String help = "usage: git [--version] [--help] <command> [<args>]\n\n" +
                "Common Git commands:\n" +
                "  init       Initialize a new Git repository\n" +
                "  clone      Clone a repository\n" +
                "  status     Show working tree status\n" +
                "  add        Add files to staging area\n" +
                "  commit     Commit changes\n" +
                "  log        Show commit history\n" +
                "  branch     List, create, or delete branches\n" +
                "  checkout   Switch branches\n" +
                "  pull       Fetch and merge from remote\n" +
                "  push       Push commits to remote\n" +
                "  remote     Manage remote repositories\n" +
                "  diff       Show changes\n";
        callback.onSuccess(help);
    }

    private String getRepoNameFromUrl(String url) {
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    // ==================== REAL SSH COMMANDS ====================
    private void handleRealSSH(String fullCommand, CommandCallback callback) {
        String[] parts = fullCommand.trim().split("\\s+");

        if (parts.length < 2) {
            callback.onError("Usage: ssh [user@]hostname [-p port]");
            return;
        }

        String target = parts[1];
        String username = null;
        String hostname = null;
        int port = 22;

        // Parse user@hostname
        if (target.contains("@")) {
            String[] userHost = target.split("@");
            username = userHost[0];
            hostname = userHost[1];
        } else {
            hostname = target;
            username = System.getProperty("user.name");
        }

        // Parse port
        for (int i = 2; i < parts.length; i++) {
            if (parts[i].equals("-p") && i + 1 < parts.length) {
                try {
                    port = Integer.parseInt(parts[i + 1]);
                } catch (NumberFormatException e) {
                    callback.onError("Invalid port number");
                    return;
                }
            }
        }

        final String finalUsername = username;
        final String finalHostname = hostname;
        final int finalPort = port;

        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(finalUsername, finalHostname, finalPort);

                // For demo purposes - in production, use proper authentication
                session.setPassword(""); // You should prompt for password

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);

                session.connect(30000);

                String sessionId = UUID.randomUUID().toString().substring(0, 8);
                realSSHSessions.put(sessionId, session);
                currentSSHSessionId = sessionId;
                inSSHMode = true;

                terminal.runOnUiThread(() -> {
                    callback.onSuccess("✓ Connected to " + finalHostname + " as " + finalUsername);
                    callback.onDirectoryChanged();
                });

            } catch (JSchException e) {
                terminal.runOnUiThread(() ->
                        callback.onError("SSH connection failed: " + e.getMessage())
                );
            }
        }).start();
    }

    private void processRealSSHCommand(String command, CommandCallback callback) {
        if (command.equals("exit") || command.equals("logout")) {
            disconnectRealSSH();
            callback.onSuccess("Connection closed");
            callback.onDirectoryChanged();
            return;
        }

        Session session = realSSHSessions.get(currentSSHSessionId);
        if (session == null || !session.isConnected()) {
            callback.onError("SSH session not connected");
            return;
        }

        new Thread(() -> {
            try {
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

                channel.setOutputStream(outputStream);
                channel.setErrStream(errorStream);

                channel.connect();

                while (!channel.isClosed()) {
                    Thread.sleep(100);
                }

                String output = outputStream.toString();
                String error = errorStream.toString();

                channel.disconnect();

                terminal.runOnUiThread(() -> {
                    if (!error.isEmpty()) {
                        callback.onError(error);
                    } else {
                        callback.onSuccess(output);
                    }
                });

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("Command execution failed: " + e.getMessage())
                );
            }
        }).start();
    }

    private void disconnectRealSSH() {
        if (currentSSHSessionId != null) {
            Session session = realSSHSessions.remove(currentSSHSessionId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            currentSSHSessionId = null;
        }
        inSSHMode = false;
    }

    // ==================== REAL NETWORK COMMANDS ====================
    private void handleRealPing(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("Usage: ping <hostname>");
            return;
        }

        String host = args[0];
        int count = 4;

        // Parse count option
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-c")) {
                try {
                    count = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    callback.onError("Invalid count");
                    return;
                }
            }
        }

        final int finalCount = count;
        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            result.append("PING ").append(host).append("\n");

            int received = 0;
            long totalTime = 0;

            try {
                InetAddress address = InetAddress.getByName(host);
                result.append("Pinging ").append(address.getHostAddress()).append("\n\n");

                for (int i = 0; i < finalCount; i++) {
                    long startTime = System.currentTimeMillis();
                    boolean reachable = address.isReachable(5000);
                    long endTime = System.currentTimeMillis();
                    long time = endTime - startTime;

                    if (reachable) {
                        result.append("Reply from ").append(address.getHostAddress())
                                .append(": bytes=32 time=").append(time).append("ms TTL=64\n");
                        received++;
                        totalTime += time;
                    } else {
                        result.append("Request timeout for ").append(address.getHostAddress()).append("\n");
                    }

                    if (i < finalCount - 1) {
                        Thread.sleep(1000);
                    }
                }

                result.append("\n--- ").append(host).append(" ping statistics ---\n");
                result.append(finalCount).append(" packets transmitted, ");
                result.append(received).append(" received, ");
                result.append((finalCount - received) * 100 / finalCount).append("% packet loss\n");

                if (received > 0) {
                    result.append("rtt min/avg/max = ").append(totalTime / received).append(" ms\n");
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("ping: " + host + ": " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealNetstat(String[] args, CommandCallback callback) {
        new Thread(() -> {
            try {
                StringBuilder result = new StringBuilder();
                result.append("Active Internet connections\n");
                result.append(String.format("%-8s %-22s %-22s %-12s\n",
                        "Proto", "Local Address", "Foreign Address", "State"));

                // Get network connections (Android specific)
                try {
                    Process process = Runtime.getRuntime().exec("netstat");
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line).append("\n");
                    }
                    reader.close();
                } catch (Exception e) {
                    // Fallback to simulated output
                    result.append("tcp        0.0.0.0:22              0.0.0.0:*               LISTEN\n");
                    result.append("tcp        0.0.0.0:80              0.0.0.0:*               LISTEN\n");
                    result.append("tcp        127.0.0.1:3306          0.0.0.0:*               LISTEN\n");
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("netstat error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealIfconfig(CommandCallback callback) {
        new Thread(() -> {
            try {
                StringBuilder result = new StringBuilder();

                // Get network interfaces
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();

                    result.append(iface.getName()).append(": ");
                    result.append("flags=").append(iface.isUp() ? "UP" : "DOWN").append("\n");

                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address) {
                            result.append("    inet ").append(addr.getHostAddress()).append("\n");
                        } else if (addr instanceof Inet6Address) {
                            result.append("    inet6 ").append(addr.getHostAddress()).append("\n");
                        }
                    }

                    byte[] mac = iface.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder macStr = new StringBuilder();
                        for (int i = 0; i < mac.length; i++) {
                            macStr.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                        }
                        result.append("    ether ").append(macStr).append("\n");
                    }
                    result.append("\n");
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("ifconfig error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealNslookup(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("Usage: nslookup <hostname>");
            return;
        }

        String hostname = args[0];

        new Thread(() -> {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(hostname);
                StringBuilder result = new StringBuilder();
                result.append("Server:\t\t8.8.8.8\n");
                result.append("Address:\t8.8.8.8#53\n\n");
                result.append("Name:\t").append(hostname).append("\n");

                for (InetAddress addr : addresses) {
                    result.append("Address: ").append(addr.getHostAddress()).append("\n");
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (UnknownHostException e) {
                terminal.runOnUiThread(() ->
                        callback.onError("** server can't find " + hostname + ": NXDOMAIN")
                );
            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("nslookup error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealTraceroute(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("Usage: traceroute <hostname>");
            return;
        }

        String host = args[0];

        new Thread(() -> {
            try {
                InetAddress target = InetAddress.getByName(host);
                StringBuilder result = new StringBuilder();
                result.append("traceroute to ").append(host).append(" (")
                        .append(target.getHostAddress()).append("), 30 hops max\n");

                // Simulate traceroute (real implementation would require raw sockets)
                int maxHops = 10;
                for (int i = 1; i <= maxHops; i++) {
                    result.append(String.format("%2d  ", i));

                    if (i < maxHops) {
                        result.append("* * *\n");
                        Thread.sleep(500);
                    } else {
                        result.append(target.getHostAddress()).append("  ")
                                .append(target.getHostName()).append("\n");
                    }
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("traceroute error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealCurl(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("Usage: curl <url>");
            return;
        }

        String url = args[0];

        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null) {
                    response.append(line).append("\n");
                }
                in.close();

                final String finalResult = response.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("curl: (" + e.getClass().getSimpleName() + ") " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealWget(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("Usage: wget <url>");
            return;
        }

        String url = args[0];
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.isEmpty()) filename = "index.html";

        String finalFilename = filename;
        String finalFilename1 = filename;
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");

                int fileSize = conn.getContentLength();

                terminal.runOnUiThread(() ->
                        callback.onSuccess("Connecting to " + urlObj.getHost() + "... connected.\n" +
                                "Saving to: '" + finalFilename + "'")
                );

                InputStream in = conn.getInputStream();
                File outputFile = new File(fsManager.getAbsoluteCurrentDirectory(), finalFilename1);
                FileOutputStream out = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                int totalRead = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                in.close();
                out.close();

                final int finalTotalRead = totalRead;
                terminal.runOnUiThread(() ->
                        callback.onSuccess("✓ Downloaded " + finalTotalRead + " bytes to '" + finalFilename1 + "'")
                );

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("wget: error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealHostname(CommandCallback callback) {
        new Thread(() -> {
            try {
                InetAddress localhost = InetAddress.getLocalHost();
                String hostname = localhost.getHostName();
                terminal.runOnUiThread(() -> callback.onSuccess(hostname));
            } catch (Exception e) {
                terminal.runOnUiThread(() -> callback.onSuccess(Build.MODEL));
            }
        }).start();
    }

    private void handleRealWifiScan(CommandCallback callback) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                callback.onError("WiFi not available");
                return;
            }

            if (!wifiManager.isWifiEnabled()) {
                callback.onError("WiFi is disabled");
                return;
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            StringBuilder result = new StringBuilder();
            result.append("Current WiFi Connection:\n");
            result.append("SSID: ").append(wifiInfo.getSSID()).append("\n");
            result.append("BSSID: ").append(wifiInfo.getBSSID()).append("\n");
            result.append("IP: ").append(formatIpAddress(wifiInfo.getIpAddress())).append("\n");
            result.append("Link Speed: ").append(wifiInfo.getLinkSpeed()).append(" Mbps\n");
            result.append("Signal Strength: ").append(wifiInfo.getRssi()).append(" dBm\n");

            callback.onSuccess(result.toString());

        } catch (Exception e) {
            callback.onError("WiFi scan error: " + e.getMessage());
        }
    }

    private String formatIpAddress(int ip) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    // ==================== REAL SYSTEM COMMANDS ====================
    private void handleRealUptime(CommandCallback callback) {
        new Thread(() -> {
            try {
                long uptime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
                long uptimeSeconds = android.os.SystemClock.elapsedRealtime() / 1000;

                long days = uptimeSeconds / 86400;
                long hours = (uptimeSeconds % 86400) / 3600;
                long minutes = (uptimeSeconds % 3600) / 60;

                String result = String.format("up %d days, %d hours, %d minutes", days, hours, minutes);
                terminal.runOnUiThread(() -> callback.onSuccess(result));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("uptime error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealTop(CommandCallback callback) {
        new Thread(() -> {
            try {
                StringBuilder result = new StringBuilder();
                result.append("Tasks: ").append(Thread.activeCount()).append(" active threads\n");
                result.append("CPU usage: ").append(getCpuUsage()).append("%\n");
                result.append("Memory: ").append(getMemoryInfo()).append("\n\n");

                result.append(String.format("%-8s %-20s %-8s\n", "TID", "NAME", "STATE"));
                result.append("─".repeat(40)).append("\n");

                Set<Thread> threads = Thread.getAllStackTraces().keySet();
                int count = 0;
                for (Thread thread : threads) {
                    if (count++ > 10) break;
                    result.append(String.format("%-8d %-20s %-8s\n",
                            thread.getId(),
                            thread.getName().substring(0, Math.min(20, thread.getName().length())),
                            thread.getState().toString()));
                }

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("top error: " + e.getMessage())
                );
            }
        }).start();
    }

    private String getCpuUsage() {
        try {
            Process process = Runtime.getRuntime().exec("top -n 1");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("CPU")) {
                    return line.split(":")[1].trim().split("%")[0];
                }
            }
            reader.close();
        } catch (Exception e) {
            // Ignore
        }
        return "N/A";
    }

    private String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        return String.format("%dM used, %dM free, %dM max",
                usedMemory, freeMemory, maxMemory);
    }

    private void handleRealLsof(CommandCallback callback) {
        new Thread(() -> {
            try {
                StringBuilder result = new StringBuilder();
                result.append("Open files and network connections:\n");
                result.append(String.format("%-10s %-8s %-8s %-50s\n",
                        "COMMAND", "PID", "USER", "NAME"));
                result.append("─".repeat(80)).append("\n");

                // Simulate lsof output
                result.append(String.format("%-10s %-8d %-8s %-50s\n",
                        "java", android.os.Process.myPid(), "app", "/dev/pts/0"));
                result.append(String.format("%-10s %-8d %-8s %-50s\n",
                        "java", android.os.Process.myPid(), "app", "socket:[12345]"));

                final String finalResult = result.toString();
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onError("lsof error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void handleRealDmesg(CommandCallback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("dmesg");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null && lineCount++ < 50) {
                    result.append(line).append("\n");
                }
                reader.close();

                final String finalResult = result.length() > 0 ? result.toString() :
                        "dmesg: read kernel buffer failed: Operation not permitted";
                terminal.runOnUiThread(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                terminal.runOnUiThread(() ->
                        callback.onSuccess("dmesg: read kernel buffer failed: Operation not permitted")
                );
            }
        }).start();
    }

    // ==================== SIMULATED SSH (Original) ====================
    private void processSSHModeCommand(String command, CommandCallback callback) {
        if (command.equals("exit") || command.equals("logout")) {
            String result = disconnectSSH(currentSimulatedSSHSession);
            currentSimulatedSSHSession = null;
            inSSHMode = false;
            callback.onSuccess(result + "\nReturned to local terminal.");
            callback.onDirectoryChanged();
            return;
        }

        String result = processRemoteCommand(currentSimulatedSSHSession, command);
        callback.onSuccess(result);
    }

    private void handleSSH(String fullCommand, CommandCallback callback) {
        String[] parts = fullCommand.trim().split("\\s+");

        if (parts.length < 2) {
            callback.onError("ssh-sim: usage: ssh-sim [user@]hostname [command]");
            return;
        }

        String target = parts[1];
        String username = null;
        String hostname = null;

        if (target.contains("@")) {
            String[] userHost = target.split("@");
            username = userHost[0];
            hostname = userHost[1];
        } else {
            hostname = target;
        }

        SSHDevice targetDevice = findDeviceByHostnameOrIP(hostname);
        if (targetDevice == null) {
            callback.onError("ssh-sim: Could not resolve hostname " + hostname);
            return;
        }

        if (!targetDevice.isOnline) {
            callback.onError("ssh-sim: connect to host " + hostname + " port 22: Connection refused");
            return;
        }

        if (username == null) {
            username = targetDevice.username;
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        SSHSession session = new SSHSession(sessionId, targetDevice);

        String result = initiateSSHConnection(session, username);
        callback.onSuccess(result);

        if (session.isAuthenticated) {
            currentSimulatedSSHSession = sessionId;
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
        StringBuilder result = new StringBuilder("Available SSH devices (simulated):\n");
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
        StringBuilder result = new StringBuilder();

        // Real SSH sessions
        if (!realSSHSessions.isEmpty()) {
            result.append("Active Real SSH sessions:\n");
            for (Map.Entry<String, Session> entry : realSSHSessions.entrySet()) {
                Session session = entry.getValue();
                String current = entry.getKey().equals(currentSSHSessionId) ? " [CURRENT]" : "";
                result.append("  ").append(entry.getKey()).append(": ")
                        .append(session.getUserName()).append("@")
                        .append(session.getHost()).append(current).append("\n");
            }
            result.append("\n");
        }

        // Simulated SSH sessions
        if (!activeSessions.isEmpty()) {
            result.append("Active Simulated SSH sessions:\n");
            result.append(String.format("%-10s %-15s %-20s %s\n", "SESSION", "USER", "HOSTNAME", "CONNECTED"));
            result.append("─".repeat(60)).append("\n");

            for (SSHSession session : activeSessions.values()) {
                String current = session.sessionId.equals(currentSimulatedSSHSession) ? " [CURRENT]" : "";
                result.append(String.format("%-10s %-15s %-20s %s%s\n",
                        session.sessionId, session.connectedUser, session.targetDevice.hostname,
                        new SimpleDateFormat("HH:mm:ss").format(new Date(session.connectionTime)), current));
            }
        }

        if (realSSHSessions.isEmpty() && activeSessions.isEmpty()) {
            result.append("No active SSH sessions.");
        }

        callback.onSuccess(result.toString());
    }

    private void handleSSHDisconnect(String[] args, CommandCallback callback) {
        if (args.length < 1) {
            if (currentSSHSessionId != null) {
                disconnectRealSSH();
                callback.onSuccess("Disconnected real SSH session");
                callback.onDirectoryChanged();
            } else if (currentSimulatedSSHSession != null) {
                String result = disconnectSSH(currentSimulatedSSHSession);
                currentSimulatedSSHSession = null;
                inSSHMode = false;
                callback.onSuccess(result);
                callback.onDirectoryChanged();
            } else {
                callback.onError("No active SSH session to disconnect.");
            }
        } else {
            String sessionId = args[0];
            if (realSSHSessions.containsKey(sessionId)) {
                Session session = realSSHSessions.remove(sessionId);
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
                if (sessionId.equals(currentSSHSessionId)) {
                    currentSSHSessionId = null;
                    inSSHMode = false;
                    callback.onDirectoryChanged();
                }
                callback.onSuccess("Disconnected session " + sessionId);
            } else {
                String result = disconnectSSH(sessionId);
                if (sessionId.equals(currentSimulatedSSHSession)) {
                    currentSimulatedSSHSession = null;
                    inSSHMode = false;
                    callback.onDirectoryChanged();
                }
                callback.onSuccess(result);
            }
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

    // Get current prompt
    public String getCurrentPrompt() {
        if (inSSHMode) {
            if (currentSSHSessionId != null) {
                Session session = realSSHSessions.get(currentSSHSessionId);
                if (session != null) {
                    return session.getUserName() + "@" + session.getHost() + ":~$ ";
                }
            } else if (currentSimulatedSSHSession != null) {
                SSHSession session = activeSessions.get(currentSimulatedSSHSession);
                if (session != null) {
                    String shortPath = session.targetDevice.currentDirectory
                            .replace("/home/" + session.connectedUser, "~");
                    return session.connectedUser + "@" + session.targetDevice.hostname + ":" + shortPath + "$ ";
                }
            }
        }
        return terminal.getCurrentUser() + "@localhost:" +
                fsManager.getCurrentDirectory().replace("/storage/emulated/0", "~") + "$ ";
    }

    public boolean isInSSHMode() {
        return inSSHMode;
    }

    // ==================== EXISTING FILE COMMANDS ====================
    private String[] parseCommand(String input) {
        return input.trim().split("\\s+");
    }

    private void handleLs(String[] args, CommandCallback callback) {
        boolean longFormat = false;
        boolean showAll = false;
        boolean showHidden = false;
        String targetDir = null;

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
                List<String> fileNames = new ArrayList<>();
                for (FileItem file : files) {
                    if (!showAll && file.getName().startsWith(".")) continue;
                    fileNames.add(file.getName());
                }

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

        Intent intent = new Intent(context, TextEditorActivity.class);
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

        callback.onSuccess("✓ Changed permissions of '" + filename + "' to " + permissions);
    }

    private void handleFind(String[] args, CommandCallback callback) {
        String searchPath = ".";
        String searchName = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-name") && i + 1 < args.length) {
                searchName = args[i + 1];
                i++;
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
        callback.onSuccess(" ");
    }

    private void handleHelp(CommandCallback callback) {
        StringBuilder help = new StringBuilder();
        help.append("🐧 Linux Terminal - Available Commands:\n\n");
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
        help.append("🔐 SSH Commands (Real & Simulated):\n");
        help.append("  ssh [user@]host   - Connect to remote server (REAL)\n");
        help.append("  ssh-sim [user@]host - Simulated SSH connection\n");
        help.append("  ssh-list          - List available SSH devices\n");
        help.append("  ssh-sessions      - Show active SSH sessions\n");
        help.append("  ssh-disconnect    - Disconnect SSH session\n");
        help.append("  exit/logout       - Exit SSH session\n\n");
        help.append("🌐 Network Commands (Real):\n");
        help.append("  ping <host>       - Test network connectivity\n");
        help.append("  netstat           - Network statistics\n");
        help.append("  ifconfig/ip       - Network interface info\n");
        help.append("  nslookup <host>   - DNS lookup\n");
        help.append("  traceroute <host> - Trace route to host\n");
        help.append("  curl <url>        - Transfer data from URL\n");
        help.append("  wget <url>        - Download files\n");
        help.append("  hostname          - Show hostname\n");
        help.append("  wifi-scan         - Scan WiFi networks\n\n");
        help.append("📦 Git Commands (Real):\n");
        help.append("  git init          - Initialize repository\n");
        help.append("  git clone <url>   - Clone repository\n");
        help.append("  git status        - Show working tree status\n");
        help.append("  git add <file>    - Add files to staging\n");
        help.append("  git commit -m \"msg\" - Commit changes\n");
        help.append("  git log           - Show commit history\n");
        help.append("  git branch        - List/create branches\n");
        help.append("  git checkout <br> - Switch branches\n");
        help.append("  git pull          - Fetch and merge\n");
        help.append("  git push          - Push to remote\n\n");
        help.append("⚙️ System Commands:\n");
        help.append("  ps                - List running processes\n");
        help.append("  top               - Show system resources\n");
        help.append("  kill <pid>        - Terminate process\n");
        help.append("  whoami            - Current user\n");
        help.append("  date              - Current date and time\n");
        help.append("  uptime            - System uptime\n");
        help.append("  uname [-a]        - System information\n");
        help.append("  df                - Disk space usage\n");
        help.append("  free              - Memory usage\n");
        help.append("  lsof              - List open files\n");
        help.append("  dmesg             - Kernel messages\n\n");
        help.append("🔧 Utilities:\n");
        help.append("  echo <text>       - Display text\n");
        help.append("  clear             - Clear terminal\n");
        help.append("  help              - Show this help\n");
        help.append("  exit              - Exit terminal\n");

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

        boolean wasRoot = terminal.isRoot();
        terminal.setRoot(true);

        processCommand(String.join(" ", args), new CommandCallback() {
            @Override
            public void onSuccess(String output) {
                terminal.setRoot(wasRoot);
                callback.onSuccess(output);
            }

            @Override
            public void onError(String error) {
                terminal.setRoot(wasRoot);
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
                "chmod", "find", "grep", "ps", "kill", "clear", "help", "exit", "whoami", "ssh",
                "ping", "curl", "wget", "git", "netstat", "ifconfig", "nslookup", "traceroute"};

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
            callback.onSuccess("Linux " + Build.MODEL + " 5.15.0-kali3-amd64 #1 SMP Debian " + Build.VERSION.RELEASE + " " + Build.HARDWARE + " GNU/Linux");
        } else {
            callback.onSuccess("Linux");
        }
    }

    private void handleBpad(String[] args, CommandCallback callback) {
        if (args.length == 0) {
            callback.onError("bpad: missing operand");
            return;
        }
        Intent intent = new Intent(context, TextEditorActivity.class);
        context.startActivity(intent);
    }
}