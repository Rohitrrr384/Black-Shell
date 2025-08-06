package com.example.linuxsimulator.data;

// ParentDirItem.java
public class ParentDirItem extends FileItem {

    public ParentDirItem() {
        super("..", true, 0, System.currentTimeMillis());
    }

    @Override
    public String getName() {
        return "..";
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean canExecute() {
        return true;
    }

    @Override
    public String toString() {
        return "../";
    }
}
