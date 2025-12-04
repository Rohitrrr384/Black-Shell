package com.example.linuxsimulator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linuxsimulator.data.FileItem;
import com.example.linuxsimulator.data.ParentDirItem;
import com.example.linuxsimulator.data.FileSystemManager;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileManagerActivity extends AppCompatActivity {
    private RecyclerView fileListView;
    private TextView currentPathView;
    private TextView diskUsageView;
    private FileSystemManager fsManager;
    private FileListAdapter adapter;
    private List<FileItem> currentFiles;
    private FileItem selectedFile;
    private String clipboardPath;
    private boolean clipboardIsCut = false;

    // UI References
    private Button btnBack, btnUp, btnHome, btnRoot;
    private Button btnNewFolder, btnNewFile, btnTerminal, btnPaste, btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        fsManager = FileSystemManager.getInstance(this);
        currentFiles = new ArrayList<>();

        initializeViews();
        setupListeners();
        setupBackPressHandler();
        refreshFileList();
        updateDiskUsage();

        // Handle directory passed from terminal
        String startDir = getIntent().getStringExtra("currentDirectory");
        if (startDir != null) {
            fsManager.changeDirectory(startDir.replace(fsManager.getAbsoluteCurrentDirectory(), ""));
            refreshFileList();
        }
    }

    private void setupBackPressHandler() {
        OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
        dispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fsManager.getCurrentDirectory().equals("/")) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    navigateUp();
                }
            }
        });
    }

    private void initializeViews() {
        fileListView = findViewById(R.id.rv_file_list);
        currentPathView = findViewById(R.id.tv_current_path);
        diskUsageView = findViewById(R.id.tv_disk_usage);

        btnBack = findViewById(R.id.btn_back);
        btnUp = findViewById(R.id.btn_up);
        btnHome = findViewById(R.id.btn_home);
        btnRoot = findViewById(R.id.btn_root);

        btnNewFolder = findViewById(R.id.btn_new_folder);
        btnNewFile = findViewById(R.id.btn_new_file);
        btnTerminal = findViewById(R.id.btn_terminal);
        btnPaste = findViewById(R.id.btn_paste);
        btnRefresh = findViewById(R.id.btn_refresh);

        fileListView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileListAdapter();
        fileListView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> navigateBack());
        btnUp.setOnClickListener(v -> navigateUp());
        btnHome.setOnClickListener(v -> navigateHome());
        btnRoot.setOnClickListener(v -> navigateRoot());

        btnNewFolder.setOnClickListener(v -> showNewFolderDialog());
        btnNewFile.setOnClickListener(v -> showNewFileDialog());
        btnTerminal.setOnClickListener(v -> openTerminal());
        btnPaste.setOnClickListener(v -> pasteFile());
        btnRefresh.setOnClickListener(v -> refreshFileList());
    }

    private void navigateBack() {
        navigateUp();
    }

    private void navigateUp() {
        String currentDir = fsManager.getCurrentDirectory();
        if (!currentDir.isEmpty() && !currentDir.equals("/")) {
            fsManager.changeDirectory("..");
            refreshFileList();
        }
    }

    private void navigateHome() {
        fsManager.changeDirectory("~");
        refreshFileList();
    }

    private void navigateRoot() {
        fsManager.changeDirectory("/");
        refreshFileList();
    }

    private void updateDiskUsage() {
        File currentDir = new File(fsManager.getAbsoluteCurrentDirectory());
        long freeSpace = currentDir.getFreeSpace();
        String freeSpaceFormatted = formatFileSize(freeSpace);
        diskUsageView.setText("Free: " + freeSpaceFormatted);
    }

    private void refreshFileList() {
        currentFiles.clear();

        String currentDir = fsManager.getCurrentDirectory();
        if (!currentDir.isEmpty() && !currentDir.equals("/")) {
            currentFiles.add(new ParentDirItem());
        }

        currentFiles.addAll(fsManager.listFiles());
        adapter.notifyDataSetChanged();

        String displayPath = currentDir.isEmpty() ? "~" : currentDir;
        if (displayPath.startsWith("/home/" + System.getProperty("user.name"))) {
            displayPath = displayPath.replace("/home/" + System.getProperty("user.name"), "~");
        }
        currentPathView.setText(displayPath);

        updateDiskUsage();
        updatePasteButton();
    }

    private void updatePasteButton() {
        if (clipboardPath != null) {
            btnPaste.setAlpha(1.0f);
            btnPaste.setEnabled(true);
        } else {
            btnPaste.setAlpha(0.5f);
            btnPaste.setEnabled(false);
        }
    }

    private void showNewFolderDialog() {
        showInputDialog("📁 Create New Folder", "Folder name:", name -> {
            if (fsManager.createDirectory(name)) {
                refreshFileList();
                showToast("✓ Folder created: " + name);
            } else {
                showToast("✗ Failed to create folder");
            }
        });
    }

    private void showNewFileDialog() {
        showInputDialog("📄 Create New File", "File name:", name -> {
            if (fsManager.createFile(name)) {
                refreshFileList();
                showToast("✓ File created: " + name);
            } else {
                showToast("✗ Failed to create file");
            }
        });
    }

    private void showInputDialog(String title, String hint, InputCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);
        layout.setBackgroundColor(getResources().getColor(R.color.kali_surface));

        EditText input = new EditText(this);
        input.setHint(hint);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 8, 0, 8);
        input.setLayoutParams(inputParams);

        input.setTextColor(getResources().getColor(R.color.kali_text_primary));
        input.setHintTextColor(getResources().getColor(R.color.kali_text_tertiary));
        input.setBackgroundResource(R.drawable.kali_edittext_background);

        layout.addView(input);

        builder.setView(layout);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                callback.onInput(text);
            }
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openTerminal() {
        Intent intent = new Intent(this, TerminalActivity.class);
        intent.putExtra("currentDirectory", fsManager.getAbsoluteCurrentDirectory());
        startActivityForResult(intent, 100);
    }

    // FIXED: Context menu with proper dialog dismissal
    private void showFileContextMenu(FileItem file) {
        selectedFile = file;

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        View contextView = getLayoutInflater().inflate(R.layout.context_menu_dialog, null);

        TextView header = contextView.findViewById(R.id.tv_menu_header);
        header.setText(file.getName());

        builder.setView(contextView);
        AlertDialog dialog = builder.create();

        // FIXED: Pass dialog reference to setupContextMenuItem
        setupContextMenuItem(contextView, R.id.menu_open, "📂 Open", () -> {
            dialog.dismiss();
            openFile(file);
        });

        if (!file.isDirectory()) {
            setupContextMenuItem(contextView, R.id.menu_edit, "✏️ Edit", () -> {
                dialog.dismiss();
                editFile(file);
            });
        } else {
            contextView.findViewById(R.id.menu_edit).setVisibility(View.GONE);
        }

        setupContextMenuItem(contextView, R.id.menu_copy, "📋 Copy", () -> {
            dialog.dismiss();
            copyFile(file);
        });

        setupContextMenuItem(contextView, R.id.menu_cut, "✂️ Cut", () -> {
            dialog.dismiss();
            cutFile(file);
        });

        setupContextMenuItem(contextView, R.id.menu_rename, "🏷️ Rename", () -> {
            dialog.dismiss();
            renameFile(file);
        });

        setupContextMenuItem(contextView, R.id.menu_delete, "🗑️ Delete", () -> {
            dialog.dismiss();
            deleteFile(file);
        });

        setupContextMenuItem(contextView, R.id.menu_properties, "ℹ️ Properties", () -> {
            dialog.dismiss();
            showProperties(file);
        });

        if (file.isDirectory()) {
            setupContextMenuItem(contextView, R.id.menu_terminal, "💻 Open in Terminal", () -> {
                dialog.dismiss();
                openInTerminal(file);
            });
        } else {
            contextView.findViewById(R.id.menu_terminal).setVisibility(View.GONE);
        }

        dialog.show();
    }

    // FIXED: Simplified setupContextMenuItem - action handles dialog dismissal
    private void setupContextMenuItem(View parent, int itemId, String text, Runnable action) {
        TextView item = parent.findViewById(itemId);
        item.setText(text);
        item.setOnClickListener(v -> action.run());
    }

    private void openFile(FileItem file) {
        if (file.isDirectory()) {
            fsManager.changeDirectory(file.getName());
            refreshFileList();
        } else {
            String name = file.getName().toLowerCase();
            if (isEditableFile(name)) {
                editFile(file);
            } else {
                showToast("Cannot open file type: " + name);
            }
        }
    }

    private boolean isEditableFile(String filename) {
        return filename.endsWith(".txt") || filename.endsWith(".sh") || filename.endsWith(".py") ||
                filename.endsWith(".java") || filename.endsWith(".c") || filename.endsWith(".cpp") ||
                filename.endsWith(".html") || filename.endsWith(".css") || filename.endsWith(".js") ||
                filename.endsWith(".xml") || filename.endsWith(".json") || filename.endsWith(".log") ||
                filename.endsWith(".conf") || filename.endsWith(".cfg") || filename.endsWith(".ini");
    }

    private void editFile(FileItem file) {
        Intent intent = new Intent(this, TextEditorActivity.class);
        intent.putExtra("filePath", fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName());
        intent.putExtra("fileName", file.getName());
        startActivity(intent);
    }

    private void openInTerminal(FileItem file) {
        if (file.isDirectory()) {
            Intent intent = new Intent(this, TerminalActivity.class);
            intent.putExtra("currentDirectory", fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName());
            startActivity(intent);
        }
    }

    private void renameFile(FileItem file) {
        showInputDialog("🏷️ Rename File", "New name:", newName -> {
            if (fsManager.renameFile(file.getName(), newName)) {
                refreshFileList();
                showToast("✓ Renamed to: " + newName);
            } else {
                showToast("✗ Failed to rename file");
            }
        });
    }

    private void copyFile(FileItem file) {
        clipboardPath = fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName();
        clipboardIsCut = false;
        showToast("📋 Copied: " + file.getName());
        updatePasteButton();
    }

    private void cutFile(FileItem file) {
        clipboardPath = fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName();
        clipboardIsCut = true;
        showToast("✂️ Cut: " + file.getName());
        updatePasteButton();
    }

    private void deleteFile(FileItem file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("🗑️ Delete");
        builder.setMessage("Delete " + file.getName() + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            if (fsManager.deleteFile(file.getName())) {
                refreshFileList();
                showToast("✓ Deleted: " + file.getName());
            } else {
                showToast("✗ Failed to delete file");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showProperties(FileItem file) {
        File f = new File(fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName());

        String type = file.isDirectory() ? "Directory" : "File";
        String size = file.isDirectory() ? "---" : formatFileSize(file.getSize());
        String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(f.lastModified()));
        String permissions = getPermissions(f);

        String info = "Name: " + file.getName() + "\n" +
                "Type: " + type + "\n" +
                "Size: " + size + "\n" +
                "Modified: " + modified + "\n" +
                "Permissions: " + permissions + "\n" +
                "Path: " + f.getAbsolutePath();

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.KaliAlertDialog);
        builder.setTitle("ℹ️ Properties");
        builder.setMessage(info);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private String getPermissions(File file) {
        StringBuilder perms = new StringBuilder();
        perms.append(file.canRead() ? "r" : "-");
        perms.append(file.canWrite() ? "w" : "-");
        perms.append(file.canExecute() ? "x" : "-");
        return perms.toString();
    }

    private void pasteFile() {
        if (clipboardPath == null) {
            showToast("Nothing to paste");
            return;
        }

        File source = new File(clipboardPath);
        String fileName = source.getName();
        String destPath = fsManager.getAbsoluteCurrentDirectory() + "/" + fileName;

        if (clipboardIsCut) {
            if (fsManager.moveFile(clipboardPath, destPath)) {
                refreshFileList();
                showToast("✓ Moved: " + fileName);
                clipboardPath = null;
                updatePasteButton();
            } else {
                showToast("✗ Failed to move file");
            }
        } else {
            if (fsManager.copyFile(clipboardPath, destPath)) {
                refreshFileList();
                showToast("✓ Pasted: " + fileName);
            } else {
                showToast("✗ Failed to copy file");
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class FileListAdapter extends RecyclerView.Adapter<FileViewHolder> {

        @Override
        public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = getLayoutInflater().inflate(R.layout.file_list_item, parent, false);
            return new FileViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(FileViewHolder holder, int position) {
            FileItem file = currentFiles.get(position);

            if (file instanceof ParentDirItem) {
                holder.iconView.setText("📁");
                holder.nameView.setText("..");
                holder.typeView.setText("Parent directory");
                holder.sizeView.setText("---");
                holder.permissionsView.setText("---");
            } else {
                if (file.isDirectory()) {
                    holder.iconView.setText("📁");
                    holder.nameView.setTextColor(getResources().getColor(R.color.kali_blue));
                    holder.typeView.setText("Directory");
                    holder.sizeView.setText("---");
                } else {
                    String name = file.getName().toLowerCase();
                    holder.iconView.setText(getFileIcon(name));
                    holder.nameView.setTextColor(getResources().getColor(R.color.kali_text_primary));
                    holder.typeView.setText(getFileType(name));
                    holder.sizeView.setText(formatFileSize(file.getSize()));
                }

                holder.nameView.setText(file.getName());

                File f = new File(fsManager.getAbsoluteCurrentDirectory() + "/" + file.getName());
                holder.permissionsView.setText(getPermissions(f));

                String perms = getPermissions(f);
                if (perms.contains("x")) {
                    holder.permissionsView.setTextColor(getResources().getColor(R.color.kali_green));
                } else if (perms.contains("w")) {
                    holder.permissionsView.setTextColor(getResources().getColor(R.color.kali_orange));
                } else {
                    holder.permissionsView.setTextColor(getResources().getColor(R.color.kali_text_tertiary));
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (file instanceof ParentDirItem) {
                    navigateUp();
                } else {
                    openFile(file);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!(file instanceof ParentDirItem)) {
                    showFileContextMenu(file);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return currentFiles.size();
        }
    }

    private String getFileIcon(String filename) {
        if (filename.endsWith(".txt") || filename.endsWith(".log")) return "📄";
        if (filename.endsWith(".sh") || filename.endsWith(".py") ||
                filename.endsWith(".java") || filename.endsWith(".c") || filename.endsWith(".cpp")) return "📜";
        if (filename.endsWith(".html") || filename.endsWith(".css") || filename.endsWith(".js")) return "🌐";
        if (filename.endsWith(".xml") || filename.endsWith(".json")) return "⚙️";
        if (filename.endsWith(".zip") || filename.endsWith(".tar") || filename.endsWith(".gz")) return "📦";
        if (filename.endsWith(".jpg") || filename.endsWith(".png") || filename.endsWith(".gif")) return "🖼️";
        return "📄";
    }

    private String getFileType(String filename) {
        if (filename.endsWith(".txt")) return "Text file";
        if (filename.endsWith(".sh")) return "Shell script";
        if (filename.endsWith(".py")) return "Python script";
        if (filename.endsWith(".java")) return "Java source";
        if (filename.endsWith(".c")) return "C source";
        if (filename.endsWith(".cpp")) return "C++ source";
        if (filename.endsWith(".html")) return "HTML document";
        if (filename.endsWith(".css")) return "CSS stylesheet";
        if (filename.endsWith(".js")) return "JavaScript";
        if (filename.endsWith(".xml")) return "XML document";
        if (filename.endsWith(".json")) return "JSON data";
        if (filename.endsWith(".log")) return "Log file";
        return "File";
    }

    private static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView iconView, nameView, typeView, sizeView, permissionsView, contextMenuView;

        public FileViewHolder(View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.tv_file_icon);
            nameView = itemView.findViewById(R.id.tv_file_name);
            typeView = itemView.findViewById(R.id.tv_file_type);
            sizeView = itemView.findViewById(R.id.tv_file_size);
            permissionsView = itemView.findViewById(R.id.tv_permissions);
            contextMenuView = itemView.findViewById(R.id.tv_context_menu);
        }
    }

    private interface InputCallback {
        void onInput(String input);
    }

    public static void launch(Activity context, String directory) {
        Intent intent = new Intent(context, FileManagerActivity.class);
        intent.putExtra("currentDirectory", directory);
        context.startActivity(intent);
    }
}