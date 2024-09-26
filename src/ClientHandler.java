import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import javax.imageio.ImageIO;

public class ClientHandler implements Runnable {

    /**
     * Socket representing the client connection
     */
    Socket clientSocket;
//dvdv
    /**
     * Reads data from the client sent to the proxy
     */
    BufferedReader proxyClientReader;

    private long startTime; // Variable to store start time for timing measurement
    private long endTime; // Variable to store end time for timing measurement
    private long totalDataSaved; // Variable to store total data saved
    /**
     * Sends data from the proxy to the client
     */
    BufferedWriter proxyClientWriter;


    /**
     * Thread used to transmit data read from client to server when using HTTPS
     * Reference to this thread is required for proper closing.
     */
    private Thread HTTPSClientServer;


    /**
     * Constructs a RequestHandler object capable of handling HTTP(S) GET requests
     * @param clientSocket Socket connected to the client
     */
    public ClientHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
        try{
            this.clientSocket.setSoTimeout(20000);
            proxyClientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            proxyClientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Reads and examines the requestString and calls the appropriate method based
     * on the request type.
     */
    @Override
    public void run() {

        // Get Request from the client
        String requestString;
        try{
            requestString = proxyClientReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error reading request from the client");
            return;
        }
        if(requestString!=null) {
            // Parsing out URL

            // Get request type + URL
            String request = requestString.substring(0, requestString.indexOf(' '));

            String urlString = requestString.substring(requestString.indexOf(' ') + 1);
            urlString = urlString.substring(0, urlString.indexOf(' '));

            // Prepend http:// if necessary to form a correct URL
            if (urlString.startsWith("/")) {
                urlString =urlString.substring(1);
            }
            if (!urlString.startsWith("http")) {
                String temp = "http://";
                urlString = temp + urlString;
            }

            // Check if the site is blocked
            if (ProxyServer.isBlocked(urlString)) {
                System.out.println("Blocked site requested: " + urlString);
                blockedSiteRequested();
                return;
            }

            // Check the request type
            if (request.equals("CONNECT")) {
                System.out.println("HTTPS Request for: " + urlString + "");
                handleHTTPS(urlString);
            } else if (request.equals("POST")) {
                System.out.println("HTTP POST for: " + urlString + "");
                // Handling POST request
                handlePOSTRequest(urlString, requestString);
            } else {
                // Check if there's a cached copy
                File file;
                startTime = System.currentTimeMillis(); // Start timing



                if ((file = ProxyServer.getCachedPage(urlString)) != null) {
                    System.out.println("Cached Copy found for: " + urlString + "");
                    sendCached(file);
                    endTime = System.currentTimeMillis(); // End timing
                    long elapsedTime = endTime - startTime; // Calculate elapsed time
                    totalDataSaved = file.length();
                    System.out.println("Time taken from cache: " + elapsedTime + " ms");
                    System.out.println("Data saved :"+totalDataSaved*8*0.000001+"Mbits");
                    System.out.println("Bandwith :"+(totalDataSaved*8*0.000001)/(elapsedTime*0.001)+"Mbits/s");

                } else {
                    System.out.println("HTTP GET for: " + urlString + "");
                    long d = sendNonCached(urlString);
                    endTime = System.currentTimeMillis(); // End timing
                    long elapsedTime = endTime - startTime; // Calculate elapsed time
                    System.out.println("Time taken without cache: " + elapsedTime + " ms");
                    System.out.println("Data saved :"+d*8*0.000001+"Mbits");
                    System.out.println("Bandwith :"+(d*8*0.0000001)/(elapsedTime*0.001)+"Mbits/s");
                }
            }
        }else{
            //System.out.println("null request");
        }
    }

    private void handlePOSTRequest(String urlString, String requestString) {
        // Extracting POST data
        String[] requestLines = requestString.split("\r\n");
        String requestType = requestLines[0].split(" ")[0]; // Getting the request type from the first line
        int contentLengthIndex = -1;
        for (int i = 0; i < requestLines.length; i++) {
            if (requestLines[i].toLowerCase().startsWith("content-length:")) {
                contentLengthIndex = i;
                break;
            }
        }
        if (contentLengthIndex != -1) {
            int contentLength = Integer.parseInt(requestLines[contentLengthIndex].split(" ")[1]);
            StringBuilder postData = new StringBuilder();
            for (int i = contentLengthIndex + 1; i < requestLines.length; i++) {
                postData.append(requestLines[i]);
            }

            // Forwarding POST data to the server
            forwardPOSTDataToServer(urlString, postData.toString());
        }  // Handling the case where the Content-Length header is missing

    }


    /**
     * Forwards POST data to the server.
     *
     * @param urlString URL requested by the client
     * @param postData  POST data to be forwarded
     */
    private void forwardPOSTDataToServer(String urlString, String postData) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            // Forwarding the POST data to the server
            try (OutputStream os = connection.getOutputStream()) {
                byte[] postDataBytes = postData.getBytes();
                os.write(postDataBytes);
                os.flush();
            }

            // You may need to handle the server's response here
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sends the specified cached file to the client
     * @param cachedFile The file to be sent (can be image/text)
     */
    private void sendCached(File cachedFile){
        // Sending a File containing cached web page
        try{
            String extension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));
            String resp;
            // Identifying images, write data to the client using buffered image.
            if((extension.contains(".png")) || extension.contains(".jpg") ||
                    extension.contains(".jpeg") || extension.contains(".gif")){
                // Reading in the image from storage
                BufferedImage image = ImageIO.read(cachedFile);

                if(image == null ){
                    System.out.println("Image " + cachedFile.getName() + " was null");
                    resp = "HTTP/1.0 404 NOT FOUND \n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyClientWriter.write(resp);
                    proxyClientWriter.flush();
                } else {
                    resp = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyClientWriter.write(resp);
                    proxyClientWriter.flush();
                    ImageIO.write(image, extension.substring(1), clientSocket.getOutputStream());
                }
            }

            // Standard text-based file requested
            else {
                BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

                resp = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                proxyClientWriter.write(resp);
                proxyClientWriter.flush();

                String line;
                while((line = cachedFileBufferedReader.readLine()) != null){
                    proxyClientWriter.write(line);
                }
                proxyClientWriter.flush();

                // Closing resources
                if(cachedFileBufferedReader != null){
                    cachedFileBufferedReader.close();
                }
            }
            // Closing down resources
            if(proxyClientWriter != null){
                proxyClientWriter.close();
            }

        } catch (IOException e) {
            System.out.println("Error Sending Cached file to client");
            e.printStackTrace();
        }
    }


    /**
     * Sends the contents of the file specified by the urlString to the client
     * @param urlString URL of the file requested
     */
    private long sendNonCached(String urlString) {
        long d=0;
        try {
            // Separation of the file name & extension
            int fileExtensionIndex = urlString.lastIndexOf(".");
            String extension;
            extension = urlString.substring(fileExtensionIndex, urlString.length());
            String fileName = urlString.substring(0, fileExtensionIndex);
            fileName = fileName.substring(7);
            //treatment of both to remove special character
            fileName = fileName.replace("/", "__");
            fileName = fileName.replace('.', '_');
            extension = extension.replace('?', '_');
            fileName = fileName.replace('?', '_');
            urlString=urlString.substring(7);
            urlString="https://"+urlString;
            if (extension.contains("/")) {
                extension = extension.replace("/", "__");
                extension = extension.replace('.', '_');
                extension += ".html";
            }

            fileName = fileName + extension;
            boolean caching = true;
            File fileToCache = null;
            BufferedWriter fileToCacheBufferedWriter = null;

            //Create the File in the "cached" directory to put the data
            try {
                fileToCache = new File("cached/" + fileName);
                File parentDir = fileToCache.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                if (!fileToCache.exists()) {
                    new File("cached/").mkdirs();
                    fileToCache.createNewFile();
                }
                fileToCacheBufferedWriter = new BufferedWriter(new FileWriter(fileToCache));
            } catch (IOException e) {
                System.out.println("Couldn't cache: " + fileName);
                caching = false;
                e.printStackTrace();
            } catch (NullPointerException e) {
                System.out.println("NPE opening file");
            }

            URL remoteURL = new URL(urlString);
            // If the document is an image, using of beffered image to write in the file
            if ((extension.contains(".png")) || extension.contains(".jpg") ||
                    extension.contains(".jpeg") || extension.contains(".gif") || extension.contains(".ico")) {
                BufferedImage image = ImageIO.read(remoteURL);
                if (image != null) {
                    assert fileToCache != null;
                    ImageIO.write(image, extension.substring(1), fileToCache);

                    String line = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyClientWriter.write(line);
                    proxyClientWriter.flush();
                    ImageIO.write(image, extension.substring(1), clientSocket.getOutputStream());
                } else {
                    System.out.println("Sending 404 to client as image wasn't received from server"
                            + fileName);
                    String error = "HTTP/1.0 404 NOT FOUND\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    proxyClientWriter.write(error);
                    proxyClientWriter.flush();
                    return d;
                }
            } else {
                // If the document is a text, retrieve data from the server and write it line by line
                HttpURLConnection proxyServerConnection = (HttpURLConnection) remoteURL.openConnection();
                proxyServerConnection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                proxyServerConnection.setRequestProperty("Content-Language", "en-US");
                proxyServerConnection.setUseCaches(false);
                proxyServerConnection.setDoOutput(true);
                BufferedReader proxyToServerBufferedReader = new BufferedReader(new InputStreamReader(proxyServerConnection.getInputStream()));

                String line = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                proxyClientWriter.write(line);

                while ((line = proxyToServerBufferedReader.readLine()) != null) {
                    proxyClientWriter.write(line);
                    if (caching) {
                        if (fileToCacheBufferedWriter != null) {
                            fileToCacheBufferedWriter.write(line);
                        }
                    }
                }

                proxyClientWriter.flush();
                if (proxyToServerBufferedReader != null) {
                    proxyToServerBufferedReader.close();
                }
            }
            //add file to hashmap
            if (caching) {
                if (fileToCacheBufferedWriter != null) {
                    fileToCacheBufferedWriter.flush();
                }
                urlString=urlString.substring(8);
                urlString="http://"+urlString;
                ProxyServer.addCachedPage(urlString, fileToCache);
            }
            d=fileToCache.length();
            if (fileToCacheBufferedWriter != null) {
                fileToCacheBufferedWriter.close();
            }

            if (proxyClientWriter != null) {
                proxyClientWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return d;
    }


    /**
     * Handles HTTPS requests between the client and remote server
     * @param urlString Desired file to be transmitted over HTTPS
     */
    private void handleHTTPS(String urlString){
        // Extracting the URL and port of the remote
        String url = urlString.substring(7);
        String[] pieces = url.split(":");
        url = pieces[0];
        int port  = Integer.parseInt(pieces[1]);

        try{
            // discard the rest of the initial data on the stream
            for(int i=0;i<5;i++){
                proxyClientReader.readLine();
            }

            // Get IP with URL thanks to DNS
            InetAddress address = InetAddress.getByName(url);

            // Opening a socket to the remote server
            Socket proxyToServerSocket = new Socket(address, port);
            proxyToServerSocket.setSoTimeout(50000);

            // Confirm connexion to the client
            String line = "HTTP/1.0 200 Connection established\r\n" +
                    "Proxy-Agent: ProxyServer/1.0\r\n" +
                    "\r\n";
            proxyClientWriter.write(line);
            proxyClientWriter.flush();

            // Both client and remote will start sending data to proxy at this point
            // Proxy needs to asynchronously read data from each party and send it to the other party

            // Creating a Buffer between proxy and remote
            BufferedWriter proxyToServerBufferedWriter = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));
            BufferedReader proxyToServerBufferedReader = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));

            // Creating a new thread to listen to the client and transmit to the server
            ClientServerHTTPS clientToServerHttps =
                    new ClientServerHTTPS(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());
            HTTPSClientServer = new Thread(clientToServerHttps);
            HTTPSClientServer.start();


            // Listening to the remote server and relaying to the client
            try {
                byte[] buffer = new byte[4096];
                int r;
                do {
                    r = proxyToServerSocket.getInputStream().read(buffer);
                    if (r > 0) {
                        clientSocket.getOutputStream().write(buffer, 0, r);
                        if (proxyToServerSocket.getInputStream().available() < 1) {
                            clientSocket.getOutputStream().flush();
                        }
                    }
                } while (r >= 0);
            }
            catch (SocketTimeoutException e) {
                handleHttpsTimeout();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            // Closing down resources
            if(proxyToServerSocket != null){
                proxyToServerSocket.close();
            }
            if(proxyToServerBufferedReader != null){
                proxyToServerBufferedReader.close();
            }
            if(proxyToServerBufferedWriter != null){
                proxyToServerBufferedWriter.close();
            }
            if(proxyClientWriter != null){
                proxyClientWriter.close();
            }
        } catch (SocketTimeoutException e) {
            String line = "HTTP/1.0 504 Timeout Occurred after 10s\n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            try{
                proxyClientWriter.write(line);
                proxyClientWriter.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        catch (Exception e){
            System.out.println("Error on HTTPS: " + urlString );
            e.printStackTrace();
        }
    }




    /**
     * Listening to data from the client and transmitting it to the server.
     * This is done on a separate thread as it must be done
     * asynchronously to reading data from the server and transmitting
     * that data to the client.
     */
    class ClientServerHTTPS implements Runnable{
        InputStream proxyClientInput;
        OutputStream proxyServerOutput;
        public ClientServerHTTPS(InputStream proxyClientInput, OutputStream proxyServerOutput) {
            this.proxyClientInput = proxyClientInput;
            this.proxyServerOutput = proxyServerOutput;
        }
        @Override
        public void run(){
            try {
                // Reading byte by byte from the client and sending directly to the server
                byte[] buffer = new byte[4096];
                int r;
                do {
                    r = proxyClientInput.read(buffer);
                    if (r > 0) {
                        proxyServerOutput.write(buffer, 0, r);
                        if (proxyClientInput.available() < 1) {
                            proxyServerOutput.flush();
                        }
                    }
                } while (r >= 0);
            }
            catch (SocketTimeoutException ste) {
                handleHttpsTimeout();
            }
            catch (IOException e) {
                System.out.println("Proxy to client HTTPS read timed out");
                handleHttpsTimeout();
                e.printStackTrace();
            }
        }
    }
    public void handleHttpsTimeout() {

    }


    /**
     * This method is called when a user requests a page that is blocked by the proxy.
     * Sends an access forbidden message back to the client
     */
    private void blockedSiteRequested(){
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            String line = "HTTP/1.0 403 Access Forbidden \n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            bufferedWriter.write(line);
            bufferedWriter.flush();
        } catch (IOException e) {
            System.out.println("Error requesting a blocked site");
            e.printStackTrace();
        }
    }
}
