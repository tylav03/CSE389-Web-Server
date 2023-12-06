// import java.io.*;
// import java.net.*;
// import java.util.concurrent.*;
// import java.util.logging.*;

// import javax.swing.SwingUtilities;

// import java.nio.file.Files;
// import java.nio.file.Paths;
// import java.nio.file.StandardOpenOption;
// import java.util.Date;

// public class JHTTP {

//     private static final Logger logger = Logger.getLogger(JHTTP.class.getCanonicalName());
//     private static final int NUM_THREADS = 50;
//     private static final String INDEX_FILE = "index.html";

//     private final File rootDirectory;
//     private final int port;

//     private ServerSocket serverSocket;
//     private ExecutorService pool;
//     private boolean isRunning; // Flag to track server status

//     public JHTTP(File rootDirectory, int port) throws IOException {
//         if (!rootDirectory.isDirectory()) {
//             throw new IOException(rootDirectory + " does not exist as a directory");
//         }
//         this.rootDirectory = rootDirectory;
//         this.port = port;
//     }

//     public boolean isRunning() {
//         return isRunning;
//     }


//     public int getPort() {
//         return port;
//     }


//     private void logConnection(Socket request) {
//     try {
//         String logEntry = "Connection from: " + request.getRemoteSocketAddress() + " at " + new Date() + "\n";
//         Files.write(Paths.get("connection_log.txt"), logEntry.getBytes(), StandardOpenOption.APPEND);
//     } catch (IOException e) {
//         logger.log(Level.WARNING, "Error logging connection", e);
//     }
// }


//     public void start() throws IOException {
//         pool = Executors.newFixedThreadPool(NUM_THREADS);
//         serverSocket = new ServerSocket(port);
//         isRunning = true;
//         logger.info("Accepting connections on port " + serverSocket.getLocalPort());
//         logger.info("Document Root: " + rootDirectory);

//         // Create ServerAdminUI instance and start the server
//         SwingUtilities.invokeLater(() -> {
//             try {
//                 ServerAdminUI adminUI = new ServerAdminUI(this);
//                 adminUI.setVisible(true);
//             } catch (Exception e) {
//                 logger.log(Level.SEVERE, "Error starting ServerAdminUI", e);
//             }
//         });

//         while (isRunning) {
//             try {
//                 Socket request = serverSocket.accept();
//                 logConnection(request);
//                 Runnable r = new RequestProcessor(rootDirectory, INDEX_FILE, request, "GET");
//                 pool.submit(r);
//             } catch (IOException ex) {
//                 logger.log(Level.WARNING, "Error accepting connection", ex);
//             }
//         }
//     }

//     public void stop() {
//         if (serverSocket != null && !serverSocket.isClosed()) {
//             try {
//                 serverSocket.close();
//                 pool.shutdown();
//                 isRunning = false;
//                 logger.info("Server stopped");
//             } catch (IOException e) {
//                 logger.log(Level.SEVERE, "Error stopping server", e);
//             }
//         }
//     }

//     public static void main(String[] args) {

//         // get the Document root
//         File docroot;
//         try {
//             docroot = new File(args[0]);
//         } catch (ArrayIndexOutOfBoundsException ex) {
//             System.out.println("Usage: java JHTTP docroot port");
//             return;
//         }

//         // set the port to listen on
//         int port;
//         try {
//             port = Integer.parseInt(args[1]);
//             if (port < 0 || port > 65535) port = 80;
//         } catch (RuntimeException ex) {
//             port = 80;
//         }

//         try {
//             JHTTP webserver = new JHTTP(docroot, port);
//             webserver.start();
//         } catch (IOException ex) {
//             logger.log(Level.SEVERE, "Server could not start", ex);
//         }
//     }


// }


