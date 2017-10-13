package de.upb.crc901.services.core;

import java.util.HashMap;

/**
 * Minimal class to represent a single line of a configuration file.
 * @author manuel
 *
 */
public class Operation {
	private final String className;
	private final String methodName;
	private final HashMap<String, String> outputMapping;

	public Operation(String className, String methodName, HashMap<String, String> outputMapping) {
		this.className = className;
		this.methodName = methodName;
		this.outputMapping = outputMapping;
	}

	public String getClassName() {
		return className;
	}

	public String opName() {
		return methodName;
	}

	public String getOperationName() {
		return className + "::" + methodName;
	}

	public HashMap<String, String> getOutputMapping() {
		return outputMapping;
	}
	
	public boolean hasOutputMapping(){
		return outputMapping.size()>0;
	}

	public String toString() {
		if (outputMapping == null)
			return className + "::" + methodName;
		else
			return className + "::" + methodName + "\t" + outputMapping.toString();

	}
}