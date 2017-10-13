package de.upb.crc901.services;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import jaicore.ml.WekaUtil;
import weka.core.Instances;

public class SciKitTester {
	private final static int PYTHON_PORT = 8002;

	private BufferedReader fromServer;
	private DataOutputStream toServer;
	private Socket pythonServerSocket;
	private HttpServiceClient client;
	private final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem(
			"testrsc/conf/types.conf");
	
	@Before
	public void init() throws Exception {
		pythonServerSocket = new Socket("127.0.0.1", PYTHON_PORT);
		toServer = new DataOutputStream(pythonServerSocket.getOutputStream());
		fromServer = new BufferedReader(new InputStreamReader(pythonServerSocket.getInputStream()));
		client = new HttpServiceClient(otms);
	}

	@Test
	public void testSciKit() throws Exception {
		Instances wekaInstances = new Instances(new BufferedReader(new FileReader("testrsc/audiology.arff")));
		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .9f);
		ObjectMapper mapper = new ObjectMapper();
		String buildSerialization = mapper.writeValueAsString(split.get(0));
		
		toServer.writeBytes(buildSerialization);
		System.out.println(fromServer.readLine());
	}

	@After
	public void shutdown() throws IOException {
		pythonServerSocket.close();
	}
}
