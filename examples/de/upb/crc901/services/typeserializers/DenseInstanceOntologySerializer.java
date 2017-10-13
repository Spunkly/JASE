package de.upb.crc901.services.typeserializers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.services.core.IOntologySerializer;
import jaicore.ml.WekaUtil;
import jaicore.ml.core.SimpleLabeledInstanceImpl;
import weka.core.DenseInstance;

public class DenseInstanceOntologySerializer implements IOntologySerializer<DenseInstance> {
	public DenseInstance unserialize(final JsonNode json) {
		SimpleLabeledInstanceImpl data = new SimpleLabeledInstanceImpl(json);
		DenseInstance inst = new DenseInstance(WekaUtil.fromJAICoreInstance(data));
		assert inst.dataset() != null : "No dataset assigned to instance " + inst;
		return inst;
	}

	public JsonNode serialize(final DenseInstance instance) {
		try {
			if (instance.classIndex() < 0)
				return new ObjectMapper().readTree(WekaUtil.toJAICoreInstance(instance).toJson());
			else
				return new ObjectMapper().readTree(WekaUtil.toJAICoreLabeledInstance(instance).toJson());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
