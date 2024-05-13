package dev.nolij.zson;

public final class Strings {
	public static String unescape(String string) {
		if (string == null || string.isEmpty()) {
			return string;
		}

		char[] chars = string.toCharArray();
		int j = 0;
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (c != '\\') {
				chars[j++] = c;
				continue;
			}
			if(i + 1 >= chars.length) {
				throw new IllegalArgumentException("Invalid escape sequence: \\EOS");
			}

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

	public static String escape(String string, char escapeQuotes) {
		if(string == null || string.isEmpty()) {
			return string;
		}

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
}
