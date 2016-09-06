package eu.dkitt;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class TLog1 {

	public static void main(String[] args) throws SecurityException, IOException {

		Handler h = new ConsoleHandler();
		h.setFormatter(new MyFormatter());
		
		Logger logParent = Logger.getLogger("eu.dkitt");
		logParent.addHandler(h);
		
		h = new FileHandler("out.log", 10000, 2, true);
		h.setFormatter(new MyFormatter());
		logParent.addHandler(h);
		
		
		logParent.setUseParentHandlers(false);
		
		Logger logger = Logger.getLogger(TLog1.class.getName());
		
		logger.info("Ahoj vole");
		
		logger.info("Nazdarek");
		
		logger.info("Ciao");
		
	}

}

class MyFormatter extends Formatter {
	private Calendar cal;
	public MyFormatter() {
		super();
		cal = new GregorianCalendar();
	}
	@Override
	public String format(LogRecord record) {
		cal.setTime(new Date(record.getMillis()));
		String str = String.format("%02d:%02d:%02d.%03d %s:%s -- %s\n",
				cal.get(Calendar.HOUR),cal.get(Calendar.MINUTE),cal.get(Calendar.SECOND),cal.get(Calendar.MILLISECOND),
				record.getLoggerName(), record.getSourceMethodName(), record.getMessage());
		return str;
	}
}
