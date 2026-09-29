package dev.arkoder.inertia4j.core;

final class JsonStrings {
    private JsonStrings() {}

    static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            if (character == '"' || character == '\\') {
                quoted.append('\\').append(character);
                continue;
            }

            if (character < 0x20) {
                quoted.append(String.format("\\u%04x", (int) character));
                continue;
            }

            quoted.append(character);
        }

        return quoted.append('"').toString();
    }
}
