package de.upb.crc901.services.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the logic to communicate to a Python process that runs sciKit.
 * @author manuel
 *
 */
public class SciKitProcess {
	private static final Logger logger = LoggerFactory.getLogger(SciKitProcess.class);
	/*
	 * Process to be started. That is, a console is started and within it
	 * python3.5 gets started with the script as an argument.
	 */
	ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c",
			"python3.5 " + this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()
					+ "../src/de/upb/crc901/services/core/test.py");
	BufferedWriter writer = null;
	BufferedReader reader = null;
	Process process = null;

	/*
	 * Creating the two pipe-objects. One from server to the python process and
	 * one backwards.
	 */
	SciKitProcess() {
		try {
			writer = new BufferedWriter(new FileWriter("test.pipe.topy"));
			File f = new File("test.pipe.frompy");
			f.deleteOnExit();
			f.createNewFile();
			reader = new BufferedReader(new FileReader("test.pipe.frompy"));
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("The pipe-objects could not be created or found");
		}
	}

	/**
	 * Returns pipe-object to communicate to the python process.
	 * @return Writer that writes to a pipe-object read out by the python process.
	 */
	public BufferedWriter getOutputStream() {
		return writer;
	}

	/**
	 * Returns pipe-object to receive communication from the python process.
	 * @return Reader that reads out a pipe-object written to by the python process.
	 */
	public BufferedReader getInputStream() {
		return reader;
	}

	/**
	 * Starts the python process.
	 */
	public void start() {
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			logger.error("SciKitProcess could not be started");
			e.printStackTrace();
		}
	}

	/**
	 * Returns whether the python process is alive.
	 */
	public boolean isAlive() {
		if (process != null)
			return process.isAlive();
		return false;
	}
	
	/**
	 * Send sciKit the files to build upon.
	 */
	public void build(){
		System.out.println("[BUILD] invoked");
	}
	
	/**
	 * Send sciKit the files to test its accuracy upon.
	 */
	public void test(){
		System.out.println("[TEST] invoked");
	}
}
