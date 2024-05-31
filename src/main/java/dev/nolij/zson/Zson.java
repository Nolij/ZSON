package dev.nolij.zson;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;

/**
 * Static utility methods for working with JSON-like data structures.
 */
@SuppressWarnings("deprecation")
public final class Zson {

	/**
	 * Create a new entry with the given key, comment, and value.
	 */
	@NotNull
	@Contract("_, _, _ -> new")
	public static Map.Entry<String, ZsonValue> entry(@Nullable String key, @Nullable String comment, @Nullable Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(comment, value));
	}

	/**
	 * Create a new entry with the given key and value. The comment will be null.
	 */
	@NotNull
	@Contract(value = "_, _ -> new", pure = true)
	public static Map.Entry<String, ZsonValue> entry(@Nullable String key, @Nullable Object value) {
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
		for (Entry<String, ZsonValue> e : entries) {
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
	 *     <li>They are static and not annotated with {@link Include @Include}</li>
	 *     <li>They are transient</li>
	 *     <li>They are not public (AKA private, protected, or package-private) and not annotated with {@link Include @Include}</li>
	 *     <li>They are annotated with {@link Exclude @Exclude}</li>
	 * </ul>
	 *
	 * Additionally, fields annotated with {@link Comment @Comment} will have their comments included in the map.
	 * @param object the object to serialize. If null, an empty object will be returned.
	 * @return a JSON map representing the object.
	 */
	@NotNull
	@Contract("_ -> new")
	public static Map<String, ZsonValue> obj2Map(@Nullable Object object) {
		if(object == null) return object();
		Map<String, ZsonValue> map = Zson.object();
		for (Field field : object.getClass().getDeclaredFields()) {
			if(!shouldInclude(field, true)) continue;
			Comment comment = field.getAnnotation(Comment.class);
			String commentValue = comment == null ? null : comment.value();
			try {
				boolean accessible = field.isAccessible();
				if (!accessible) field.setAccessible(true);
				map.put(field.getName(), new ZsonValue(commentValue, field.get(object)));
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
	 *                           static fields are included if they are annotated with {@link Include @Include},
	 *                           otherwise they are not included at all.
	 * @return true if the field should be included in a JSON map, false otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private static boolean shouldInclude(Field field, boolean forDeserialization) {
		Exclude exclude = field.getAnnotation(Exclude.class);
		if (exclude != null) return false; // if field is annotated with @Exclude, ignore it no matter what
		int modifiers = field.getModifiers();
		if(Modifier.isTransient(modifiers)) return false; // ignore transient fields too

		Include include = field.getAnnotation(Include.class);

		// include:
		// - if it's static, only if it's for serialization
		// - if it's not public, only if it's annotated with @Include
		return (forDeserialization || !Modifier.isStatic(modifiers)) && (include != null || Modifier.isPublic(modifiers));
	}

	/**
	 * Sets the value of the given field in the given object to the given value, attempting to cast it to the field's type.
	 * @param field the field to set. Must not be null.
	 * @param object the object to set the field in. Must not be null.
	 * @param value the value to set the field to. May be null. If primitive, will be an int or double.
	 * @param <T> the type of the field.
	 */
	private static <T> void setField(Field field, Object object, Object value) {
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) field.getType();
		boolean accessible = field.isAccessible();
		if(!accessible) field.setAccessible(true);
		try {
			if(type.isPrimitive()) {
				switch (type.getName()) {
					case "boolean" -> field.setBoolean(object, (boolean) value);
					case "short" -> field.setShort(object, (short) (int) value);
					case "int" -> field.setInt(object, (int) value);
					case "float" -> field.setFloat(object, (float) (double) value);
					case "double" -> field.setDouble(object, (double) value);
					case "long" -> field.setLong(object, (long) value);
					case "byte" -> field.setByte(object, (byte) (int) value);
					case "char" -> field.setChar(object, (char) value);
				}
			} else {
				field.set(object, type.cast(value));
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

	private Zson() {
	}
}
