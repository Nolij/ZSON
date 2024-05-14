package dev.nolij.zson;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ZsonWriter {

	public String indent = "\t";
	public boolean expandArrays = false;
	public boolean quoteKeys = true;

	public String stringify(Map<String, ZsonValue> data) {
		StringWriter output = new StringWriter();
		
		try {
			write(data, output);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return output.toString();
	}

	public void write(Map<String, ZsonValue> data, Path path) throws IOException {
		try (Writer output = Files.newBufferedWriter(path)) {
			write(data, output);
			output.flush();
		}
	}
	
	public void write(Map<String, ZsonValue> data, Appendable output) throws IOException {
		output.append("{\n");
		
		for (var entry : data.entrySet()) {
			String key = entry.getKey();
			ZsonValue zv = entry.getValue();
			String comment = zv.comment;
			
			if (comment != null) {
				for (String line : comment.split("\n")) {
					output
						.append(indent)
						.append("// ")
						.append(line)
						.append("\n");
				}
			}
			
			output.append(indent);
			if (quoteKeys)
				output.append('"');
			
			output.append(key);
			if (quoteKeys)
				output.append('"');
			
			output.append(": ").append(value(zv.value)).append(",\n");
		}
		
		output.append("}");
	}
	
	private String value(Object value) {
		if (value instanceof Map<?, ?>) {
			try {
				//noinspection unchecked
				return stringify((Map<String, ZsonValue>) value).replace("\n", "\n" + indent);
			} catch (ClassCastException e) {
				if(e.getMessage().contains("cannot be cast to")) {
					// TODO: better error message (currently just prints "got path.to.MapClass" without type parameters)
					throw new ClassCastException("expected Map<String, ZsonValue>, got " + value.getClass().getName());
				} else {
					throw e;
				}
			} catch (StackOverflowError e) {
				// rethrow but without the recursive cause
				throw new StackOverflowError("Map is circular");
			}
		} else if (value instanceof String stringValue) {
			return '"' + Zson.escape(stringValue, '"') + '"';
		} else if (value instanceof Number || value instanceof Boolean || value == null) {
			return String.valueOf(value);
		} else if (value instanceof Iterable<?> iterableValue) {
			StringBuilder output = new StringBuilder("[");
			output.append(expandArrays ? "\n" : " ");
			
			for (Object obj : iterableValue) {
				if (expandArrays)
					output.append(indent).append(indent);
				output.append(value(obj).replace("\n", "\n" + indent + indent))
						.append(",")
						.append(expandArrays ? "\n" : " ");
			}
			
			if (expandArrays)
				output.append(indent);
			
			return output.append("]").toString();
		}
		
		throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
	}

	public ZsonWriter withIndent(String indent) {
		this.indent = indent;
		return this;
	}

	public ZsonWriter withExpandArrays(boolean expandArrays) {
		this.expandArrays = expandArrays;
		return this;
	}

	public ZsonWriter withQuoteKeys(boolean quoteKeys) {
		this.quoteKeys = quoteKeys;
		return this;
	}
	
}
