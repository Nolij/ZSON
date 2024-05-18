package dev.nolij.zson;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;


public final class Zson {

	@NotNull
	@Contract("_, _, _ -> new")
	public static Map.Entry<String, ZsonValue> entry(@Nullable String key, @Nullable String comment, @Nullable Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(comment, value));
	}

	@NotNull
	@Contract(value = "_, _ -> new", pure = true)
	public static Map.Entry<String, ZsonValue> entry(@Nullable String key, @Nullable Object value) {
		return new AbstractMap.SimpleEntry<>(key, new ZsonValue(value));
	}

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

	@NotNull
	@Contract("_ -> new")
	public static List<?> array(@NotNull Object... values) {
		List<Object> list = new ArrayList<>();
		Collections.addAll(list, values);
		
		return list;
	}

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

	@NotNull
	@Contract("_ -> new")
	public static Map<String, ZsonValue> obj2Map(@NotNull Object object) {
		Map<String, ZsonValue> map = Zson.object();
		for (Field field : object.getClass().getDeclaredFields()) {
			if(!shouldInclude(field, true)) continue;
			Comment comment = field.getAnnotation(Comment.class);
			String commentValue = comment == null ? null : comment.value();
			try {
				map.put(field.getName(), new ZsonValue(commentValue, field.get(object)));
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Failed to get field " + field.getName(), e);
			}
		}
		return map;
	}

	@NotNull
	@Contract("_ , _ -> new")
	public static <T> T map2Obj(@NotNull Map<String, ZsonValue> map, @NotNull Class<T> type) {
		try {
			T object = type.getDeclaredConstructor().newInstance();
			for (Field field : type.getDeclaredFields()) {
				if(!shouldInclude(field, false)) continue;
				setField(field, object, map.get(field.getName()).value);
			}
			return object;
		} catch (Exception e) {
			throw new RuntimeException("Failed to create object of type " + type.getSimpleName(), e);
		}
	}

	private static boolean shouldInclude(Field field, boolean includeStatic) {
		int modifiers = field.getModifiers();
		Exclude exclude = field.getAnnotation(Exclude.class);
		if(exclude != null) return false;

		boolean privateOrProtected = Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers);
		boolean isStatic = Modifier.isStatic(modifiers) && !includeStatic;

		Include include = field.getAnnotation(Include.class);
		if(include != null) {
			if(!isStatic) {
				if(privateOrProtected) {
					field.setAccessible(true);
				}
				return true;
			} else {
				return false;
			}
		}
		if(privateOrProtected) {
			return false;
		}
		return !isStatic;
	}

	private static <T> void setField(Field field, Object object, Object value) {
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) field.getType();
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
			throw new RuntimeException(
				"Failed to set field " + field.getName() + " (type " + type.getSimpleName() + ") to " + value + " " +
					"(type " + value.getClass().getSimpleName() + ")", e
			);
		}
	}

	private Zson() {
	}
}
