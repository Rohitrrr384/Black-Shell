package com.example.linuxsimulator;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linuxsimulator.data.FileSystemManager;
import com.example.linuxsimulator.terminal.CommandProcessor;

import java.util.*;

import org.json.JSONObject;
import org.json.JSONArray;
import okhttp3.*;

public class TerminalActivity extends AppCompatActivity {
    private RecyclerView terminalOutput;
    private EditText commandInput;
    private TextView promptView;
    private Button btnClear, btnFileManager, btnExit, btnKeyboard, btnExecute;
    private ScrollView terminalScrollView;
    private Button btnExplainCommand;

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

    // Gemini API - CHANGE THIS TO YOUR API KEY
    private static final String GEMINI_API_KEY = "add_your_api_key_here";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY;
    private OkHttpClient httpClient;
    private AlertDialog explanationDialog;
    private AlertDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        initializeColors();
        initializeComponents();
        setupTerminal();
        setupListeners();

        // Initialize HTTP client with reasonable timeouts
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

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
        btnExplainCommand = findViewById(R.id.btn_explain_command);
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        terminalOutput.setLayoutManager(layoutManager);
        outputAdapter = new TerminalOutputAdapter(outputLines);
        terminalOutput.setAdapter(outputAdapter);

        commandInput.setTypeface(Typeface.MONOSPACE);
        promptView.setTypeface(Typeface.MONOSPACE);
    }

    private void setupListeners() {
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                executeCommand();
                return true;
            }
            return false;
        });

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

        btnClear.setOnClickListener(v -> clearTerminal());
        btnFileManager.setOnClickListener(v -> openFileManager());
        btnExit.setOnClickListener(v -> finish());
        btnKeyboard.setOnClickListener(v -> toggleKeyboard());
        btnExecute.setOnClickListener(v -> executeCommand());

        if (btnExplainCommand != null) {
            btnExplainCommand.setOnClickListener(v -> explainCurrentCommand());
        }

        if (btnQuickLs != null) btnQuickLs.setOnClickListener(v -> insertAndExecuteCommand("ls"));
        if (btnQuickPwd != null) btnQuickPwd.setOnClickListener(v -> insertAndExecuteCommand("pwd"));
        if (btnQuickCd != null) btnQuickCd.setOnClickListener(v -> insertCommand("cd .."));
        if (btnQuickClear != null) btnQuickClear.setOnClickListener(v -> insertAndExecuteCommand("clear"));
        if (btnQuickHelp != null) btnQuickHelp.setOnClickListener(v -> insertAndExecuteCommand("help"));
        if (btnQuickWhoami != null) btnQuickWhoami.setOnClickListener(v -> insertAndExecuteCommand("whoami"));

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
        addOutputLine("Welcome to Black Shell Terminal Simulator", colorOutput);
        addOutputLine("Type 'help' for available commands", colorOutput);
        addOutputLine("Click 'Explain' button to get AI-powered command explanations!", colorOutput);
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

        if (!commandHistory.isEmpty() && !commandHistory.get(commandHistory.size() - 1).equals(input)) {
            commandHistory.add(input);
        } else if (commandHistory.isEmpty()) {
            commandHistory.add(input);
        }
        historyIndex = -1;

        String currentDir = fsManager.getCurrentDirectory();
        String displayDir = currentDir.replace("/home/" + currentUser, "~");
        if (displayDir.isEmpty()) displayDir = "~";

        String fullPrompt = String.format("┌──(%s@%s)-[%s]\n└─%s %s",
                currentUser, hostname, displayDir, isRoot ? "#" : "$", input);

        addOutputLine(fullPrompt, colorCommand);

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

    private void explainCurrentCommand() {
        String command = commandInput.getText().toString().trim();

        if (command.isEmpty()) {
            Toast.makeText(this, "Please enter a command first!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if API key is configured
        if (GEMINI_API_KEY.equals("API_KEY")) {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ API Key Required")
                    .setMessage("Please replace YOUR_API_KEY_HERE with your actual Gemini API key in the code.\n\nGet your free API key at:\nhttps://makersuite.google.com/app/apikey")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        android.util.Log.d("GeminiAPI", "Starting explanation for command: " + command);

        showLoadingDialog();

        getCommandExplanation(command, new ExplanationCallback() {
            @Override
            public void onSuccess(String explanation) {
                android.util.Log.d("GeminiAPI", "Success! Explanation received");
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showExplanationDialog(command, explanation);
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("GeminiAPI", "Error in callback: " + error);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    new AlertDialog.Builder(TerminalActivity.this)
                            .setTitle("❌ Error")
                            .setMessage("Failed to get AI explanation:\n\n" + error +
                                    "\n\nPlease check:\n" +
                                    "• Internet connection\n" +
                                    "• API key validity\n" +
                                    "• Network permissions in AndroidManifest.xml")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Check Logs", (dialog, which) -> {
                                Toast.makeText(TerminalActivity.this,
                                        "Check Logcat with tag 'GeminiAPI'",
                                        Toast.LENGTH_LONG).show();
                            })
                            .show();
                });
            }
        });
    }

    private void getCommandExplanation(String command, ExplanationCallback callback) {
        new Thread(() -> {
            Response response = null;
            try {
                android.util.Log.d("GeminiAPI", "=== Starting API Request ===");
                android.util.Log.d("GeminiAPI", "Command: " + command);
                android.util.Log.d("GeminiAPI", "API URL: " + GEMINI_API_URL);

                // Create JSON request body
                JSONObject requestBody = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();

                String prompt = "Explain this Linux terminal command in simple, clear terms (2-4 sentences). Focus on what it does and when to use it: " + command;

                part.put("text", prompt);
                parts.put(part);
                content.put("parts", parts);
                content.put("role", "user");
                contents.put(content);
                requestBody.put("contents", contents);

                // Add generation config for better responses
                JSONObject generationConfig = new JSONObject();
                generationConfig.put("temperature", 0.7);
                generationConfig.put("maxOutputTokens", 800);
                generationConfig.put("topP", 0.95);
                generationConfig.put("topK", 40);
                requestBody.put("generationConfig", generationConfig);

                // Add safety settings to avoid blocking
                JSONArray safetySettings = new JSONArray();
                String[] categories = {
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                };
                for (String category : categories) {
                    JSONObject setting = new JSONObject();
                    setting.put("category", category);
                    setting.put("threshold", "BLOCK_NONE");
                    safetySettings.put(setting);
                }
                requestBody.put("safetySettings", safetySettings);

                String requestJson = requestBody.toString(2);
                android.util.Log.d("GeminiAPI", "Request JSON: " + requestJson);

                // Create request
                RequestBody body = RequestBody.create(
                        requestJson,
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(GEMINI_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                android.util.Log.d("GeminiAPI", "Sending request...");

                // Execute request
                response = httpClient.newCall(request).execute();

                int responseCode = response.code();
                android.util.Log.d("GeminiAPI", "Response Code: " + responseCode);

                if (response.body() == null) {
                    android.util.Log.e("GeminiAPI", "Response body is null!");
                    callback.onError("Empty response from server");
                    return;
                }

                String responseBody = response.body().string();
                android.util.Log.d("GeminiAPI", "Response Body: " + responseBody);

                if (!response.isSuccessful()) {
                    android.util.Log.e("GeminiAPI", "Request failed with code: " + responseCode);

                    // Parse error message if available
                    String errorMessage = "API Error " + responseCode;
                    try {
                        JSONObject errorJson = new JSONObject(responseBody);
                        if (errorJson.has("error")) {
                            JSONObject error = errorJson.getJSONObject("error");
                            if (error.has("message")) {
                                errorMessage = error.getString("message");
                            }
                        }
                    } catch (Exception e) {
                        errorMessage = responseBody.substring(0, Math.min(200, responseBody.length()));
                    }

                    callback.onError(errorMessage);
                    return;
                }

                JSONObject jsonResponse = new JSONObject(responseBody);

                // Check for blocking
                if (jsonResponse.has("promptFeedback")) {
                    JSONObject promptFeedback = jsonResponse.getJSONObject("promptFeedback");
                    if (promptFeedback.has("blockReason")) {
                        String blockReason = promptFeedback.getString("blockReason");
                        android.util.Log.e("GeminiAPI", "Content blocked: " + blockReason);
                        callback.onError("Content blocked: " + blockReason);
                        return;
                    }
                }

                // Parse candidates
                if (!jsonResponse.has("candidates")) {
                    android.util.Log.e("GeminiAPI", "No candidates in response");
                    callback.onError("No response generated. Try a different command.");
                    return;
                }

                JSONArray candidates = jsonResponse.getJSONArray("candidates");
                if (candidates.length() == 0) {
                    android.util.Log.e("GeminiAPI", "Empty candidates array");
                    callback.onError("Empty response from API");
                    return;
                }

                JSONObject candidate = candidates.getJSONObject(0);
                android.util.Log.d("GeminiAPI", "Candidate: " + candidate.toString(2));

                // Check finish reason
                if (candidate.has("finishReason")) {
                    String finishReason = candidate.getString("finishReason");
                    android.util.Log.d("GeminiAPI", "Finish reason: " + finishReason);
                    if (finishReason.equals("SAFETY")) {
                        callback.onError("Response blocked by safety filters");
                        return;
                    }
                }

                // Extract text
                if (!candidate.has("content")) {
                    android.util.Log.e("GeminiAPI", "No content in candidate");
                    callback.onError("No content in response");
                    return;
                }

                JSONObject contentObj = candidate.getJSONObject("content");
                if (!contentObj.has("parts")) {
                    android.util.Log.e("GeminiAPI", "No parts in content");
                    callback.onError("Invalid response format");
                    return;
                }

                JSONArray partsArray = contentObj.getJSONArray("parts");
                if (partsArray.length() == 0) {
                    android.util.Log.e("GeminiAPI", "Empty parts array");
                    callback.onError("No text in response");
                    return;
                }

                JSONObject partObj = partsArray.getJSONObject(0);
                if (!partObj.has("text")) {
                    android.util.Log.e("GeminiAPI", "No text in part");
                    callback.onError("No text field in response");
                    return;
                }

                String explanation = partObj.getString("text").trim();
                android.util.Log.d("GeminiAPI", "Explanation: " + explanation);

                if (explanation.isEmpty()) {
                    callback.onError("Received empty explanation");
                    return;
                }

                callback.onSuccess(explanation);

            } catch (java.net.SocketTimeoutException e) {
                android.util.Log.e("GeminiAPI", "Timeout exception", e);
                callback.onError("Connection timed out. Check your internet connection.");
            } catch (java.net.UnknownHostException e) {
                android.util.Log.e("GeminiAPI", "Unknown host", e);
                callback.onError("Cannot reach server. Check your internet connection.");
            } catch (javax.net.ssl.SSLException e) {
                android.util.Log.e("GeminiAPI", "SSL exception", e);
                callback.onError("SSL Error: " + e.getMessage());
            } catch (java.io.IOException e) {
                android.util.Log.e("GeminiAPI", "IO exception", e);
                callback.onError("Network error: " + e.getMessage());
            } catch (org.json.JSONException e) {
                android.util.Log.e("GeminiAPI", "JSON parsing error", e);
                callback.onError("Invalid response format: " + e.getMessage());
            } catch (Exception e) {
                android.util.Log.e("GeminiAPI", "Unexpected error", e);
                e.printStackTrace();
                callback.onError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }).start();
    }

    private void showLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        TextView textView = dialogView.findViewById(android.R.id.text1);
        textView.setText("🤖 Getting AI explanation...\nPlease wait...");
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setPadding(40, 60, 40, 60);



        builder.setView(dialogView);
        builder.setCancelable(true);
        builder.setOnCancelListener(dialog -> {
            android.util.Log.d("GeminiAPI", "Loading dialog canceled by user");
        });

        loadingDialog = builder.create();
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void showExplanationDialog(String command, String explanation) {
        if (explanationDialog != null && explanationDialog.isShowing()) {
            explanationDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("💡 Command Explanation");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView commandView = new TextView(this);
        commandView.setText("Command: " + command);
        commandView.setTextSize(16);
        commandView.setTypeface(null, Typeface.BOLD);
        commandView.setTextColor(colorCommand);
        commandView.setPadding(0, 0, 0, 20);
        layout.addView(commandView);

        TextView explanationView = new TextView(this);
        explanationView.setText(explanation);
        explanationView.setTextSize(14);
        explanationView.setLineSpacing(1.2f, 1.2f);
        explanationView.setTextColor(Color.BLACK);
        layout.addView(explanationView);

        TextView footerView = new TextView(this);
        footerView.setText("\n✨ Happy Learning! Keep exploring! 🚀");
        footerView.setTextSize(12);
        footerView.setTextColor(colorSuccess);
        footerView.setTypeface(null, Typeface.ITALIC);
        footerView.setPadding(0, 30, 0, 0);
        layout.addView(footerView);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Got it! ✓", (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton("Execute", (dialog, which) -> {
            executeCommand();
            dialog.dismiss();
        });
        builder.setNeutralButton("Close", (dialog, which) -> dialog.dismiss());

        explanationDialog = builder.create();
        explanationDialog.show();
    }

    interface ExplanationCallback {
        void onSuccess(String explanation);
        void onError(String error);
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
            parts[parts.length - 1] = files.get(0);
            commandInput.setText(String.join(" ", parts));
            commandInput.setSelection(commandInput.getText().length());
        } else if (files.size() > 1) {
            addOutputLine("", colorOutput);
            addOutputLine(String.join("  ", files), colorOutput);
            scrollToBottom();
        }
    }

    private void insertCommand(String command) {
        commandInput.setText(command);
        commandInput.setSelection(commandInput.getText().length());
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (explanationDialog != null && explanationDialog.isShowing()) {
            explanationDialog.dismiss();
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

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

    public FileSystemManager getFileSystemManager() { return fsManager; }
    public void addOutput(String output, int color) { addOutputLine(output, color); }
    public int getColorOutput() { return colorOutput; }
    public int getColorError() { return colorError; }
    public int getColorSuccess() { return colorSuccess; }
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public String getCurrentUser() { return currentUser; }
    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) {
        isRoot = root;
        currentUser = root ? "root" : "kali";
        updatePrompt();
    }
}
