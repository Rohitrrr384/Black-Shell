package com.example.linuxsimulator;

import android.content.Context;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class GitSimulator {
    private File currentDir;
    private Map<String, RepoData> repos;
    private String currentBranch = "main";
    private Context context;

    public GitSimulator(File currentDir, Context context) {
        this.currentDir = currentDir;
        this.context = context;
        this.repos = new HashMap<>();
    }

    // Add method to update current directory
    public void setCurrentDirectory(File newDir) {
        this.currentDir = newDir;
    }

    // Add method to get current repo name based on current directory
    public String getCurrentRepoName() {
        // Check if current directory or any parent directory is a git repo
        File dir = currentDir;
        while (dir != null) {
            for (Map.Entry<String, RepoData> entry : repos.entrySet()) {
                if (entry.getValue().path.equals(dir)) {
                    return entry.getKey();
                }
                // Also check if we're inside the repo directory
                if (dir.getAbsolutePath().startsWith(entry.getValue().path.getAbsolutePath())) {
                    return entry.getKey();
                }
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private static class RepoData {
        File path;
        String origin;
        List<String> files;
        List<String> stagedFiles;
        List<Commit> commits;
        List<String> branches;
        boolean remoteExists;

        RepoData(File path, String origin) {
            this.path = path;
            this.origin = origin;
            this.files = new ArrayList<>();
            this.stagedFiles = new ArrayList<>();
            this.commits = new ArrayList<>();
            this.branches = new ArrayList<>();
            this.branches.add("main");
            this.remoteExists = true;
        }
    }

    private static class Commit {
        String hash;
        String message;
        String author;
        String date;
        String branch;
        List<String> files;

        Commit(String hash, String message, String author, String date, String branch, List<String> files) {
            this.hash = hash;
            this.message = message;
            this.author = author;
            this.date = date;
            this.branch = branch;
            this.files = files;
        }
    }

    public String clone(String repoUrl) {
        try {
            String[] parts = repoUrl.split("/");
            String repoName = parts[parts.length - 1];
            if (repoName.endsWith(".git")) {
                repoName = repoName.substring(0, repoName.length() - 4);
            }

            File repoPath = new File(currentDir, repoName);
            if (repoPath.exists()) {
                return "Error: Directory '" + repoName + "' already exists.";
            }

            if (!repoPath.mkdirs()) {
                return "Error: Failed to create repository directory.";
            }

            // Simulate download progress
            StringBuilder output = new StringBuilder();
            output.append("Cloning into '").append(repoName).append("'...\n");

            // Simulate some realistic git clone output
            output.append("remote: Enumerating objects: 500, done.\n");
            output.append("remote: Counting objects: 100% (500/500), done.\n");
            output.append("remote: Compressing objects: 100% (300/300), done.\n");
            output.append("remote: Total 500 (delta 200), reused 450 (delta 150), pack-reused 0\n");
            output.append("Receiving objects: 100% (500/500), 1.25 MiB | 2.50 MiB/s, done.\n");
            output.append("Resolving deltas: 100% (200/200), done.\n");

            // Initialize repo data
            RepoData repoData = new RepoData(repoPath, repoUrl);

            // Add some sample files to make it more realistic
            repoData.files.add("README.md");
            repoData.files.add("src/main.java");
            repoData.files.add(".gitignore");

            repoData.commits.add(new Commit(
                    generateHash(),
                    "Initial commit",
                    "repository-owner <owner@example.com>",
                    getCurrentDateTime(),
                    currentBranch,
                    new ArrayList<>(repoData.files)
            ));

            repos.put(repoName, repoData);
            return output.toString();

        } catch (Exception e) {
            return "Error cloning repository: " + e.getMessage();
        }
    }
    

    public String status(String repoName) {
        if (!repos.containsKey(repoName)) {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        RepoData repo = repos.get(repoName);
        StringBuilder output = new StringBuilder();

        output.append("On branch ").append(currentBranch).append("\n");

        // Check if there are unpushed commits
        if (repo.commits.size() > 1) {
            int unpushedCommits = repo.commits.size() - 1; // Excluding initial commit
            output.append("Your branch is ahead of 'origin/").append(currentBranch).append("' by ")
                    .append(unpushedCommits).append(" commit").append(unpushedCommits > 1 ? "s" : "").append(".\n")
                    .append("  (use \"git push\" to publish your local commits)\n\n");
        } else {
            output.append("Your branch is up to date with 'origin/").append(currentBranch).append("'.\n\n");
        }

        if (!repo.stagedFiles.isEmpty()) {
            output.append("Changes to be committed:\n")
                    .append("  (use \"git restore --staged <file>...\" to unstage)\n");
            for (String file : repo.stagedFiles) {
                // Check if file existed before
                boolean isNewFile = true;
                for (Commit commit : repo.commits) {
                    if (commit.files.contains(file)) {
                        isNewFile = false;
                        break;
                    }
                }
                output.append("        ").append(isNewFile ? "new file:   " : "modified:   ").append(file).append("\n");
            }
            output.append("\n");
        }

        // Find untracked files (files in directory but not in any commit and not staged)
        List<String> untracked = new ArrayList<>();
        File repoDir = repo.path;
        if (repoDir.exists()) {
            addUntrackedFiles(repoDir, repo, untracked, "");
        }

        if (!untracked.isEmpty()) {
            output.append("Untracked files:\n")
                    .append("  (use \"git add <file>...\" to include in what will be committed)\n");
            for (String file : untracked) {
                output.append("        ").append(file).append("\n");
            }
            output.append("\n");
        }

        if (repo.stagedFiles.isEmpty() && untracked.isEmpty()) {
            output.append("nothing to commit, working tree clean\n");
        }

        return output.toString();
    }

    private void addUntrackedFiles(File dir, RepoData repo, List<String> untracked, String relativePath) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String fileName = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();

                if (file.isDirectory() && !file.getName().equals(".git")) {
                    addUntrackedFiles(file, repo, untracked, fileName);
                } else if (file.isFile()) {
                    // Check if file is tracked (in any commit) or staged
                    boolean isTracked = false;
                    for (Commit commit : repo.commits) {
                        if (commit.files.contains(fileName)) {
                            isTracked = true;
                            break;
                        }
                    }
                    if (!isTracked && !repo.stagedFiles.contains(fileName)) {
                        untracked.add(fileName);
                    }
                }
            }
        }
    }

    public String add(String repoName, List<String> files) {
        if (!repos.containsKey(repoName)) {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        RepoData repo = repos.get(repoName);
        List<String> addedFiles = new ArrayList<>();

        for (String file : files) {
            if (file.equals(".")) {
                // Add all untracked files
                File repoDir = repo.path;
                List<String> allFiles = new ArrayList<>();
                addAllFiles(repoDir, allFiles, "");
                for (String f : allFiles) {
                    if (!repo.stagedFiles.contains(f)) {
                        repo.stagedFiles.add(f);
                        addedFiles.add(f);
                    }
                }
            } else {
                // Add specific file
                File targetFile = new File(repo.path, file);
                if (targetFile.exists() || repo.files.contains(file)) {
                    if (!repo.stagedFiles.contains(file)) {
                        repo.stagedFiles.add(file);
                        addedFiles.add(file);
                    }
                } else {
                    return "fatal: pathspec '" + file + "' did not match any files";
                }
            }
        }

        return ""; // Git add typically shows no output on success
    }

    private void addAllFiles(File dir, List<String> allFiles, String relativePath) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals(".git")) continue;

                String fileName = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                if (file.isDirectory()) {
                    addAllFiles(file, allFiles, fileName);
                } else {
                    allFiles.add(fileName);
                }
            }
        }
    }

    public String commit(String repoName, String message) {
        if (!repos.containsKey(repoName)) {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        RepoData repo = repos.get(repoName);
        if (repo.stagedFiles.isEmpty()) {
            return "On branch " + currentBranch + "\nnothing to commit, working tree clean";
        }

        Commit commit = new Commit(
                generateHash(),
                message,
                "user <user@example.com>",
                getCurrentDateTime(),
                currentBranch,
                new ArrayList<>(repo.stagedFiles)
        );

        repo.commits.add(commit);

        // Count insertions (simulate)
        int insertions = repo.stagedFiles.size() * 10; // Rough estimate
        String filesChanged = repo.stagedFiles.size() == 1 ? "1 file changed" : repo.stagedFiles.size() + " files changed";

        repo.stagedFiles.clear();

        return String.format("[%s %s] %s\n %s, %d insertions(+)",
                currentBranch,
                commit.hash.substring(0, 7),
                message,
                filesChanged,
                insertions);
    }

    public String push(String repoName) {
        if (!repos.containsKey(repoName)) {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        RepoData repo = repos.get(repoName);
        if (!repo.remoteExists) {
            return "fatal: 'origin' does not appear to be a git repository\nfatal: Could not read from remote repository.\n\nPlease make sure you have the correct access rights\nand the repository exists.";
        }

        // Check if there are commits to push (more than just initial commit)
        int commitsToShow = 0;
        for (Commit commit : repo.commits) {
            if (!commit.message.equals("Initial commit")) {
                commitsToShow++;
            }
        }

        if (commitsToShow == 0) {
            return "Everything up-to-date";
        }

        StringBuilder output = new StringBuilder();
        output.append("Enumerating objects: ").append(commitsToShow * 3).append(", done.\n")
                .append("Counting objects: 100% (").append(commitsToShow * 3).append("/").append(commitsToShow * 3).append("), done.\n")
                .append("Delta compression using up to 8 threads\n")
                .append("Compressing objects: 100% (").append(commitsToShow * 2).append("/").append(commitsToShow * 2).append("), done.\n")
                .append(String.format(Locale.US, "Writing objects: 100%% (%d/%d), %d bytes | %d.00 KiB/s, done.\n",
                        commitsToShow * 3, commitsToShow * 3, commitsToShow * 1024, commitsToShow))
                .append("Total ").append(commitsToShow * 3).append(" (delta ").append(commitsToShow).append("), reused 0 (delta 0), pack-reused 0\n")
                .append("To ").append(repo.origin).append("\n");

        // Show the range of commits being pushed
        if (repo.commits.size() >= 2) {
            String oldHash = repo.commits.get(repo.commits.size() - commitsToShow - 1).hash.substring(0, 7);
            String newHash = repo.commits.get(repo.commits.size() - 1).hash.substring(0, 7);
            output.append("   ").append(oldHash).append("..").append(newHash).append("  ")
                    .append(currentBranch).append(" -> ").append(currentBranch).append("\n");
        }

        return output.toString();
    }

    public String log(String repoName) {
        if (!repos.containsKey(repoName)) {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        RepoData repo = repos.get(repoName);
        if (repo.commits.isEmpty()) {
            return "fatal: your current branch '" + currentBranch + "' does not have any commits yet";
        }

        StringBuilder output = new StringBuilder();
        // Show commits in reverse order (newest first)
        for (int i = repo.commits.size() - 1; i >= 0; i--) {
            Commit commit = repo.commits.get(i);
            output.append("commit ").append(commit.hash).append("\n")
                    .append("Author: ").append(commit.author).append("\n")
                    .append("Date:   ").append(commit.date).append("\n\n")
                    .append("    ").append(commit.message).append("\n");

            if (i > 0) {
                output.append("\n");
            }
        }

        return output.toString();
    }

    public String help() {
        return "Git Simulation Commands:\n" +
                "  clone <repo_url>    - Clone a repository\n" +
                "  status              - Show the working tree status\n" +
                "  add <file>          - Add file contents to the index\n" +
                "  add .               - Add all files to the index\n" +
                "  commit -m <message> - Record changes to the repository\n" +
                "  push                - Update remote refs along with associated objects\n" +
                "  log                 - Show commit history\n" +
                "  help                - Show this help message";
    }

    private String generateHash() {
        Random random = new Random();
        StringBuilder hash = new StringBuilder();
        String chars = "0123456789abcdef";
        for (int i = 0; i < 40; i++) {
            hash.append(chars.charAt(random.nextInt(chars.length())));
        }
        return hash.toString();
    }

    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
        return sdf.format(new Date());
    }
}