package dev.nolij.zson;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ZsonWriter {

	public static String stringify(Map<String, ZsonValue> zson, String indent) {
		StringWriter writer = new StringWriter();
		try {
			write(zson, writer, indent);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return writer.toString();
	}
	
	public static void write(Map<String, ZsonValue> zson, Writer writer, String indent) throws IOException {
		writer.write("{\n");
		
		for (var entry : zson.entrySet()) {
			String key = entry.getKey();
			ZsonValue zv = entry.getValue();
			String comment = zv.comment;
			
			if (comment != null) {
				for (String line : comment.split("\n")) {
					writer.write(indent);
					writer.write("// ");
					writer.write(line);
					writer.write("\n");
				}
			}
			
			writer.write(indent);
			writer.write('"');
			writer.write(key);
			writer.write("\": ");
			writer.write(value(zv.value, indent));
			writer.write(",\n");
		}
		
		writer.write("}");
		writer.flush();
	}
	
	private static String value(Object value, String indent) {
		if(value instanceof Map) {
			try {
				//noinspection unchecked
				return stringify((Map<String, ZsonValue>) value, indent).replace("\n", "\n" + indent);
			} catch (StackOverflowError e) {
				throw new StackOverflowError("Map is circular");
			}
		} else if(value instanceof String s) {
			return '"' + Strings.escape(s, '"') + '"';
		} else if(value instanceof Number || value instanceof Boolean || value == null) {
			return String.valueOf(value);
		} else {
			throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
		}
	}
	
	public static Map.Entry<String, ZsonValue> entry(String key, String comment, Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(comment, value));
	}
	
	public static Map.Entry<String, ZsonValue> entry(String key, Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(value));
	}
	
	@SafeVarargs
	public static Map<String, ZsonValue> object(Map.Entry<String, ZsonValue>... entries) {
		Map<String, ZsonValue> map = new LinkedHashMap<>();
		for(var e : entries) {
			map.put(e.getKey(), e.getValue());
		}
		return map;
	}
}
