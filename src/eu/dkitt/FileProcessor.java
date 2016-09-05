package eu.dkitt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Properties;

import eu.dkitt.FileProcessor.InvalidFileContents;

/**
 * Encapsulate File Handling
 * <ul>
 * <li>Test for existence of an input file to send</li>
 * <li>Retrieving contents of an input file as an array of bytes</li>
 * <li>Moving the input file into a backup directory</li>
 * <li>Generating a received data output file name</li>
 * <li>Writing received data into the output file</li>
 * </ul>
 * 
 * @author dkittrich
 *
 */
public class FileProcessor implements FileVisitor<Path> {
	
	/**
	 * Infinite running counter of output file name.<br/>
	 * Must be incremented and saved externally after each new file generated.
	 */ 
	private int outFileCounter = 1;
	
	private File outFileCounterStore;
	/** 
	 * Buffer for caching frames data to write.<br/>
	 * We assume that no message will be larger than this buffer.<br/>
	 * (There is no reallocation of the buffer)
	 */
	private byte [] outbuf = new byte[100000];
	/**
	 * Index into the outbuf where the next frame will be copied.
	 */
	private int outbuf_index = 0;
	/**
	 * Before writing a frame into the buffer this method must be called
	 * in order to clear the next data index.
	 */
	public void prepareForNextFile() {
		outbuf_index = 0;
	}
	/**
	 * Append one frame to the current contents of the cache buffer.
	 * @param bytes .. array of bytes to write
	 * @param N ... number of bytes to write
	 */
	public void	addOneFrame(byte [] bytes, int N){
		for(int i=0; i<N; ++i) {
			outbuf[outbuf_index + i] = bytes[i];
		}
		outbuf_index += N;
	}
	/**
	 * Generate a new output file name and write current frame cache to it.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public	void	commitFile() throws FileNotFoundException, IOException {
		try(BufferedReader freader = new BufferedReader(new  FileReader(outFileCounterStore));) {
			String str = freader.readLine();
			str = str.trim();
			outFileCounter = Integer.parseInt(str);
		} catch (FileNotFoundException ex){
			outFileCounter = 1;
		}
		String fileName = String.format(fileRcvdFmt, outFileCounter);
		Path path = fileRcvd.resolve(fileName);
		System.out.println("path to write: " + path);
		try(OutputStream os = new FileOutputStream(path.toFile()) ){
			os.write(outbuf, 0, outbuf_index);
			os.close();
			outFileCounter++;
			
			try(FileWriter fw = new FileWriter(outFileCounterStore);) {
				fw.write(""+outFileCounter);
			}
		}
	}
	
	/**
	 * Read data of a file to sent into a cache.
	 * We never read/write at the same time.
	 * We use the same cache both for reading and for writing files.
	 * @return	number of bytes read
	 * @throws IOException  - error while reading - do not try again
	 * @throws FileNotFoundException - should not happen
	 * @throws InvalidFileContents - do not try again
	 */
	public int	readFileData() throws InvalidPathException, FileNotFoundException, IOException, InvalidFileContents {
		if(file==null) {
			throw new FileNotFoundException("Not file name specified.");
		}
		System.out.println("path to read: " + file);
		int n = -1;
		try(InputStream istream = new FileInputStream(file.toFile())){
			n = istream.read(outbuf);
		}
		if(n<=0)
			throw new InvalidFileContents();
		return n;
	}
	/**
	 * Retrieve read data after a successful readFileData().
	 * @return
	 */
	public byte[] getData() {
		return outbuf;
	}

	public void backupSentFile() throws IOException {
		String fileName = file.toFile().getName();
		Files.move(file,fileBackup.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}
	
	@SuppressWarnings("serial")
	static class InvalidFileContents extends Exception {
		InvalidFileContents() {
			super("Invalid file contents.");
		}
	}
	
	/** Properties specifying source / target files and directories. */
	private final Properties properties;
	
	/** Path of a file to send.*/
	private Path file = null;
	
	/** opts parameter to the search algoritgh */
	EnumSet<FileVisitOption> opts;
	
	/** Directory to be searched for files to send */
	private final Path fileStart;
	
	/** Search mask for files to send - should be as restrictive as possible */
	private final PathMatcher matcher;
	
	/** Directory where to move sent files */
	private final Path fileBackup;
	
	/** Directory where to write received files */
	private final Path fileRcvd;
	
	/**
	 * Format of filenames generated by receiver - 
	 * must include %d specification for unique filenames generation
	 */
	private final String fileRcvdFmt;
	
	/**
	 * Callback of a tree walker routine.
	 * The filename is processed by a matcher.
	 * The first file found will be registered and the walker routine
	 * will be terminated.
	 * @param file	path to test
	 * @param attr	attributes of a provided file parameter
	 * @return TERMINATE when the first file that match was detected, CONTINUE otherwise
	 */
	public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
		if (attr.isRegularFile()) {
			Path name = file.getFileName();
			if(matcher.matches(name)){
				this.file = file;
				return FileVisitResult.TERMINATE;
			}
		}
		return FileVisitResult.CONTINUE;
	}
	
	/**
	 * Constructor store a reference to properties.
	 * It also ananlyzes properties and resolves relevant options:
	 * <ul>
	 * <li>Directore where to search for files to send.</li>
	 * <li>Mask of files to send - only files in the directory that match the mask will be sent.</li>
	 * <li>Directory where to move files after being successfully sent.</li>
	 * <li>Directore where to store received message files.</li>
	 * <li>Name generation mask for received files - must contain %d (%05d) where running counter will be presented.</li> 
	 * </ul>
	 * @param properties	properties initiated from a property file and/or command line parameters
	 */
	public	FileProcessor(Properties properties) {
		String directory;
		String mask;
		this.properties = properties;
		opts = EnumSet.noneOf(FileVisitOption.class);
		directory = this.properties.getProperty(T1.OPTION_DIRECTORY_2_SEND);
		fileStart = Paths.get(directory);
		mask = this.properties.getProperty(T1.OPTION_FILES_2_SEND_MASK);
		matcher = FileSystems.getDefault().getPathMatcher("glob:" + mask);
		directory = this.properties.getProperty(T1.OPTION_DIRBACKUP_2_SEND);
		fileBackup = Paths.get(directory);
		directory = this.properties.getProperty(T1.OPTION_DIRECTORY_RCVD);
		fileRcvd = Paths.get(directory);
		outFileCounterStore = fileRcvd.resolve(".counter").toFile();
		fileRcvdFmt = this.properties.getProperty(T1.OPTION_FILES_RCVD_NAME);
	}
	
	/**
	 * Initiates a file search tree walk.
	 * Will return true if a file was found.
	 * The file path will be stored in an instance member file.
	 * @return true if found
	 */
	public	boolean	hasFileToSend() {
		file = null;
		try {
			Files.walkFileTree(fileStart, opts, 1, this);
		} catch (IOException e) {
			file = null;
		}
		return file != null;
	}
	
	/**
	 * Returns a path when a file was found.
	 * @return file path of detected input file.
	 */
	public Path getFile() {
		return file;
	}
	
	/**
	 * Return file name component of the file.
	 * @return
	 */
	public String getFileName() {
		return file.toFile().getName();
	}

	/**
	 * Ignored - we use only a single directory level.
	 */
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	/**
	 * Ignored - we use only a single directory level.
	 */
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		return FileVisitResult.CONTINUE;
	}

	/**
	 * Ignored - we skip any failures.
	 */
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		return FileVisitResult.CONTINUE;
	}
	
}
