package dev.nolij.zson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZsonParser {

    public static Object parseString(String str) throws IOException {
        return parse(new BufferedReader(new StringReader(str)));
    }

	/**
	 * @return One of:
	 * 		     - Map<String, ZsonValue>
	 * 		     - List<ZsonValue>
	 * 		     - String
	 * 		     - Number
	 * 		     - Boolean
	 * 		     - null
	 */
	public static Object parse(Reader r) throws IOException {
        while (true) {
            if (skipWhitespace(r)) {
                continue;
            }
            if (skipComment(r)) {
                continue;
            }

            int c = r.read();
            if (c == -1) {
                throw unexpectedEOF();
            }
	        switch (c) {
		        case '{' -> {
			        return parseObject(r);
		        }
		        case '[' -> {
			        return parseArray(r);
		        }
		        case '"', '\'' -> {
			        return Strings.unescape(parseString(r, (char) c));
		        }
		        case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'N', 'I' -> {
			        return parseNumber(r, (char) c);
		        }
		        case 'n' -> {
			        char[] chars = new char[3];
					if (r.read(chars) == 3 && chars[0] == 'u' && chars[1] == 'l' && chars[2] == 'l') {
				        return null;
			        } else {
				        throw new IllegalArgumentException("Expected 'null', got 'n" + new String(chars) + "'");
			        }
		        }
	        }
            throw unexpected(c);
        }
    }

    private static Map<String, ZsonValue> parseObject(Reader r) throws IOException {
        Map<String, ZsonValue> map = new LinkedHashMap<>();
        boolean comma = false;
        boolean colon = false;
        String key = null;
        while (true) {
	        if (skipWhitespace(r) || skipComment(r)) {
		        continue;
	        }
			
	        r.mark(1);
            int c = r.read();
            if (c == '}') {
                return map;
            }
            if (comma) {
                if (c != ',') {
                    throw new IllegalArgumentException("Expected comma");
                }
                comma = false;
                continue;
            }
            if (colon) {
                if (c != ':') {
                    throw new IllegalArgumentException("Expected colon");
                }
                colon = false;
                continue;
            }
            if (c == -1) {
                throw unexpectedEOF();
            }
            if (key == null) {
	            key = switch (c) {
		            case '"', '\'' ->  Strings.unescape(parseString(r, (char) c));
		            default -> {
			            if (Character.isLetter(c) || c == '_' || c == '$' || c == '\\') {
				            yield parseIdentifier(r, (char) c);
			            } else {
				            throw unexpected(c);
			            }
		            }
	            };
                colon = true;
            } else {
                r.reset();
                Object value = parse(r);
                map.put(key, new ZsonValue(value));
                key = null;
                comma = true;
            }
        }
    }

    private static List<ZsonValue> parseArray(Reader r) {
        List<ZsonValue> list = new ArrayList<>();
        boolean comma = false;
        while (true) {
            try {
                if (skipWhitespace(r)) {
                    continue;
                }
                if (skipComment(r)) {
                    continue;
                }

                r.mark(1);
                int c = r.read();
                if (c == ']') {
                    return list;
                }
                if (comma) {
                    if (c != ',') {
                        throw new IllegalArgumentException("Expected comma");
                    } else {
                        comma = false;
                        continue;
                    }
                }
                if (c == -1) {
                    throw unexpectedEOF();
                }
                r.reset();
                Object value = parse(r);
                list.add(new ZsonValue(value));
                comma = true;
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private static String parseString(Reader r, char start) throws IOException {
        int escapes = 0;
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) != -1) {
            if (c == start) {
                if (escapes == 0) {
                    return sb.toString();
                }
                sb.append(Character.toChars(c));
                escapes--;
            }
            if (c == '\n') {
                if (escapes == 0) {
                    throw new IllegalArgumentException("Unexpected newline");
                }
                escapes = 0;
                sb.append('\n');
                continue;
            }
            if (c == '\\') {
                escapes++;
                if (escapes == 2) {
                    sb.append('\\');
                    escapes = 0;
                }
            } else {
                if (escapes == 1) {
                    sb.append('\\');
                }
                sb.append(Character.toChars(c));
                escapes = 0;
            }
        }
        throw unexpectedEOF();
    }

    private static String parseIdentifier(Reader r, char start) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(start);
        int c;
        r.mark(1);
        while ((c = r.read()) != -1) {
            // TODO: verify this works properly... https://262.ecma-international.org/5.1/#sec-7.6
            if (!Character.isWhitespace(c)) {
                r.mark(1);
                sb.append(Character.toChars(c));
            } else {
                r.reset();
                return sb.toString();
            }
        }
        throw unexpectedEOF();
    }

    private static Number parseNumber(Reader r, char start) throws IOException {
	    switch (start) {
		    case '-' -> {
			    Number n = parseNumber(r, (char) r.read());
			    if (n instanceof Double d) {
				    return -d;
			    } else if (n instanceof Long l) {
				    return -l;
			    } else if (n instanceof BigInteger b) {
				    return b.negate();
			    } else {
				    return -((Integer) n);
			    }
		    }
		    case 'N' -> {
			    char n = (char) r.read();
			    if (r.read() != 'a') throw unexpected(n);
			    n = (char) r.read();
			    if (n != 'N') throw unexpected(n);
			    return Float.NaN;
		    }
		    case 'I' -> {
			    char[] chars = new char[7];
				if(r.read(chars) != 7 || "nfinity".equals(new String(chars)))
					throw new IllegalArgumentException("Expected 'Infinity', got 'I" + new String(chars) + "'");
			    return Double.POSITIVE_INFINITY;
		    }
		    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
			    StringBuilder sb = new StringBuilder();
			    sb.append(start);
			    int c;
			    r.mark(1);
			    while ((c = r.read()) != -1) {
				    if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					    r.mark(1);
					    sb.append(Character.toChars(c));
				    } else {
					    r.reset();
					    String s = sb.toString();
					    if (s.contains(".") || s.contains("e") || s.contains("E")) {
						    return Double.parseDouble(s);
					    } else {
						    try {
							    return Integer.parseInt(s);
						    } catch (NumberFormatException e) {
							    try {
								    return Long.parseLong(s);
							    } catch (NumberFormatException e2) {
								    return new BigInteger(s);
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

    private static boolean skipWhitespace(Reader br) throws IOException {
        br.mark(1);
        int c;
        int skipped = 0;
        while ((c = br.read()) != -1) {
            if (!Character.isWhitespace(c)) {
                br.reset();
                return skipped != 0;
            }
            skipped++;
            br.mark(1);
        }
        throw unexpectedEOF();
    }

    private static boolean skipComment(Reader br) throws IOException {
        br.mark(2);
        int c = br.read();
        if (c == '/') {
            int c2 = br.read();
            if (c2 == '/') {
                while ((c = br.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                }
                return true;
            } else if (c2 == '*') {
                while ((c = br.read()) != -1) {
                    if (c == '*') {
                        if (br.read() == '/') {
                            return true;
                        }
                    }
                }
                throw unexpectedEOF();
            } else {
                br.reset();
            }
        } else {
            br.reset();
        }
        return false;
    }
	
	private static IllegalArgumentException unexpected(int c) {
		return new IllegalArgumentException("Unexpected character: " + (char) c);
	}

	private static IllegalArgumentException unexpectedEOF() {
		return new IllegalArgumentException("Unexpected EOF");
	}
}