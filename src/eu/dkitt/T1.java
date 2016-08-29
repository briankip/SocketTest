/*
 * Here we added a comment.
 */
package eu.dkitt;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

public class T1 {
	
	static class UsageException extends Exception {
		
		private static final long serialVersionUID = 1L;

		UsageException(String message){
			super(message);		
		}
		
		void	printOutput() {
			System.out.println(getMessage());
			System.out.println("Usage:\n" + "app -props <file> [-port <number>] [-simul]\n\n"
					+ "Contents of the property file:\n"
					+ "  port=<number> ... port number in the range 1..65535"
					+ "  simul\n");
			
		}
	}

	public static Properties properties = null;
	
	public static void main(String[] args) {
		
		int N = args.length;
		Properties properties_arguments = new Properties();
		int i=-1;
		try {
			while ((++i) < N) {
				switch (args[i]) {
				case "-props":
					if (i > N-1)
						throw new UsageException("Missing properties file");
					properties = new Properties();
					try (Reader reader = new FileReader(args[i + 1])) {
						properties.load(reader);
					} catch (FileNotFoundException e) {
						throw new UsageException("Could not find file: " + args[i + 1]);
					} catch (IOException e) {
						throw new UsageException("Error " + e + "\n" + " while reading property file: " + args[i + 1]);
					}
					i++;
					break;
				case "-port":
					if (i > N-1)
						throw new UsageException("Port number missing after -port parameter");
					properties_arguments.put("port", args[i+1]);
					i++;
					break;
				case "-simul":
					properties_arguments.put("simul", "");
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
		if(!properties.containsKey("port")){
			new UsageException("Port number was not specified").printOutput();
			return;
		}
		// Check that the port is a valid number
		int blaportno;
		try {
			blaportno = Integer.parseInt(properties.getProperty("port"));
		} catch (NumberFormatException e) {
			blaportno = -1;
		}
		if( blaportno < 1 || blaportno >= 65536) {
			new UsageException("Invalid 'port' property: " + properties.getProperty("port")).printOutput();
			return;
		}
		
		System.out.println("Options: ");
		for( Object o : properties.keySet()){
			System.out.println(((String)o) + "=" + properties.put(o, properties.get(o)));
		}
		
	}

}
