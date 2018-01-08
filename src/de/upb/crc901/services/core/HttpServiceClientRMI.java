/**
 * HttpServiceClient.java
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
package de.upb.crc901.services.core;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import org.slf4j.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.logic.LiteralParam;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;

public class HttpServiceClientRMI {
	private static final String PORT = "8000";
	private static final String IP_ADRESS = "127.0.0.1";
	
	private static Logger logger = LoggerFactory.getLogger(HttpServiceClientRMI.class.getName());

	private final OntologicalTypeMarshallingSystem otms;

	public HttpServiceClientRMI(OntologicalTypeMarshallingSystem otms) {
		super();
		this.otms = otms;
	}

	/**
	 * Calls the given service call on the server with the inputs as parameters.
	 * 
	 * @param serviceCall
	 *            Service call to be invoked.
	 * @param inputs
	 *            Parameters for the service.
	 * @return Response from the server.
	 * @throws IOException
	 */
	public ServiceCompositionResult callServiceOperation(String serviceCall, Object... inputs) throws IOException {
		return callServiceOperation(ServiceUtil.getOperationInvocation(serviceCall, inputs),
				new SequentialComposition(new CompositionDomain()), inputs);
	}

	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography)
			throws IOException {
		return callServiceOperation(call, coreography, new HashMap<>());
	}

	/**
	 * Calls the given service call on the server with the inputs as parameters.
	 * 
	 * @param call
	 *            Next call to be made from the given choreography
	 * @param coreography
	 *            Composition of service calls to be invoked.
	 * @param additionalInputs
	 *            TODO Same input as it is in the call already
	 * @return Response from the server.
	 * @throws IOException
	 */
	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography,
			Object... additionalInputs) throws IOException {
		return callServiceOperation(call, coreography, ServiceUtil.getObjectInputMap(additionalInputs));
	}

	/**
	 * Calls the given service call on the server with the inputs as parameters.
	 * 
	 * @param call
	 *            Next call to be made from the given choreography
	 * @param coreography
	 *            Composition of service calls to be invoked.
	 * @param additionalInputs
	 *            TODO Same input as it is in the call already
	 * @return Response from the server.
	 * @throws IOException
	 */
	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography,
			Map<String, Object> additionalInputs) throws IOException {

		/* separate service and operation from name */
		String opFQName = call.getOperation().getName();
		String service = opFQName.substring(0, opFQName.indexOf("::"));
		String opName = opFQName.substring(service.length() + 2);

		/* setup connection */
		URL url = new URL("http://" + service + "/" + opName);
		logger.info("Connecting to URL: " + url);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoOutput(true);

		/* send data */
		DataOutputStream serverInput = new DataOutputStream(con.getOutputStream());
		SequentialCompositionSerializer compositionSerializer = new SequentialCompositionSerializer();
		int index = 0;
		for (OperationInvocation opInv : coreography) {
			logger.debug("OperationInvocation in coreography on index: " + index + " | " + opInv);
			if (opInv.equals(call)) {
				logger.info("Coreography matches call on index: " + index);
				break;
			}
			index++;
		}

		/*
		 * writes the inputs parameter name as the index of an array "inputs", followed
		 * by the serialized representation of the corresponding object.
		 */
		for (String input : additionalInputs.keySet()) {
			Object inputObject = additionalInputs.get(input);
			String serialization;
			if (inputObject instanceof Number || inputObject instanceof String)
				serialization = inputObject.toString();
			else
				serialization = ((inputObject instanceof JsonNode) ? (JsonNode) inputObject
						: otms.objectToJson(inputObject)).toString();
			serverInput.writeBytes("inputs[" + input + "]=" + URLEncoder.encode(serialization, "UTF-8") + "&");
		}

		// TODO Could yield in wrong result; iterator() returns a new Instance
		// FIXME Had to make '<' instead of '<=' in for-loop to avoid crash.
		// if (coreography.iterator().hasNext()) {
		// serverInput.writeBytes("coreography=" +
		// URLEncoder.encode(compositionSerializer.serializeComposition(coreography),
		// "UTF-8") + "&currentindex=" + index);
		// }
		Iterator<OperationInvocation> coreographyIterator = coreography.iterator();
		for (int i = 0; i < index; i++) {
			coreographyIterator.next();
		}
		if (coreographyIterator.hasNext())
			serverInput.writeBytes(
					"coreography=" + URLEncoder.encode(compositionSerializer.serializeComposition(coreography), "UTF-8")
							+ "&currentindex=" + index);

		serverInput.flush();
		serverInput.close();

		/* Read out the response from the server. */
		InputStream serverOutput = con.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(serverOutput));
		String curline;
		StringBuilder response = new StringBuilder();
		while ((curline = reader.readLine()) != null) {
			response.append(curline + '\n');
		}
		reader.close();
		con.disconnect();

		/*
		 * Traversing over the Jackson Json tree to get a ServiceCompositionResult
		 */
		JsonNode root = new ObjectMapper().readTree(response.toString());
		ServiceCompositionResult result = new ServiceCompositionResult();
		Iterator<String> it = root.fieldNames();
		while (it.hasNext()) {
			String field = it.next();
			JsonNode object = root.get(field);
			result.put(field, object);
			logger.debug("<<< " + field + "= "
					+ ((object.toString().length()) > 200 ? object.toString().substring(0, 200) + "[...]" : object));
		}
		return result;
	}

	private ServiceCompositionResult callServiceOperation(SequentialComposition coreography,
			Map<String, Object> additionalInputs) throws IOException {
		/* setup connection */
		URL url = new URL("http://" + IP_ADRESS + ":" + PORT + "::sfb_demonstrator");
		logger.info("Connecting to URL: " + url);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoOutput(true);

		DataOutputStream serverInput = new DataOutputStream(con.getOutputStream());
		SequentialCompositionSerializer compositionSerializer = new SequentialCompositionSerializer();

		/*
		 * writes the inputs parameter name as the index of an array "inputs", followed
		 * by the serialized representation of the corresponding object.
		 */
		for (String input : additionalInputs.keySet()) {
			Object inputObject = additionalInputs.get(input);
			String serialization;
			if (inputObject instanceof Number || inputObject instanceof String)
				serialization = inputObject.toString();
			else
				serialization = ((inputObject instanceof JsonNode) ? (JsonNode) inputObject
						: otms.objectToJson(inputObject)).toString();
			serverInput.writeBytes("inputs[" + input + "]=" + URLEncoder.encode(serialization, "UTF-8") + "&");
		}
		
		serverInput.writeBytes(
				"coreography=" + URLEncoder.encode(compositionSerializer.serializeComposition(coreography), "UTF-8"));
		serverInput.flush();
		serverInput.close();

		/* Read out the response from the server. */
		InputStream serverOutput = con.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(serverOutput));
		String curline;
		StringBuilder response = new StringBuilder();
		while ((curline = reader.readLine()) != null) {
			response.append(curline + '\n');
		}
		reader.close();
		con.disconnect();

		/*
		 * Traversing over the Jackson Json tree to get a ServiceCompositionResult
		 */
		JsonNode root = new ObjectMapper().readTree(response.toString());
		ServiceCompositionResult result = new ServiceCompositionResult();
		Iterator<String> it = root.fieldNames();
		while (it.hasNext()) {
			String field = it.next();
			JsonNode object = root.get(field);
			result.put(field, object);
			logger.debug("<<< " + field + "= "
					+ ((object.toString().length()) > 200 ? object.toString().substring(0, 200) + "[...]" : object));
		}
		return result;
	}

	public ServiceCompositionResult invokeServiceComposition(SequentialComposition composition,
			Map<String, Object> inputs) throws IOException {

		/* First check that all inputs are given. */
		Collection<String> availableInputs = new HashSet<>(inputs.keySet());
		for (OperationInvocation opInv : composition) {
			for (LiteralParam l : opInv.getInputMapping().values()) {
				boolean isNumber = NumberUtils.isNumber(l.getName());
				boolean isString = !isNumber && l.getName().startsWith("\"") && l.getName().endsWith("\"");
				if (!availableInputs.contains(l.getName()) && !isNumber && !isString)
					throw new IllegalArgumentException(
							"Parameter " + l.getName() + " required for " + opInv + " is missing in the invocation.");
			}
			availableInputs.addAll(
					opInv.getOutputMapping().values().stream().map(p -> p.getName()).collect(Collectors.toList()));
		}

		return callServiceOperation(composition, inputs);
	}

	public ServiceCompositionResult invokeServiceComposition(SequentialComposition composition,
			Object... additionalInputs) throws IOException {
		return invokeServiceComposition(composition, ServiceUtil.getObjectInputMap(additionalInputs));
	}
}
