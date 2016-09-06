package eu.dkitt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.InvalidPathException;
import java.util.Properties;
import java.util.logging.Logger;

import eu.dkitt.FileProcessor.InvalidFileContents;

public class Executor {
	
	private static final Logger logger = Logger.getLogger(Executor.class.getName());
	
	static class MyTimer {
		long timeout;
		long timeend;
		MyTimer(long timeout) {
			this.timeout = timeout;
			timeend = System.currentTimeMillis();
		}
		/**
		 * Start the default timeout measurement.
		 */
		void	start() {
			timeend = System.currentTimeMillis() + timeout;
		}
		/**
		 * Start explicit timeout measurement.
		 * @param millis	milliseconds to wait before a timeout
		 */
		void	start(long millis) {
			timeend = System.currentTimeMillis() + millis;
		}
		/**
		 * Check whether timeout was reached.
		 * @return true if timeout elapsed
		 */
		boolean	tout() {
			return System.currentTimeMillis() >= timeend; 
		}
	}

	static final int ENQ = 5;
	static final int ACK = 6;
	static final int NAK = 21;
	static final int CR = 13;
	static final int LF = 10;
	static final int STX = 2;
	static final int ETX = 3;
	static final int EOT = 4;
	static final int ETB = 23;

	private MyTimer	timerBusy;
	private MyTimer timerContention;
	private MyTimer	timerFrame;
	
	private	boolean bSimulator;
	private Properties properties;
	private Socket socket;
	
	private	InputStream inStream;
	private	OutputStream outStream;
	
	private FileProcessor fileProcessor;
	
	public Executor(Socket socket, Properties properties, boolean bSimulator) {
		this.properties = properties;
		this.socket = socket;
		this.bSimulator = bSimulator; 
		timerBusy = new MyTimer(10000);
		timerContention = new MyTimer(bSimulator?1000:20000);
		timerFrame = new MyTimer(30000);
		fileProcessor = new FileProcessor(properties);
	}
	
	public void execute() throws IOException {
		
		int c;
		
		inStream = socket.getInputStream();
		outStream = socket.getOutputStream();
		
		main_loop:
		while (true) {
			/**
			 * We are idle
			 */
			if(timerContention.tout() && timerBusy.tout() && fileProcessor.hasFileToSend()){
				logger.info("There is a file to transfer - starting establishment phase");
				outStream.write(ENQ);
				socket.setSoTimeout(15000);
			  	// keep reading while not getting either a valid byte or a timeout
				while (true) {
					try {
						c = inStream.read();
					} catch (SocketTimeoutException ex) {
						logger.fine("No valid byte received - starting busy timer for 30 seconds");
						outStream.write(EOT);
						timerBusy.start(30000);
						continue main_loop; // stay in idle state
					}
					if (c == ACK) {
						logger.fine("<ACK> received - starting transfer phase (sender)");
						transmit();
						logger.fine("Going idle");
						continue main_loop; // stay in idle state
					}
					if (c == NAK) {
						logger.fine("<NAK> received - starting busy timer for 10 seconds");
						timerBusy.start(10000);
						continue main_loop; // stay in idle state
					}
					if (c == ENQ) {
						logger.fine("<ENQ> received - starting contention timer for " + (bSimulator?"1 second":"20 seconds"));
						timerContention.start();
						continue main_loop;	// stay in idle state
					}
				}
			}
			/**
			 * We have nothing to send or waiting because of busy or contention
			 */
			socket.setSoTimeout(1000);
			try {
				c = inStream.read();
			} catch (SocketTimeoutException ex) {
				continue main_loop; // stay in idle state
			}
			if(c != ENQ) {
				if(c == -1) {
					// socket was closed - there is no remedy - this thread must die !
					logger.fine("Socket was close - this thread will give up ...");
					return;
				}
				logger.fine("Waiting for <ENQ> - Unexpected byte received : " + c);
				continue main_loop;	// ignore - stay in idle state
			}
			logger.fine("<ENQ> received");
			if(bSimulator && fileProcessor.hasFileToSend()){
				logger.fine("Simulator has file to send - request will be rejected by <NAK>");
				outStream.write(NAK);
				continue main_loop; // stay in idle state
			}
			logger.fine("Request confirmed by <ACK> - starting transfer phase (receiver)");
			outStream.write(ACK);	// confirm readiness to receive
			receive();
			logger.fine("Going idle");
		}
		
		
		
	}

