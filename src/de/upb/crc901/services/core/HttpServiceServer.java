/**
 * HttpServiceServer.java
 * Copyright (C) 2017 Paderborn University, Germany
 * 
 * This class provides (configured) Java functionality over the web
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.logic.LiteralParam;
import de.upb.crc901.configurationsetting.logic.VariableParam;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;
import jaicore.basic.FileUtil;
import weka.core.Instance;

public class HttpServiceServer {

	private static final Logger logger = LoggerFactory.getLogger(HttpServiceServer.class);

	private static File folder = new File("http");

	private final HttpServer server;
	private final HttpServiceClient clientForSubSequentCalls;
	private final OntologicalTypeMarshallingSystem otms;
	private final Set<String> supportedOperations = new HashSet<>();
	// Mapping of the fully qualified name of a operation invocation <class
	// name>::<operation name> to a mapping of the inputs and their
	// associated values.
	private final Map<String, Map<String, String>> resultMaps = new HashMap<>();

	/**
	 * Handle for services created on the server. Stores an unique id and a
	 * reference to the service object.
	 *
	 */
	class ServiceHandle {
		long id;
		Object service;

		public ServiceHandle(long id, Object service) {
			super();
			this.id = id;
			this.service = service;
		}
	}

	/**
	 * 
	 * HttpHandler for Java classes that shall be available as a service on the
	 * server. Handler for operation invocations and constructor calls likewise.
	 *
	 */
	class JavaClassHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange t) throws IOException {

			String response = "";
			try {

				/* determine method to be executed */
				String address = t.getRequestURI().getPath().substring(1);
				logger.info("Received query for {}", address);
				String[] parts = address.split("/");
				String clazz = parts[0];
				String objectId = parts[1];
				System.out.println("class: " + clazz + "\nObjectID: " + objectId);
				/*
				 * initiate state with the non-constant inputs given in post
				 * (non-int and non-doubles are treated as strings)
				 * 
				 * initialState is a mapping of the inputs index to the
				 * associated object (inputs[INDEX]=Object).
				 */
				Map<String, Object> initialState = new HashMap<>();
				Map<String, Object> post = parsePostParameters(t);
				for (String input : post.keySet()) {
					if (!input.startsWith("inputs["))
						continue;
					String index = input.substring(7, input.length() - 1);
					JsonNode inputObject = new ObjectMapper().readTree(post.get(input).toString());
					if (!inputObject.has("type"))
						throw new IllegalArgumentException("Input " + index + " has no type attribute!");
					if (!otms.isKnownType(inputObject.get("type").asText()))
						throw new IllegalArgumentException(
								"Ontological type of of input " + index + " is not known to the system!");
					initialState.put(index, inputObject);
				}
				logger.info("Input keys are: {}", initialState.keySet());
				Map<String, Object> state = new HashMap<>(initialState);

				/*
				 * Analyze choreography in order to see what we actually will
				 * execute right away: 1. move to position of current call; 2.
				 * compute all subsequent calls on same host and on services
				 * spawned from here.
				 **/
				SequentialComposition comp = null;
				SequentialComposition subsequenceComp = new SequentialComposition(new CompositionDomain());
				OperationInvocation invocationToMakeFromHere = null;
				if (post.containsKey("coreography")) {
					SequentialCompositionSerializer srs = new SequentialCompositionSerializer();
					comp = srs.readComposition(post.get("coreography").toString());
					int currentIndex = Integer.parseInt(post.get("currentindex").toString());
					Iterator<OperationInvocation> it = comp.iterator();
					for (int i = 0; i < currentIndex; i++) {
						it.next();
					}
					Collection<String> servicesInExecEnvironment = new HashSet<>();
					while (it.hasNext()) {
						OperationInvocation opInv = it.next();
						invocationToMakeFromHere = opInv;
						String opName = opInv.getOperation().getName();
						if (opName.contains("/")) {
							String host = opName.substring(0, opName.indexOf("/"));
							if (!host.equals(t.getLocalAddress().toString().substring(1)))
								break;

							/*
							 * if this is a constructor, also add the created
							 * instance to the locally available services
							 */
							servicesInExecEnvironment.add(opName);
							if (objectId.equals("__construct")) {
								servicesInExecEnvironment
										.add(opInv.getOutputMapping().values().iterator().next().getName());
							}
						} else if (!servicesInExecEnvironment.contains(opName.substring(0, opName.indexOf("::")))) {
							break;
						}
						subsequenceComp.addOperationInvocation(opInv);
						invocationToMakeFromHere = null;
					}
				} else {
					OperationInvocation opinv;
					if (objectId.equals("__construct")) {

						/* creating new object */
						opinv = ServiceUtil.getOperationInvocation(
								t.getLocalAddress().toString().substring(1) + "/" + clazz + "::__construct", state);

					} else {
						opinv = ServiceUtil.getOperationInvocation(t.getLocalAddress().toString().substring(1) + "/"
								+ clazz + "/" + objectId + "::" + parts[2], state);
					}
					subsequenceComp.addOperationInvocation(opinv);
				}

				/* execute the whole induced composition */
				for (OperationInvocation opInv : subsequenceComp) {
					invokeOperation(opInv, state);
				}
				logger.info("Finished local execution. Now invoking {}", invocationToMakeFromHere);

				/* call next service */
				if (invocationToMakeFromHere != null) {

					/*
					 * extract vars from state that are in json (ordinary data
					 * but not service references)
					 */
					Map<String, Object> inputsToForward = new HashMap<>();
					for (String key : state.keySet()) {
						Object val = state.get(key);
						if (val instanceof JsonNode)
							inputsToForward.put(key, (JsonNode) val);
					}
					ServiceCompositionResult result = clientForSubSequentCalls
							.callServiceOperation(invocationToMakeFromHere, comp, inputsToForward);
					response += result.toString();
					logger.info("Received answer from subsequent service.");
				}

				/*
				 * now returning the serializations of all created (non-service)
				 * objects
				 */
				logger.info("Returning answer to sender");
				ObjectNode objectsToReturn = new ObjectMapper().createObjectNode();
				for (String key : state.keySet()) {
					Object answerObject = state.get(key);
					if (!initialState.containsKey(key)) {
						if (answerObject == null)
							objectsToReturn.putNull(key);
						else if ((answerObject instanceof ObjectNode))
							objectsToReturn.set(key, (JsonNode) answerObject);
						else if (answerObject instanceof String)
							objectsToReturn.put(key, (String) answerObject);
						else if (answerObject instanceof Double)
							objectsToReturn.put(key, (Double) answerObject);
						else if (answerObject instanceof Integer)
							objectsToReturn.put(key, (Integer) answerObject);
						else if (answerObject instanceof ServiceHandle)
							objectsToReturn.put(key, t.getLocalAddress().toString().substring(1) + "/" + clazz + "/"
									+ ((ServiceHandle) answerObject).id);
						else
							throw new IllegalArgumentException("Do not know how to treat object " + answerObject
									+ " as it is not serialized to some json thing");
					}
				}
				response += objectsToReturn.toString();
			} catch (InvocationTargetException e) {
				e.getTargetException().printStackTrace();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
			}

		}
	}

	/**
	 * Invokes an operation defined in an OperationInvocation object.
	 * 
	 * @param operationInvocation
	 *            Operation to be called.
	 * @param state
	 *            Parameters for the operation call.
	 * @return Mapping of return value(s) of the operation invocation. Can be a
	 *         handle for a new service if a constructor was called or the
	 *         result of an operation.
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 */
	private OperationInvocationResult invokeOperation(OperationInvocation operationInvocation,
			Map<String, Object> state)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
			SecurityException, ClassNotFoundException, InstantiationException {
		logger.info("Performing invocation {} in state {}", operationInvocation, state);
		List<VariableParam> inputs = operationInvocation.getOperation().getInputParameters();
		String[] types = new String[inputs.size()];
		Object[] values = new Object[inputs.size()];
		Map<VariableParam, LiteralParam> inputMapping = operationInvocation.getInputMapping();
		for (int j = 0; j < inputs.size(); j++) {
			String val = inputMapping.get(inputs.get(j)).getName();

			/* first try native types */
			if (NumberUtils.isNumber(val)) {
				if (val.contains(".")) {
					// TODO Kein Eintrag in Types ?
					values[j] = Double.valueOf(val);
				} else {
					types[j] = Integer.TYPE.getName();
					values[j] = Integer.valueOf(val);
				}
			} else if (val.startsWith("\"") && val.endsWith("\"")) {
				types[j] = String.class.getName();
				values[j] = val;
			}

			/* if the value is a variable in our current state, use this */
			else if (state.containsKey(val)) {
				JsonNode var = (JsonNode) state.get(val);
				if (var.isNumber()) {
					if (var.asText().contains(".")) {
						// TODO Kein Eintrag in Types ?
						values[j] = var.asDouble();
					} else {
						types[j] = Integer.TYPE.getName();
						values[j] = var.asInt();
					}
				} else if (var.isTextual()) {
					types[j] = String.class.getName();
					values[j] = var.asText();
				} else {
					types[j] = var.get("type").asText();
					values[j] = var;
				}
			} else
				throw new IllegalArgumentException("Cannot find value for argument " + val + " in state table.");
		}

		/*
		 * if this operation is a constructor, create the corresponding service
		 * and return the url
		 */
		String opName = operationInvocation.getOperation().getName();
		Map<VariableParam, VariableParam> outputMapping = operationInvocation.getOutputMapping();
		String fqOpName = "";
		Object basicResult = null;
		if (opName.contains("/")) { // if this is a service called via an
									// address

			logger.info("Run invocation on a service that has not been created previously");

			fqOpName = opName;
			String[] parts = opName.substring(opName.indexOf("/") + 1).split("::");
			String clazz = parts[0];
			String methodName = parts[1];
			if (methodName.equals("__construct")) {
				logger.info("The invocation creates a new service instance");
				Constructor<?> constructor = getConstructor(Class.forName(clazz), types);
				if (logger.isDebugEnabled())
					logger.debug("{}/{}/{}", types.length, constructor.getParameterCount(), constructor);
				Object newService = constructor.newInstance(values);

				/* serialize result */
				long id = System.currentTimeMillis();
				if (newService instanceof Serializable) {
					try {
						FileUtil.serializeObject(newService,
								folder + File.separator + "objects" + File.separator + clazz + File.separator + id);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				OperationInvocationResult result = new OperationInvocationResult();
				ServiceHandle sh = new ServiceHandle(id, newService);
				//
				result.put("out", sh);
				state.put(outputMapping.values().iterator().next().getName(), sh);
				return result;
			}

			/* otherwise execute the operation on the given service */
			else {

				logger.info("Run invocation on an existing service instance");
				clazz = parts[0].substring(0, parts[0].lastIndexOf("/"));
				String objectId = parts[0].substring(clazz.length() + 1);
				try {
					Method method = getMethod(Class.forName(clazz), methodName, types);
					if (method == null)
						throw new UnsupportedOperationException("Cannot invoke " + methodName + " for types "
								+ Arrays.toString(types) + ". The method does not exist in class " + clazz + ".");
					Object service = FileUtil.unserializeObject(
							folder + File.separator + "objects" + File.separator + clazz + File.separator + objectId);

					/* rewrite values according to the choice */
					Class<?>[] requiredTypes = method.getParameterTypes();
					logger.info("Values that will be used: {}", Arrays.toString(values));
					for (int i = 0; i < requiredTypes.length; i++) {
						if (!requiredTypes[i].isPrimitive() && !requiredTypes[i].getName().equals("String")) {
							logger.debug("map {}-th input to {}", i, requiredTypes[i].getName());
							if (values[i] instanceof ObjectNode) {
								// System.out.print(values[i] + " -> ");
								// System.out.println(otms.jsonToObject((JsonNode)
								// values[i], requiredTypes[i]));
							}
							values[i] = otms.jsonToObject((JsonNode) values[i], requiredTypes[i]);
						}
					}
					logger.info(
							"Computed inputs for invocation: {}: {}", (values[0] instanceof Instance)
									? ((Instance) values[0]).classIndex() : values[0].getClass(),
							Arrays.toString(values));
					basicResult = method.invoke(service, values);
					logger.info("Invocation ready. Result is: {}", basicResult);
					FileUtil.serializeObject(service,
							folder + File.separator + "objects" + File.separator + clazz + File.separator + objectId);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else { // if this is a call on a created services (this cannot be a
					// constructor)
			String[] parts = opName.split("::");
			String objectId = parts[0];
			Object object = state.get(objectId);
			if (object instanceof ServiceHandle)
				object = ((ServiceHandle) object).service;
			String methodName = parts[1];
			Method method = getMethod(object.getClass(), methodName, types);
			fqOpName = object.getClass().getName() + "::" + methodName;

			/* rewrite values according to the choice */
			Class<?>[] requiredTypes = method.getParameterTypes();
			for (int i = 0; i < requiredTypes.length; i++) {
				if (!requiredTypes[i].isPrimitive() && !requiredTypes[i].getName().equals("String")) {
					values[i] = otms.jsonToObject((JsonNode) values[i], requiredTypes[i]);
				}
			}

			/* check argument count and then execute the method */
			assert (method.getParameterCount() == values.length) : "Required number of parameters: "
					+ method.getParameterCount() + " but " + values.length + " are given.";
			basicResult = method.invoke(object, values);
		}

		/*
		 * compute the result of the invocation (resolve call-by-reference
		 * outputs)
		 */
		OperationInvocationResult result = new OperationInvocationResult();
		if (resultMaps.containsKey(fqOpName)) {
			Map<String, String> map = resultMaps.get(fqOpName);
			for (String key : map.keySet()) {
				String val = map.get(key);
				if (val.equals("return")) {
					result.put(key, basicResult);
				} else if (val.matches("i[\\d]+")) {
					int inputIndex = Integer.parseInt(val.substring(1));
					result.put(key, values[inputIndex - 1]);
				} else {
					logger.error("Cannot process result map entry {}", val);
				}
			}
		} else
			result.put("out", basicResult);

		/* now update state table based on result mapping */
		for (String key : result.keySet()) {
			VariableParam targetParam = outputMapping.get(new VariableParam(key));
			if (targetParam == null)
				throw new IllegalArgumentException("The parameter " + key + " used in the result mapping of " + fqOpName
						+ " is not a declared output parameter of the operation! Declared output params are: "
						+ operationInvocation.getOperation().getOutputParameters());
			String nameOfStateVariableToStoreResultIn = targetParam.getName();
			Object processedResult = result.get(key);
			state.put(nameOfStateVariableToStoreResultIn,
					processedResult != null ? (processedResult instanceof Number || processedResult instanceof String
							? processedResult : otms.objectToJson(processedResult)) : null);
		}
		return result;
	}

	/**
	 * Returns a constructor for the given class that matches the required types
	 * with its parameters. If no such constructor is found null is returned.
	 * Note that __construct must be part of the supported operations for the
	 * class.
	 * 
	 * @param clazz
	 *            Class to find a constructor for.
	 * @param types
	 *            Types to serve as the constructors input.
	 * @return A matching constructor if it exists. Null otherwise.
	 */
	private Constructor<?> getConstructor(Class<?> clazz, String[] types) {
		if (!supportedOperations.contains(clazz.getName() + "::__construct"))
			throw new IllegalArgumentException("This server is not configured to create new objects of " + clazz);
		for (Constructor<?> constr : clazz.getDeclaredConstructors()) {
			Class<?> requiredParams[] = constr.getParameterTypes();
			if (matchParameters(requiredParams, types))
				return constr;
		}
		return null;
	}

	/**
	 * Checks whether the provided types match with the types required by a
	 * classes constructor.
	 * 
	 * @param requiredTypes
	 *            Types to check against.
	 * @param providedTypes
	 *            Types provided by the invocation.
	 * @return False if the types don't match. True otherwise.
	 */
	private boolean matchParameters(Class<?>[] requiredTypes, String[] providedTypes) {
		if (requiredTypes.length > providedTypes.length)
			return false;
		for (int i = 0; i < requiredTypes.length; i++) {
			if (requiredTypes[i].isPrimitive()) {
				if (!requiredTypes[i].getName().equals(providedTypes[i]))
					return false;
			} else {
				if (!otms.hasMappingForClass(requiredTypes[i]))
					return false;
				logger.debug("Type: ", requiredTypes[i]);
			}
		}
		return true;
	}

	/**
	 * Returns the method of the given class that suffices the required method
	 * name and the required types as parameters.
	 * 
	 * @param clazz
	 *            The class to find the method on.
	 * @param methodName
	 *            Method name to search for.
	 * @param types
	 *            Parameters that the method shall match.
	 * @return The method of the class if it's found. Null otherwise.
	 */
	private Method getMethod(Class<?> clazz, String methodName, String[] types) {
		if (!supportedOperations.contains(clazz.getName() + "::" + methodName))
			throw new IllegalArgumentException(
					"The operation " + clazz.getName() + "::" + methodName + " is not supported by this server.");
		for (Method method : clazz.getMethods()) {
			if (!method.getName().equals(methodName))
				continue;
			Class<?> requiredParams[] = method.getParameterTypes();
			if (matchParameters(requiredParams, types))
				return method;
			else {
				logger.debug("Method {} with params {} matches the required method name but is not satisfied by ",
						methodName, Arrays.toString(requiredParams), Arrays.toString(types));
			}
		}
		return null;
	}

	public HttpServiceServer(int port) throws IOException {
		this(port, "conf/operations.conf", "conf/types.conf");
	}

	/**
	 * Starts a HTTP server to receive the service calls from the client.
	 * 
	 * @param port
	 *            Port to listen on.
	 * @param FILE_CONF_OPS
	 *            Listing of operations provided by the server.
	 * @param FILE_CONF_TYPES
	 *            Listing of classes and their ontological type.
	 * @throws IOException
	 *             If the configurations couldn't be read out.
	 */
	public HttpServiceServer(int port, String FILE_CONF_OPS, String FILE_CONF_TYPES) throws IOException {
		OperationFileParser confParser = new OperationFileParser();
		confParser.parseFile(FileUtil.readFileAsList(FILE_CONF_OPS));
		for (Operation op : confParser) {
			supportedOperations.add(op.getOperationName());
			resultMaps.put(op.getOperationName(), op.getOutputMapping());
		}
		otms = new OntologicalTypeMarshallingSystem(FILE_CONF_TYPES);
		clientForSubSequentCalls = new HttpServiceClient(otms);
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", new JavaClassHandler());
		server.start();
		logger.info("Server is up ...");
	}

	/**
	 * 
	 * @param exchange
	 *            HTTP message received by the server.
	 * @return Map of input parameters and their associated objects.
	 * @throws IOException
	 *             If the exchange is no post or if something goes wrong with
	 *             reading out the exchange body.
	 */
	private Map<String, Object> parsePostParameters(HttpExchange exchange) throws IOException {
		if ((!"post".equalsIgnoreCase(exchange.getRequestMethod())))
			throw new UnsupportedEncodingException("No post request");
		Map<String, Object> parameters = new HashMap<String, Object>();
		BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
		String query = br.readLine();
		parseQuery(query, parameters);
		return parameters;
	}

	/**
	 * Separates the given query according to delimiters (atm. '&') and splits
	 * each of it up into the input variable name and the Object that is
	 * assigned to it.
	 * 
	 * @param query
	 *            The query to parse.
	 * @param parameters
	 *            Reference to the Map that shall contain the result of the
	 *            parsing.
	 * @throws UnsupportedEncodingException
	 *             If the encoding method could not be received from the system.
	 */
	@SuppressWarnings("unchecked")
	public void parseQuery(String query, Map<String, Object> parameters) throws UnsupportedEncodingException {
		if (query != null) {
			String pairs[] = query.split("[&]");
			for (String pair : pairs) {
				String param[] = pair.split("[=]");
				String key = null;
				String value = null;
				if (param.length > 0) {
					key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
				}

				if (param.length > 1) {
					value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));
				}
				// TODO The if-condition seems never to be true. Should add new
				// value a list if the key of this value already exists in the
				// parameters.
				if (parameters.containsKey(key)) {
					Object obj = parameters.get(key);
					if (obj instanceof List<?>) {
						List<String> values = (List<String>) obj;
						// FIXME the values-variable is not further used.
						values.add(value);

					} else if (obj instanceof String) {
						List<String> values = new ArrayList<String>();
						values.add((String) obj);
						values.add(value);
						parameters.put(key, values);
					}
				} else {
					parameters.put(key, value);
				}
			}
		}
	}

	/**
	 * Shuts down the server.
	 */
	public void shutdown() {
		server.stop(0);
	}

	/**
	 * Starts the HTTP server on port 8000.
	 * 
	 * @param args
	 *            Unused
	 * @throws Exception
	 *             If configuration file or type file couldn't be opened.
	 */
	public static void main(String[] args) throws Exception {
		new HttpServiceServer(8000);
	}
}