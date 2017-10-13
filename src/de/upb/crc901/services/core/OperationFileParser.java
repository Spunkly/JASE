package de.upb.crc901.services.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.basic.FileUtil;

/**
 * Checks for each line of the operation file to be well-formed. Defines grammar
 * for the operation file and separates it into the operations.
 * 
 * @author manuel
 *
 */
public class OperationFileParser implements Iterable<Operation> {

	private static final Logger logger = LoggerFactory.getLogger(OperationFileParser.class);
	private static final String COMMENT_SIGN = "#";
	private static final String OPNAME_OUTPUT_DELIMITER = "\t";
	private static final Pattern OP_PATTERN = Pattern
			.compile("([^" + COMMENT_SIGN + "]+)::([^" + OPNAME_OUTPUT_DELIMITER + "]+)(.*)");
	private static final Pattern DELIMITER_PATTERN = Pattern
			.compile("[" + OPNAME_OUTPUT_DELIMITER + "]+([^" + OPNAME_OUTPUT_DELIMITER + "]+)");
	private static final Pattern OUTPUT_PATTERN = Pattern.compile("\\{([^\\}^\\{]+)\\}");
	private static final Pattern SINGLE_OUTPUT_MAPPING = Pattern.compile("([^,]*)=([^,]*)");

	private ArrayList<Operation> operations = new ArrayList<>();

	public OperationFileParser(){}
	
	public OperationFileParser(String fileContent){
		parseFile(fileContent);
	}

	public OperationFileParser(List<String> fileLines){
		parseFile(fileLines);
	}
	
	/**
	 * Parses the given file content. The lines are checked against a pattern
	 * and are stored, separated by class name, method name and output mapping.
	 * 
	 * @param fileContent
	 *            file content to be parsed.
	 * @return Number of well-formed lines.
	 */
	public int parseFile(String fileContent) {
		try {
			return parseFile(FileUtil.readFileAsList(fileContent));
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Could not parse File as a list.");
		}
		return 0;
	}

	/**
	 * Parses the given list of file lines. The lines are checked against a
	 * pattern and are stored, separated by class name, method name and output
	 * mapping.
	 * 
	 * @param fileLines
	 *            file lines to be parsed.
	 * @return Number of well-formed lines.
	 */
	public int parseFile(List<String> fileLines) {
		int matches = 0;
		for (int index = 0; index < fileLines.size(); index++) {
			String line = fileLines.get(index);
			Matcher matcher = OP_PATTERN.matcher(line);
			if (matcher.matches()) {
				matches++;
				/*
				 * If an output mapping is given, it is parsed. Otherwise an
				 * empty HashMap is set to signal that there is not output
				 * mapping.
				 */
				HashMap<String, String> outputMapping = new HashMap<>();
				if (matcher.groupCount() == 3) {
					try {
						outputMapping = parseOutputMapping(matcher.group(3));
					} catch (InputMismatchException e) {
						logger.error("InputMismatchException on file line: " + index);
					}
				}
				operations.add(new Operation(matcher.group(1), matcher.group(2), outputMapping));
			} else if (line.startsWith(COMMENT_SIGN) || line.equals(""))
				continue;
			else {
				if (matcher.groupCount() >= 1)
					logger.debug("Matcher Group 1: " + matcher.group(1));
				if (matcher.groupCount() >= 2)
					logger.debug("Matcher Group 2: " + matcher.group(2));
				if (matcher.groupCount() >= 3)
					logger.debug("Matcher Group 3: " + matcher.group(3));
				logger.warn("Not well-formed line in configuration file:\n\t" + line);
			}
		}
		return matches;
	}

	/**
	 * Parses the output mapping. Checks whether the output mapping is
	 * well-formed and constructs a map that maps variable names to their
	 * bindings.
	 * 
	 * @param outMappingString
	 *            Output mapping to parse.
	 * @return Mapping between variables and their bindings.
	 * @throws InputMismatchException
	 *             It is a critical error if a single output is not well-formed.
	 */
	private HashMap<String, String> parseOutputMapping(String outMappingString) throws InputMismatchException {
		if (outMappingString == null || outMappingString.length() == 0)
			return null;
		Matcher delimiterMatcher = DELIMITER_PATTERN.matcher(outMappingString);
		if (!delimiterMatcher.matches()) {
			logger.error("Output mapping not well-formed! No delimiter found: " + outMappingString);
			throw new InputMismatchException();
		}
		Matcher outputMatcher = OUTPUT_PATTERN.matcher(delimiterMatcher.group(1));
		if (!outputMatcher.matches()) {
			logger.error("Output mapping not well-formed! Curly braces missing? : " + outMappingString);
			throw new InputMismatchException();
		}
		logger.debug("OUTPUT_PATTERN Match on: >>" + outputMatcher.group(1) + "<<");
		HashMap<String, String> result = new HashMap<>();
		String[] outputMap = outputMatcher.group(1).split(",");
		for (String outputMapping : outputMap) {
			Matcher singleMatcher = SINGLE_OUTPUT_MAPPING.matcher(outputMapping);
			if (!singleMatcher.matches()) {
				logger.error("Output mapping not well-formed on a single argument! : " + outputMapping);
				throw new InputMismatchException();
			}
			result.put(singleMatcher.group(1), singleMatcher.group(2));
		}
		return result;
	}

	@Override
	public Iterator<Operation> iterator() {
		return operations.iterator();
	}

	/**
	 * Returns a specific operation on the given index of the stored list.
	 * 
	 * @param index
	 *            Index to return
	 * @return Operation on that index
	 */
	public Operation get(int index) {
		try {
			return operations.get(index);
		} catch (IndexOutOfBoundsException e) {
			logger.error("IndexOutOfBoundsException bei Zugriff auf Index " + index + "des OperationFileParsers");
			return null;
		}
	}
}
