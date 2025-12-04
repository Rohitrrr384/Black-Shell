package com.example.linuxsimulator;

import java.util.*;
import java.util.regex.Pattern;

public class NetworkToolsSimulator {

    // Mock network connection data
    private static class NetworkConnection {
        String protocol;
        String localAddress;
        String foreignAddress;
        String state;
        String processName;
        int pid;

        NetworkConnection(String protocol, String localAddress, String foreignAddress,
                          String state, String processName, int pid) {
            this.protocol = protocol;
            this.localAddress = localAddress;
            this.foreignAddress = foreignAddress;
            this.state = state;
            this.processName = processName;
            this.pid = pid;
        }
    }

    // Generate realistic mock network connections
    private static List<NetworkConnection> generateMockConnections() {
        List<NetworkConnection> connections = new ArrayList<>();

        // Common system services
        connections.add(new NetworkConnection("tcp", "0.0.0.0:22", "0.0.0.0:*", "LISTEN", "sshd", 1234));
        connections.add(new NetworkConnection("tcp", "127.0.0.1:631", "0.0.0.0:*", "LISTEN", "cupsd", 2345));
        connections.add(new NetworkConnection("tcp", "0.0.0.0:80", "0.0.0.0:*", "LISTEN", "apache2", 3456));
        connections.add(new NetworkConnection("tcp", "0.0.0.0:443", "0.0.0.0:*", "LISTEN", "apache2", 3457));
        connections.add(new NetworkConnection("tcp", "127.0.0.1:3306", "0.0.0.0:*", "LISTEN", "mysqld", 4567));

        // Active connections
        connections.add(new NetworkConnection("tcp", "192.168.1.100:45678", "142.250.191.142:443", "ESTABLISHED", "firefox", 5678));
        connections.add(new NetworkConnection("tcp", "192.168.1.100:54321", "52.84.77.25:80", "ESTABLISHED", "chrome", 6789));
        connections.add(new NetworkConnection("tcp", "192.168.1.100:33445", "192.168.1.1:53", "TIME_WAIT", "", 0));

        // UDP connections
        connections.add(new NetworkConnection("udp", "0.0.0.0:53", "0.0.0.0:*", "", "systemd-resolve", 7890));
        connections.add(new NetworkConnection("udp", "127.0.0.1:323", "0.0.0.0:*", "", "chronyd", 8901));
        connections.add(new NetworkConnection("udp", "0.0.0.0:68", "0.0.0.0:*", "", "dhclient", 9012));

        return connections;
    }

    // Handle netstat command
    public static String handleNetstat(String command) {
        String[] parts = command.trim().split("\\s+");
        boolean showTcp = false;
        boolean showUdp = false;
        boolean showListening = false;
        boolean showAll = false;
        boolean showProcesses = false;
        boolean showNumeric = false;

        // Parse flags
        for (String part : parts) {
            if (part.startsWith("-")) {
                if (part.contains("t")) showTcp = true;
                if (part.contains("u")) showUdp = true;
                if (part.contains("l")) showListening = true;
                if (part.contains("a")) showAll = true;
                if (part.contains("p")) showProcesses = true;
                if (part.contains("n")) showNumeric = true;
            }
        }

        // Default behavior
        if (!showTcp && !showUdp) {
            showTcp = true;
            showUdp = true;
        }
        if (!showListening && !showAll) {
            showAll = true;
        }

        StringBuilder output = new StringBuilder();
        output.append("Active Internet connections ");
        if (showListening && !showAll) {
            output.append("(only servers)\n");
        } else if (showAll) {
            output.append("(w/o servers)\n");
        } else {
            output.append("\n");
        }

        // Header
        if (showProcesses) {
            output.append("Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name\n");
        } else {
            output.append("Proto Recv-Q Send-Q Local Address           Foreign Address         State\n");
        }

        List<NetworkConnection> connections = generateMockConnections();

        for (NetworkConnection conn : connections) {
            boolean include = false;

            // Filter by protocol
            if ((showTcp && conn.protocol.equals("tcp")) ||
                    (showUdp && conn.protocol.equals("udp"))) {

                // Filter by state
                if (showAll) {
                    include = true;
                } else if (showListening && conn.state.equals("LISTEN")) {
                    include = true;
                } else if (!showListening && !conn.state.equals("LISTEN") && !conn.state.isEmpty()) {
                    include = true;
                }
            }

            if (include) {
                output.append(String.format("%-5s %-6s %-6s %-23s %-23s %-11s",
                        conn.protocol, "0", "0", conn.localAddress, conn.foreignAddress, conn.state));

                if (showProcesses) {
                    if (conn.pid > 0) {
                        output.append(String.format(" %d/%s", conn.pid, conn.processName));
                    } else {
                        output.append(" -");
                    }
                }
                output.append("\n");
            }
        }

        return output.toString();
    }

