package eu.dkitt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.InvalidPathException;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
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
	
	private	boolean bSimulator;
	private Properties properties;
	private Socket socket;
	
	private	InputStream inStream;
	private	OutputStream outStream;
	
	private FileProcessor fileProcessor;
	
	public Socket getSocket() {
		return socket;
	}
	
	public Executor(Socket socket, Properties properties, boolean bSimulator) {
		this.properties = properties;
		this.socket = socket;
		this.bSimulator = bSimulator; 
		timerBusy = new MyTimer(10000);
		timerContention = new MyTimer(bSimulator?1000:20000);
		fileProcessor = new FileProcessor(properties);
	}
	
	public void execute() throws IOException {
		
		int c;
		
		try {
		inStream = socket.getInputStream();
		outStream = socket.getOutputStream();
		} catch(IOException ex) {
			logger.warning("Cannot get in/out stream of a socket: " + ex.getMessage());
			throw ex;
		}
		
		main_loop:
		while (true) {
			/**
			 * We are idle
			 */
			if(logger.isLoggable(Level.INFO)){
				Handler[] hs = logger.getHandlers();
				for(Handler h : hs){
					h.flush();
				}
			}
			
			if(timerContention.tout() && timerBusy.tout() && fileProcessor.hasFileToSend()){
				logger.info("There is a file to transfer - starting establishment phase");
				logger.finest("writing <ENQ>");
				outStream.write(ENQ);
				socket.setSoTimeout(15000);
			  	// keep reading while not getting either a valid byte or a timeout
				while (true) {
					try {
						c = inStream.read();
					} catch (SocketTimeoutException ex) {
						logger.fine("No valid byte received - starting busy timer for 30 seconds");
						outStream.write(EOT);
						logger.finest("writing <EOT>");
						timerBusy.start(30000);
						continue main_loop; // stay in idle state
					}
					if (c == ACK) {
						logger.fine("<ACK> received - starting transfer phase (sender)");
						if( !transmit() ) {
							timerBusy.start(30000);
							logger.fine("Start busy timer");
						}
						logger.fine("Going idle");
						outStream.write(EOT);
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
				logger.fine("Writing a file");
				fileProcessor.commitFile();
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
				if(c == -1) {
					// socket was closed - there is no remedy - this thread must die !
					logger.fine("Socket was close - this thread will give up ...");
					return;
				}
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
			
			outStream.write(ACK);
			logger.fine("Frame confirmed by <ACK>");
		}
			
	}
	
	/**
	 * Execute the transmission phase as a sender.
	 * The method does not care about the phase,
	 * it is the responsibility of a caller to terminate the transfer phase
	 * by <EOT> byte.<br/>
	 * <EOT> must be send regardless of whether transmission succeeded or failed.
	 * @return true in success, else it returns false and caller should insert a busy timer
	 * @throws IOException
	 */
	private boolean transmit() throws IOException {
		if(!fileProcessor.hasFileToSend()){
			logger.fine("There is no file to send - unexpected");
			return false;
		}
		int iStart, nLen, frame_index, nBytes;
		boolean bTerminalFrame;
		byte [] bytes;
		try {
			nBytes = fileProcessor.readFileData();
		} catch (InvalidPathException e) {
			logger.warning("Invalid path - " + e);
			e.printStackTrace();
			return false;
		} catch (InvalidFileContents e) {
			logger.warning("Invalid file contents - " + e);
			e.printStackTrace();
			return false;
		}
		logger.fine(" File to send: " + fileProcessor.getFileName());
		bytes = fileProcessor.getData();
		/** 
		 * initialize starting condition
		 */
		iStart = 0;
		nLen = 0;
		frame_index = 0;
	main_loop:
		while(true) {
			frame_index++;
			iStart += nLen;
			while(true) {
				if(iStart >= nBytes) {
					break main_loop;
				}
				if(bytes[iStart]!=CR && bytes[iStart]!=LF)
					break;
				iStart++;
			}
			nLen = 1;
			int i;
			while(nLen < 240 && (i=iStart + nLen) < nBytes && bytes[i]!=CR && bytes[i]!=LF )
				nLen++;
			if(!(bTerminalFrame = (nLen<240))){
				nLen = 239;
			}
			if(!sendOneFrame(bytes,iStart,nLen,frame_index,bTerminalFrame)) {
				return false;
			}
		}
		logger.fine("File was sent, move it to a backup directory");
		fileProcessor.backupSentFile();
		logger.info("File sent " + fileProcessor.getFileName());
		return true;
	}
	
	/**
	 * Send one frame of data.
	 * The array of bytes must not contain control bytes.
	 * Number of bytes to write must be less than 240.
	 * It is assumed that the array of bytes to write does not contain terminal <CR>
	 * as it will be appended by the method.
	 * @param data	array of bytes to write
	 * @param offset	index of the starting byte in the array
	 * @param len	number of bytes to write - must be in the range <1,239>
	 * @param frame_index	index of the frame to send in the range <1,...). Method will calculate % 8.
	 * @param bLastFrame	frame will be terminated either by <ETX> if true, else <ETB> will be used.
	 * @return	true if frame was confirmed by the 
	 * @throws IOException
	 */
	private boolean sendOneFrame(byte [] data, int offset, int len, int frame_index, boolean bLastFrame) throws IOException {
		
		int frame_summ;
		int nRetries = 6;
		frame_index %= 8; 
		while((nRetries--)>0) {
			outStream.write(STX);
			outStream.write(48 + frame_index);
			outStream.write(data, offset, len);
			outStream.write(CR);
			outStream.write(bLastFrame ? ETX : ETB);
			frame_summ = 48 + frame_index + (bLastFrame ? ETX : ETB) + CR;
			for(int i=0; i<len; ++i){
				frame_summ += 0xFF & (data[offset+i]);
			}
			int cs1 = (frame_summ & 0xF0)>>4;
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
			logger.fine("Frame " + frame_index + ": " +len + " bytes sent");
			/**
			 * Waiting for confirmation or until a timeout
			 */
			socket.setSoTimeout(15000);
			int c;
			while (true) {
				try {
					c = inStream.read();
				} catch (SocketTimeoutException ex) {
					logger.warning("No reaction received - timeout");
					return false;
				}
				if (c == ACK || c == EOT) {
					logger.fine( "Frame accepted by " + (c == ACK ? "<ACK>":"<EOT>"));
					return true;
				}
				if (c == NAK) {
					logger.info("Frame rejected by <NAK>");
					break;
				}
				if (c == -1) {
					/**
					 * socket was closed - there is no remedy - this thread must die
					 */
					logger.warning("Socket was closed - this thread will give up ...");
					return false;
				}
			}
		}
		logger.warning("Too many rejections - stop trying");
		return false;
	}

}
