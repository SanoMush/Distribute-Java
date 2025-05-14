package edu.coursera.distributed;

import java.net.ServerSocket;
import java.net.Socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

/**
 * A basic and very limited implementation of a file server that responds to GET
 * requests from HTTP clients.
 */
public final class FileServer {
    /**
     * Main entrypoint for the basic file server.
     *
     * @param socket Provided socket to accept connections on.
     * @param fs     A proxy filesystem to serve files from. See the PCDPFilesystem
     *               class for more detailed documentation of its usage.
     * @throws IOException If an I/O error is detected on the server. This
     *                     should be a fatal error, your file server
     *                     implementation is not expected to ever throw
     *                     IOExceptions during normal operation.
     */
    public void run(final ServerSocket socket, final PCDPFilesystem fs)
            throws IOException {

        while (true) {
            try (
                    Socket clientSocket = socket.accept(); 
                    InputStream in = clientSocket.getInputStream();
                    OutputStream out = clientSocket.getOutputStream();
            ) {

                byte[] buffer = new byte[1024];
                int read = in.read(buffer);
                if (read == -1) {
                    continue;
                }
                String request = new String(buffer, 0, read);
                String[] lines = request.split("\r\n");
                String[] tokens = lines[0].split(" ");


                if (!tokens[0].equals("GET")) {
                    continue;
                }

                String path = tokens[1];


                String fileContents = fs.readFile(new PCDPPath(path));


                if (fileContents != null) {
                    String response = "HTTP/1.0 200 OK\r\n" +
                            "Server: FileServer\r\n" +
                            "\r\n" +
                            fileContents + "\r\n";
                    out.write(response.getBytes());
                } else {
                    String response = "HTTP/1.0 404 Not Found\r\n" +
                            "Server: FileServer\r\n" +
                            "\r\n";
                    out.write(response.getBytes());
                }

                out.flush();
            }
        }
    }
}