    // Handle ss command (modern replacement for netstat)
    public static String handleSs(String command) {
        String[] parts = command.trim().split("\\s+");
        boolean showTcp = false;
        boolean showUdp = false;
        boolean showListening = false;
        boolean showAll = false;
        boolean showProcesses = false;
        boolean showNumeric = false;
        boolean showExtended = false;

        // Parse flags
        for (String part : parts) {
            if (part.startsWith("-")) {
                if (part.contains("t")) showTcp = true;
                if (part.contains("u")) showUdp = true;
                if (part.contains("l")) showListening = true;
                if (part.contains("a")) showAll = true;
                if (part.contains("p")) showProcesses = true;
                if (part.contains("n")) showNumeric = true;
                if (part.contains("e")) showExtended = true;
            }
        }

        // Default behavior for ss
        if (!showTcp && !showUdp) {
            showTcp = true;
        }
        if (!showListening && !showAll) {
            showAll = true;
        }

        StringBuilder output = new StringBuilder();

        // Header for ss
        if (showProcesses) {
            output.append("Netid  State      Recv-Q Send-Q  Local Address:Port   Peer Address:Port  Process\n");
        } else {
            output.append("Netid  State      Recv-Q Send-Q  Local Address:Port   Peer Address:Port\n");
        }

        List<NetworkConnection> connections = generateMockConnections();

        for (NetworkConnection conn : connections) {
            boolean include = false;

            // Filter by protocol
            if ((showTcp && conn.protocol.equals("tcp")) ||
                    (showUdp && conn.protocol.equals("udp"))) {

                // Filter by state
                if (showAll) {
                    include = true;
                } else if (showListening && conn.state.equals("LISTEN")) {
                    include = true;
                } else if (!showListening && !conn.state.equals("LISTEN") && !conn.state.isEmpty()) {
                    include = true;
                }
            }

            if (include) {
                String state = conn.state.isEmpty() ? "UNCONN" : conn.state;
                output.append(String.format("%-6s %-10s %-6s %-6s  %-18s %-18s",
                        conn.protocol, state, "0", "0", conn.localAddress, conn.foreignAddress));

                if (showProcesses) {
                    if (conn.pid > 0) {
                        output.append(String.format(" users:((\"%s\",pid=%d,fd=3))", conn.processName, conn.pid));
                    }
                }
                output.append("\n");
            }
        }

        return output.toString();
    }

    // Main handler for your command processor
    public static String handleNetworkCommand(String command) {
        command = command.trim().toLowerCase();

        if (command.startsWith("netstat")) {
            return handleNetstat(command);
        } else if (command.startsWith("ss")) {
            return handleSs(command);
        } else if (command.equals("netstat --help") || command.equals("netstat -h")) {
            return getNetstatHelp();
        } else if (command.equals("ss --help") || command.equals("ss -h")) {
            return getSsHelp();
        }

        return "Unknown command. Try 'netstat --help' or 'ss --help'";
    }

    private static String getNetstatHelp() {
        return "Usage: netstat [options]\n" +
                "  -a, --all                display all sockets (default: connected)\n" +
                "  -l, --listening          display listening server sockets\n" +
                "  -n, --numeric            don't resolve hosts\n" +
                "  -p, --programs           display PID/Program name for sockets\n" +
                "  -t, --tcp                display only TCP connections\n" +
                "  -u, --udp                display only UDP connections\n" +
                "  -h, --help               display this help\n\n" +
                "Examples:\n" +
                "  netstat -tulpn           # Show all TCP/UDP ports with processes\n" +
                "  netstat -ln              # Show only listening ports\n";
    }

    private static String getSsHelp() {
        return "Usage: ss [options]\n" +
                "  -a, --all                display all sockets\n" +
                "  -l, --listening          display listening sockets\n" +
                "  -n, --numeric            don't resolve service names\n" +
                "  -p, --processes          show process using socket\n" +
                "  -t, --tcp                display only TCP sockets\n" +
                "  -u, --udp                display only UDP sockets\n" +
                "  -e, --extended           show detailed socket information\n" +
                "  -h, --help               display this help\n\n" +
                "Examples:\n" +
                "  ss -tulpn                # Show all TCP/UDP ports with processes\n" +
                "  ss -ln                   # Show only listening sockets\n";
    }
}

// Integration instructions:
// 1. Add this class to your project as NetworkToolsSimulator.java
// 2. In your CommandProcessor switch statement, add the cases shown below
// 3. The commands will work with flags like: netstat -tulpn, ss -ln, etc.