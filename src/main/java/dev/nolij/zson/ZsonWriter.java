package dev.nolij.zson;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public final class ZsonWriter {

	public String indent = "  ";
	public boolean expandArrays = false;
	public boolean quoteKeys = true;

	public String stringify(Map<String, ZsonValue> zson) {
		StringWriter writer = new StringWriter();
		try {
			write(zson, writer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return writer.toString();
	}
	
	public void write(Map<String, ZsonValue> zson, Writer writer) throws IOException {
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
			if(quoteKeys)
				writer.write('"');
			writer.write(key);
			if(quoteKeys)
				writer.write('"');
			writer.write(": ");
			writer.write(value(zv.value));
			writer.write(",\n");
		}
		
		writer.write("}");
		writer.flush();
	}
	
	private String value(Object value) {
		if(value instanceof Map<?, ?>) {
			try {
				//noinspection unchecked
				return stringify((Map<String, ZsonValue>) value).replace("\n", "\n" + indent);
			} catch (ClassCastException e) {
				if(e.getMessage().contains("cannot be cast to")) {
					//TODO: better error message (currently just prints "got path.to.MapClass" without type parameters)
					throw new ClassCastException("expected Map<String, ZsonValue>, got " + value.getClass().getName());
				} else {
					throw e;
				}
			} catch (StackOverflowError e) {
				// rethrow but without the recursive cause
				throw new StackOverflowError("Map is circular");
			}
		} else if(value instanceof String s) {
			return '"' + Zson.escape(s, '"') + '"';
		} else if(value instanceof Number || value instanceof Boolean || value == null) {
			return String.valueOf(value);
		} else if(value instanceof Iterable<?> l) {
			StringBuilder sb = new StringBuilder("[");
			sb.append(expandArrays ? "\n" : " ");
			for (Object o : l) {
				if(expandArrays)
					sb.append(indent).append(indent);
				sb.append(value(o).replace("\n", "\n" + indent + indent))
						.append(",")
						.append(expandArrays ? "\n" : " ");
			}
			if(expandArrays)
				sb.append(indent);
			return sb.append("]").toString();
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
