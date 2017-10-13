package de.upb.crc901.services.typeserializers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import jaicore.ml.core.SimpleLabeledInstancesImpl;

public class SimpleLabeledInstancesImplOntologySerializer implements IOntologySerializer<SimpleLabeledInstancesImpl>  {
	public SimpleLabeledInstancesImpl unserialize(final JsonNode json) {
		SimpleLabeledInstancesImpl data = new SimpleLabeledInstancesImpl(json);
		return data;
	}

	public JsonNode serialize(final SimpleLabeledInstancesImpl instances) {
		try {
			return (new ObjectMapper().readTree(instances.toJson()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
