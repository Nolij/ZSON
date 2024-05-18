package dev.nolij.zson;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class ZsonParser {

	/**
	 * Parses a JSON value from the contents of the given {@link Path}.
	 * If the file contains multiple JSON values, only the first one will be parsed.
	 * @param path The path to the file to parse
	 * @return see {@link #parse(Reader)}
	 */
	@Nullable
	@Contract(pure = true)
	public static <T> T parseFile(@NotNull Path path) throws IOException {
		try(var reader = Files.newBufferedReader(path)) {
			return parse(reader);
		}
	}

	/**
	 * Parses a JSON value from the given {@link String}.
	 * If the string contains multiple JSON values, only the first one will be parsed.
	 * @param serialized The JSON string to parse
	 * @return see {@link #parse(Reader)}
	 */
	@Nullable
	@Contract(pure = true)
	public static <T> T parseString(@NotNull String serialized) {
		try {
			return parse(new BufferedReader(new StringReader(serialized)));
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Parses a JSON value from the given {@link Reader}. The reader should be positioned at the start of the JSON value.
	 * If the reader contains multiple JSON values, only the first one will be parsed.
	 * @return One of:
	 * <ul>
	 *     <li>{@link Map} - for JSON objects</li>
	 *     <li>{@link List} - for JSON arrays</li>
	 *     <li>{@link String} - for JSON strings</li>
	 *     <li>{@link Number} - for JSON numbers</li>
	 *     <li>{@link Boolean} - for JSON booleans</li>
	 *     <li>{@code null} - for JSON nulls</li>
	 * </ul>
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	@Contract(mutates = "param")
	public static <T> T parse(Reader input) throws IOException {
		if(!input.markSupported()) {
			input = new BufferedReader(input);
		}

		while (true) {
			if (skipWhitespace(input) || skipComment(input))
				continue;

			int ch = input.read();
			if (ch == -1) {
				throw unexpectedEOF();
			}

			switch (ch) {
				case '{' -> {
					return (T) parseObject(input);
				}
				case '[' -> {
					return (T) parseArray(input);
				}
				case '"', '\'' -> {
					return (T) Zson.unescape(parseString(input, (char) ch));
				}
				case '-', '+', 'N', 'I',
					 '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
					return (T) parseNumber(input, (char) ch);
				}
				case 'n' -> {
					char[] chars = new char[3];
					if (input.read(chars) == 3 && chars[0] == 'u' && chars[1] == 'l' && chars[2] == 'l') {
						return null;
					} else {
						throw new IllegalArgumentException("Expected 'null', got 'n" + new String(chars) + "'");
					}
				}
				case 't', 'f' -> {
					return (T) parseBoolean(input, (char) ch);
				}
			}

			throw unexpected(ch);
		}
	}

	/**
	 * Parses a JSON object from the given {@link Reader}. The reader should be positioned at the start of the object.
	 * @param input The reader to parse the object from
	 * @return A map of the key-value pairs in the object
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param")
	private static Map<String, ZsonValue> parseObject(Reader input) throws IOException {
		var map = Zson.object();

		var comma = false;
		var colon = false;
		String key = null;

		while (true) {
			if (skipWhitespace(input) || skipComment(input))
				continue;

			input.mark(1);
			int ch = input.read();

			if (ch == '}')
				return map;

			if (comma) {
				if (ch != ',')
					throw new IllegalArgumentException("Expected comma, got " + (char) ch);

				comma = false;
				continue;
			}

			if (colon) {
				if (ch != ':')
					throw new IllegalArgumentException("Expected colon, got " + (char) ch);

				colon = false;
				continue;
			}

			if (ch == -1)
				throw unexpectedEOF();

			if (key == null) {
				key = switch (ch) {
					case '"', '\'' -> Zson.unescape(parseString(input, (char) ch));
					default -> {
						if (Character.isLetter(ch) || ch == '_' || ch == '$' || ch == '\\') {
							yield parseIdentifier(input, (char) ch);
						} else {
							throw unexpected(ch);
						}
					}
				};
				colon = true;
			} else {
				input.reset();
				Object value = parse(input);
				map.put(key, new ZsonValue(value));
				key = null;
				comma = true;
			}
		}
	}

	/**
	 * Parses a JSON array from the given {@link Reader}. The reader should be positioned at the start of the array.
	 * @param input The reader to parse the array from
	 * @return A list of the values in the array
	 */
	@Contract(mutates = "param")
	private static List<Object> parseArray(Reader input) {
		var list = new ArrayList<>();
		boolean comma = false;

		while (true) {
			try {
				if (skipWhitespace(input) || skipComment(input))
					continue;

				input.mark(1);
				int ch = input.read();
				if (ch == ']')
					return list;

				if (comma) {
					if (ch != ',')
						throw new IllegalArgumentException("Expected comma, got " + (char) ch);

					comma = false;
					continue;
				}

				if (ch == -1)
					throw unexpectedEOF();

				input.reset();
				Object value = parse(input);
				list.add(value);
				comma = true;
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	/**
	 * Parses a JSON string from the given {@link Reader}. The reader should be positioned at the start of the string.
	 * @param input The reader to parse the string from
	 * @param start The first character of the string
	 * @return The parsed string
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param1")
	private static String parseString(Reader input, char start) throws IOException {
		int escapes = 0;
		var output = new StringBuilder();
		int c;

		while ((c = input.read()) != -1) {
			if (c == start) {
				if (escapes == 0)
					return output.toString();

				output.append(Character.toChars(c));
				escapes--;
			}

			if (c == '\n') {
				if (escapes == 0)
					throw new IllegalArgumentException("Unexpected newline");

				escapes = 0;
				output.append('\n');
				continue;
			}

			if (c == '\\') {
				escapes++;
				if (escapes == 2) {
					output.append('\\');
					escapes = 0;
				}
			} else {
				if (escapes == 1) {
					output.append('\\');
				}
				output.append(Character.toChars(c));
				escapes = 0;
			}
		}
		throw unexpectedEOF();
	}

	/**
	 * Parses a JSON identifier from the given {@link Reader}. The reader should be positioned at the start of the identifier.
	 * @param input The reader to parse the identifier from
	 * @param start The first character of the identifier
	 * @return The parsed identifier
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param1")
	private static String parseIdentifier(Reader input, char start) throws IOException {
		var output = new StringBuilder();
		output.append(start);

		int c;
		input.mark(1);
		while ((c = input.read()) != -1) {
			// TODO: verify this works properly... https://262.ecma-international.org/5.1/#sec-7.6
			if (!Character.isWhitespace(c)) {
				input.mark(1);
				output.append(Character.toChars(c));
			} else {
				input.reset();
				return output.toString();
			}
		}

		throw unexpectedEOF();
	}

	/**
	 * Parses a JSON boolean from the given {@link Reader}. The reader should be positioned at the start of the boolean.
	 * @param input The reader to parse the boolean from
	 * @param start The first character of the boolean
	 * @return The parsed boolean
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param1")
	private static Boolean parseBoolean(Reader input, char start) throws IOException {
		if (start == 't') {
			char[] chars = new char[3];
			if (input.read(chars) == 3 && chars[0] == 'r' && chars[1] == 'u' && chars[2] == 'e') {
				return true;
			} else {
				throw new IllegalArgumentException("Expected 'true', got 't" + new String(chars) + "'");
			}
		} else {
			char[] chars = new char[4];
			if (input.read(chars) == 4 && chars[0] == 'a' && chars[1] == 'l' && chars[2] == 's' && chars[3] == 'e') {
				return false;
			} else {
				throw new IllegalArgumentException("Expected 'false', got 'f" + new String(chars) + "'");
			}
		}
	}

	/**
	 * Parses a JSON number from the given {@link Reader}. The number can be a floating-point number less than {@link Double#MAX_VALUE},
	 * an integer between -2<sup>{@code Integer.MAX_VALUE}</sup> and 2<sup>{@code Integer.MAX_VALUE}</sup>
	 * (will try to parse as an {@link Integer} or {@link Long} first before parsing as a {@link BigInteger}),
	 *
	 * @param input The reader to parse the number from
	 * @param start The first character of the number
	 * @return One of:
	 * <ul>
	 *     <li>{@link Integer} - for numbers that can be represented as an integer</li>
	 *     <li>{@link Long} - for numbers that can be represented as a long</li>
	 *     <li>{@link BigInteger} - for numbers that cannot be represented as an integer or long</li>
	 *     <li>{@link Double} - for floating-point numbers</li>
	 * </ul>
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param1")
	private static Number parseNumber(Reader input, char start) throws IOException {
		switch (start) {
			case '-' -> {
				Number numberValue = parseNumber(input, (char) input.read());
				return switch(numberValue) {
					case Double d -> -d;
					case Long l -> -l;
					case BigInteger b -> b.negate();
					default -> -((Integer) numberValue);
				};
			}
			case 'N' -> {
				int n = input.read();
				if (n != 'a' || (n = input.read()) != 'N') {
					throw unexpected(n);
				}

				return Double.NaN;
			}
			case 'I' -> {
				char[] chars = new char[7];
				if ((input.read(chars) != 7))
					throw unexpectedEOF();
				if (!"nfinity".equals(new String(chars))) {
					throw new IllegalArgumentException("Expected 'Infinity', got 'I" + new String(chars) + "'");
				}

				return Double.POSITIVE_INFINITY;
			}
			case '+', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
				return parseDecimal(input, start);
			}

			case '0' -> {
				input.mark(1);
				int c = input.read();
				if (c == 'x' || c == 'X') {
					StringBuilder hexValueBuilder = new StringBuilder();
					input.mark(1);
					while ((c = input.read()) != -1) {
						if (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
							input.mark(1);
							hexValueBuilder.append(Character.toChars(c));
						} else {
							input.reset();
							return Integer.parseInt(hexValueBuilder.toString(), 16);
						}
					}

					throw unexpectedEOF();
				} else {
					input.reset();
					return parseDecimal(input, '0');
				}
			}
		}

		throw unexpected(start);
	}

	/**
	 * Parses a JSON decimal number from the given {@link Reader}. The reader should be positioned at the start of the number.
	 * @param input The reader to parse the number from
	 * @param c The first character of the number
	 * @return One of:
	 * <ul>
	 *     <li>{@link Integer} - for numbers that can be represented as an integer</li>
	 *     <li>{@link Long} - for numbers that can be represented as a long</li>
	 *     <li>{@link BigInteger} - for numbers that cannot be represented as an integer or long</li>
	 *     <li>{@link Double} - for floating-point numbers</li>
	 * </ul>
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param1")
	private static Number parseDecimal(Reader input, char c) throws IOException {
		StringBuilder stringValueBuilder = new StringBuilder().append(c);

		int ch;
		input.mark(1);
		while ((ch = input.read()) != -1) {
			if (Character.isDigit(ch) || ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
				input.mark(1);
				stringValueBuilder.append(Character.toChars(ch));
			} else {
				input.reset();
				String stringValue = stringValueBuilder.toString();
				if (stringValue.contains(".") || stringValue.contains("e") || stringValue.contains("E")) {
					return Double.parseDouble(stringValue);
				}

				Number number = null;
				try {
					BigInteger bigIntValue = new BigInteger(stringValue);
					number = bigIntValue;
					number = bigIntValue.longValueExact();
					number = bigIntValue.intValueExact();
				} catch (ArithmeticException ignored) {
				}

				return number;
			}
		}

		throw unexpectedEOF();
	}

	/**
	 * Advances the reader until a non-whitespace character is found.
	 * @param input The reader to skip whitespace in
	 * @return {@code true} if any whitespace was skipped, {@code false} otherwise
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param")
	private static boolean skipWhitespace(Reader input) throws IOException {
		input.mark(1);
		int c;
		var skipped = 0;
		while ((c = input.read()) != -1) {
			if (!Character.isWhitespace(c)) {
				input.reset();

				return skipped != 0;
			}

			skipped++;
			input.mark(1);
		}

		throw unexpectedEOF();
	}

	/**
	 * Advances the reader until a non-comment character is found.
	 * @param input The reader to skip comments in
	 * @return {@code true} if any comments were skipped, {@code false} otherwise
	 * @throws IOException If an I/O error occurs
	 */
	@Contract(mutates = "param")
	private static boolean skipComment(Reader input) throws IOException {
		input.mark(2);
		int c = input.read();
		if (c == '/') {
			int c2 = input.read();
			if (c2 == '/') {
				while ((c = input.read()) != -1)
					if (c == '\n')
						break;

				return true;
			} else if (c2 == '*') {
				while ((c = input.read()) != -1)
					if (c == '*' && input.read() == '/')
						return true;

				throw unexpectedEOF();
			} else {
				input.reset();
			}
		} else {
			input.reset();
		}

		return false;
	}

	@Contract("_ -> fail")
	private static IllegalArgumentException unexpected(int ch) {
		return new IllegalArgumentException("Unexpected character: " + (char) ch);
	}

	@Contract(" -> fail")
	private static IllegalArgumentException unexpectedEOF() {
		return new IllegalArgumentException("Unexpected EOF");
	}

	private ZsonParser() {
	}
}