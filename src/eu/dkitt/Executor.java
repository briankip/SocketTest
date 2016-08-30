package eu.dkitt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Properties;

public class Executor {

	static final int ENQ = 5;
	static final int ACK = 6;
	static final int NAK = 21;
	static final int CR = 13;
	static final int LF = 10;
	static final int STX = 2;
	static final int ETX = 3;
	static final int EOT = 4;
	
	boolean bSimulator;
	Properties properties;
	Socket socket;
	
	InputStream inStream;
	OutputStream outStream;
	
	public Executor(Socket socket, Properties properties, boolean bSimulator) {
		this.properties = properties;
		this.socket = socket;
		this.bSimulator = bSimulator; 
	}
	
	public void execute() throws IOException {
		
		inStream = socket.getInputStream();
		outStream = socket.getOutputStream();
		
		socket.getSoTimeout();
		socket.setSoTimeout(1000);
		
		while(true) {
			// TODO: do not check availability here - use timeout instead !!!
			if(inStream.available()>0){
				int c = inStream.read();
				System.out.println("c = " + c);
				if(c == ENQ) {
					outStream.write(ACK);
					receive();
				}
				continue;
			}
			// TODO: check for a file to send
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		
		
		
	}

	private void receive() {
		// TODO Auto-generated method stub
		
	}

}