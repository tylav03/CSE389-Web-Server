import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.logging.*;
import java.util.Date;

public class RequestProcessor implements Runnable {

    private static final Logger logger = Logger.getLogger(RequestProcessor.class.getCanonicalName());

    private File rootDirectory;
    private String indexFileName = "index.html";
    private Socket connection;
    private String method;

    // Constructor for the RequestProcessor class
    public RequestProcessor(File rootDirectory, String indexFileName, Socket connection, String method) 
    {
        if (rootDirectory.isFile())  // Check if rootDirectory is a directory, not a file
        {
            throw new IllegalArgumentException("rootDirectory must be a directory, not a file");
        }
        try 
        {
            rootDirectory = rootDirectory.getCanonicalFile();
        } 
        catch (IOException ex) 
        {
            logger.log(Level.WARNING, "Error getting canonical file", ex);
        }
        this.rootDirectory = rootDirectory;

        if (indexFileName != null) this.indexFileName = indexFileName;
        this.connection = connection;
        this.method = method;
    }
    // Entry point for the thread handling the request
    @Override
    public void run() {
        try (OutputStream raw = new BufferedOutputStream(connection.getOutputStream());
             Writer out = new OutputStreamWriter(raw);
             BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "US-ASCII"))) {

            // Read the request line
            String requestLine = in.readLine();

            // Log the request information
            logger.info(connection.getRemoteSocketAddress() + " " + requestLine);

            // Parse the request line
            String[] tokens = requestLine.split("\\s+");
            String version = "";

            if (tokens.length > 2) 
            {
                version = tokens[2];
            }
            // Handle different HTTP methods
            if ("GET".equals(method) || "HEAD".equals(method)) 
            {
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

     // Handle GET and HEAD requests
    private void handleGetHeadRequest(String[] tokens, String version, Writer out, OutputStream raw) throws IOException 
    {   
         // Extract requested file name from the request
        String fileName = tokens[1];

        if (fileName.endsWith("/")) fileName += indexFileName;

         // Get content type based on file extension
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);

         // Create File object representing the requested file
        File theFile = new File(rootDirectory, fileName.substring(1));

        if (theFile.canRead() && theFile.getCanonicalPath().startsWith(rootDirectory.getPath())) {
            // Read file content into a byte array
            byte[] data = Files.readAllBytes(theFile.toPath());

            // Respond to HEAD request with headers only
            if ("HEAD".equals(method)) {
                sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
            } else {  // Respond to GET request with headers and file content
                sendHeader(out, "HTTP/1.0 200 OK", contentType, data.length);
                raw.write(data);
                raw.flush();
            }
        } else {
            handleFileNotFound(out, raw, version);
        }
    }
    // Handle POST requests
    private void handlePostRequest(String[] tokens, String version, BufferedReader in, Writer out, OutputStream raw) throws IOException {
        
        // Process the POST request data, read data sent in body of POST request
        StringBuilder requestBody = new StringBuilder(); //accumalate data line by line
        while (in.ready()) { // continue loop until in.ready returns false, which means there is no more data left to read
            requestBody.append(in.readLine()); //Reads a line of text from the BufferedReader using readLine() and appends it to the requestBody. The readLine() method reads characters until a newline character ('\n') is encountered, and it returns the line excluding the newline character.
        }

        // Process the POST request data 
        String responseBody = "<HTML><HEAD><TITLE>POST Request Processed</TITLE></HEAD><BODY>"
                + "<H1>POST Request Processed</H1><p>Request Body: " + requestBody.toString() + "</p></BODY></HTML>"; //inserts the content of the requestBody (the data received in the POST request) into the HTML.

        sendHeader(out, "HTTP/1.0 200 OK", "text/html; charset=utf-8", responseBody.length());
        if (!"HEAD".equals(method)) {  // Respond to POST request with HTML body
            out.write(responseBody);
            out.flush();
        }
    }
     // Send HTTP headers
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
