package de.upb.crc901.services.typeserializers;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import Catalano.Imaging.FastBitmap;
import de.upb.crc901.services.CatalanoWrapper;
import de.upb.crc901.services.core.IOntologySerializer;
import jaicore.ml.core.SimpleInstanceImpl;

public class FastBitmapOntologySerializer implements IOntologySerializer<FastBitmap> {

	public FastBitmap unserialize(final JsonNode json) {
		return new FastBitmap(CatalanoWrapper.instance2FastBitmap(new SimpleInstanceImpl(json)));
	}

	public JsonNode serialize(final FastBitmap fb) {
		try {
			return (new ObjectMapper().readTree(CatalanoWrapper.fastBitmap2Instance(fb).toJson()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
