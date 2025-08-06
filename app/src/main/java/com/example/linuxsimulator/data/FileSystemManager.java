package com.example.linuxsimulator.data;// FileSystemManager.java
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class FileSystemManager {
    private static FileSystemManager instance;
    private String currentDirectory;
    private String homeDirectory;
    private Context context;
    private Stack<String> navigationHistory;

    private FileSystemManager(Context context) {
        this.context = context;
        this.navigationHistory = new Stack<>();

        // Set home directory
        homeDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (homeDirectory == null || homeDirectory.isEmpty()) {
            homeDirectory = "/storage/emulated/0";
        }

        // Start in home directory
        currentDirectory = "";
    }

    public static synchronized FileSystemManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileSystemManager(context);
        }
        return instance;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public String getAbsoluteCurrentDirectory() {
        if (currentDirectory.isEmpty() || currentDirectory.equals("~")) {
            return homeDirectory;
        }

        if (currentDirectory.startsWith("/")) {
            return currentDirectory;
        }

        return homeDirectory + "/" + currentDirectory;
    }

    public boolean changeDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        String newPath;
        String absoluteCurrentDir = getAbsoluteCurrentDirectory();

        if (path.equals("~")) {
            // Go to home directory
            currentDirectory = "";
            return true;
        } else if (path.equals("/")) {
            // Go to root directory
            currentDirectory = "/";
            return true;
        } else if (path.equals("..")) {
            // Go up one directory
            if (currentDirectory.isEmpty() || currentDirectory.equals("/")) {
                return false; // Already at root
            }

            File parentFile = new File(absoluteCurrentDir).getParentFile();
            if (parentFile != null && parentFile.exists()) {
                String parentPath = parentFile.getAbsolutePath();
                if (parentPath.equals(homeDirectory)) {
                    currentDirectory = "";
                } else if (parentPath.startsWith(homeDirectory + "/")) {
                    currentDirectory = parentPath.substring(homeDirectory.length() + 1);
                } else {
                    currentDirectory = parentPath;
                }
                return true;
            }
            return false;
        } else if (path.startsWith("/")) {
            // Absolute path
            newPath = path;
        } else {
            // Relative path
            newPath = absoluteCurrentDir + "/" + path;
        }

        File newDir = new File(newPath);
        if (newDir.exists() && newDir.isDirectory()) {
            if (newPath.equals(homeDirectory)) {
                currentDirectory = "";
            } else if (newPath.startsWith(homeDirectory + "/")) {
                currentDirectory = newPath.substring(homeDirectory.length() + 1);
            } else {
                currentDirectory = newPath;
            }
            return true;
        }

        return false;
    }

    public List<FileItem> listFiles() {
        List<FileItem> fileList = new ArrayList<>();
        File dir = new File(getAbsoluteCurrentDirectory());

        if (!dir.exists() || !dir.isDirectory()) {
            return fileList;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            // Sort files: directories first, then by name
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                } else {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });

            for (File file : files) {
                // Skip hidden files (starting with .)
                if (!file.getName().startsWith(".") || showHiddenFiles()) {
                    FileItem item = new FileItem(
                            file.getName(),
                            file.isDirectory(),
                            file.length(),
                            file.lastModified(),
                            file.canRead(),
                            file.canWrite(),
                            file.canExecute()
                    );
                    fileList.add(item);
                }
            }
        }

        return fileList;
    }

    public boolean createDirectory(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        File newDir = new File(getAbsoluteCurrentDirectory(), name.trim());
        return newDir.mkdir();
    }

    public boolean createFile(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        try {
            File newFile = new File(getAbsoluteCurrentDirectory(), name.trim());
            return newFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteFile(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        File file = new File(getAbsoluteCurrentDirectory(), name);
        return deleteRecursive(file);
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    public boolean renameFile(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.trim().isEmpty() || newName.trim().isEmpty()) {
            return false;
        }

        File oldFile = new File(getAbsoluteCurrentDirectory(), oldName);
        File newFile = new File(getAbsoluteCurrentDirectory(), newName.trim());

        return oldFile.renameTo(newFile);
    }

    public boolean copyFile(String sourcePath, String destPath) {
        try {
            File source = new File(sourcePath);
            File dest = new File(destPath);

            if (source.isDirectory()) {
                return copyDirectory(source, dest);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean copyDirectory(File source, File dest) {
        try {
            if (!dest.exists()) {
                dest.mkdirs();
            }

            File[] files = source.listFiles();
            if (files != null) {
                for (File file : files) {
                    File destFile = new File(dest, file.getName());
                    if (file.isDirectory()) {
                        if (!copyDirectory(file, destFile)) {
                            return false;
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean moveFile(String sourcePath, String destPath) {
        try {
            File source = new File(sourcePath);
            File dest = new File(destPath);

            // If it's a simple rename in the same directory, use renameTo
            if (source.getParent().equals(dest.getParent())) {
                return source.renameTo(dest);
            }

            // Otherwise, copy then delete
            if (copyFile(sourcePath, destPath)) {
                return deleteRecursive(source);
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String readFile(String filename) {
        try {
            File file = new File(getAbsoluteCurrentDirectory(), filename);
            if (!file.exists() || file.isDirectory()) {
                return null;
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean writeFile(String filename, String content) {
        try {
            File file = new File(getAbsoluteCurrentDirectory(), filename);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    public long getDirectorySize(File directory) {
        long size = 0;
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getDirectorySize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }

    public boolean hasPermission(String filename, String permission) {
        File file = new File(getAbsoluteCurrentDirectory(), filename);
        switch (permission.toLowerCase()) {
            case "read":
            case "r":
                return file.canRead();
            case "write":
            case "w":
                return file.canWrite();
            case "execute":
            case "x":
                return file.canExecute();
            default:
                return false;
        }
    }

    public File getCurrentDirectoryFile() {
        return new File(getAbsoluteCurrentDirectory());
    }

    public String getHomeDirectory() {
        return homeDirectory;
    }

    // Navigation history methods
    public void pushToHistory(String directory) {
        navigationHistory.push(directory);
    }

    public String popFromHistory() {
        if (!navigationHistory.isEmpty()) {
            return navigationHistory.pop();
        }
        return null;
    }

    public boolean hasHistory() {
        return !navigationHistory.isEmpty();
    }

    // Settings
    private boolean showHiddenFiles() {
        // You can implement a settings system here
        return false; // Default: don't show hidden files
    }

    // Get detailed file information
    public FileItem getFileInfo(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }

        File file = new File(getAbsoluteCurrentDirectory(), filename);
        if (!file.exists()) {
            return null;
        }

        return new FileItem(
                file.getName(),
                file.isDirectory(),
                file.length(),
                file.lastModified(),
                file.canRead(),
                file.canWrite(),
                file.canExecute()
        );
    }

    // Get file information by absolute path
    public FileItem getFileInfoByPath(String absolutePath) {
        if (absolutePath == null || absolutePath.trim().isEmpty()) {
            return null;
        }

        File file = new File(absolutePath);
        if (!file.exists()) {
            return null;
        }

        return new FileItem(
                file.getName(),
                file.isDirectory(),
                file.length(),
                file.lastModified(),
                file.canRead(),
                file.canWrite(),
                file.canExecute()
        );
    }

    // Check if file exists
    public boolean fileExists(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        File file = new File(getAbsoluteCurrentDirectory(), filename);
        return file.exists();
    }

    // Get file MIME type (basic implementation)
    public String getFileType(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unknown";
        }

        String extension = getFileExtension(filename).toLowerCase();

        // Basic MIME type mapping
        switch (extension) {
            case "txt":
            case "log":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "text/javascript";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "pdf":
                return "application/pdf";
            case "zip":
                return "application/zip";
            case "tar":
            case "gz":
                return "application/gzip";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "sh":
                return "application/x-shellscript";
            case "py":
                return "text/x-python";
            case "java":
                return "text/x-java-source";
            case "c":
                return "text/x-c";
            case "cpp":
            case "cxx":
                return "text/x-c++";
            default:
                return "application/octet-stream";
        }
    }

    // Get file extension
    public String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1);
    }

    // Search functionality
    public List<FileItem> searchFiles(String query) {
        List<FileItem> results = new ArrayList<>();
        searchInDirectory(new File(getAbsoluteCurrentDirectory()), query.toLowerCase(), results);
        return results;
    }

    private void searchInDirectory(File directory, String query, List<FileItem> results) {
        if (!directory.isDirectory()) return;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().toLowerCase().contains(query)) {
                    FileItem item = new FileItem(
                            file.getAbsolutePath(), // Use full path for search results
                            file.isDirectory(),
                            file.length(),
                            file.lastModified(),
                            file.canRead(),
                            file.canWrite(),
                            file.canExecute()
                    );
                    results.add(item);
                }

                // Recursively search subdirectories (limit depth to avoid infinite loops)
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    searchInDirectory(file, query, results);
                }
            }
        }
    }
}