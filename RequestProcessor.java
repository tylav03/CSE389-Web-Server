import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.logging.*;
import java.util.Date;
import java.util.Map;

public class RequestProcessor implements Runnable {

    // Logger for logging messages
    private static final Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());

    // Fields to store information about the request and processing context
    private File rootDirectory;
    private String indexFileName = "index.html";
    private Socket connection;
    private String method;
    private Map<String, byte[]> cacheMap;

    // Constructor to initialize the RequestProcessor with necessary information
    public RequestProcessor(File rootDirectory, String indexFileName, Socket connection, String method, Map<String, byte[]> cacheMap) {
        // Check if rootDirectory is a directory, not a file
        if (rootDirectory.isFile()) {
            throw new IllegalArgumentException("rootDirectory must be a directory, not a file");
        }

        try {
            // Get the canonical file representation of rootDirectory
            rootDirectory = rootDirectory.getCanonicalFile();
        } catch (IOException ex) {
            // Log a warning if there's an error getting the canonical file
            logger.log(Level.WARNING, "Error getting canonical file", ex);
        }

        // Initialize fields with provided values
        this.rootDirectory = rootDirectory;
        if (indexFileName != null) this.indexFileName = indexFileName;
        this.connection = connection;
        this.method = method;
        this.cacheMap = cacheMap;
    }

    // Runnable interface method to handle the request
    @Override
    public void run() {
        try (
            OutputStream raw = new BufferedOutputStream(connection.getOutputStream());
            Writer out = new OutputStreamWriter(raw);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "US-ASCII"))
        ) {
            // Read the request line
            String requestLine = in.readLine();
            logger.info(connection.getRemoteSocketAddress() + " " + requestLine);

            // Parse the request line
            String[] tokens = requestLine.split("\\s+");
            String version = (tokens.length > 2) ? tokens[2] : "";

            // Handle the request based on the HTTP method
            if ("GET".equals(method) || "HEAD".equals(method)) {
                handleGetHeadRequest(tokens, version, out, raw);
            } else if ("POST".equals(method)) {
                handlePostRequest(tokens, version, in, out, raw);
            } else {
                handleNotImplemented(out, version);
            }
        } catch (IOException ex) {
            // Log a warning if there's an error during request processing
            logger.log(Level.WARNING, "Error talking to " + connection.getRemoteSocketAddress(), ex);
        } finally {
            try {
                // Close the connection
                connection.close();
            } catch (IOException ex) {
                // Log a warning if there's an error closing the connection
                logger.log(Level.WARNING, "Error closing connection", ex);
            }
        }
    }

    // Method to handle GET and HEAD requests
    private void handleGetHeadRequest(String[] tokens, String version, Writer out, OutputStream raw) throws IOException {
        // Extract the requested file name from the request tokens
        String fileName = tokens[1];

        // If the file name ends with "/", append the default index file name
        if (fileName.endsWith("/")) fileName += indexFileName;

        // Get the content type for the file
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);

        // Check if the file is cached
        if (cacheMap.containsKey(fileName)) {
            byte[] cachedData = cacheMap.get(fileName);
            sendHeader(out, "HTTP/1.0 200 OK", contentType, cachedData.length);

            // If it's a GET request, send the cached data
            if ("GET".equals(method)) {
                raw.write(cachedData);
                raw.flush();
            }
        } else {
            // If the file is not cached, read it from the file system
            File theFile = new File(rootDirectory, fileName.substring(1));

            // Check if the file is readable and within the server's root directory
            if (theFile.canRead() && theFile.getCanonicalPath().startsWith(rootDirectory.getPath())) {
                // Read the file data
                byte[] data = Files.readAllBytes(theFile.toPath());

                // Cache the file data
                cacheMap.put(fileName, data);

                // Send the HTTP header
                sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);

                // If it's a GET request, send the file data
                if ("GET".equals(method)) {
                    raw.write(data);
                    raw.flush();
                }
            } else {
                // If the file is not readable or outside the root directory, handle file not found
                handleFileNotFound(out, raw, version);
            }
        }
    }

    // Method to handle POST requests
    private void handlePostRequest(String[] tokens, String version, BufferedReader in, Writer out, OutputStream raw) throws IOException {
        // Process the POST request data, read data sent in the body of POST request
        StringBuilder requestBody = new StringBuilder();
        while (in.ready()) {
            requestBody.append(in.readLine());
        }

        // Create the HTML response body for the POST request
        String responseBody = "<HTML><HEAD><TITLE>POST Request Processed</TITLE></HEAD><BODY>"
                + "<H1>POST Request Processed</H1><p>Request Body: " + requestBody.toString() + "</p></BODY></HTML>";

        // Send the HTTP header for the POST response
        sendHeader(out, "HTTP/1.0 200 OK", "text/html; charset=utf-8", responseBody.length());

        // If it's not a HEAD request, send the response body
        if (!"HEAD".equals(method)) {
            out.write(responseBody);
            out.flush();
        }
    }

    // Method to send the HTTP header
    private void sendHeader(Writer out, String responseCode, String contentType, int length) throws IOException {
        out.write(responseCode + "\r\n");
        Date now = new Date();
        out.write("Date: " + now + "\r\n");
        out.write("Server: JHTTP 2.0\r\n");
        out.write("Content-length: " + length + "\r\n");
        out.write("Content-type: " + contentType + "\r\n\r\n");
        out.flush();
    }

    // Method to handle file not found errors
    private void handleFileNotFound(Writer out, OutputStream raw, String version) throws IOException {
        String body = "<HTML><HEAD><TITLE>File Not Found</TITLE></HEAD><BODY>"
                + "<H1>HTTP Error 404: File Not Found</H1></BODY></HTML>";

        // Send the HTTP header for file not found
        if (version.startsWith("HTTP/")) {
            sendHeader(out, "HTTP/1.0 404 File Not Found", "text/html; charset=utf-8", body.length());
        }

        // If it's not a HEAD request, send the response body
        if (!"HEAD".equals(method)) {
            out.write(body);
            out.flush();
        }
    }

    // Method to handle not implemented errors
    private void handleNotImplemented(Writer out, String version) throws IOException {
        // Create the HTML response body for the not implemented error
        String body = "<HTML><HEAD><TITLE>Not Implemented</TITLE></HEAD><BODY>"
                + "<H1>HTTP Error 501: Not Implemented</H1></BODY></HTML>";

        // Send the HTTP header for not implemented error
        if (version.startsWith("HTTP/")) {
            sendHeader(out, "HTTP/1.0 501 Not Implemented", "text/html; charset=utf-8", body.length());
        }

        // If it's not a HEAD request, send the response body
        if (!"HEAD".equals(method)) {
            out.write(body);
            out.flush();
        }
    }
}
