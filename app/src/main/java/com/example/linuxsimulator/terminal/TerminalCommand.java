package com.example.linuxsimulator.terminal;

/**
 * Represents a terminal command with its execution logic
 */
public abstract class TerminalCommand {
    protected String name;
    protected String description;
    protected String usage;

    public TerminalCommand(String name, String description, String usage) {
        this.name = name;
        this.description = description;
        this.usage = usage;
    }

    /**
     * Execute the command with given arguments
     * @param args Command arguments
     * @param callback Callback to handle result
     */
    public abstract void execute(String[] args, CommandCallback callback);

    /**
     * Get command name
     * @return Command name
     */
    public String getName() {
        return name;
    }

    /**
     * Get command description
     * @return Command description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get command usage
     * @return Command usage
     */
    public String getUsage() {
        return usage;
    }

    /**
     * Check if the command matches the given name
     * @param commandName Name to check
     * @return True if matches
     */
    public boolean matches(String commandName) {
        return name.equalsIgnoreCase(commandName);
    }

    /**
     * Callback interface for command execution
     */
    public interface CommandCallback {
        void onSuccess(String output);
        void onError(String error);
        void onDirectoryChanged();
    }
}