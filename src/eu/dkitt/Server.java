package eu.dkitt;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Properties;
import java.util.logging.Logger;

public class Server {
	
	private static final Logger logger = Logger.getLogger(Server.class.getName());
	
	Properties properties;
	
	public Server(Properties properties) {
		this.properties = properties;
	}
	
	/**
	 * The method is starting a server in the main thread.
	 * @throws IOException 
	 */
	public void execute() throws IOException {
		int worker_counter=1;
		ServerSocket serverSocket = null;
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		logger.info("Starting server on port = "+portno);
		try {
			serverSocket = new ServerSocket(portno);
			serverSocket.setSoTimeout(500);
			logger.fine("Server socket retrieved");
		} catch (IOException e1) {
			// We did not get a server socket - no way to continue - just exit
			logger.warning("Could not retrieve server socket");
			e1.printStackTrace();
			return;
		}
		/**
		 * Wait in a loop for new connections.
		 * We assume just a single client connected at a time.
		 * Because of that when a new connection was accepted
		 * we terminate any previous one.
		 */
		
		Socket clientSocket = null;
		
		System.out.println("Press [Enter] to terminate the server.");
		
		while(true) {
			Socket newSocket;
			try {
				newSocket = serverSocket.accept();
			} catch (SocketTimeoutException exTout) {
				while (System.in.available() > 0) {
					int c = System.in.read();
					if (c == 10) {
						serverSocket.close();
						System.out.println("Server stopped");
						if (clientSocket != null) {
							clientSocket.close();
						}
						return;
					}
				}
				continue;
			} catch (IOException e1) {
				logger.warning("Socket accept failed " + e1);
				e1.printStackTrace();
				return;
			}
			logger.info("New client connected");
			if(clientSocket!=null) {
				// close previous socket - any worker associated with it will take care and die eventually...
				try {clientSocket.close();} catch (IOException e) {}
			}
			clientSocket = newSocket;
			// Start a new server worker thread associated with the socket.
			final Executor executor = new Executor(clientSocket,properties,false);
			Thread worker = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							executor.execute();
							logger.info("Client connection exited.");
						} catch (IOException e) {
							logger.info("Client connection exception: " + e.getMessage());
						}
					}
				}, "Worker_"+(worker_counter++));
			worker.start();
			
		}
	}
	
}
