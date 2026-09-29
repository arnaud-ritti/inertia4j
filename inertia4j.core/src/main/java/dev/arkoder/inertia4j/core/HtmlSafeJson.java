package dev.arkoder.inertia4j.core;

/**
 * Escapes JSON so it can be embedded in a {@code <script>} element: {@code /} becomes {@code \/},
 * {@code <} becomes {@code <} and {@code >} becomes {@code >}, so no {@code </script>} sequence
 * can close the element early. The escaped JSON parses to the same value.
 */
final class HtmlSafeJson {
    private HtmlSafeJson() {}

    static String escape(String json) {
        StringBuilder escaped = new StringBuilder(json.length() + 16);

        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);

            if (character == '\\' && i + 1 < json.length()) {
                escaped.append(character).append(json.charAt(++i));
                continue;
            }

            if (character == '/') {
                escaped.append("\\/");
                continue;
            }

            if (character == '<') {
                escaped.append("\\u003C");
                continue;
            }

            if (character == '>') {
                escaped.append("\\u003E");
                continue;
            }

            escaped.append(character);
        }

        return escaped.toString();
    }
}
