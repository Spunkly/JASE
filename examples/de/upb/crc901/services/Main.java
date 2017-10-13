package de.upb.crc901.services;

import java.io.IOException;

public class Main {
	public static void main(String[] args) throws IOException {
		ExampleTester2 et = new ExampleTester2();
		try {
			et.init();
			et.testSciKit();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			et.shutdown();
		}
	}
}
