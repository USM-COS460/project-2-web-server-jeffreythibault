import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HTTPServer {
    private static File documentRoot;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java HTTPServer <port> <document_root_path>");
            System.exit(1);
        }

        int port = -1;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[0]);
            System.exit(1);
        }

        documentRoot = new File(args[1]);
        if (!documentRoot.isDirectory()) {
            System.err.println("Document root is not a valid directory: " + args[1]);
            System.exit(1);
        }

        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Serving files from: " + documentRoot.getAbsolutePath());
            System.out.println("Press Ctrl+C to stop...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new RequestHandler(clientSocket, documentRoot));
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    //MIME
    private static String getMimeType(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType != null) {
            return mimeType;
        }
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        }
        //default
        return "application/octet-stream";
    }

    private static class RequestHandler implements Runnable {
        private final Socket clientSocket;
        private final File docRoot;

        public RequestHandler(Socket socket, File documentRoot) {
            this.clientSocket = socket;
            this.docRoot = documentRoot;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                
                String requestLine = in.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    return; 
                }
                
                System.out.println("Received: " + requestLine);
                String[] parts = requestLine.split(" ");

                while (true) {
                    String headerLine = in.readLine();
                    if (headerLine == null || headerLine.isEmpty()) {
                        break;
                    }
                }

                if (parts.length < 3 || !parts[0].equalsIgnoreCase("GET")) {
                    sendErrorResponse(out, "400 Bad Request", "text/html", "<h1>400 Bad Request: Only GET is supported.</h1>");
                    return;
                }

                String requestPath = parts[1];
                if (requestPath.contains("..")) {
                     sendErrorResponse(out, "403 Forbidden", "text/html", "<h1>403 Forbidden: Invalid Path.</h1>");
                     return;
                }
                
                if (requestPath.equals("/")) {
                    requestPath = "/index.html"; 
                }
                
                File requestedFile = new File(docRoot, requestPath.substring(1));

                if (!requestedFile.exists() || requestedFile.isDirectory()) {
                    sendErrorResponse(out, "404 Not Found", "text/html", "<h1>404 Not Found: " + requestPath + "</h1>");
                } else {
                    sendFile(out, requestedFile);
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
        
        private void sendFile(OutputStream out, File file) throws IOException {
            String mimeType = getMimeType(file);
            long contentLength = file.length();
            
            // Build the response header
            String header = "HTTP/1.1 200 OK\r\n" +
                            "Date: " + new Date() + "\r\n" +
                            "Server: Simple-Java-Server/1.0\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + contentLength + "\r\n" +
                            "\r\n"; // Blank line separates headers and body

            out.write(header.getBytes("UTF-8"));

            // Write the file content (the body)
            try (FileInputStream fileIn = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            out.flush();
        }


        private void sendErrorResponse(OutputStream out, String statusCode, String mimeType, String body) throws IOException {
             String header = "HTTP/1.1 " + statusCode + "\r\n" +
                            "Date: " + new Date() + "\r\n" +
                            "Server: Simple-Java-Server/1.0\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + body.length() + "\r\n" +
                            "\r\n"; // Blank line
            
            out.write(header.getBytes("UTF-8"));
            out.write(body.getBytes("UTF-8"));
            out.flush();
        }
    }
}