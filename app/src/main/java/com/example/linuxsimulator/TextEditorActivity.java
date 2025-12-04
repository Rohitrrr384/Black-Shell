package com.example.linuxsimulator;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class TextEditorActivity extends AppCompatActivity {

    // UI Components
    private EditText textEditor;
    private TextView lineNumbers;
    private TextView currentFilePath;
    private TextView fileStatus;
    private TextView lineInfo;
    private TextView charCount;
    private EditText searchEditText;
    private EditText commandInput;
    private LinearLayout searchContainer;
    private ScrollView editorScrollView;
    private ScrollView lineNumberScrollView;

    // File Management
    private File currentFile;
    private boolean isModified = false;
    private String originalContent = "";

    // Undo/Redo System
    private Stack<String> undoStack = new Stack<>();
    private Stack<String> redoStack = new Stack<>();
    private boolean isUndoRedoOperation = false;

    // Search
    private int currentSearchPosition = -1;
    private String lastSearchQuery = "";

    // Permissions
    private static final int STORAGE_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        initializeViews();
        setupEventListeners();
        setupBackPressHandler();
        requestStoragePermission();
        updateLineNumbers();
        updateStatusBar();

        // Load file if path is provided
        String filePath = getIntent().getStringExtra("filePath");
        if (filePath != null) {
            File file = new File(filePath);
            if (file.exists()) {
                loadFile(file);
            }
        }
    }

    private void initializeViews() {
        textEditor = findViewById(R.id.textEditor);
        lineNumbers = findViewById(R.id.lineNumbers);
        currentFilePath = findViewById(R.id.currentFilePath);
        fileStatus = findViewById(R.id.fileStatus);
        lineInfo = findViewById(R.id.lineInfo);
        charCount = findViewById(R.id.charCount);
        searchEditText = findViewById(R.id.searchEditText);
        commandInput = findViewById(R.id.commandInput);
        searchContainer = findViewById(R.id.searchContainer);
        editorScrollView = findViewById(R.id.editorScrollView);
        lineNumberScrollView = findViewById(R.id.lineNumberScrollView);
    }

    private void setupEventListeners() {
        // Text change listener
        textEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLineNumbers();
                updateStatusBar();
                if (!isUndoRedoOperation) {
                    setModified(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Save current state for undo (only if not undo/redo operation)
                if (!isUndoRedoOperation) {
                    String currentText = s.toString();
                    if (undoStack.isEmpty() || !undoStack.peek().equals(currentText)) {
                        undoStack.push(currentText);
                        if (undoStack.size() > 50) { // Limit undo stack size
                            undoStack.remove(0);
                        }
                        redoStack.clear();
                    }
                }
            }
        });

        // Scroll synchronization
        editorScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                lineNumberScrollView.scrollTo(0, scrollY));

        // Button listeners
        findViewById(R.id.btnNew).setOnClickListener(v -> newFile());
        findViewById(R.id.btnOpen).setOnClickListener(v -> openFile());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveFile());
        findViewById(R.id.btnSaveAs).setOnClickListener(v -> saveAsFile());
        findViewById(R.id.btnUndo).setOnClickListener(v -> undo());
        findViewById(R.id.btnRedo).setOnClickListener(v -> redo());
        findViewById(R.id.btnFind).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btnSearchNext).setOnClickListener(v -> searchNext());
        findViewById(R.id.btnSearchClose).setOnClickListener(v -> closeSearch());
        findViewById(R.id.btnExecuteCommand).setOnClickListener(v -> executeCommand());

        // Command input listener
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                executeCommand();
                return true;
            }
            return false;
        });

        // Search input listener
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchNext();
                return true;
            }
            return false;
        });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    showToast("Please grant storage permission");
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        STORAGE_PERMISSION_CODE);
            }
        }
    }

    private void newFile() {
        if (isModified) {
            showSaveDialog(() -> createNewFile());
        } else {
            createNewFile();
        }
    }

    private void createNewFile() {
        isUndoRedoOperation = true;
        textEditor.setText("");
        isUndoRedoOperation = false;
        currentFile = null;
        currentFilePath.setText("untitled.txt");
        originalContent = "";
        setModified(false);
        undoStack.clear();
        redoStack.clear();
        showToast("New file created");
    }

    private void openFile() {
        if (isModified) {
            showSaveDialog(this::selectFileToOpen);
        } else {
            selectFileToOpen();
        }
    }

    private void selectFileToOpen() {
        // Get app's documents directory (more reliable than external storage)
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (storageDir == null || !storageDir.exists()) {
            storageDir = Environment.getExternalStorageDirectory();
        }
        showFileDialog(storageDir, true, this::loadFile);
    }

    private void loadFile(File file) {
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            isUndoRedoOperation = true;
            textEditor.setText(content.toString());
            isUndoRedoOperation = false;

            currentFile = file;
            currentFilePath.setText(file.getName());
            originalContent = content.toString();
            setModified(false);
            undoStack.clear();
            redoStack.clear();
            undoStack.push(content.toString()); // Add initial state
            showToast("File opened: " + file.getName());
        } catch (IOException e) {
            showToast("Error opening file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveAsFile();
        } else {
            saveToFile(currentFile);
        }
    }

    private void saveAsFile() {
        // Use app's documents directory for saving
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (storageDir == null || !storageDir.exists()) {
            storageDir = Environment.getExternalStorageDirectory();
        }

        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        showSaveDialog(storageDir);
    }

    private void saveToFile(File file) {
        try {
            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Write to file
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(textEditor.getText().toString());
            writer.close();

            currentFile = file;
            currentFilePath.setText(file.getName());
            originalContent = textEditor.getText().toString();
            setModified(false);
            showToast("✓ File saved: " + file.getName());
        } catch (IOException e) {
            showToast("Error saving file: " + e.getMessage());
            e.printStackTrace();

            // Show detailed error dialog
            new AlertDialog.Builder(this)
                    .setTitle("Save Error")
                    .setMessage("Failed to save file:\n" + e.getMessage() +
                            "\n\nTry saving to a different location.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            showToast("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.push(undoStack.pop());
            String previousState = undoStack.peek();

            isUndoRedoOperation = true;
            textEditor.setText(previousState);
            textEditor.setSelection(textEditor.getText().length()); // Move cursor to end
            isUndoRedoOperation = false;

            showToast("Undo");
        } else {
            showToast("Nothing to undo");
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            String nextState = redoStack.pop();
            undoStack.push(nextState);

            isUndoRedoOperation = true;
            textEditor.setText(nextState);
            textEditor.setSelection(textEditor.getText().length()); // Move cursor to end
            isUndoRedoOperation = false;

            showToast("Redo");
        } else {
            showToast("Nothing to redo");
        }
    }

    private void toggleSearch() {
        if (searchContainer.getVisibility() == View.GONE) {
            searchContainer.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
        } else {
            closeSearch();
        }
    }

    private void closeSearch() {
        searchContainer.setVisibility(View.GONE);
        clearSearchHighlight();
    }

    private void searchNext() {
        String query = searchEditText.getText().toString();
        if (query.isEmpty()) {
            showToast("Enter search text");
            return;
        }

        String content = textEditor.getText().toString();
        if (!query.equals(lastSearchQuery)) {
            currentSearchPosition = -1;
            lastSearchQuery = query;
        }

        int startPos = currentSearchPosition + 1;
        int foundPos = content.toLowerCase().indexOf(query.toLowerCase(), startPos);

        if (foundPos == -1 && currentSearchPosition != -1) {
            // Search from beginning
            foundPos = content.toLowerCase().indexOf(query.toLowerCase(), 0);
        }

        if (foundPos != -1) {
            currentSearchPosition = foundPos;
            textEditor.setSelection(foundPos, foundPos + query.length());
            textEditor.requestFocus();
            showToast("Found at position " + foundPos);
        } else {
            showToast("Not found");
        }
    }

    private void clearSearchHighlight() {
        currentSearchPosition = -1;
        lastSearchQuery = "";
    }

    private void executeCommand() {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) return;

        commandInput.setText("");
        processCommand(command);
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                showCommandHelp();
                break;
            case "new":
                newFile();
                break;
            case "open":
                if (parts.length > 1) {
                    openFileByPath(parts[1]);
                } else {
                    openFile();
                }
                break;
            case "save":
                saveFile();
                break;
            case "saveas":
                saveAsFile();
                break;
            case "clear":
                isUndoRedoOperation = true;
                textEditor.setText("");
                isUndoRedoOperation = false;
                break;
            case "count":
                showWordCount();
                break;
            case "goto":
                if (parts.length > 1) {
                    gotoLine(parts[1]);
                } else {
                    showToast("Usage: goto [line_number]");
                }
                break;
            case "find":
                if (parts.length > 1) {
                    searchEditText.setText(parts[1]);
                    toggleSearch();
                    searchNext();
                } else {
                    toggleSearch();
                }
                break;
            case "replace":
                if (parts.length > 2) {
                    replaceText(parts[1], parts[2]);
                } else {
                    showToast("Usage: replace [old_text] [new_text]");
                }
                break;
            case "exit":
                finish();
                break;
            default:
                showToast("Unknown command: " + cmd + ". Type 'help' for available commands.");
                break;
        }
    }

    private void showCommandHelp() {
        String help = "📝 Available Commands:\n\n" +
                "help - Show this help\n" +
                "new - Create new file\n" +
                "open [path] - Open file\n" +
                "save - Save current file\n" +
                "saveas - Save as new file\n" +
                "clear - Clear editor\n" +
                "count - Show word count\n" +
                "goto [line] - Go to line number\n" +
                "find [text] - Search for text\n" +
                "replace [old] [new] - Replace text\n" +
                "exit - Exit editor";

        new AlertDialog.Builder(this)
                .setTitle("Command Help")
                .setMessage(help)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openFileByPath(String path) {
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            loadFile(file);
        } else {
            // Try relative path from documents directory
            File docsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (docsDir != null) {
                file = new File(docsDir, path);
                if (file.exists() && file.isFile()) {
                    loadFile(file);
                    return;
                }
            }
            showToast("File not found: " + path);
        }
    }

    private void showWordCount() {
        String text = textEditor.getText().toString();
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        int lines = text.isEmpty() ? 0 : text.split("\n").length;

        String message = String.format("📊 Document Statistics:\n\nCharacters: %d\nWords: %d\nLines: %d",
                chars, words, lines);
        new AlertDialog.Builder(this)
                .setTitle("Statistics")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void gotoLine(String lineStr) {
        try {
            int line = Integer.parseInt(lineStr);
            String text = textEditor.getText().toString();
            String[] lines = text.split("\n");

            if (line > 0 && line <= lines.length) {
                int position = 0;
                for (int i = 0; i < line - 1; i++) {
                    position += lines[i].length() + 1; // +1 for newline
                }
                textEditor.setSelection(position);
                textEditor.requestFocus();
                showToast("✓ Moved to line " + line);
            } else {
                showToast("Line number out of range (1-" + lines.length + ")");
            }
        } catch (NumberFormatException e) {
            showToast("Invalid line number");
        }
    }

    private void replaceText(String oldText, String newText) {
        String content = textEditor.getText().toString();
        int count = 0;
        String temp = content;
        while (temp.contains(oldText)) {
            temp = temp.replaceFirst(oldText, "");
            count++;
        }

        if (count > 0) {
            String replaced = content.replace(oldText, newText);
            isUndoRedoOperation = true;
            textEditor.setText(replaced);
            isUndoRedoOperation = false;
            showToast("✓ Replaced " + count + " occurrence(s)");
        } else {
            showToast("Text not found: " + oldText);
        }
    }

    private void updateLineNumbers() {
        String text = textEditor.getText().toString();
        int lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;

        StringBuilder lineNums = new StringBuilder();
        for (int i = 1; i <= lineCount; i++) {
            lineNums.append(i).append("\n");
        }
        lineNumbers.setText(lineNums.toString());
    }

    private void updateStatusBar() {
        String text = textEditor.getText().toString();
        int cursorPos = textEditor.getSelectionStart();

        // Calculate line and column
        String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
        String[] lines = beforeCursor.split("\n", -1);
        int line = lines.length;
        int column = lines[lines.length - 1].length() + 1;

        lineInfo.setText(String.format("Ln %d, Col %d", line, column));
        charCount.setText(text.length() + " chars");
    }

    private void setModified(boolean modified) {
        isModified = modified;
        fileStatus.setText(modified ? "●" : "○");
        fileStatus.setTextColor(modified ?
                ContextCompat.getColor(this, android.R.color.holo_red_light) :
                ContextCompat.getColor(this, android.R.color.holo_green_light));
    }

    private void showSaveDialog(Runnable onDiscard) {
        new AlertDialog.Builder(this)
                .setTitle("💾 Unsaved Changes")
                .setMessage("Do you want to save changes before continuing?")
                .setPositiveButton("Save", (dialog, which) -> {
                    saveFile();
                    if (!isModified) { // Only continue if save was successful
                        onDiscard.run();
                    }
                })
                .setNegativeButton("Discard", (dialog, which) -> onDiscard.run())
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showFileDialog(File directory, boolean isOpen, FileDialogCallback callback) {
        if (directory == null || !directory.exists()) {
            showToast("Cannot access directory");
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            showToast("Cannot read directory");
            return;
        }

        List<File> fileList = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();

        // Add parent directory option
        if (directory.getParentFile() != null && directory.getParentFile().canRead()) {
            fileList.add(directory.getParentFile());
            fileNames.add("📁 ../");
        }

        // Add directories first
        for (File file : files) {
            if (file.isDirectory() && file.canRead()) {
                fileList.add(file);
                fileNames.add("📁 " + file.getName());
            }
        }

        // Add text files
        for (File file : files) {
            if (file.isFile() && file.canRead()) {
                String name = file.getName();
                if (name.endsWith(".txt") || name.endsWith(".log") ||
                        name.endsWith(".md") || name.endsWith(".java") ||
                        name.endsWith(".xml") || name.endsWith(".json")) {
                    fileList.add(file);
                    fileNames.add("📄 " + name);
                }
            }
        }

        if (fileList.isEmpty()) {
            showToast("No files found in this directory");
            if (!isOpen) {
                showSaveNameDialog(directory, callback);
            }
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isOpen ? "📂 Open File" : "📂 Save Location");
        builder.setItems(fileNames.toArray(new String[0]), (dialog, which) -> {
            File selectedFile = fileList.get(which);
            if (selectedFile.isDirectory()) {
                showFileDialog(selectedFile, isOpen, callback);
            } else {
                if (isOpen) {
                    callback.onFileSelected(selectedFile);
                } else {
                    // Ask for confirmation to overwrite
                    new AlertDialog.Builder(this)
                            .setTitle("Overwrite File?")
                            .setMessage("File already exists. Overwrite?")
                            .setPositiveButton("Overwrite", (d, w) -> callback.onFileSelected(selectedFile))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        });

        if (!isOpen) {
            builder.setNeutralButton("💾 New File", (dialog, which) -> {
                showSaveNameDialog(directory, callback);
            });
        }

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSaveDialog(File directory) {
        showFileDialog(directory, false, this::saveToFile);
    }

    private void showSaveNameDialog(File directory, FileDialogCallback callback) {
        final EditText input = new EditText(this);
        input.setHint("filename.txt");
        input.setText(currentFile != null ? currentFile.getName() : "untitled.txt");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("💾 Enter filename")
                .setMessage("Current location: " + directory.getAbsolutePath())
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String filename = input.getText().toString().trim();
                    if (!filename.isEmpty()) {
                        File file = new File(directory, filename);
                        if (file.exists()) {
                            // Ask for confirmation
                            new AlertDialog.Builder(this)
                                    .setTitle("Overwrite File?")
                                    .setMessage("File already exists. Overwrite?")
                                    .setPositiveButton("Overwrite", (d, w) -> callback.onFileSelected(file))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            callback.onFileSelected(file);
                        }
                    } else {
                        showToast("Please enter a filename");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface FileDialogCallback {
        void onFileSelected(File file);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isModified) {
                    showSaveDialog(() -> finish());
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("✓ Storage permission granted");
            } else {
                showToast("⚠ Storage permission denied. Limited functionality.");
            }
        }
    }
}