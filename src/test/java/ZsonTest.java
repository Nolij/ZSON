import org.junit.jupiter.api.Test;

import dev.nolij.zson.ZsonField;
import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static dev.nolij.zson.Zson.*;

@SuppressWarnings({"unused", "DataFlowIssue", "FieldMayBeFinal"})
public class ZsonTest {
	@Test
	public void testReadWrite() {
		Zson writer = new Zson()
				.withExpandArrays(false)
				.withIndent("  ")
				.withQuoteKeys(true);

		Map<String, ZsonValue> zsonMap = Zson.object();
		zsonMap.put("name", new ZsonValue("The name of the person\nlook, a second line!", "John Doe"));
		zsonMap.put("age", new ZsonValue("The age of the person", 30));
		zsonMap.put("address",  new ZsonValue("The address of the person", Zson.object(
				entry("street", "The street of the address", "123 Main St"),
				entry("city", "The city of the address", "Springfield"),
				entry("state", "The state of the address", "IL"),
				entry("zip", "The zip code of the address", 62701)
		)));
		zsonMap.put("phoneNumbers",  new ZsonValue("The phone numbers of the person", Zson.object(
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

		assertEquals(expected, json);

		Map<String, ZsonValue> parsed = Zson.parseString(json);

		assertEquals(zsonMap, parsed);
	}

	@Test
	public void testInvalidRead() {
		assertThrows(IllegalArgumentException.class, () -> Zson.parseString("wow look such invalid"));
	}

	@Test
	public void testRead() {
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
			"multiline-string": "wow look\\
			a multiline string",
		}
		""";

		Map<String, ZsonValue> map = Zson.parseString(json);

		@SuppressWarnings("unchecked")
		List<Object> arr = (List<Object>) map.get("arr").value;
		assertEquals(1, arr.get(0));
		assertEquals(2, arr.get(1));
		assertEquals(3, arr.get(2));

		Map<String, ZsonValue> obj = object(
			entry("a", 1),
			entry("b", "2"),
			entry("c", object(
				entry("d", 3),
				entry("e", array(4, 5, 6))
			))
		);
		assertEquals(obj, map.get("obj").value);

		assertEquals("hello", map.get("str").value);
		assertEquals(42, map.get("num").value);
		assertEquals(true, map.get("bool").value);
		assertNull(map.get("nil").value);
		assertEquals(Double.POSITIVE_INFINITY, map.get("inf").value);
		assertEquals(Double.NEGATIVE_INFINITY, map.get("neginf").value);
		assertTrue(Double.isNaN((double) map.get("nan").value));
		assertEquals("wow look\n\ta multiline string", map.get("multiline-string").value);
	}

	@Test
	public void testNumbers() {
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
		}""";

		Map<String, ZsonValue> map = Zson.parseString(json);

		assertEquals(42, map.get("int").value);
		assertEquals(3.14, map.get("float").value);
		assertEquals(6.022e23, map.get("exp").value);
		assertEquals(-1, map.get("neg").value);
		assertEquals(42, map.get("hex").value);
		assertEquals(Double.POSITIVE_INFINITY, map.get("inf").value);
		assertTrue(Double.isNaN((Double) map.get("w").value));
		assertEquals(Double.NEGATIVE_INFINITY, map.get("neginf").value);

		assertEquals("""
		{
			"int": 42,
			"float": 3.14,
			"exp": 6.022E23,
			"neg": -1,
			"hex": 42,
			"inf": Infinity,
			"w": NaN,
			"neginf": -Infinity,
		}""", new Zson().stringify(map));
	}

	@Test
	public void testObject() {
		Map<String, ZsonValue> json = Zson.obj2Map(new TestObject());
		String expected = """
		{
			// look a comment
			"wow": 42,
			"such": "amaze",
			"very": true,
			"constant": "wow",
			"testEnum": "ONE",
		}""";

		String actual = new Zson().stringify(json);

		assertEquals(expected, actual);

		json = Zson.parseString(actual);

		TestObject obj = Zson.map2Obj(json, TestObject.class);
		assertEquals(42, obj.wow);
		assertEquals("amaze", obj.such);
		assertTrue(obj.very);
		assertEquals(3.14, obj.pi);
		assertEquals(TestEnum.ONE, obj.testEnum);
	}

	@Test
	public void testNonexistentFieldInMap() {
		Map<String, ZsonValue> json = Map.of("such", new ZsonValue("working"));
		TestObject obj = Zson.map2Obj(json, TestObject.class);
		assertEquals("working", obj.such);
		assertEquals(42, obj.wow);
	}

	@Test
	public void testPrimitiveConversion() {
		Map<String, ZsonValue> json = Zson.parseString("""
		{
			"f": 1,
			"d": 2,
		}""");
		assertEquals(1, json.get("f").value);
		assertEquals(2, json.get("d").value);
		AllTypes obj = Zson.map2Obj(json, AllTypes.class);
		assertEquals(1, obj.f);
		assertEquals(2, obj.d);
	}

	public static class TestObject {
		@ZsonField(comment = "look a comment")
		public int wow = 42;
		public String such = "amaze";

		@ZsonField(include = true)
		private boolean very = true;

		@ZsonField(exclude = true)
		public double pi = 3.14;

		@ZsonField(include = true)
		public static final String constant = "wow";

		public TestEnum testEnum = TestEnum.ONE;
	}

	public static class AllTypes {
		public boolean bool;
		public byte b;
		public short s;
		public int i;
		public long l;
		public float f;
		public double d;
		public char c;
		public String str;
		public TestEnum e;
	}

	public enum TestEnum {
		ONE, TWO, THREE
	}
}
