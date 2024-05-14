import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonParser;
import dev.nolij.zson.ZsonWriter;
import dev.nolij.zson.ZsonValue;

import java.nio.file.Paths;
import java.util.Map;

public class Main {
	public static void main(String[] args) throws Throwable {
		testReadWrite();
		testWriteAllTypes();
		testAllTypes();
		testNumbers();
	}

	private static void testReadWrite() throws Throwable {
		ZsonWriter writer = new ZsonWriter()
				.withExpandArrays(false)
				.withIndent("  ")
				.withQuoteKeys(true);

		Map<String, ZsonValue> zsonMap = Zson.object();
		zsonMap.put("name", new ZsonValue("The name of the person\nlook, a second line!", "John Doe"));
		zsonMap.put("age", new ZsonValue("The age of the person", 30));
		zsonMap.put("address",  new ZsonValue("The address of the person", Zson.object(
				Zson.entry("street", "The street of the address", "123 Main St"),
				Zson.entry("city", "The city of the address", "Springfield"),
				Zson.entry("state", "The state of the address", "IL"),
				Zson.entry("zip", "The zip code of the address", 62701)
		)));
		zsonMap.put("phoneNumbers",  new ZsonValue("The phone numbers of the person", Zson.object(
				Zson.entry("home", "217-555-1234"),
				Zson.entry("cell", "217-555-5678")
		)));
		String json = writer.stringify(zsonMap);
		writer.write(zsonMap, Paths.get("person.json5"));
		System.out.println(json);

		Map<String, ZsonValue> parsed = ZsonParser.parseString(json);

		System.out.println(zsonMap);
		System.out.println(parsed);
		if(zsonMap.equals(parsed)) {
			System.out.println("equal!");
		} else {
			System.out.println("not equal");
		}
	}

	private static void testWriteAllTypes() throws Throwable{
		ZsonWriter writer = new ZsonWriter()
				.withExpandArrays(false)
				.withIndent("  ")
				.withQuoteKeys(true);

		Map<String, ZsonValue> map = Zson.object();
		map.put("arr", new ZsonValue("look, an array", Zson.array(1, 2, 3)));
		map.put("obj", new ZsonValue("look, an object", Zson.object(
				Zson.entry("a", 1),
				Zson.entry("b", "2"),
				Zson.entry("c", Zson.object(
						Zson.entry("d", 3),
						Zson.entry("e", Zson.array(4, 5, 6))
				))
		)));
		map.put("str", new ZsonValue("look, a string", "hello"));
		map.put("num", new ZsonValue("look, a number", 42));
		map.put("bool", new ZsonValue("look, a boolean", true));
		map.put("nil", new ZsonValue("look, a null", null));
		map.put("inf", new ZsonValue("look, infinity", Double.POSITIVE_INFINITY));
		map.put("neginf", new ZsonValue("look, negative infinity", Double.NEGATIVE_INFINITY));
		map.put("nan", new ZsonValue("look, not a number", Double.NaN));
		String json2 = writer.stringify(map);

		System.out.println(json2);
		writer.write(map, Paths.get("test.json5"));
		ZsonParser.parseString(json2);
	}

	private static void testAllTypes() throws Throwable {
		String json = """
			{
				"arr": [1, 2, 3],
				"obj": {
					"a": 1,
					"b": "2",
					"c": {
						"d": 3,
						"e": [4, 5, 6]
					}
				},
				"str": "hello",
				"num": 42,
				"bool": true,
				"nil": null,
				"inf": Infinity,
				"neginf": -Infinity,
				"nan": NaN,
				"multiline-string": "wow look \
					a multiline string",
			}
		""";

		Map<String, ZsonValue> map = ZsonParser.parseString(json);
		System.out.println(map);
	}

	private static void testNumbers() throws Throwable {
		String json = """
			{
				"int": 42,
				"float": 3.14,
				"exp": 6.022e23,
				"neg": -1,
				"hex": 0x2A,
				"inf": Infinity,
				"w": NaN,
				"neginf": -Infinity,
			}
		""";

		Map<String, ZsonValue> map = ZsonParser.parseString(json);
		System.out.println(map);
	}
}
