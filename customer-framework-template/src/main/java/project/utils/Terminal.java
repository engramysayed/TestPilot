package project.utils;

import project.utils.Logs.LogsManager;

public class Terminal
{
    public static void executeCommand(String ... command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode= process.waitFor();
            if (exitCode!=0) {
                LogsManager.error("Command execution failed with exit code " + exitCode);
            }
        } catch (Exception e) {
           LogsManager.error("Failed to execute command: " + e.getMessage());
        }
    }
}
