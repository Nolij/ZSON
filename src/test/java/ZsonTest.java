import org.junit.jupiter.api.Test;

import dev.nolij.zson.ZsonField;
import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static dev.nolij.zson.Zson.*;

@SuppressWarnings({"unused", "DataFlowIssue", "FieldMayBeFinal"})
public class ZsonTest {

	@Test
	public void makeSureTestsRun() {
		System.out.println("Tests are running!");
	}

	@Test
	public void json5Spec() throws IOException {

		Map<String, ZsonValue> map = parseFile(Path.of("spec.json5"));

		assertEquals(map, object(
			entry("unquoted", "and you can quote me on that"),
			entry("singleQuotes", "I can use \"double quotes\" here"),
			entry("lineBreaks", "Look, Mom! No \\n's!"),
			entry("hexadecimal", 0xdecaf),
			entry("leadingDecimalPoint", .8675309),
			entry("andTrailing", 8675309.),
			entry("positiveSign", +1),
			entry("trailingComma", "in objects"),
			entry("andIn", array("arrays")),
			entry("backwardsCompatible", "with JSON")
		));
	}

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
		    "zip": 62701
		  },
		  // The phone numbers of the person
		  "phoneNumbers": {
		    "home": "217-555-1234",
		    "cell": "217-555-5678"
		  }
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
		assertEquals("wow look\ta multiline string", map.get("multiline-string").value);
	}

	@Test
	public void testNumbers() {
		String json = """
		{
			"int": 42,
			"float": 3.14,
			"exp": 6.022E23,
			"neg": -1,
			"hex": 0x2A,
			"inf": Infinity,
			"w": NaN,
			java: 0XcAfeBabE,
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
		assertEquals(0xcAfeBabEL, map.get("java").value); // the extra L is because it's a long
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
			"java": 3405691582,
			"neginf": -Infinity
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
			"testEnum": "ONE"
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

	@Test
	public void testFunkyFormatting() {
		String json = """
		{"hmm": true,
											"constant":false,a:   "seven"}""";

		assertEquals(parseString(json), object(
			entry("hmm", true),
			entry("constant", false),
			entry("a", "seven")
		));

		json = """
				{
			unquoted: "key",
			"num": 2.2,
		\\bee\\f: "yum" // comment
		 }""";

		assertEquals(parseString(json), object(
			entry("unquoted", "key"),
			entry("num", 2.2),
			entry("\bee\f", "yum")
		));

		json = "{a:1,b:2,c:[3,4,5],d:{e:6,f:7}}";
		assertEquals(parseString(json), object(
			entry("a", 1),
			entry("b", 2),
			entry("c", array(3, 4, 5)),
			entry("d", object(
				entry("e", 6),
				entry("f", 7)
			))
		));

		json = "{/*comment *//*comment */ a  :/*comment */1/*comment */, b/*comment */ : 2 }//comment";

		assertEquals(parseString(json), object(
			entry("a", 1),
			entry("b", 2)
		));

		json = "/**/[/**/1/**/,\"str\"  ,/**/\t7]";
		assertEquals(parseString(json), array(1, "str", 7));

		assertThrows(IllegalArgumentException.class, () -> new Zson().stringify(object(
			entry(null, 1)
		)));
	}

	@Test
	public void testUnquotedKeys() {
		TestObject obj = new TestObject();
		obj.testEnum = TestEnum.TWO;
		Map<String, ZsonValue> json = obj2Map(obj);
		String expected = """
		{
			// look a comment
			wow: 42,
			such: "amaze",
			very: true,
			constant: "wow",
			testEnum: "TWO"
		}""";

		String actual = new Zson().withQuoteKeys(false).stringify(json);

		assertEquals(expected, actual);

		Map<String, ZsonValue> parsed = parseString(actual);
		convertEnum(parsed, "testEnum", TestEnum.class);
		assertEquals(json, parsed);

		TestObject obj2 = map2Obj(parsed, TestObject.class);

		assertEquals(obj, obj2);
	}

	@Test
	public void newlinesInStrings() throws IOException {
		Map<String, ZsonValue> map = parseFile(Path.of("multiline.json5")); // kept in a separate file because the newlines are weird

		assertEquals("newline", map.get("cr").value);
		assertEquals("newline", map.get("lf").value);
		assertEquals("newline", map.get("crlf").value);
		assertEquals("newline", map.get("u2028").value);
		assertEquals("newline", map.get("u2029").value);
		assertEquals("new\nline", map.get("escaped").value);
	}

	@Test
	public void otherRandomStuff() {
		Map<String, ZsonValue> map = parseString("""
		{
			weirdEscapes: "\\A\\C\\/\\D\\C",
			
			// contains all "valid" whitespace characters
			whitespace:\u0009\u000a\u000b\u000c\u000d\u0020\u00a0\u2028\u2029\ufeff ""
		}
		"""); // weirdEscapes is actually \A\C/\D\C but we need to escape the backslashes for java

		assertEquals("AC/DC", map.get("weirdEscapes").value);
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

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof TestObject other)) return false;
			return wow == other.wow
				   && such.equals(other.such)
				   && very == other.very
				   && pi == other.pi
				   && testEnum == other.testEnum;
		}
	}

	@Test
	public void testObjectFields() {
		Map<String, ZsonValue> json = Zson.obj2Map(new ObjectFields());
		String expected = """
		{
			"a": 0,
			"set": [ "a", "b", "c" ],
			"b": {
				"bool": false,
				"b": 0,
				"s": 0,
				"i": 0,
				"l": 0,
				"f": 0.0,
				"d": 0.0,
				"c": "\\0",
				"str": null,
				"e": null
			},
			"c": "ONE"
		}""";

		String actual = new Zson().stringify(json);

		assertEquals(expected, actual);

		json = Zson.parseString(actual);

		ObjectFields obj = Zson.map2Obj(json, ObjectFields.class);
		assertEquals(0, obj.a);
		assertEquals(0, obj.b.i);
		assertEquals(TestEnum.ONE, obj.c);
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

	public static class ObjectFields {
		public int a;
		public Set<String> set = new HashSet<>();
		public AllTypes b = new AllTypes();
		public TestEnum c = TestEnum.ONE;

		{
			set.add("a");
			set.add("b");
			set.add("c");
		}
	}

	public enum TestEnum {
		ONE, TWO, THREE
	}
}