import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class RequestProcessor implements Runnable {

    private static final Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());

    private File rootDirectory;
    private String indexFileName = "index.html";
    private Socket connection;
    private String method;

    public RequestProcessor(File rootDirectory, String indexFileName, Socket connection, String method) {
        if (rootDirectory.isFile()) {
            throw new IllegalArgumentException("rootDirectory must be a directory, not a file");
        }
        try {
            rootDirectory = rootDirectory.getCanonicalFile();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error getting canonical file", ex);
        }
        this.rootDirectory = rootDirectory;

        if (indexFileName != null) this.indexFileName = indexFileName;
        this.connection = connection;
        this.method = method;
    }

    @Override
    public void run() {
        try (OutputStream raw = new BufferedOutputStream(connection.getOutputStream());
             Writer out = new OutputStreamWriter(raw);
             BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "US-ASCII"))) {

            // Read the request line
            String requestLine = in.readLine();

            System.out.println(connection.getRemoteSocketAddress() + " " + requestLine);

            // Log headers for debugging
            String header;
            System.out.println("All Headers:");
            while (!(header = in.readLine()).isEmpty()) {
            System.out.println(header);
            }

            String[] tokens = requestLine.split("\\s+");

            // Log headers for debugging
            Arrays.stream(tokens)
            .filter(token -> !token.isEmpty())
             .forEach(token -> System.out.println("Header: " + token));

            String authorizationHeader = findAuthorizationHeader(tokens);
            System.out.println("Authorization Header: " + authorizationHeader);


            logger.info(connection.getRemoteSocketAddress() + " " + requestLine);

            // Parse the request line
            //String[] tokens = requestLine.split("\\s+");
            String version = "";

            if (tokens.length > 2) {
                version = tokens[2];
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                handleGetHeadRequest(tokens, version, out, raw);
            } else if ("POST".equals(method)) {
                handlePostRequest(tokens, version, in, out, raw);
            } else {
                handleNotImplemented(out, version);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error talking to " + connection.getRemoteSocketAddress(), ex);
        } finally {
            try {
                connection.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error closing connection", ex);
            }
        }
    }

    private void handleGetHeadRequest(String[] tokens, String version, Writer out, OutputStream raw) throws IOException {
        String fileName = tokens[1];
        if (fileName.endsWith("/")) fileName += indexFileName;
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);
    
        File theFile = new File(rootDirectory, fileName.substring(1));
    
        if (theFile.canRead() && theFile.getCanonicalPath().startsWith(rootDirectory.getPath())) {
            // Check if the requested resource is MyResource.html
            if ("/MyResource.html".equals(fileName)) {
                // Perform authentication before allowing access
                if (authenticateUser(tokens)) {
                    // Continue processing if authentication is successful
                    sendAuthenticatedResource(out, raw, theFile, contentType);
                } else {
                    // Respond with authentication failure
                    sendAuthenticationRequired(out, raw);
                }
            } else {
                // Handle other resources as usual
                byte[] data = Files.readAllBytes(theFile.toPath());
    
                if ("HEAD".equals(method)) {
                    sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
                } else {
                    sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
                    raw.write(data);
                    raw.flush();
                }
            }
        } else {
            handleFileNotFound(out, raw, version);
        }
    }
    private boolean authenticateUser(String[] tokens) {
        System.out.println("Entering authenticateUser method");
    
        // Decode the Base64-encoded username:password string
        String authorizationHeader = findAuthorizationHeader(tokens);
        for(int i = 0; i<tokens.length; i++){
            System.out.println(tokens[i]);
        }
        System.out.println("Authorization Header: " + authorizationHeader);
    
        if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authorizationHeader.substring(6)));
            System.out.println("Decoded credentials: " + credentials);
    
            String[] userPass = credentials.split(":");
            String username = userPass[0].trim();
            String password = userPass[1].trim();
    
            // Check against the user.txt file
            Path userFilePath = rootDirectory.toPath().resolve("user.txt");
            System.out.println("User file path: " + userFilePath.toAbsolutePath());
    
            try (BufferedReader userFileReader = new BufferedReader(new FileReader(userFilePath.toFile()))) {
                String line;
                while ((line = userFileReader.readLine()) != null) {
                    System.out.println("Line: " + line);  // Log each line for debugging
                    String[] parts = line.split(":");
                    if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
                        System.out.println("Authentication successful!");
                        return true;
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading user.txt file", e);
            }
        }
    
        System.out.println("Exiting authenticateUser method");
        return false; // Authentication failure
    }
    
           
    

