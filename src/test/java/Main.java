import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonParser;
import dev.nolij.zson.ZsonWriter;
import dev.nolij.zson.ZsonValue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
	public static void main(String[] args) throws Throwable {
		ZsonWriter writer = new ZsonWriter().withExpandArrays(false).withIndent("  ").withQuoteKeys(true);
		Map<String, ZsonValue> zsonMap = new LinkedHashMap<>();
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
		writer.write(zsonMap, Files.newBufferedWriter(Paths.get("person.json5"), StandardCharsets.UTF_8));
		System.out.println(json);

		Map<String, ZsonValue> parsed = ZsonParser.parseString(json);

		System.out.println(zsonMap);
		System.out.println(parsed);
		if(zsonMap.equals(parsed)) {
			System.out.println("equal!");
		} else {
			System.out.println("not equal");
		}

		Map<String, ZsonValue> map = new LinkedHashMap<>();
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
		String json2 = writer.stringify(map);

		System.out.println(json2);
		writer.write(map, Files.newBufferedWriter(Paths.get("test.json5"), StandardCharsets.UTF_8));
//		ZsonParser.parseString(json2);
	}
}
