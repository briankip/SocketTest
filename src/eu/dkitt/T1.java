/*
 * Here we added a comment.
 */
package eu.dkitt;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.InvalidPathException;
import java.util.Properties;

import eu.dkitt.FileProcessor.InvalidFileContents;


public class T1 {
	
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
			System.out.println("Usage:\n" + "app -props <file> [-port <number>] [-indir <directory>] [-inmask <mask>] [-simul]\n\n"
					+ "Contents of the property file:\n"
					+ "  port=<number> ... port number in the range 1..65535\n"
					+ "  indir=<directory> ... directory where to search files to send\n"
					+ "  inmask=<mask> ... mask to search for files\n"
					+ "  inbackup=<directory> ... directore where to move files sent\n"
					+ "  outdir=<directory> ... directory where to write received files\n"
					+ "  outmask=<format> ... specification of file names received\n"
					+ "  simul\n");
			
		}
	}

	public static Properties properties = null;
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		int N = args.length;
		Properties properties_arguments = new Properties();
		properties = new Properties();
		// prepare some defaults
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
		// Summarize all options
		System.out.println("Options: ");
		for( Object o : properties.keySet()){
			System.out.println(((String)o) + "=" + properties.put(o, properties.get(o)));
		}
		
		{
			
			FileProcessor fp = new FileProcessor(properties);
			
			byte [] bytes = {'a','h','o','j'};
			
			fp.prepareForNextFile();
			fp.addOneFrame(bytes,bytes.length);
			fp.commitFile();
			fp.prepareForNextFile();
			fp.addOneFrame(bytes,bytes.length);
			fp.addOneFrame(bytes,bytes.length);
			fp.commitFile();
			
			System.out.println("File found: " + fp.hasFileToSend() + ": " + fp.getFile());
					
			if(fp.hasFileToSend()){
					int Nread = 0;
					try {
						Nread = fp.readFileData();
					} catch (InvalidPathException e) {
						e.printStackTrace();
					} catch (InvalidFileContents e) {
						e.printStackTrace();
					}
					System.out.println("Bytes read: " + Nread);
					bytes = fp.getData();
					
					fp.backupSentFile();
				
					
			} else
				System.out.println("No file found");
			
			if(true)
				return;
		}
		
		if(bSimul)
			new Simulator(properties).execute();
		else
			new Server(properties).execute();
		
	}

}
