import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * The ProxyServer class implements a simple proxy server that caches web pages and blocks certain sites.
 */
public class ProxyServer implements Runnable {
    private ServerSocket socketServer;
    private final int port;

    // Volatile boolean to control the server's running state
    private volatile boolean running = true;

    // HashMap to store cached web pages
    static HashMap<String, File> cache;

    // HashMap to store blocked sites
    static HashMap<String, String> blockedSites;

    // ArrayList to hold client handler threads
    static ArrayList<Thread> threadList;

    /**
     * Initializes the proxy server with a specified port.
     * @param port The port number to run the proxy server on.
     */
    public ProxyServer(int port) {
        this.port = port;
        cache = new HashMap<>();
        blockedSites = new HashMap<>();
        threadList = new ArrayList<>();
        new Thread(this).start();

        try {
            // Load cached sites from file if they exist
            File cachedSites = new File("cache.txt");
            if (!cachedSites.exists()) {
                System.out.println("No cached sites found - creating new file");
                cachedSites.createNewFile();
            } else {
                FileInputStream fileInputStream = new FileInputStream(cachedSites);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                cache = (HashMap<String, File>) objectInputStream.readObject();
                fileInputStream.close();
                objectInputStream.close();
            }

            // Load blocked sites from file if they exist
            File blockedSitesTxtFile = new File("block.txt");
            if (!blockedSitesTxtFile.exists()) {
                System.out.println("No blocked sites found - creating new file");
                blockedSitesTxtFile.createNewFile();
            } else {
                FileInputStream fileInputStream = new FileInputStream(blockedSitesTxtFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                blockedSites = (HashMap<String, String>) objectInputStream.readObject();
                fileInputStream.close();
                objectInputStream.close();
            }
        } catch (IOException e) {
            System.out.println("Error loading previously cached sites file");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found loading in previously cached sites file");
            e.printStackTrace();
        }

    }

    /**
     * Starts the proxy server.
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        int port = 5555; // Default port for HTTP
        ProxyServer proxyServer = new ProxyServer(port);
        proxyServer.init();
        proxyServer.acceptConnections();
    }

    /**
     * Handles console commands.
     */
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        String command;
        while (running) {
            System.out.println("Enter a site to block, or type \"blocked\" to see blocked sites, \"cached\" to see cached sites, or \"close\" to close the server.");
            command = scanner.nextLine();
            if (command.equalsIgnoreCase("blocked")) {
                System.out.println("\nCurrently Blocked Sites");
                for (String key : blockedSites.keySet()) {
                    System.out.println(key);
                }
                System.out.println();
            } else if (command.equalsIgnoreCase("cached")) {
                System.out.println("\nCurrently Cached Sites");
                for (String key : cache.keySet()) {
                    System.out.println(key);
                }
                System.out.println();
            } else if (command.equals("close")) {
                running = false;
                closeServer();
            } else {
                blockedSites.put(command, command);
                System.out.println("\n" + command + " blocked successfully \n");
            }
        }
        scanner.close();
    }

    /**
     * Initializes the server socket.
     */
    public void init() {
        try {
            socketServer = new ServerSocket(port);
            System.out.println("Waiting for clients on port " + socketServer.getLocalPort() + "..");
            running = true;
        } catch (Exception e) {
            System.out.println("Error starting the server: " + e.getMessage());
        }
    }

    /**
     * Accepts incoming client connections and starts a new thread to handle each connection.
     */
    public void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = socketServer.accept();
                System.out.println();
                System.out.println("New connection: " + clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                Thread thread = new Thread(clientHandler);
                threadList.add(thread);
                thread.start();
            } catch (Exception e) {
                //System.out.println("Error with the new connection: " + e.getMessage());
            }
        }
    }

    /**
     * Closes the server and performs cleanup tasks.
     */
    private void closeServer() {
        System.out.println("\nClosing Server..");
        running = false;
        try {
            // Save cache to file
            FileOutputStream fileOutputStream = new FileOutputStream("cache.txt");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(cache);
            objectOutputStream.close();
            fileOutputStream.close();
            System.out.println("Cache saved");

            // Save blocked sites to file
            FileOutputStream fileOutputStream2 = new FileOutputStream("block.txt");
            ObjectOutputStream objectOutputStream2 = new ObjectOutputStream(fileOutputStream2);
            objectOutputStream2.writeObject(blockedSites);
            objectOutputStream2.close();
            fileOutputStream2.close();
            System.out.println("Blocked Sites saved");

            // Wait for all client threads to close
            try {
                for (Thread thread : threadList) {
                    if (thread.isAlive()) {
                        System.out.print("Waiting on " + thread.getId() + " to close..");
                        thread.join();
                        System.out.println(" closed");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.out.println("Error saving cache/blocked sites");
            e.printStackTrace();
        }
        try {
            // Close server socket
            System.out.println("Terminating Connection");
            socketServer.close();
        } catch (Exception e) {
            System.out.println("Exception closing proxy's server socket");
            e.printStackTrace();
        }

    }

    /**
     * Retrieves a cached web page for a given URL.
     * @param url The URL of the web page.
     * @return The cached file if it exists, null otherwise.
     */
    public static File getCachedPage(String url) {
        return cache.get(url);
    }

    /**
     * Adds a web page to the cache.
     * @param urlString The URL of the web page.
     * @param fileToCache The file to be cached.
     */
    public static void addCachedPage(String urlString, File fileToCache) {
        cache.put(urlString, fileToCache);
    }

    /**
     * Checks if a URL is blocked.
     * @param url The URL to be checked.
     * @return True if the URL is blocked, false otherwise.
     */
    public static boolean isBlocked(String url) {
        return blockedSites.get(url) != null;
    }
}
