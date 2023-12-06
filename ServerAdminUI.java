import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.io.BufferedReader;

public class ServerAdminUI extends JFrame {

    private JLabel statusLabel;
    private JLabel connectionsLabel;
    private JHTTP jhttp;
    private JTextArea logTextArea;
    private File docRoot;  // Store the selected document root directory

    public ServerAdminUI(JHTTP jhttp) {
        super("Server Admin UI");
        this.jhttp = jhttp;

        // Initialize UI components
        statusLabel = new JLabel("Server Status: Not Checked");

        JButton checkStatusButton = new JButton("Check Status");
        checkStatusButton.addActionListener(e -> checkServerStatus());

        JButton updateLogButton = new JButton("Update Log");
        updateLogButton.addActionListener(e -> loadAndDisplayLog());

        // Add a JTextArea for displaying log
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);

        // Use BorderLayout for better placement of components
        setLayout(new BorderLayout());

        // Create a panel for labels
        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new FlowLayout());
        labelPanel.add(statusLabel);

        // Create a panel for buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        buttonPanel.add(checkStatusButton);
        buttonPanel.add(updateLogButton);

        // Add panels to the frame
        add(labelPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(logScrollPane, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 400); // Adjusted size to accommodate the log display
        setLocationRelativeTo(null);
        setVisible(true);

        // Load and display the log with the initially selected document root
        loadAndDisplayLog();
    }

    private void updateStatus(JHTTP jhttp) {
        // Update the server status label on the UI
        SwingUtilities.invokeLater(() -> {
            if (jhttp.isRunning()) {
                statusLabel.setText("Server Status: Running");
            } else {
                statusLabel.setText("Server Status: Not Running");
            }
        });
    }

    private void checkServerStatus() {
        // Check server status by attempting to connect to localhost
        SwingUtilities.invokeLater(() -> {
            try (Socket socket = new Socket("localhost", jhttp.getPort())) {
                // If connection succeeds, update status
                statusLabel.setText("Server Status: Running");
            } catch (IOException ex) {
                // If connection fails, update status
                statusLabel.setText("Server Status: Not Running");
            }
        });
    }

    private void loadAndDisplayLog() {
        // Load and display the connection log in the JTextArea
        SwingUtilities.invokeLater(() -> {
            try {
                // Use the stored document root directory
                if (docRoot == null) {
                    docRoot = getDocRoot();
                }

                // Read the log file and display its content
                File logFile = new File(docRoot, "connection_log.txt");

                try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                    StringBuilder logContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logContent.append(line).append("\n");
                    }
                    logTextArea.setText(logContent.toString());
                } catch (IOException e) {
                    logTextArea.setText("Error loading log file: " + e.getMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
                handleException("An error occurred while loading the log file.", e);
            }
        });
    }

    public static void main(String[] args) {
        // Main method to initialize and start the UI
        SwingUtilities.invokeLater(() -> {
            int port = 8080;

            try {
                // Initialize JHTTP server and ServerAdminUI
                JHTTP jhttp = new JHTTP(getDocRoot(), port);
                new ServerAdminUI(jhttp);
            } catch (IOException e) {
                e.printStackTrace();
                handleException("An error occurred while starting the server.", e);
            }
        });
    }

    private static File getDocRoot() {
        // Open a file chooser to select the document root directory
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Choose Document Root Directory");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        } else {
            // Default directory if none selected
            return new File(System.getProperty("user.home"));
        }
    }

    private static void handleException(String message, Exception e) {
        // Display an error message in a dialog box and print to console
        JOptionPane.showMessageDialog(null, message + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        System.err.println("Exception: " + e.getMessage());
    }
}
