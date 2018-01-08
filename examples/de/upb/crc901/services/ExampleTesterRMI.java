/**
 * ExampleTester.java
 * Copyright (C) 2017 Paderborn University, Germany
 * 
 * @author: Felix Mohr (mail@felixmohr.de)
 */

/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.upb.crc901.services;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;
import java.util.Random;

import javax.swing.JOptionPane;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import Catalano.Imaging.FastBitmap;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import de.upb.crc901.services.core.HttpServiceClient;
import de.upb.crc901.services.core.HttpServiceServer;
import de.upb.crc901.services.core.HttpServiceServerPython;
import de.upb.crc901.services.core.HttpServiceServerRMI;
import de.upb.crc901.services.core.OntologicalTypeMarshallingSystem;
import de.upb.crc901.services.core.ServiceCompositionResult;
import jaicore.basic.FileUtil;
import jaicore.basic.MathExt;
import jaicore.ml.WekaUtil;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomTree;
import weka.core.Instance;
import weka.core.Instances;

public class ExampleTesterRMI {

	private final static int PORT = 8000;

	private HttpServiceServerRMI server;

	private SequentialComposition composition;
	private SequentialCompositionSerializer sqs;
	private HttpServiceClient client;
	private final OntologicalTypeMarshallingSystem otms = new OntologicalTypeMarshallingSystem(
			"testrsc/conf/types.conf");

	@Before
	public void init() throws Exception {
		/* start server */
		server = new HttpServiceServerRMI(PORT, "testrsc/conf/operations.conf", "testrsc/conf/types.conf");

		/* read in composition */
		sqs = new SequentialCompositionSerializer();
		composition = sqs.readComposition(FileUtil.readFileAsList("testrsc/composition.txt"));

		client = new HttpServiceClient(otms);
	}

	@Test
	public void testClassifier() throws Exception {

		/* read instances */
		Instances wekaInstances = new Instances(new BufferedReader(new FileReader("testrsc/audiology.arff")));
		wekaInstances.setClassIndex(wekaInstances.numAttributes() - 1);
		List<Instances> split = WekaUtil.getStratifiedSplit(wekaInstances, new Random(0), .9f);

		/* create and train classifier service */
		Classifier c = new RandomTree();
		String serviceId = client
				.callServiceOperation("127.0.0.1:" + PORT + "/" + c.getClass().getName() + "::__construct")
				.get("out").asText();
		client.callServiceOperation(serviceId + "::buildClassifier", split.get(0));

		/* eval instances on service */
		int mistakes = 0;
		for (Instance i : split.get(1)) {
			ServiceCompositionResult resource = client.callServiceOperation(serviceId + "::classifyInstance", i);
			double prediction = Double.parseDouble(resource.get("out").toString());
			if (prediction != i.classValue())
				mistakes++;
		}

		/* report score */
		System.out.println(mistakes + "/" + split.get(1).size());
		System.out.println("Accuracy: " + MathExt.round(1 - mistakes * 1f / split.get(1).size(), 2));
	}

	@Test
	public void testImageProcessor() throws Exception {
		System.out.print("Now running the following composition: \n ");
		for (String callString : sqs.serializeComposition(composition).split(";"))
			System.out.println(callString + ";");

		File imageFile = new File("testrsc/FelixMohr.jpg");
		FastBitmap fb = new FastBitmap(imageFile.getAbsolutePath());
		JOptionPane.showMessageDialog(null, fb.toIcon(), "Input Image", JOptionPane.PLAIN_MESSAGE);

		ServiceCompositionResult resource = client.invokeServiceComposition(composition, fb);
		FastBitmap result = otms.jsonToObject(resource.get("fb3"), FastBitmap.class);

		JOptionPane.showMessageDialog(null, result.toIcon(), "Result", JOptionPane.PLAIN_MESSAGE);
	}

	@After
	public void shutdown() throws IOException {
		System.out.println("Shutting down ...");
		server.shutdown();
	}
}
