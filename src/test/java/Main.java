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
		Map<String, ZsonValue> zsonMap = new LinkedHashMap<>();
		zsonMap.put("name", new ZsonValue("The name of the person\nlook, a second line!", "John Doe"));
		zsonMap.put("age", new ZsonValue("The age of the person", 30));
		zsonMap.put("address",  new ZsonValue("The address of the person", ZsonWriter.object(
			ZsonWriter.entry("street", "The street of the address", "123 Main St"),
			ZsonWriter.entry("city", "The city of the address", "Springfield"),
			ZsonWriter.entry("state", "The state of the address", "IL"),
			ZsonWriter.entry("zip", "The zip code of the address", 62701)
		)));
		zsonMap.put("phoneNumbers",  new ZsonValue("The phone numbers of the person", ZsonWriter.object(
			ZsonWriter.entry("home", "217-555-1234"),
			ZsonWriter.entry("cell", "217-555-5678")
		)));
		String json = ZsonWriter.stringify(zsonMap, "  ");
		ZsonWriter.write(zsonMap, Files.newBufferedWriter(Paths.get("person.json5"), StandardCharsets.UTF_8), "  ");
		System.out.println(json);

		Map<String, ZsonValue> parsed = (Map<String, ZsonValue>) ZsonParser.parseString(json);

		System.out.println(zsonMap);
		System.out.println(parsed);
		if(!zsonMap.equals(parsed)) {
			System.out.println("not equal");
		} else {
			System.out.println("equal!");
		}
		
//		Map<String, ZsonValue> map2 = new LinkedHashMap<>();
//		Map<String, ZsonValue> map3 = new LinkedHashMap<>();
//		map3.put("test", new ZsonValue("a", map2));
//		map2.put("test", new ZsonValue("b", map3));
//		System.out.println(zson.stringify(map2));
	}
}
