package dev.nolij.zson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


// TODO: Add support for:
//   - hex/octal/binary numbers
//   - unquoted keys (writer can optionally output these)
//   - multi-line strings?
//   - trailing commas (important! writer always outputs these)
public final class ZsonParser {

	public static <T> T parseString(String serialized) throws IOException {
		return parse(new BufferedReader(new StringReader(serialized)));
	}

	/**
	 * @return One of:
	 * - Map<String, ZsonValue>
	 * - List<ZsonValue>
	 * - String
	 * - Number
	 * - Boolean
	 * - null
	 */
	@SuppressWarnings("unchecked")
	public static <T> T parse(Reader input) throws IOException {
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
				case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'N', 'I' -> {
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
					throw new IllegalArgumentException("Expected comma");
				
				comma = false;
				continue;
			}
			
			if (colon) {
				if (ch != ':')
					throw new IllegalArgumentException("Expected colon");
				
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

	private static List<ZsonValue> parseArray(Reader input) {
		List<ZsonValue> list = new ArrayList<>();
		var comma = false;
		
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
						throw new IllegalArgumentException("Expected comma");
					
					comma = false;
					continue;
				}
				
				if (ch == -1)
					throw unexpectedEOF();
				
				input.reset();
				Object value = parse(input);
				list.add(new ZsonValue(value));
				comma = true;
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	private static String parseString(Reader input, char start) throws IOException {
		var escapes = 0;
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
				char n = (char) input.read();
				if (input.read() != 'a')
					throw unexpected(n);
				
				n = (char) input.read();
				if (n != 'N')
					throw unexpected(n);
				
				return Float.NaN;
			}
			case 'I' -> {
				char[] chars = new char[7];
				if (input.read(chars) != 7 || "nfinity".equals(new String(chars)))
					throw new IllegalArgumentException("Expected 'Infinity', got 'I" + new String(chars) + "'");
				
				return Double.POSITIVE_INFINITY;
			}
			case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
				StringBuilder stringValueBuilder = new StringBuilder();
				stringValueBuilder.append(start);
				
				int c;
				input.mark(1);
				while ((c = input.read()) != -1) {
					if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
						input.mark(1);
						stringValueBuilder.append(Character.toChars(c));
					} else {
						input.reset();
						String stringValue = stringValueBuilder.toString();
						if (stringValue.contains(".") || stringValue.contains("e") || stringValue.contains("E")) {
							return Double.parseDouble(stringValue);
						} else {
							try {
								return Integer.parseInt(stringValue);
							} catch (NumberFormatException e) {
								try {
									return Long.parseLong(stringValue);
								} catch (NumberFormatException e2) {
									return new BigInteger(stringValue);
								}
							}
						}
					}
				}
				
				throw unexpectedEOF();
			}
		}
		
		throw unexpected(start);
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

	private static IllegalArgumentException unexpected(int ch) {
		return new IllegalArgumentException("Unexpected character: " + (char) ch);
	}

	private static IllegalArgumentException unexpectedEOF() {
		return new IllegalArgumentException("Unexpected EOF");
	}
}