import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.swing.SwingUtilities;

public class JHTTP {

    // Logger for logging messages
    private static final Logger logger = Logger.getLogger(JHTTP.class.getCanonicalName());
    // Maximum number of threads in the server's thread pool
    private static final int NUM_THREADS = 50;
    // Default file to serve if the requested file is a directory
    private static final String INDEX_FILE = "index.html";

    // Document root directory and port number for the web server
    private final File rootDirectory;
    private final int port;

    // Server socket, thread pool, and flag to track server status
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private boolean isRunning;

    // Cache map to store frequently requested files in memory
    private Map<String, byte[]> cacheMap = new HashMap<>();

    // Constructor initializes the web server with a document root directory and a port number
    public JHTTP(File rootDirectory, int port) throws IOException {
        // Check if the specified rootDirectory is a directory
        if (!rootDirectory.isDirectory()) {
            throw new IOException(rootDirectory + " does not exist as a directory");
        }
        this.rootDirectory = rootDirectory;
        this.port = port;
    }

    // Getter method for checking if the server is running
    public boolean isRunning() {
        return isRunning;
    }

    // Getter method for getting the port number
    public int getPort() {
        return port;
    }

    // Logs information about incoming connections, including remote address and timestamp
    private void logConnection(Socket request) {
        try {
            // Create a log entry and append it to the connection_log.txt file
            String logEntry = "Connection from: " + request.getRemoteSocketAddress() + " at " + new Date() + "\n";
            Files.write(Paths.get("connection_log.txt"), logEntry.getBytes(), StandardOpenOption.APPEND);

            // Log cache-related information
            logCacheInfo();
        } catch (IOException e) {
            // Log a warning if an error occurs while logging the connection
            logger.log(Level.WARNING, "Error logging connection", e);
        }
    }

    // Logs information about the cache, including cache size and details of each cached file
    private void logCacheInfo() {
        logger.info("Cache Size: " + cacheMap.size());
        for (Map.Entry<String, byte[]> entry : cacheMap.entrySet()) {
            logger.info("Cached File: " + entry.getKey() + ", Size: " + entry.getValue().length);
        }
    }

    // Starts the web server, accepts incoming connections, and processes them using a thread pool
    public void start() throws IOException {
        // Create a fixed-size thread pool for handling incoming requests
        pool = Executors.newFixedThreadPool(NUM_THREADS);
        // Create a server socket to listen on the specified port
        serverSocket = new ServerSocket(port);
        // Set the server status flag to true
        isRunning = true;
        // Log server information
        logger.info("Accepting connections on port " + serverSocket.getLocalPort());
        logger.info("Document Root: " + rootDirectory);

        // Create ServerAdminUI instance and start the server
        SwingUtilities.invokeLater(() -> {
            try {
                ServerAdminUI adminUI = new ServerAdminUI(this);
                adminUI.setVisible(true);
            } catch (Exception e) {
                // Log a severe error if starting ServerAdminUI fails
                logger.log(Level.SEVERE, "Error starting ServerAdminUI", e);
            }
        });

        // Continuously accept and process incoming connections while the server is running
        while (isRunning) {
            try {
                // Accept an incoming connection
                Socket request = serverSocket.accept();
                // Log information about the connection
                logConnection(request);
                // Create a RequestProcessor instance for the incoming request and submit it to the thread pool
                Runnable r = new RequestProcessor(rootDirectory, INDEX_FILE, request, "GET", cacheMap);
                pool.submit(r);
            } catch (IOException ex) {
                // Log a warning if an error occurs while accepting a connection
                logger.log(Level.WARNING, "Error accepting connection", ex);
            }
        }
    }

    // Stops the web server by closing the server socket and shutting down the thread pool
    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                // Close the server socket
                serverSocket.close();
                // Shut down the thread pool
                pool.shutdown();
                // Set the server status flag to false
                isRunning = false;
                // Log a message indicating that the server has stopped
                logger.info("Server stopped");
            } catch (IOException e) {
                // Log a severe error if an error occurs while stopping the server
                logger.log(Level.SEVERE, "Error stopping server", e);
            }
        }
    }

    // Main method to start the web server with the specified document root and port
    public static void main(String[] args) {
        File docroot;
        int port;

        try {
            // Parse the command line arguments to get the document root and port
            docroot = new File(args[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            // Display a usage message if the command line arguments are missing
            System.out.println("Usage: java JHTTP docroot port");
            return;
        }
        try {
            // Parse the command line arguments to get the document root and port
            docroot = new File(args[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            // Display a usage message if the command line arguments are missing
            System.out.println("Usage: java JHTTP docroot port");
            return;
        }

        try {
            // Parse the port number, default to 80 if not specified or invalid
            port = Integer.parseInt(args[1]);
            if (port < 0 || port > 65535) port = 80;
        } catch (RuntimeException ex) {
            // Default to port 80 if parsing the port number fails
            port = 80;
        }

        try {
            // Create an instance of the JHTTP web server with the specified document root and port
            JHTTP webserver = new JHTTP(docroot, port);
            // Start the web server
            webserver.start();

            // Continuous loop to log cache information
            while (webserver.isRunning()) {
                webserver.logCacheInfo();
            }

        } catch (IOException ex) {
            // Log a severe error if the web server fails to start
            logger.log(Level.SEVERE, "Server could not start", ex);
        }
    }
}


