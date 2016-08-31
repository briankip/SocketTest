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
	
	public void execute() throws InterruptedException {
		
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		System.out.println("Starting simulator using port = "+portno);
		
		try(Socket socket = new Socket("localhost", portno);) {
			
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
			
			clientthread.start();
			
			System.out.println("Press [Enter] to terminate the server.");
			
			boolean bRun = true;
			
			while(bRun){
				Thread.sleep(500);
				if(!clientthread.isAlive()){
					bRun = false;
					System.out.println("Simulator exited.");
					continue;
				}
				while(System.in.available()>0){
					int c = System.in.read();
					if(c==10){
						System.out.println("Simulator will stop.");
						bRun = false;
					}
				}
				continue;
			};

			try {
				clientthread.join();
			} catch (InterruptedException e) {
			}
			
			System.out.println("Terminated.");
			
		} catch (UnknownHostException e) {
			System.out.println("Unknown host loalhost");
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}
			
				
	}
	
}
