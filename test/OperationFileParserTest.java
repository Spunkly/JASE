import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import de.upb.crc901.services.core.Operation;
import de.upb.crc901.services.core.OperationFileParser;
import jaicore.basic.FileUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OperationFileParserTest {
	List<String> testFileContent;
	OperationFileParser parser;

	@Before
	public void readFile() throws IOException {
		testFileContent = FileUtil.readFileAsList("test/testOperationFile.conf");
		parser = new OperationFileParser();
	}

	@Test
	public void parseFile() {
		OperationFileParser parser = new OperationFileParser();
		int matches = parser.parseFile(testFileContent);
		System.out.println(matches);
		for (Operation op : parser)
			System.out.println(op);
		assertEquals(16, matches);
		assertEquals("Catalano.Imaging.Filters.Crop", parser.get(0).getClassName());
		assertEquals("__construct", parser.get(0).getOperationName());
		assertEquals("Catalano.Imaging.Filters.Crop::__construct", parser.get(0).getOperationName());
		assertTrue(parser.get(15).getClassName().equals("weka.classifiers.trees.RandomForest"));
		assertTrue(parser.get(15).getOperationName().equals("classifyInstance"));
		assertTrue(parser.get(15).getOperationName().equals("weka.classifiers.trees.RandomForest::classifyInstance"));
		assertTrue(parser.get(3).getClassName().equals("Catalano.Imaging.Filters.Resize"));
		assertTrue(parser.get(3).getOperationName().equals("applyInPlace"));
		assertTrue(parser.get(3).getOperationName().equals("Catalano.Imaging.Filters.Resize::applyInPlace"));
		assertEquals(2, parser.get(3).getOutputMapping().size());
		assertTrue(parser.get(1).getOutputMapping().get("out").equals("i1"));
		assertTrue(parser.get(3).getOutputMapping().get("out2").equals("i2"));
	}
}
