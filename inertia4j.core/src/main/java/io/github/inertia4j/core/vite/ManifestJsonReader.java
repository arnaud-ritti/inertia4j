package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for the Vite manifest, so that the core module needs no JSON library at runtime.
 */
@NullMarked
class ManifestJsonReader {
    private final String json;
    private int position;

    private ManifestJsonReader(String json) {
        this.json = json;
    }

    static @Nullable Object read(String json) {
        ManifestJsonReader reader = new ManifestJsonReader(json);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();

        if (reader.position != json.length()) {
            throw reader.error("unexpected trailing content");
        }

        return value;
    }

    private @Nullable Object readValue() {
        char current = peek();

        if (current == '{') {
            return readObject();
        }

        if (current == '[') {
            return readArray();
        }

        if (current == '"') {
            return readString();
        }

        if (current == 't') {
            readLiteral("true");
            return Boolean.TRUE;
        }

        if (current == 'f') {
            readLiteral("false");
            return Boolean.FALSE;
        }

        if (current == 'n') {
            readLiteral("null");
            return null;
        }

        if (current == '-' || (current >= '0' && current <= '9')) {
            return readNumber();
        }

        throw error("unexpected character '" + current + "'");
    }

    private Map<String, @Nullable Object> readObject() {
        Map<String, @Nullable Object> object = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();

        if (peek() == '}') {
            position++;
            return object;
        }

        while (true) {
            skipWhitespace();

            if (peek() != '"') {
                throw error("expected string key");
            }

            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();

            if (peek() == ',') {
                position++;
                continue;
            }

            expect('}');
            return object;
        }
    }

    private List<@Nullable Object> readArray() {
        List<@Nullable Object> array = new ArrayList<>();
        expect('[');
        skipWhitespace();

        if (peek() == ']') {
            position++;
            return array;
        }

        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();

            if (peek() == ',') {
                position++;
                continue;
            }

            expect(']');
            return array;
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();

        while (true) {
            char current = next();

            if (current == '"') {
                return builder.toString();
            }

            if (current != '\\') {
                builder.append(current);
                continue;
            }

            builder.append(readEscape());
        }
    }

    private char readEscape() {
        char escaped = next();

        switch (escaped) {
            case '"':
                return '"';
            case '\\':
                return '\\';
            case '/':
                return '/';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'u':
                return readUnicodeEscape();
            default:
                throw error("invalid escape '\\" + escaped + "'");
        }
    }

    private char readUnicodeEscape() {
        if (position + 4 > json.length()) {
            throw error("invalid unicode escape");
        }

        String hex = json.substring(position, position + 4);

        try {
            char decoded = (char) Integer.parseInt(hex, 16);
            position += 4;
            return decoded;
        } catch (NumberFormatException e) {
            throw error("invalid unicode escape");
        }
    }

    private Double readNumber() {
        int start = position;

        while (position < json.length() && "+-0123456789.eE".indexOf(json.charAt(position)) >= 0) {
            position++;
        }

        try {
            return Double.valueOf(json.substring(start, position));
        } catch (NumberFormatException e) {
            position = start;
            throw error("invalid number");
        }
    }

    private void readLiteral(String literal) {
        if (!json.startsWith(literal, position)) {
            throw error("unexpected character '" + json.charAt(position) + "'");
        }

        position += literal.length();
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("expected '" + expected + "'");
        }

        position++;
    }

    private char peek() {
        if (position >= json.length()) {
            throw error("unexpected end of input");
        }

        return json.charAt(position);
    }

    private char next() {
        char current = peek();
        position++;
        return current;
    }

    private void skipWhitespace() {
        while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
            position++;
        }
    }

    private ViteException error(String reason) {
        return new ViteException("Invalid Vite manifest: " + reason + " at offset " + position);
    }
}
