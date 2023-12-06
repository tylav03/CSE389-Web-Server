import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;

import javax.swing.SwingUtilities;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;

public class JHTTP {

    private static final Logger logger = Logger.getLogger(JHTTP.class.getCanonicalName());
    private static final int NUM_THREADS = 50;
    private static final String INDEX_FILE = "index.html";

    private final File rootDirectory;
    private final int port;

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private boolean isRunning; // Flag to track server status

    public JHTTP(File rootDirectory, int port) throws IOException {
        if (!rootDirectory.isDirectory()) {
            throw new IOException(rootDirectory + " does not exist as a directory");
        }
        this.rootDirectory = rootDirectory;
        this.port = port;
    }

    public boolean isRunning() {
        return isRunning;
    }


    public int getPort() {
        return port;
    }


    private void logConnection(Socket request) {
    try {
        String logEntry = "Connection from: " + request.getRemoteSocketAddress() + " at " + new Date() + "\n";
        Files.write(Paths.get("connection_log.txt"), logEntry.getBytes(), StandardOpenOption.APPEND);
    } catch (IOException e) {
        logger.log(Level.WARNING, "Error logging connection", e);
    }
}


    public void start() throws IOException {
        pool = Executors.newFixedThreadPool(NUM_THREADS);
        serverSocket = new ServerSocket(port);
        isRunning = true;
        logger.info("Accepting connections on port " + serverSocket.getLocalPort());
        logger.info("Document Root: " + rootDirectory);

        // Create ServerAdminUI instance and start the server
        SwingUtilities.invokeLater(() -> {
            try {
                ServerAdminUI adminUI = new ServerAdminUI(this);
                adminUI.setVisible(true);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error starting ServerAdminUI", e);
            }
        });

        while (isRunning) {
            try {
                Socket request = serverSocket.accept();
                logConnection(request);
                Runnable r = new RequestProcessor(rootDirectory, INDEX_FILE, request, "GET");
                pool.submit(r);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error accepting connection", ex);
            }
        }
    }

    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                pool.shutdown();
                isRunning = false;
                logger.info("Server stopped");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error stopping server", e);
            }
        }
    }

    public static void main(String[] args) {

        // get the Document root
        File docroot;
        try {
            docroot = new File(args[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("Usage: java JHTTP docroot port");
            return;
        }

        // set the port to listen on
        int port;
        try {
            port = Integer.parseInt(args[1]);
            if (port < 0 || port > 65535) port = 80;
        } catch (RuntimeException ex) {
            port = 80;
        }

        try {
            JHTTP webserver = new JHTTP(docroot, port);
            webserver.start();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Server could not start", ex);
        }
    }


}

