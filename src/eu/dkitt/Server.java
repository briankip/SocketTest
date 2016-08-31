package eu.dkitt;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Properties;

public class Server {
	
	Properties properties;
	
	public Server(Properties properties) {
		this.properties = properties;
	}
	
	/**
	 * The method is starting a server in the main thread.
	 */
	public void execute() {
		int worker_counter=1;
		ServerSocket serverSocket = null;
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		System.out.println("Starting server on port = "+portno);
		try {
			serverSocket = new ServerSocket(portno);
		} catch (IOException e1) {
			// We did not get a server socket - no way to continue - just exit
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
		while(true) {
			Socket newSocket;
			try {
				newSocket = serverSocket.accept();
			} catch (SocketTimeoutException exTout) {
				// TODO: Set timeout to the server socket and check user input here - request to terminate
				continue;
			}
			catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
			System.out.println("New client connected");
			if(clientSocket!=null) {
				// close previous socket - any worker associated with it will take care and die eventually...
				try {clientSocket.close();} catch (IOException e) {}
			}
			clientSocket = newSocket;
			// Start a new worker associated with the socket.
			final Executor executor = new Executor(clientSocket,properties,false);
			Thread worker = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							executor.execute();
						} catch (IOException e) {
						}
					}
				}, "Worker_"+(worker_counter++));
			worker.start();
			
		}
		// System.out.println("Terminated");
	}
	
}
