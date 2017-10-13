/**
 * HttpServiceServer.java
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jaicore.basic.FileUtil;

public class OntologicalTypeMarshallingSystem {

	private final Logger logger = LoggerFactory.getLogger(OntologicalTypeMarshallingSystem.class);
	private final Map<Class<?>, String> ontologyMap = new HashMap<>();

	public OntologicalTypeMarshallingSystem(String confFile) {

		/* read config files */
		try {
			for (String typemapping : FileUtil.readFileAsList(confFile)) {
				if (typemapping.trim().startsWith("#") || typemapping.trim().isEmpty())
					continue;
				String[] split = typemapping.split("\t");
				try {
					ontologyMap.put(Class.forName(split[0].trim()), split[split.length - 1].trim());
				} catch (ClassNotFoundException e) {
					logger.error("Cannot bind class {} to {} as the class {} could not be found.", split[0].trim(), split[1].trim(), split[0].trim());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		logger.info("Loaded OTMS: {}", ontologyMap);
	}

	/**
	 * Transforms the given object into an Json file given that there is an OnthologySerializer for the specific class.
	 * @param o Object to serialize.
	 * @return JsonNode of the object.
	 */
	public JsonNode objectToJson(Object o) {
		try {
			if (o == null)
				throw new IllegalArgumentException("Cannot serialize null-objects.");
			if (!ontologyMap.containsKey(o.getClass()))
				throw new IllegalArgumentException("Cannot serialize objects of type " + o.getClass().getName() + " as no ontological concept was defined for them.");
			String serializerClassName = "de.upb.crc901.services.typeserializers." + o.getClass().getSimpleName() + "OntologySerializer";
			Method method = MethodUtils.getMatchingAccessibleMethod(Class.forName(serializerClassName), "serialize", o.getClass());
			assert method != null : "Could not find method \"serialize(" + o.getClass() + ")\" in serializer class " + serializerClassName;
			IOntologySerializer<?> serializer = (IOntologySerializer<?>)Class.forName(serializerClassName).getConstructor().newInstance();
			JsonNode dataNode = (JsonNode) method.invoke(serializer, o);
			ObjectNode root = new ObjectMapper().createObjectNode();
			root.put("type", ontologyMap.get(o.getClass()));
			root.set("data", dataNode);
			return root;
		} catch (ClassNotFoundException e) {
			throw new UnsupportedOperationException("Cannot convert objects of type " + o.getClass().getName()
					+ " to JSON. The necessary serializer class \"de.upb.crc901.services.typeserializers." + o.getClass().getSimpleName() + "OntologySerializer\" was not found.");
		} catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | InstantiationException | NoSuchMethodException e) {
			e.printStackTrace();
			throw new UnsupportedOperationException(
					"Cannot convert objects of type " + o.getClass().getName() + " to JSON objects. The necessary serializer class \"de.upb.crc901.services.typeserializers."
							+ o.getClass().getSimpleName() + "OntologySerializer\" throws an exception.");
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T jsonToObject(JsonNode json, Class<T> clazz) {
		if (!json.has("type"))
			throw new IllegalArgumentException("Given object serialization is malformed. It has no \"type\" field.");
		if (!json.has("data"))
			throw new IllegalArgumentException("Given object serialization is malformed. It has no \"data\" field.");
		String type = json.get("type").asText();

		/* determine serializer */
		try {
			Class<?> serializerClass = Class.forName("de.upb.crc901.services.typeserializers." + clazz.getSimpleName() + "OntologySerializer");
			Method method = MethodUtils.getAccessibleMethod(serializerClass, "unserialize",
					JsonNode.class);
			if (method == null)
				throw new UnsupportedOperationException("Cannot convert objects of type " + type + " to a Java object of class " + clazz.getName()
						+ ". The serializer class \"de.upb.crc901.services.typeserializers." + clazz.getSimpleName()
						+ "OntologySerializer\" has no method \"unserialize(JsonNode)\".");
			
			Object rawSerializer = serializerClass.getConstructor().newInstance();
			if (!(IOntologySerializer.class.isInstance(rawSerializer)))
				throw new ClassCastException("The ontological serializer for " + clazz.getSimpleName() + " does not implement the IOntologySerializer interface!");
			
			/* unserialize the JSON string to an actual Java object */
			return (T) method.invoke(rawSerializer, json.get("data"));
		} catch (ClassNotFoundException e) {
			throw new UnsupportedOperationException("Cannot convert objects of type " + type + " to a Java object of class " + clazz.getName()
					+ ". The necessary serializer class \"de.upb.crc901.services.typeserializers." + clazz.getSimpleName() + "OntologySerializer\" was not found.");
		} catch (InvocationTargetException e) {
			e.getTargetException().printStackTrace();
			throw new UnsupportedOperationException("Cannot convert objects of type " + type + " to a Java object of class " + clazz.getName()
					+ ". The necessary serializer class \"de.upb.crc901.services.typeserializers." + clazz.getSimpleName() + "OntologySerializer\" throws an exception.");
		} catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException e) {
			e.printStackTrace();
			throw new UnsupportedOperationException("Cannot convert objects of type " + type + " to a Java object of class " + clazz.getName()
					+ ". The necessary serializer class \"de.upb.crc901.services.typeserializers." + clazz.getSimpleName() + "OntologySerializer\" throws an exception.");
		}
	}

	public Collection<Class<?>> getClassesMappedToType(String type) {
		return ontologyMap.keySet().stream().filter(k -> ontologyMap.get(k).equals(type)).collect(Collectors.toList());
	}

	public boolean hasMappingForClass(Class<?> clazz) {
		return ontologyMap.containsKey(clazz);
	}

	public boolean isKnownType(String type) {
		return ontologyMap.values().contains(type);
	}
}
