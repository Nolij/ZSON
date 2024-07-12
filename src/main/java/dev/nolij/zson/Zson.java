package dev.nolij.zson;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import java.nio.file.Files;
import java.nio.file.Path;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.math.BigInteger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.nolij.zson.ZsonValue.NO_COMMENT;

@SuppressWarnings({"deprecation", "UnstableApiUsage"})
public final class Zson {
	//region -------------------- Helper Methods --------------------

	/**
	 * Create a new entry with the given key, comment, and value.
	 */
	@NotNull
	@Contract("_, _, _ -> new")
	public static Map.Entry<String, ZsonValue> entry(@NotNull String key, @NotNull String comment, @Nullable Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(comment, value));
	}

	/**
	 * Create a new entry with the given key and value, and no comment.
	 */
	@NotNull
	@Contract(value = "_, _ -> new", pure = true)
	public static Map.Entry<String, ZsonValue> entry(@NotNull String key, @Nullable Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(value));
	}

	/**
	 * Create a new JSON object with the given entries.
	 * @param entries the entries to add to the object. The array must not be null, and none of the entries may be null.
	 * @return a new JSON object with the given entries.
	 */
	@NotNull
	@SafeVarargs
	@Contract("_ -> new")
	public static Map<String, ZsonValue> object(@NotNull Map.Entry<String, ZsonValue>... entries) {
		Map<String, ZsonValue> map = new LinkedHashMap<>();
		for (Map.Entry<String, ZsonValue> e : entries) {
			map.put(e.getKey(), e.getValue());
		}
		return map;
	}

	/**
	 * Create a new JSON array with the given values.
	 * @param values the values to add to the array. The array must not be null, but may contain null values.
	 * @return a new JSON array with the given values.
	 */
	@NotNull
	@Contract("_ -> new")
	public static List<?> array(@NotNull Object... values) {
		List<Object> list = new ArrayList<>();
		Collections.addAll(list, values);
		
		return list;
	}

	public static <E extends Enum<E>> void convertEnum(Map<String, ZsonValue> json, String key, Class<E> enumClass) {
		ZsonValue value = json.get(key);
		if(value == null) return;
		if(value.value instanceof String s) {
			json.put(key, new ZsonValue(value.comment, Enum.valueOf(enumClass, s)));
		} else if(!enumClass.isInstance(value.value)) {
			throw new IllegalArgumentException("Expected string, got " + value.value);
		}
	}

	/**
	 * "Un-escapes" a string by replacing escape sequences with their actual characters.
	 * @param string the string to un-escape. May be null.
	 * @return the un-escaped string, or null if the input was null.
	 */
	@Nullable
	@Contract("null -> null; !null -> !null")
	public static String unescape(@Nullable String string) {
		if (string == null || string.isEmpty())
			return string;
		
		var chars = string.toCharArray();
		var j = 0;
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			
			if (c != '\\') {
				chars[j++] = c;
				continue;
			}
			
			if (i + 1 >= chars.length)
				throw new IllegalArgumentException("Invalid escape sequence: \\EOS");
			
			char d = chars[++i];
			c = switch (d) {
				case 'b' -> '\b';
				case 'f' -> '\f';
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 's' -> ' ';
				case 't' -> '\t';
				case '\'', '\"', '\\', '\n', '\r' -> d;
				case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
					int limit = d < '4' ? 2 : 1;
					int code = d - '0';
					for (int k = 1; k < limit; k++) {
						char e = chars[i + 1];
						if (e >= '0' && e <= '9') {
							code = code * 10 + e - '0';
							i++;
						}
					}
					yield (char) code;
				}
				case 'u' -> {
					String hex = new String(chars, i, 4);
					if (hex.length() != 4) {
						throw new IllegalArgumentException("Invalid unicode escape: " + hex + ", expected 4 characters, found EOS");
					}
					i += 4;
					yield (char) Integer.parseInt(hex, 16);
				}
				default -> throw new IllegalArgumentException(String.format("Invalid escape sequence: \\%c \\\\u%04X", d, (int) d));
			};

			chars[j++] = c;
		}
		return new String(chars, 0, j);
	}

	/**
	 * Escapes a string by replacing special characters with escape sequences.
	 * The following characters are escaped:
	 * <ul>
	 *     <li>Control characters: {@code \0}, {@code \t}, {@code \b}, {@code \n}, {@code \r}, {@code \f}</li>
	 *     <li>Quotes: {@code \'} and {@code \"}</li>
	 *     <li>Backslash: {@code \\}</li>
	 *     <li>Surrogate, private use, and unassigned characters</li>
	 * </ul>
	 * @param string the string to escape. May be null.
	 * @param escapeQuotes the character to escape quotes with.
	 * @return the escaped string, or null if the input was null.
	 */
	@Nullable
	@Contract("null, _ -> null; !null, _ -> !null")
	public static String escape(@Nullable String string, char escapeQuotes) {
		if (string == null || string.isEmpty())
			return string;

		final StringBuilder result = new StringBuilder(string.length());
		for (int i = 0; i < string.length(); i++) {
			final char c = string.charAt(i);
			switch (c) {
				case '\0' -> result.append("\\0");
				case '\t' -> result.append("\\t");
				case '\b' -> result.append("\\b");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\f' -> result.append("\\f");
				case '\'', '"' -> {
					if (escapeQuotes == c) {
						result.append('\\');
					}
					result.append(c);
				}
				case '\\' -> result.append("\\\\");
				default -> {
					final int type = Character.getType(c);
					if (type != Character.UNASSIGNED && type != Character.CONTROL && type != Character.SURROGATE) {
						result.append(c);
					} else if (c < 0x10) {
						result.append("\\x0").append(Character.forDigit(c, 16));
					} else {
						final String hex = Integer.toHexString(c);
						if (c < 0x100) {
							result.append("\\x").append(hex);
						} else if (c < 0x1000) {
							result.append("\\u0").append(hex);
						} else {
							result.append("\\u").append(hex);
						}
					}
				}
			}
		}
		
		return result.toString();
	}

	/**
	 * Converts the given object to a JSON map. Fields of the object will be serialized in order of declaration.
	 * Fields will not be included in the map if:
	 * <ul>
	 *     <li>They are static and not annotated with {@link ZsonField @ZsonField(include = true)}
	 *     <li>They are transient</li>
	 *     <li>They are not public (AKA private, protected, or package-private) and not annotated with {@link ZsonField @ZsonField(include = true)}</li>
	 *     <li>They are annotated with {@link ZsonField @ZsonField(exclude = true)}</li>
	 * </ul>
	 *
	 * Additionally, fields annotated with {@link ZsonField @ZsonField(comment = "...")} will have their comments included in the map.
	 * @param object the object to serialize. If null, an empty object will be returned.
	 * @return a JSON map representing the object.
	 */
	@NotNull
	@Contract("_ -> new")
	public static Map<String, ZsonValue> obj2Map(@Nullable Object object) {
		if(object == null) return object();
		Map<String, ZsonValue> map = object();
		for (Field field : object.getClass().getDeclaredFields()) {
			if(!shouldInclude(field, true)) continue;
			ZsonField value = field.getAnnotation(ZsonField.class);
			String comment = value == null ? NO_COMMENT : value.comment();
			try {
				boolean accessible = field.isAccessible();
				if (!accessible) field.setAccessible(true);
				map.put(field.getName(), new ZsonValue(comment, field.get(object)));
				if (!accessible) field.setAccessible(false);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Failed to get field " + field.getName(), e);
			}
		}
		return map;
	}

	/**
	 * Converts the given map to an object of the given type. The map must contain all fields of the object, but they
	 * may be in any order. Fields will be set in order of declaration.
	 * @param map the map to deserialize. Must not be null.
	 * @param type the type of object to create. Must not be null, and must have a no-args constructor.
	 * @return a new object of the given type with fields set from the map.
	 * @param <T> the type of object to create.
	 */
	@NotNull
	@Contract("_ , _ -> new")
	public static <T> T map2Obj(@NotNull Map<String, ZsonValue> map, @NotNull Class<T> type) {
		try {
			T object = type.getDeclaredConstructor().newInstance();
			for (Field field : type.getDeclaredFields()) {
				if(!shouldInclude(field, false)) continue;
				if(!map.containsKey(field.getName())) {
					continue;
				}
				setField(field, object, map.get(field.getName()).value);
			}
			return object;
		} catch (Exception e) {
			throw new RuntimeException("Failed to create object of type " + type.getSimpleName(), e);
		}
	}

	/**
	 * Checks if the given field should be included in a JSON map.
	 * @param field the field to check.
	 * @param forDeserialization if true, the field is being checked for deserialization. If false, it's being checked for serialization.
	 *                           This affects whether static fields are included: if true,
	 *                           static fields are included if they are annotated with {@link ZsonField @ZsonField(include = true)};
	 *                           otherwise they are not included at all.
	 * @return true if the field should be included in a JSON map, false otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private static boolean shouldInclude(Field field, boolean forDeserialization) {
		ZsonField value = field.getAnnotation(ZsonField.class);

		int modifiers = field.getModifiers();
		if(Modifier.isTransient(modifiers)) return false; // ignore transient fields
		boolean defaultInclude = (forDeserialization || !Modifier.isStatic(modifiers)) && Modifier.isPublic(modifiers);

		if(value == null) return defaultInclude;
		if (value.exclude()) return false; // if field is explicitly excluded, ignore it
		return defaultInclude || (forDeserialization && value.include());
	}

	/**
	 * Sets the value of the given field in the given object to the given value, attempting to cast it to the field's type.
	 * @param field the field to set. Must not be null.
	 * @param object the object to set the field in. Must not be null.
	 * @param value the value to set the field to. May be null. If primitive, will be an int or double.
	 * @param <T> the type of the field.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> void setField(Field field, Object object, Object value) {
		Class<T> type = (Class<T>) field.getType();
		boolean accessible = field.isAccessible();
		if(!accessible) field.setAccessible(true);
		try {
			if(type.isPrimitive()) {
				switch (type.getName()) {
					case "boolean" -> field.setBoolean(object, (boolean) value);
					case "short" -> field.setShort(object, ((Number) value).shortValue());
					case "int" -> field.setInt(object, ((Number) value).intValue());
					case "float" -> field.setFloat(object, ((Number) value).floatValue());
					case "double" -> field.setDouble(object, ((Number) value).doubleValue());
					case "long" -> field.setLong(object, ((Number) value).longValue());
					case "byte" -> field.setByte(object, ((Number) value).byteValue());
					case "char" -> field.setChar(object, (char) value);
				}
			} else {
				Object finalValue = value;
				if (type.isEnum() && value instanceof String) {
					finalValue = Enum.valueOf((Class<Enum>) type, (String) value);
				}
				field.set(object, finalValue);
			}
		} catch (Exception e) {
			throw new AssertionError(
				"Failed to set field " + field.getName() + " (type " + type.getSimpleName() + ") to " + value + " " +
					"(type " + value.getClass().getSimpleName() + ")", e
			);
		} finally {
			if(!accessible) field.setAccessible(false);
		}
	}

	//endregion

	//region -------------------- Parser --------------------
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
	public static <T> T parseString(@NotNull @Language("json5") String serialized) {
		try {
			return parse(new StringReader(serialized));
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
					return (T) unescape(parseString(input, (char) ch));
				}
				case '.', '-', '+', 'N', 'I',
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
		var map = object();

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
					case '"', '\'' -> unescape(parseString(input, (char) ch));
					default -> {
						if (Character.isJavaIdentifierStart(ch) || ch == '\\') {
							yield parseIdentifier(input, ch);
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
					output.append("\\\\");
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
	// TODO: handle multi-character escapes
	@Contract(mutates = "param1")
	private static String parseIdentifier(Reader input, int start) throws IOException {
		var output = new StringBuilder();
		boolean escaped = start == '\\';

		if(!escaped)
			output.append((char) start);

		int c;
		input.mark(1);
		while ((c = input.read()) != -1) {
			if(escaped) {
				if(c == 'n' || c == 'r') {
					throw unexpected(c);
				}
				output.append(unescape("\\" + (char) c));
				input.mark(1);
				escaped = false;
			} else if (c == '\\') {
				input.mark(1);
				escaped = true;
			} else if (isIdentifierChar(c)) {
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
	 * Checks if the given character is a valid EMCAScript <i>IdentifierName</i> character.
	 * This is true if the character is an underscore ({@code _}), a dollar sign ({@code $}),
	 * or a character in one of the following Unicode categories:
	 * <ul>
	 *     <li>Uppercase letter (Lu)</li>
	 *     <li>Lowercase letter (Ll)</li>
	 *     <li>Titlecase letter (Lt)</li>
	 *     <li>Modifier letter (Lm)</li>
	 *     <li>Other letter (Lo)</li>
	 *     <li>Letter Number (Nl)</li>
	 *     <li>Non-spacing mark (Mn)</li>
	 *     <li>Combining spacing mark (Mc)</li>
	 *     <li>Decimal number (Nd)</li>
	 *     <li>Connector punctuation (Pc)</li>
	 * </ul>
	 * @param c the code point to check
	 * @return {@code true} if the character is a valid identifier character, {@code false} otherwise
	 * @see <a href="https://262.ecma-international.org/5.1/#sec-7.6">ECMAScript 5.1 §7.6</a>
	 */
	private static boolean isIdentifierChar(int c) {
		if(c == '_' || c == '$') return true;
		int type = Character.getType(c);
		return type == Character.UPPERCASE_LETTER || type == Character.LOWERCASE_LETTER || type == Character.TITLECASE_LETTER ||
			type == Character.MODIFIER_LETTER || type == Character.OTHER_LETTER || type == Character.LETTER_NUMBER ||
			type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.DECIMAL_DIGIT_NUMBER ||
			type == Character.CONNECTOR_PUNCTUATION;
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
	 * or a special value (NaN, Infinity, -Infinity).
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

			case '.' -> {
				return parseDecimal(input, '.');
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
			if (!isWhitespace(c) && !isLineTerminator(c)) {
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
				while ((c = input.read()) != -1 && c != '\n');

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

	/**
	 * @see <a href="https://262.ecma-international.org/5.1/#sec-7.2">ECMAScript 5.1 §7.2</a>
	 */
	private static boolean isWhitespace(int c) {
		return c == '\t' || c == '\n' || c == '\f' || c == '\r' || c == ' '
				|| c == 0x00A0 || c == 0xFEFF || Character.getType(c) == Character.SPACE_SEPARATOR;
	}

	/**
	 * @see <a href="https://262.ecma-international.org/5.1/#sec-7.3">ECMAScript 5.1 §7.3</a>

	 */
	private static boolean isLineTerminator(int c) {
		return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029';
	}
	//endregion

	//region -------------------- Writer --------------------
	public String indent;
	public boolean expandArrays; // whether to put each array element on its own line
	public boolean quoteKeys;

	public Zson() {
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
			ZsonValue zv = entry.getValue();
			String comment = zv.comment;

			if (!NO_COMMENT.equals(comment)) {
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

			output.append(checkIdentifier(entry.getKey()));
			if (quoteKeys)
				output.append('"');

			output.append(": ").append(value(zv.value)).append(",\n");
		}

		output.append("}");
	}

	/**
	 * Checks if the given string is a valid identifier.
	 * @param key The string to check.
	 * @return {@code true} if the string is a valid identifier, {@code false} otherwise.
	 * @see #isIdentifierChar(int)
	 * @see <a href="https://262.ecma-international.org/5.1/#sec-7.6">ECMAScript 5.1 §7.6</a>
	 */
	private String checkIdentifier(String key) {
		if(key == null || key.isEmpty()) throw new IllegalArgumentException("Key cannot be null or empty");
		int c = key.charAt(0);
		if(!Character.isJavaIdentifierStart(c) && c != '\\') throw new IllegalArgumentException("Key must start with a valid identifier character: " + key.charAt(0));
		for (int i = 1; i < key.length(); i++) {
			if(!isIdentifierChar(key.charAt(i))) throw new IllegalArgumentException("Key must be a valid Java identifier: " + key);
		}

		return key;
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
			return '"' + escape(stringValue, '"') + '"';
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
		} else if(value instanceof Enum<?> enumValue) {
			return '"' + enumValue.name() + '"';
		}

		throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
	}

	@Contract(value = "_ -> this", mutates = "this")
	public Zson withIndent(String indent) {
		for(char c : indent.toCharArray()) {
			if(!isWhitespace(c)) {
				throw new IllegalArgumentException("Indent '" + indent + "' must be a whitespace string");
			}
		}
		this.indent = indent;
		return this;
	}

	@Contract(value = "_ -> this", mutates = "this")
	public Zson withExpandArrays(boolean expandArrays) {
		this.expandArrays = expandArrays;
		return this;
	}

	@Contract(value = "_ -> this", mutates = "this")
	public Zson withQuoteKeys(boolean quoteKeys) {
		this.quoteKeys = quoteKeys;
		return this;
	}
	//endregion
}
