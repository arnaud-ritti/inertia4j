package io.github.inertia4j.typescript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.inertia4j.core.DefaultPageObjectSerializer;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.PropsExtractor;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.typescript.fixtures.parity.ParityOwner;
import io.github.inertia4j.typescript.fixtures.parity.ParityProps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.inertia4j.typescript.GeneratorTestSupport.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeParityTest {
    private static final Pattern PropertyLine = Pattern.compile("^  (\\w+)(\\??): ");

    @ParameterizedTest
    @EnumSource(PropertyNaming.class)
    void jsonKeys_withAllValuesSet_equalAllGeneratedProperties(PropertyNaming naming) throws Exception {
        Map<String, Map<String, Boolean>> interfaces = generatedInterfaces(naming);
        ParityProps props = new ParityProps("Miles", new ParityOwner("Main", "75001", "https://example.com", "Mo"), "Dewey", "Davis");

        JsonNode json = serializedProps(props, naming);

        assertEquals(interfaces.get("ParityProps").keySet(), keys(json));
        assertEquals(interfaces.get("ParityOwner").keySet(), keys(json.get(ownerKey(naming))));
    }

    @ParameterizedTest
    @EnumSource(PropertyNaming.class)
    void jsonKeys_withNullValues_omitExactlyTheOptionalGeneratedProperties(PropertyNaming naming) throws Exception {
        Map<String, Map<String, Boolean>> interfaces = generatedInterfaces(naming);
        ParityProps props = new ParityProps("Miles", new ParityOwner("Main", "75001", "https://example.com", null), null, null);

        JsonNode json = serializedProps(props, naming);

        assertEquals(requiredProperties(interfaces.get("ParityProps")), keys(json));
        assertEquals(requiredProperties(interfaces.get("ParityOwner")), keys(json.get(ownerKey(naming))));
    }

    private static String ownerKey(PropertyNaming naming) {
        return naming.apply("homeOwner");
    }

    private static Map<String, Map<String, Boolean>> generatedInterfaces(PropertyNaming naming) {
        GeneratorOptions options = new GeneratorOptions(
            List.of("io.github.inertia4j.typescript.fixtures.parity"),
            naming,
            ErrorValueType.String,
            false
        );
        String content = generate(options).content();

        Map<String, Map<String, Boolean>> interfaces = new LinkedHashMap<>();
        Map<String, Boolean> current = null;
        for (String line : content.split("\n")) {
            if (line.startsWith("export interface ")) {
                current = new TreeMap<>();
                interfaces.put(line.substring("export interface ".length(), line.indexOf(' ', "export interface ".length())), current);
                continue;
            }

            if (line.equals("}")) {
                current = null;
                continue;
            }

            Matcher matcher = PropertyLine.matcher(line);
            if (current != null && matcher.find()) {
                current.put(matcher.group(1), matcher.group(2).isEmpty());
            }
        }

        return interfaces;
    }

    private static JsonNode serializedProps(ParityProps props, PropertyNaming naming) throws Exception {
        PageObject pageObject = new PageObject("Parity/Show", PropsExtractor.toMap(props, naming), "/", false, false, "1");
        String json = new DefaultPageObjectSerializer(naming).serialize(pageObject, null);

        return new ObjectMapper().readTree(json).get("props");
    }

    private static Set<String> requiredProperties(Map<String, Boolean> properties) {
        return properties.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);

        return keys;
    }
}
