package dev.nolij.zson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ZsonParser {

	public static <T> T parseString(String serialized) {
		try {
			return parse(new BufferedReader(new StringReader(serialized)));
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * @return One of:
	 * - Map<String, ZsonValue> (object)
	 * - List<Object> (array)
	 * - String
	 * - Number
	 * - Boolean
	 * - null
	 */
	@SuppressWarnings("unchecked")
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
					 '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
					 'a', 'b', 'c', 'd', 'e', 'f',
					 'A', 'B', 'C', 'D', 'E', 'F' -> {
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
			}

			throw unexpected(ch);
		}
	}

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

	private static Number parseNumber(Reader input, char start) throws IOException {
		switch (start) {
			case '-' -> {
				Number numberValue = parseNumber(input, (char) input.read());
				if (numberValue instanceof Double doubleValue) {
					return -doubleValue;
				} else if (numberValue instanceof Long longValue) {
					return -longValue;
				} else if (numberValue instanceof BigInteger bigIntValue) {
					return bigIntValue.negate();
				} else {
					return -((Integer) numberValue);
				}
			}
			case 'N' -> {
				int n = input.read();
				if (n != 'a') {
					throw unexpected(n, 'a');
				}

				n = (char) input.read();
				if (n != 'N')
					throw unexpected(n, 'N');

				return Float.NaN;
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
				return parseDecimal(start, input);
			}

			case '0' -> {
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
					return parseDecimal('0', input);
				}
			}
		}

		throw unexpected(start);
	}

	private static Number parseDecimal(char c, Reader input) throws IOException {
		StringBuilder stringValueBuilder = new StringBuilder();
		stringValueBuilder.append(c);

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

	private static IllegalArgumentException unexpected(int ch, char... expected) {
		String message = "Unexpected character: " + (char) ch;
		if(expected.length > 0) {
			message += "\nExpected one of: " + Arrays.toString(expected);
		}
		return new IllegalArgumentException(message);
	}

	private static IllegalArgumentException unexpectedEOF() {
		return new IllegalArgumentException("Unexpected EOF");
	}
}