	private void receive() throws IOException {
		
		
		int i, c;
		byte [] bytes = new byte[100000];
		
		socket.setSoTimeout(15000);
		
		fileProcessor.prepareForNextFile();
		
		while(true) {
			c =inStream.read();
			if(c == EOT) {
				logger.fine("<EOT> received - transfer terminated");
				return;	// end of transmission
			}
			if(c != STX) {
				if(c == -1) {
				// socket was closed - there is no remedy - this thread must die !
					logger.fine("Socket was close - this thread will give up ...");
					return;
				}
				logger.fine("Waiting for <STX> - Unexpected byte received : " + c);
				continue;
			}
			logger.fine("<STX> received");
			c = inStream.read();
			logger.fine("Frame index received : " + c);
			i=0;
			boolean bTerminal;
			while(true) {
				c = inStream.read();
				if( c != ETB && c != ETX ) {
					bytes[i++] = (byte)c;
					logger.finest("byte received : " + c);
					continue;
				}
				bTerminal = c == ETX;
				logger.fine("End of frame received (" + (bTerminal?"terminal":"continuation") + "): " + c);
				break;
			}
			c = inStream.read();
			logger.fine("checksumm byte 1 received : " + c);
			c = inStream.read();
			logger.fine("checksumm byte 2 received : " + c);
			c = inStream.read();
			logger.fine("byte received (cr): " + c);
			c = inStream.read();
			logger.fine("byte received (lf): " + c);
			
			// TODO: Check correct checksumm and react accordingly
			
			logger.fine("Frame contents length : " + i);
			
			fileProcessor.addOneFrame(bytes, i);
			
			if(bTerminal) {
				logger.fine("Writing a file");
				fileProcessor.commitFile();
				fileProcessor.prepareForNextFile();
			}
				
			outStream.write(ACK);
			logger.fine("Frame confirmed by <ACK>");
		}
			
	}
	
	private boolean transmit() throws IOException {
		
		if(!fileProcessor.hasFileToSend()){
			logger.fine("There is no file to send - unexpected, sending <EOT>");
			outStream.write(EOT);
			return true;
		}
		int N_remain, N_2_send, N_sent, frame_index, frame_summ;
		byte [] bytes;
		try {
			N_remain = fileProcessor.readFileData();
		} catch (InvalidPathException e) {
			logger.warning("Invalid path - " + e);
			e.printStackTrace();
			return false;
		} catch (InvalidFileContents e) {
			logger.warning("Invalid file contents - " + e);
			e.printStackTrace();
			return false;
		}
		logger.fine(N_remain + " bytes read from " + fileProcessor.getFileName());
		bytes = fileProcessor.getData();
		N_sent = 0;
		frame_index = 1;	// first frame index will be 1
		boolean bLastFrame;
	main_loop:
		while(N_remain>0) {
				
			N_2_send = N_remain;
			if(N_2_send > 240) {
				N_2_send = 240;
			}
			bLastFrame = N_remain <= 240;
			
			outStream.write(STX);
			outStream.write(48 + frame_index);
			outStream.write(bytes, N_sent, N_2_send);
			outStream.write(bLastFrame ? ETX : ETB);
			
			frame_summ = 48 + frame_index + (bLastFrame ? ETX : ETB);
			for(int i=0; i<N_2_send; ++i){
				frame_summ += 0xFF & (bytes[N_sent+i]);
			}
			
			int cs1 = (frame_summ & 0xF0)>>8;
			int cs2 = frame_summ & 0x0F;
			
			if(cs1 < 10)
				cs1 = 48 + cs1;
			else
				cs1 = 65 + cs1-10;
			
			if(cs2 < 10)
				cs2 = 48 + cs2;
			else
				cs2 = 65 + cs2-10;
			
			outStream.write(cs1);
			outStream.write(cs2);
			outStream.write(CR);
			outStream.write(LF);
			
			logger.fine(N_2_send + " bytes sent");
			
			socket.setSoTimeout(15000);
			
			// keep reading while not getting either a valid byte or a timeout
			int c;
			while (true) {
				try {
					c = inStream.read();
				} catch (SocketTimeoutException ex) {
					logger.warning("No reaction from the receiver - sending <EOT>");
					outStream.write(EOT);
					return false; // abort procedure
				}
				if (c == ACK || c == EOT) {
					logger.fine("<ACK> or <EOT> received : " + c);
					N_remain -= N_2_send;
					N_sent += N_2_send;
					frame_index = (frame_index+1)%8;
					continue main_loop;	// continue sending frames
				}
				if (c == NAK) {
					logger.info("<NAK> received - resending the last frame");
					continue main_loop; // repeat sending last frame
				}
				if(c==-1) {
					// socket was closed - there is no remedy - this thread must die !
					logger.warning("Socket was close - this thread will give up ...");
					return false;
				}
			}
		}
		
		logger.fine("File was sent, move it to a backup directory");
		fileProcessor.backupSentFile();
		logger.fine("Terminate transfer phase - sending <EOT> ");
		outStream.write(EOT);
		
		logger.info("File sent " + fileProcessor.getFileName());
		
		return true;
	}

}
