package africa.civitas.egen.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecteur JSON minimal (RFC 8259) — ecrit a la main parce que le SDK n'admet
 * aucune dependance (docs/architecture/12). Renvoie {@code Map<String,Object>},
 * {@code List<Object>}, {@code String}, {@code Long} ou {@code Double},
 * {@code Boolean} et {@code null}. Strict : tout contenu apres la valeur
 * racine, ou toute syntaxe invalide, leve {@link IllegalArgumentException}.
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("contenu inattendu apres la valeur racine");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("un objet JSON etait attendu");
        }
        return (Map<String, Object>) value;
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw error("fin de document inattendue");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return readNumber();
                }
                throw error("caractere inattendu '" + c + "'");
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("nom de champ attendu");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else if (peek() == '}') {
                pos++;
                return map;
            } else {
                throw error("',' ou '}' attendu");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else if (peek() == ']') {
                pos++;
                return list;
            } else {
                throw error("',' ou ']' attendu");
            }
        }
    }

    private String readString() {
        pos++; // "
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("chaine non terminee");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("caractere de controle non echappe dans une chaine");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("echappement incomplet");
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("sequence \\u incomplete");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("sequence \\u invalide");
                    }
                    pos += 4;
                    break;
                default:
                    throw error("echappement inconnu \\" + escaped);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean fractional = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                fractional = true;
                pos++;
            } else {
                break;
            }
        }
        String raw = text.substring(start, pos);
        try {
            return fractional ? (Object) Double.valueOf(raw) : (Object) Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw error("nombre invalide \"" + raw + "\"");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("litteral invalide");
        }
        pos += word.length();
        return value;
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("fin de document inattendue");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("'" + c + "' attendu");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON invalide en position " + pos + " : " + message);
    }

    /** Litteral de chaine JSON (RFC 8259) : guillemets, antislash et caracteres de controle echappes. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
