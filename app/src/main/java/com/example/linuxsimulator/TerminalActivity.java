package com.example.linuxsimulator;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linuxsimulator.data.FileSystemManager;
import com.example.linuxsimulator.terminal.CommandProcessor;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class TerminalActivity extends AppCompatActivity {
    private RecyclerView terminalOutput;
    private EditText commandInput;
    private TextView promptView;
    private Button btnClear, btnFileManager, btnExit, btnKeyboard, btnExecute;
    private ScrollView terminalScrollView;

    // Quick command buttons
    private Button btnQuickLs, btnQuickPwd, btnQuickCd, btnQuickClear, btnQuickHelp, btnQuickWhoami;

    private FileSystemManager fsManager;
    private CommandProcessor commandProcessor;
    private TerminalOutputAdapter outputAdapter;
    private List<String> outputLines;
    private List<String> commandHistory;
    private int historyIndex = -1;
    private Handler handler;

    // Terminal state
    private String currentUser = "kali";
    private String hostname = "linux";
    private boolean isRoot = false;
    private Map<String, String> environmentVariables;

    // Colors
    private int colorPrompt, colorCommand, colorOutput, colorError, colorSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        initializeColors();
        initializeComponents();
        setupTerminal();
        setupListeners();

        // Handle directory passed from file manager
        String startDir = getIntent().getStringExtra("currentDirectory");
        if (startDir != null) {
            fsManager.changeDirectory(startDir.replace(fsManager.getAbsoluteCurrentDirectory(), ""));
        }

        showWelcomeMessage();
        updatePrompt();
    }

    private void initializeColors() {
        colorPrompt = ContextCompat.getColor(this, R.color.kali_green);
        colorCommand = ContextCompat.getColor(this, R.color.kali_blue);
        colorOutput = ContextCompat.getColor(this, R.color.kali_text_primary);
        colorError = ContextCompat.getColor(this, R.color.kali_red);
        colorSuccess = ContextCompat.getColor(this, R.color.kali_green);
    }

    private void initializeComponents() {
        terminalOutput = findViewById(R.id.rv_terminal_output);
        commandInput = findViewById(R.id.et_command_input);
        promptView = findViewById(R.id.tv_prompt);
        btnClear = findViewById(R.id.btn_clear);
        btnFileManager = findViewById(R.id.btn_file_manager);
        btnExit = findViewById(R.id.btn_exit);
        btnKeyboard = findViewById(R.id.btn_keyboard);
        btnExecute = findViewById(R.id.btn_execute);
        terminalScrollView = findViewById(R.id.scroll_terminal);

        // Initialize quick command buttons
        btnQuickLs = findViewById(R.id.btn_quick_ls);
        btnQuickPwd = findViewById(R.id.btn_quick_pwd);
        btnQuickCd = findViewById(R.id.btn_quick_cd);
        btnQuickClear = findViewById(R.id.btn_quick_clear);
        btnQuickHelp = findViewById(R.id.btn_quick_help);
        btnQuickWhoami = findViewById(R.id.btn_quick_whoami);

        fsManager = FileSystemManager.getInstance(this);
        commandProcessor = new CommandProcessor(this, fsManager);
        outputLines = new ArrayList<>();
        commandHistory = new ArrayList<>();
        handler = new Handler();

        // Initialize environment variables
        environmentVariables = new HashMap<>();
        environmentVariables.put("HOME", "/home/" + currentUser);
        environmentVariables.put("USER", currentUser);
        environmentVariables.put("PWD", fsManager.getAbsoluteCurrentDirectory());
        environmentVariables.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        environmentVariables.put("SHELL", "/bin/bash");
        environmentVariables.put("TERM", "xterm-256color");
    }

    private void setupTerminal() {
        // Setup RecyclerView for terminal output
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        terminalOutput.setLayoutManager(layoutManager);
        outputAdapter = new TerminalOutputAdapter(outputLines);
        terminalOutput.setAdapter(outputAdapter);

        // Set monospace font for command input
        commandInput.setTypeface(Typeface.MONOSPACE);
        promptView.setTypeface(Typeface.MONOSPACE);
    }

    private void setupListeners() {
        // Command input listeners
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                executeCommand();
                return true;
            }
            return false;
        });

        // History navigation with volume keys or arrow keys
        commandInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_UP:
                        navigateHistory(-1);
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        navigateHistory(1);
                        return true;
                    case KeyEvent.KEYCODE_TAB:
                        autoComplete();
                        return true;
                }
            }
            return false;
        });

        // Button listeners
        btnClear.setOnClickListener(v -> clearTerminal());
        btnFileManager.setOnClickListener(v -> openFileManager());
        btnExit.setOnClickListener(v -> finish());
        btnKeyboard.setOnClickListener(v -> toggleKeyboard());

        // Execute button listener - THIS WAS MISSING!
        btnExecute.setOnClickListener(v -> executeCommand());

        // Quick command buttons listeners
        if (btnQuickLs != null) {
            btnQuickLs.setOnClickListener(v -> insertAndExecuteCommand("ls"));
        }
        if (btnQuickPwd != null) {
            btnQuickPwd.setOnClickListener(v -> insertAndExecuteCommand("pwd"));
        }
        if (btnQuickCd != null) {
            btnQuickCd.setOnClickListener(v -> insertCommand("cd .."));
        }
        if (btnQuickClear != null) {
            btnQuickClear.setOnClickListener(v -> insertAndExecuteCommand("clear"));
        }
        if (btnQuickHelp != null) {
            btnQuickHelp.setOnClickListener(v -> insertAndExecuteCommand("help"));
        }
        if (btnQuickWhoami != null) {
            btnQuickWhoami.setOnClickListener(v -> insertAndExecuteCommand("whoami"));
        }

        // Auto-scroll to bottom when new output is added
        outputAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            public void onItemInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });
    }

    private void showWelcomeMessage() {
        addOutputLine("", colorOutput);
        addOutputLine("┌──────────────────────────────────────────────────────────┐", colorPrompt);
        addOutputLine("│                 🐧 black shell Terminal                    │", colorPrompt);
        addOutputLine("│                     Mobile Simulator                       │", colorPrompt);
        addOutputLine("└──────────────────────────────────────────────────────────┘", colorPrompt);
        addOutputLine("", colorOutput);
        addOutputLine("Welcome to Kali Linux Terminal Simulator", colorOutput);
        addOutputLine("Type 'help' for available commands", colorOutput);
        addOutputLine("", colorOutput);
    }

    private void updatePrompt() {
        String currentDir = fsManager.getCurrentDirectory();
        String displayDir = currentDir.replace("/home/" + currentUser, "~");
        if (displayDir.isEmpty()) displayDir = "~";

        String prompt = String.format("┌──(%s@%s)-[%s]\n└─%s ",
                currentUser, hostname, displayDir, isRoot ? "#" : "$");

        SpannableStringBuilder spannablePrompt = new SpannableStringBuilder(prompt);
        spannablePrompt.setSpan(new ForegroundColorSpan(colorPrompt), 0, prompt.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        promptView.setText(spannablePrompt);
    }

    private void executeCommand() {
        String input = commandInput.getText().toString().trim();
        if (input.isEmpty()) return;

        // Add command to history
        if (!commandHistory.isEmpty() && !commandHistory.get(commandHistory.size() - 1).equals(input)) {
            commandHistory.add(input);
        } else if (commandHistory.isEmpty()) {
            commandHistory.add(input);
        }
        historyIndex = -1;

        // Show command with prompt in output
        String currentDir = fsManager.getCurrentDirectory();
        String displayDir = currentDir.replace("/home/" + currentUser, "~");
        if (displayDir.isEmpty()) displayDir = "~";

        String fullPrompt = String.format("┌──(%s@%s)-[%s]\n└─%s %s",
                currentUser, hostname, displayDir, isRoot ? "#" : "$", input);

        addOutputLine(fullPrompt, colorCommand);

        // Process command
        commandProcessor.processCommand(input, new CommandProcessor.CommandCallback() {
            @Override
            public void onSuccess(String output) {
                if (!output.isEmpty()) {
                    addOutputLine(output, colorOutput);
                }
                commandInput.setText("");
                updatePrompt();
                scrollToBottom();
            }

            @Override
            public void onError(String error) {
                addOutputLine(error, colorError);
                commandInput.setText("");
                updatePrompt();
                scrollToBottom();
            }

            @Override
            public void onDirectoryChanged() {
                updatePrompt();
                environmentVariables.put("PWD", fsManager.getAbsoluteCurrentDirectory());
            }
        });

        commandInput.setText("");
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = -1;
            commandInput.setText("");
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size() - 1;
        }

        if (historyIndex >= 0) {
            commandInput.setText(commandHistory.get(historyIndex));
            commandInput.setSelection(commandInput.getText().length());
        }
    }

    private void autoComplete() {
        String currentText = commandInput.getText().toString();
        String[] parts = currentText.split(" ");
        if (parts.length == 0) return;

        String lastPart = parts[parts.length - 1];

        // Try to complete file/directory names
        List<String> files = new ArrayList<>();
        try {
            for (com.example.linuxsimulator.data.FileItem file : fsManager.listFiles()) {
                if (file.getName().startsWith(lastPart)) {
                    files.add(file.getName());
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try to complete command names
        if (parts.length == 1) {
            String[] commands = {"ls", "cd", "pwd", "mkdir", "touch", "rm", "cp", "mv", "cat", "nano",
                    "chmod", "find", "grep", "ps", "kill", "clear", "help", "exit", "whoami"};
            for (String cmd : commands) {
                if (cmd.startsWith(lastPart)) {
                    files.add(cmd);
                }
            }
        }

        if (files.size() == 1) {
            // Complete automatically
            parts[parts.length - 1] = files.get(0);
            commandInput.setText(String.join(" ", parts));
            commandInput.setSelection(commandInput.getText().length());
        } else if (files.size() > 1) {
            // Show options
            addOutputLine("", colorOutput);
            addOutputLine(String.join("  ", files), colorOutput);
            scrollToBottom();
        }
    }

    // Helper method for quick command buttons - just insert text
    private void insertCommand(String command) {
        commandInput.setText(command);
        commandInput.setSelection(commandInput.getText().length());
    }

    // Helper method for quick command buttons - insert and execute immediately
    private void insertAndExecuteCommand(String command) {
        commandInput.setText(command);
        commandInput.setSelection(commandInput.getText().length());
        executeCommand();
    }

    private void addOutputLine(String line, int color) {
        outputLines.add(line);
        outputAdapter.notifyItemInserted(outputLines.size() - 1);
    }

    private void clearTerminal() {
        outputLines.clear();
        outputAdapter.notifyDataSetChanged();
        showWelcomeMessage();
    }

    private void openFileManager() {
        Intent intent = new Intent(this, FileManagerActivity.class);
        intent.putExtra("currentDirectory", fsManager.getAbsoluteCurrentDirectory());
        startActivity(intent);
    }

    private void toggleKeyboard() {
        // Toggle soft keyboard
        commandInput.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void scrollToBottom() {
        handler.post(() -> {
            if (outputLines.size() > 0) {
                terminalOutput.scrollToPosition(outputLines.size() - 1);
            }
        });
    }

    // Adapter for terminal output
    private class TerminalOutputAdapter extends RecyclerView.Adapter<TerminalOutputAdapter.OutputViewHolder> {
        private List<String> lines;

        public TerminalOutputAdapter(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public OutputViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.terminal_output_item, parent, false);
            return new OutputViewHolder(view);
        }

        @Override
        public void onBindViewHolder(OutputViewHolder holder, int position) {
            String line = lines.get(position);
            holder.textView.setText(line);

            // Apply syntax highlighting
            if (line.startsWith("┌──") || line.startsWith("└─")) {
                holder.textView.setTextColor(colorPrompt);
            } else if (line.contains("error") || line.contains("Error") || line.contains("ERROR")) {
                holder.textView.setTextColor(colorError);
            } else if (line.contains("success") || line.contains("✓")) {
                holder.textView.setTextColor(colorSuccess);
            } else {
                holder.textView.setTextColor(colorOutput);
            }
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        class OutputViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            OutputViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.tv_output_line);
                textView.setTypeface(Typeface.MONOSPACE);
            }
        }
    }

    public FileSystemManager getFileSystemManager() {
        return fsManager;
    }

    public void addOutput(String output, int color) {
        addOutputLine(output, color);
    }

    public int getColorOutput() { return colorOutput; }
    public int getColorError() { return colorError; }
    public int getColorSuccess() { return colorSuccess; }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public String getCurrentUser() { return currentUser; }
    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) {
        isRoot = root;
        currentUser = root ? "root" : "kali";
        updatePrompt();
    }
}