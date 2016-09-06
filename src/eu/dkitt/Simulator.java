package eu.dkitt;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.logging.Logger;

public class Simulator {
	
	private static final Logger logger = Logger.getLogger(Simulator.class.getName());
	
	Properties properties;

	public	Simulator(Properties properties) {
		this.properties = properties;
	}
	
	public void execute() throws InterruptedException {
		
		String host = properties.getProperty(T1.OPTION_HOST, "localhost");
		int portno = Integer.parseInt(properties.getProperty(T1.OPTION_PORT));
		
		logger.info("Starting simulator using server = "+host+":"+portno);
		
		try(Socket socket = new Socket(host, portno);) {
			
			final Executor executor = new Executor(socket,properties,true);
			
			Thread clientthread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						executor.execute();
						logger.info("Simulator exited.");
					} catch (IOException e) {
						logger.info("Simulator exit exception: " + e.getMessage());
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
						System.out.println("Simulator will terminate.");
						bRun = false;
						socket.close();
						break;
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
