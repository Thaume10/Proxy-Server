This project is a basic web proxy server that allows clients to access web content through it, with caching and site-blocking capabilities. It uses a simple command-line interface for server management and handles both HTTP and HTTPS requests.

# Features
  Caching: Stores frequently accessed pages locally to improve performance.
  Site Blocking: Blocks specific URLs based on user input.
  Multithreading: Manages multiple client connections concurrently using threads.
  Command-Line Interface: Allows users to interact with the server to block sites, view blocked sites, view cached sites, and close the server.

# How It Works
  Server Initialization: The ProxyServer class initializes a server socket to listen for incoming client connections.
  Client Handling: Each client request is processed by a ClientHandler class that manages HTTP and HTTPS traffic, including caching and blocking mechanisms.
  Caching Mechanism: Pages are cached locally and served directly to clients if available, reducing load and improving speed.
  Site Blocking: If a site is blocked, a "403 Forbidden" response is returned to the client.

# Running the Server
  To start the server, run the ProxyServer class from the command line with the desired port. The server will handle client connections automatically.

# Recommendations for Future Enhancements
  Implement HTTPS caching.
  Add advanced cache expiration policies.
  Enhance security with content filtering and malware detection.
