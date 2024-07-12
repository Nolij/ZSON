import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.util.Map;

import static dev.nolij.zson.Zson.*;

public final class NoJUnitTest {
	public static void main(String[] args) {
		Zson writer = new Zson()
				.withExpandArrays(false)
				.withIndent("  ")
				.withQuoteKeys(true);

		Map<String, ZsonValue> zsonMap = object();
		zsonMap.put("name", new ZsonValue("The name of the person\nlook, a second line!", "John Doe"));
		zsonMap.put("age", new ZsonValue("The age of the person", 30));
		zsonMap.put("address",  new ZsonValue("The address of the person", object(
				entry("street", "The street of the address", "123 Main St"),
				entry("city", "The city of the address", "Springfield"),
				entry("state", "The state of the address", "IL"),
				entry("zip", "The zip code of the address", 62701)
		)));
		zsonMap.put("phoneNumbers",  new ZsonValue("The phone numbers of the person", object(
				entry("home", "217-555-1234"),
				entry("cell", "217-555-5678")
		)));
		String json = writer.stringify(zsonMap);

		String expected = """
		{
		  // The name of the person
		  // look, a second line!
		  "name": "John Doe",
		  // The age of the person
		  "age": 30,
		  // The address of the person
		  "address": {
		    // The street of the address
		    "street": "123 Main St",
		    // The city of the address
		    "city": "Springfield",
		    // The state of the address
		    "state": "IL",
		    // The zip code of the address
		    "zip": 62701,
		  },
		  // The phone numbers of the person
		  "phoneNumbers": {
		    "home": "217-555-1234",
		    "cell": "217-555-5678",
		  },
		}""";

		assert expected.equals(json);

		Map<String, ZsonValue> parsed = parseString(json);

		assert zsonMap.equals(parsed);
	}
}
