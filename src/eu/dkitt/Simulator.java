package eu.dkitt;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

public class Simulator {
	
	Properties properties;

	public	Simulator(Properties properties) {
		this.properties = properties;
	}
	
	public void execute() {
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		System.out.println("Starting simulator using port = "+portno);
		try(
			Socket socket = new Socket("localhost", portno);
				) {
			
			final Executor executor = new Executor(socket,properties,true);
			
			Thread clientthread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						executor.execute();
					} catch (IOException e) {
					}
				}
			},"Client"); 

			System.out.println("Client started - press ^C to close it...");
			System.in.read();
			
			
		} catch (UnknownHostException e) {
			System.out.println("Unknown host loalhost");
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}
			
				
	}
	
}