private String findAuthorizationHeader(String[] tokens) {
    // Find and return the Authorization header
    for (int i = 1; i < tokens.length - 1; i++) {
        if ("Authorization:".equalsIgnoreCase(tokens[i]) && i + 1 < tokens.length) {
            return tokens[i + 1].trim();
        }
    }
    return null;
}
    
    private void sendAuthenticatedResource(Writer out, OutputStream raw, File theFile, String contentType) throws IOException {
        // Process the request for authenticated users
        byte[] data = Files.readAllBytes(theFile.toPath());
    
        if ("HEAD".equals(method)) {
            sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
        } else {
            sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
            raw.write(data);
            raw.flush();
        }
    }

    private void sendAuthenticationRequired(Writer out, OutputStream raw) throws IOException {
        String body = "<HTML><HEAD><TITLE>Authentication Required</TITLE></HEAD><BODY>"
                + "<H1>HTTP Error 401: Authentication Required</H1></BODY></HTML>";
    
        // Include the WWW-Authenticate header
        out.write("HTTP/1.1 401 Unauthorized\r\n");
        out.write("WWW-Authenticate: Basic realm=\"MyRealm\"\r\n"); // Change MyRealm to an appropriate realm name
        out.write("Content-Type: text/html; charset=utf-8\r\n");
        out.write("Content-Length: " + body.length() + "\r\n");
        out.write("\r\n");
        
        if (!"HEAD".equals(method)) {
            out.write(body);
            out.flush();
        }
    }

    private void handlePostRequest(String[] tokens, String version, BufferedReader in, Writer out, OutputStream raw) throws IOException {
        
        // Process the POST request data, read data sent in body of POST request
        StringBuilder requestBody = new StringBuilder(); //accumalate data line by line
        String line;
        while ((line = in.readLine()) != null) { // continue loop until in.ready returns false, which means there is no more data left to read
            requestBody.append(in.readLine()); //Reads a line of text from the BufferedReader using readLine() and appends it to the requestBody. The readLine() method reads characters until a newline character ('\n') is encountered, and it returns the line excluding the newline character.
        }

        // Process the POST request data 
        String responseBody = "<HTML><HEAD><TITLE>POST Request Processed</TITLE></HEAD><BODY>"
                + "<H1>POST Request Processed</H1><p>Request Body: " + requestBody.toString() + "</p></BODY></HTML>"; //inserts the content of the requestBody (the data received in the POST request) into the HTML.

        sendHeader(out, "HTTP/1.0 200 OK", "text/html; charset=utf-8", responseBody.length());
        if (!"HEAD".equals(method)) {
            out.write(responseBody);
            out.flush();
        }
    }

    private void sendHeader(Writer out, String responseCode, String contentType, int length) throws IOException {
        out.write(responseCode + "\r\n");
        Date now = new Date();
        out.write("Date: " + now + "\r\n");
        out.write("Server: JHTTP 2.0\r\n");
        out.write("Content-length: " + length + "\r\n");
        out.write("Content-type: " + contentType + "\r\n\r\n");
        out.flush();
    }

    private void handleFileNotFound(Writer out, OutputStream raw, String version) throws IOException {
        String body = "<HTML><HEAD><TITLE>File Not Found</TITLE></HEAD><BODY>"
                + "<H1>HTTP Error 404: File Not Found</H1></BODY></HTML>";

        if (version.startsWith("HTTP/")) {
            sendHeader(out, "HTTP/1.0 404 File Not Found", "text/html; charset=utf-8", body.length());
        }
        if (!"HEAD".equals(method)) {
            out.write(body);
            out.flush();
        }
    }

    private void handleNotImplemented(Writer out, String version) throws IOException {
        String body = "<HTML><HEAD><TITLE>Not Implemented</TITLE></HEAD><BODY>"
                + "<H1>HTTP Error 501: Not Implemented</H1></BODY></HTML>";

        if (version.startsWith("HTTP/")) {
            sendHeader(out, "HTTP/1.0 501 Not Implemented", "text/html; charset=utf-8", body.length());
        }
        if (!"HEAD".equals(method)) {
            out.write(body);
            out.flush();
        }
    }
}


