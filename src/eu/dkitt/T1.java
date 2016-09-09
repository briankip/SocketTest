/*
 * Here we added a comment.
 */
package eu.dkitt;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import eu.dkitt.FileProcessor.InvalidFileContents;


public class T1 {
	
	public static	final	String OPTION_LOGFILE = "logfile";
	public static	final	String OPTION_LOGLEVEL = "loglevel";
	public static	final	String OPTION_HOST = "host";
	public static	final	String OPTION_PORT = "port"; 
	public static	final	String OPTION_SIMUL = "simul"; 
	public static	final	String OPTION_DIRECTORY_2_SEND = "indir";
	public static	final	String OPTION_FILES_2_SEND_MASK = "inmask";
	public static	final	String OPTION_DIRBACKUP_2_SEND = "inbackup";
	public static	final	String OPTION_DIRECTORY_RCVD = "outdir";
	public static	final	String OPTION_FILES_RCVD_NAME = "outname";
	
	static class UsageException extends Exception {
		
		private static final long serialVersionUID = 1L;

		UsageException(String message){
			super(message);		
		}
		
		void	printOutput() {
			System.out.println(getMessage());
			System.out.println("Usage:\n" + "app -props <file> [-host <hostaddr>] [-port <number>] [-indir <directory>] [-inmask <mask>] [-simul]\n\n"
					+ "Contents of the property file:\n"
					+ "  logfile=<logfilepath> ... logfile\n"
					+ "  loglevel=<level> ... severe|warning|info|config|fine|finer|finest\n"
					+ "  host=<hostaddr> ... address of host - DNS or IP address\n"
					+ "  port=<number> ... port number in the range 1..65535\n"
					+ "  indir=<directory> ... directory where to search files to send\n"
					+ "  inmask=<mask> ... mask to search for files\n"
					+ "  inbackup=<directory> ... directore where to move files sent\n"
					+ "  outdir=<directory> ... directory where to write received files\n"
					+ "  outname=<format> ... specification of file names received\n"
					+ "  simul ... if present the application is simulating an instrument\n");
			
		}
	}

	public static Properties properties = null;
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		int N = args.length;
		Properties properties_arguments = new Properties();
		properties = new Properties();
		// prepare some defaults
		properties.setProperty(OPTION_LOGFILE, "app.log");
		properties.setProperty(OPTION_LOGLEVEL, "fine");
		
		properties.setProperty(OPTION_HOST, "localhost");
		properties.setProperty(OPTION_DIRECTORY_2_SEND, ".");
		properties.setProperty(OPTION_FILES_2_SEND_MASK, "*_2_send.txt");
		properties.setProperty(OPTION_DIRBACKUP_2_SEND, "sent");
		properties.setProperty(OPTION_FILES_RCVD_NAME, "msg_received_%05d.txt");
		properties.setProperty(OPTION_DIRECTORY_RCVD, ".");
		
