package dev.nolij.zson;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes Zson data to a string or file.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ZsonWriter {

	public String indent;
	public boolean expandArrays;
	public boolean quoteKeys;

	public ZsonWriter() {
		this.indent = "\t";
		this.expandArrays = false;
		this.quoteKeys = true;
	}

	/**
	 * Converts the given data to a JSON5 string.
	 * @param data The data to convert.
	 * @return The JSON5 string.
	 */
	@NotNull
	public String stringify(@NotNull Map<String, ZsonValue> data) {
		StringWriter output = new StringWriter();
		
		try {
			write(data, output);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return output.toString();
	}

	/**
	 * Writes the given data in JSON5 format to the given file.
	 * @param data The data to write.
	 * @param path The file to write to.
	 * @throws IOException If an I/O error occurs.
	 */
	@Contract(mutates = "param2")
	public void write(@NotNull Map<String, ZsonValue> data, @NotNull Path path) throws IOException {
		try (Writer output = Files.newBufferedWriter(path)) {
			write(data, output);
			output.flush();
		}
	}

	/**
	 * Writes the given data in JSON5 format to the given output.
	 * @param data The data to write.
	 * @param output The output to write to.
	 * @throws IOException If an I/O error occurs.
	 */
	@Contract(mutates = "param2")
	public void write(@NotNull Map<String, ZsonValue> data, @NotNull Appendable output) throws IOException {
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

	/**
	 * Converts the given object to a JSON5 value.
	 * @param value The value to convert.
	 * @return a JSON5-compatible string representation of the value.
	 */
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

	@Contract(value = "_ -> this", mutates = "this")
	public ZsonWriter withIndent(String indent) {
		this.indent = indent;
		return this;
	}

	@Contract(value = "_ -> this", mutates = "this")
	public ZsonWriter withExpandArrays(boolean expandArrays) {
		this.expandArrays = expandArrays;
		return this;
	}

	@Contract(value = "_ -> this", mutates = "this")
	public ZsonWriter withQuoteKeys(boolean quoteKeys) {
		this.quoteKeys = quoteKeys;
		return this;
	}
}
