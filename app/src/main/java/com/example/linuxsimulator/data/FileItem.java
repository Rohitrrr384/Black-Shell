package com.example.linuxsimulator.data;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

// FileItem.java
public class FileItem {
    private String name;
    private boolean isDirectory;
    private long size;
    private long lastModified;
    private boolean canRead;
    private boolean canWrite;
    private boolean canExecute;

    public FileItem(String name, boolean isDirectory, long size, long lastModified) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.canRead = true;
        this.canWrite = true;
        this.canExecute = false;
    }

    public FileItem(String name, boolean isDirectory, long size, long lastModified,
                    boolean canRead, boolean canWrite, boolean canExecute) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canExecute = canExecute;
    }

    // Getters
    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean canRead() {
        return canRead;
    }

    public boolean canWrite() {
        return canWrite;
    }

    public boolean canExecute() {
        return canExecute;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void setCanRead(boolean canRead) {
        this.canRead = canRead;
    }

    public void setCanWrite(boolean canWrite) {
        this.canWrite = canWrite;
    }

    public void setCanExecute(boolean canExecute) {
        this.canExecute = canExecute;
    }

    @Override
    public String toString() {
        return name + (isDirectory ? "/" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FileItem fileItem = (FileItem) obj;
        return name.equals(fileItem.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
