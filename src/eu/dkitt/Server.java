package eu.dkitt;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class Server {
	
	Properties properties;
	
	public Server(Properties properties) {
		this.properties = properties;
	}
	
	public void execute() {
		
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		
		System.out.println("Starting server on port = "+portno);
		try(
			ServerSocket serverSocket = new ServerSocket(portno); 
			Socket clientSocket = serverSocket.accept();) {
			
			final Executor executor = new Executor(clientSocket,properties,false);
			
			Thread serverthread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						executor.execute();
					} catch (IOException e) {
					}
				}
			},"Server"); 

			System.out.println("Server started - press ^C to close it...");
			System.in.read();
			
		} catch (IOException e) {
			System.out.println("Server failed: " + e.getMessage());
			return;
		}
	}
	

}
