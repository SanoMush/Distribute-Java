package edu.coursera.distributed;

import java.net.ServerSocket;
import java.net.Socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileServer {
    public void run(final ServerSocket socket, final PCDPFilesystem fs, final int ncores) throws IOException {
        // Buat thread pool dengan jumlah thread sesuai jumlah core
        final ExecutorService threadPool = Executors.newFixedThreadPool(ncores);

        while (true) {
            final Socket clientSocket = socket.accept();

            threadPool.submit(() -> {
                try {
                    handleClient(clientSocket, fs);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void handleClient(Socket clientSocket, PCDPFilesystem fs) throws IOException {
        InputStream input = clientSocket.getInputStream();
        OutputStream output = clientSocket.getOutputStream();

        // Baca request HTTP
        byte[] buffer = new byte[1024];
        int bytesRead = input.read(buffer);

        if (bytesRead == -1) {
            return;
        }

        String request = new String(buffer, 0, bytesRead);
        String[] lines = request.split("\r\n");

        if (lines.length > 0 && lines[0].startsWith("GET")) {
            String[] tokens = lines[0].split(" ");
            if (tokens.length >= 2) {
                String path = tokens[1]; // path setelah GET
                PCDPPath filePath = new PCDPPath(path);
                String fileContents = fs.readFile(filePath);

                if (fileContents != null) {
                    String response = "HTTP/1.0 200 OK\r\n" +
                            "Server: FileServer\r\n" +
                            "\r\n" +
                            fileContents;
                    output.write(response.getBytes());
                } else {
                    String response = "HTTP/1.0 404 Not Found\r\n" +
                            "Server: FileServer\r\n" +
                            "\r\n";
                    output.write(response.getBytes());
                }
            }
        }

        output.flush();
    }
}