		int i=-1;
		try {
			while ((++i) < N) {
				switch (args[i]) {
				case "-props":
					if (i > N-1)
						throw new UsageException("Missing properties file");
					try (Reader reader = new FileReader(args[i + 1])) {
						properties.load(reader);
					} catch (FileNotFoundException e) {
						throw new UsageException("Could not find file: " + args[i + 1]);
					} catch (IOException e) {
						throw new UsageException("Error " + e + "\n" + " while reading property file: " + args[i + 1]);
					}
					i++;
					break;
					
				case "-"+OPTION_LOGFILE:
					if (i > N-1)
						throw new UsageException("Missing logfile specification");
					properties_arguments.put(OPTION_LOGFILE, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_LOGLEVEL:
					if (i > N-1)
						throw new UsageException("Missing loglevel specification");
					properties_arguments.put(OPTION_LOGLEVEL, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_DIRECTORY_2_SEND:
					if (i > N-1)
						throw new UsageException("Missing input directory");
					properties_arguments.put(OPTION_DIRECTORY_2_SEND, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_FILES_2_SEND_MASK:
					if (i > N-1)
						throw new UsageException("Missing input mask");
					properties_arguments.put(OPTION_FILES_2_SEND_MASK, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_DIRBACKUP_2_SEND:
					if (i > N-1)
						throw new UsageException("Missing input backup directory");
					properties_arguments.put(OPTION_DIRBACKUP_2_SEND, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_DIRECTORY_RCVD:
					if (i > N-1)
						throw new UsageException("Missing output directory");
					properties_arguments.put(OPTION_DIRECTORY_RCVD, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_FILES_RCVD_NAME:
					if (i > N-1)
						throw new UsageException("Missing output filename format");
					properties_arguments.put(OPTION_FILES_RCVD_NAME, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_HOST:
					if (i > N-1)
						throw new UsageException("Missing host");
					properties_arguments.put(OPTION_HOST, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_PORT:
					if (i > N-1)
						throw new UsageException("Port number missing after -port parameter");
					properties_arguments.put(OPTION_PORT, args[i+1]);
					i++;
					break;
					
				case "-"+OPTION_SIMUL:
					properties_arguments.put(OPTION_SIMUL, "");
					break;
				default:
					throw new UsageException("Unknown parameter: " + args[i]);
				}
			}
		} catch (UsageException ex) {
			ex.printOutput();
			return;
		}
		// There may have been no property file:
		if(properties == null){
			properties = new Properties();
		}
		
		
		for( Object o : properties_arguments.keySet()){
			properties.put(o, properties_arguments.get(o));
		}
		
		// Prepare for logging
		String loglevel = properties.getProperty(OPTION_LOGLEVEL,"info").toUpperCase();
		String logfile = properties.getProperty(OPTION_LOGFILE,"app.log");
		Level level = Level.parse(loglevel);
		
		Logger logParent = Logger.getLogger("eu.dkitt");
		logParent.setUseParentHandlers(false);
		
		Handler hConsoleHandler = new ConsoleHandler();
		hConsoleHandler.setFormatter(new MyFormatter());
		hConsoleHandler.setLevel(level);
		logParent.addHandler(hConsoleHandler);
		
		Handler hLogFileHandler = new FileHandler(logfile, 1000000, 9, true);
		hLogFileHandler.setFormatter(new MyFormatter());
		hLogFileHandler.setLevel(level);
		logParent.addHandler(hLogFileHandler);
		
		logParent.setLevel(level);
		
		
		// Check that port was specified
		if(!properties.containsKey(OPTION_PORT)){
			new UsageException("Port number was not specified").printOutput();
			return;
		}
		// Check that the port is a valid number
		int portno;
		boolean bSimul = properties.containsKey(OPTION_SIMUL); 
				
		try {
			portno = Integer.parseInt(properties.getProperty(OPTION_PORT));
		} catch (NumberFormatException e) {
			portno = -1;
		}
		if( portno < 1 || portno >= 65536) {
			new UsageException("Invalid property "+OPTION_PORT+": " + properties.getProperty(OPTION_PORT)).printOutput();
			return;
		}
		
		{
			/**
			 * We temporarily assign a specialized formatter so that we printout the configuration
			 * in a concise format both to console and to the file handler.
			 * We print effective options in alphabetical order.
			 * We use 'severe' log level, so that the configuration shoule be printed in most cases.
			 * In order to cancel any printout, user must specify the log level OFF.
			 */
			Formatter fmtConsole = hConsoleHandler.getFormatter();
			Formatter fmtLogFile = hLogFileHandler.getFormatter();
			hConsoleHandler.setFormatter(new MyConfigFormatter());
			hLogFileHandler.setFormatter(new MyConfigFormatter());
			Calendar cal = new GregorianCalendar(); 
			cal.setTime(new Date());
			String header = String.format("%04d/%02d/%02d %02d:%02d:%02d.%03d %s started.\n",
					cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH),
					cal.get(Calendar.HOUR),cal.get(Calendar.MINUTE),cal.get(Calendar.SECOND),cal.get(Calendar.MILLISECOND),
					(bSimul ? "Simulator" : "Server"));
			
			logParent.severe("========================================================================\n");
			logParent.severe(header);
			logParent.severe("------------------------------------------------------------------------\n");
			logParent.severe("Options:\n");
			Set set = properties.keySet();
			Object[] array = set.toArray();
			Arrays.sort(array);
			for( Object o : array){
				logParent.severe("" + ((String)o) + "=" + properties.put(o, properties.get(o)) + "\n");
			}
			hConsoleHandler.setFormatter(fmtConsole);
			hLogFileHandler.setFormatter(fmtLogFile);
		}
		
		if(bSimul)
			new Simulator(properties).execute();
		else
			new Server(properties).execute();
			
		
	}
	
	static private class MyFormatter extends Formatter {
		private Calendar cal;
		public MyFormatter() {
			super();
			cal = new GregorianCalendar();
		}
		@Override
		public String format(LogRecord record) {
			cal.setTime(new Date(record.getMillis()));
			String str = String.format("%04d/%02d/%02d %02d:%02d:%02d.%03d %-8s %s:%s -- %s\n",
					cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH),
					cal.get(Calendar.HOUR),cal.get(Calendar.MINUTE),cal.get(Calendar.SECOND),cal.get(Calendar.MILLISECOND),
					record.getLevel().getName(),
					record.getLoggerName(), record.getSourceMethodName(), record.getMessage());
			return str;
		}
	}
	
	static private class MyConfigFormatter extends Formatter {
		public MyConfigFormatter() {
			super();
		}
		@Override
		public String format(LogRecord record) {
			return record.getMessage();
		}
	}

}
