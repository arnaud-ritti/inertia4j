package io.github.inertia4j.typescript;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps Java reflection types to TypeScript types. User classes are resolved through the {@code references}
 * callback, which registers them for emission and returns their TypeScript name.
 */
final class TypeMapper {
    private static final Map<String, TsType> BuiltIns = Map.ofEntries(
        Map.entry("java.lang.String", TsType.StringKeyword),
        Map.entry("char", TsType.StringKeyword),
        Map.entry("java.lang.Character", TsType.StringKeyword),
        Map.entry("java.util.UUID", TsType.StringKeyword),
        Map.entry("java.net.URI", TsType.StringKeyword),
        Map.entry("java.net.URL", TsType.StringKeyword),
        Map.entry("java.time.Instant", TsType.StringKeyword),
        Map.entry("java.time.LocalDate", TsType.StringKeyword),
        Map.entry("java.time.LocalDateTime", TsType.StringKeyword),
        Map.entry("java.time.OffsetDateTime", TsType.StringKeyword),
        Map.entry("java.time.ZonedDateTime", TsType.StringKeyword),
        Map.entry("java.time.LocalTime", TsType.StringKeyword),
        Map.entry("java.time.Duration", TsType.StringKeyword),
        Map.entry("int", TsType.NumberKeyword),
        Map.entry("long", TsType.NumberKeyword),
        Map.entry("short", TsType.NumberKeyword),
        Map.entry("byte", TsType.NumberKeyword),
        Map.entry("double", TsType.NumberKeyword),
        Map.entry("float", TsType.NumberKeyword),
        Map.entry("java.lang.Integer", TsType.NumberKeyword),
        Map.entry("java.lang.Long", TsType.NumberKeyword),
        Map.entry("java.lang.Short", TsType.NumberKeyword),
        Map.entry("java.lang.Byte", TsType.NumberKeyword),
        Map.entry("java.lang.Double", TsType.NumberKeyword),
        Map.entry("java.lang.Float", TsType.NumberKeyword),
        Map.entry("java.math.BigDecimal", TsType.NumberKeyword),
        Map.entry("java.math.BigInteger", TsType.NumberKeyword),
        Map.entry("boolean", TsType.BooleanKeyword),
        Map.entry("java.lang.Boolean", TsType.BooleanKeyword)
    );

    private static final Set<String> SilentUnknowns = Set.of(
        "java.lang.Object",
        "com.fasterxml.jackson.databind.JsonNode",
        "tools.jackson.databind.JsonNode"
    );

    private static final Set<String> Wrappers = Set.of(
        Annotations.DeferredProp,
        Annotations.MergeProp,
        "java.util.function.Supplier",
        "kotlin.jvm.functions.Function0"
    );

    interface References {
        String register(Class<?> type);
    }

    private final List<String> packages;
    private final References references;
    private final List<String> warnings;
    private String context = "";

    TypeMapper(List<String> packages, References references, List<String> warnings) {
        this.packages = packages;
        this.references = references;
        this.warnings = warnings;
    }

    /**
     * @param context {@code Owner.property} used in warnings and errors.
     */
    void setContext(String context) {
        this.context = context;
    }

    static boolean isDeferred(Type type) {
        return rawClass(type) != null && rawClass(type).getName().equals(Annotations.DeferredProp);
    }

    TsType map(Type type) {
        if (type instanceof Class<?> typeClass) {
            return mapClass(typeClass);
        }

        if (type instanceof ParameterizedType parameterized) {
            return mapParameterized(parameterized);
        }

        if (type instanceof GenericArrayType array) {
            return new TsType.Array(map(array.getGenericComponentType()));
        }

        if (type instanceof TypeVariable<?> variable) {
            return new TsType.Variable(variable.getName());
        }

        if (type instanceof WildcardType wildcard) {
            return mapWildcard(wildcard);
        }

        return unknown(type.getTypeName());
    }

    private TsType mapClass(Class<?> type) {
        if (type == byte[].class) {
            return TsType.StringKeyword;
        }

        if (type.isArray()) {
            return new TsType.Array(mapClass(type.getComponentType()));
        }

        TsType builtIn = BuiltIns.get(type.getName());
        if (builtIn != null) {
            return builtIn;
        }

        if (SilentUnknowns.contains(type.getName())) {
            return TsType.UnknownKeyword;
        }

        if (Wrappers.contains(type.getName())) {
            return TsType.UnknownKeyword;
        }

        if (type.getName().equals("java.util.Optional")) {
            return TsType.UnknownKeyword;
        }

        if (Collection.class.isAssignableFrom(type)) {
            return new TsType.Array(TsType.UnknownKeyword);
        }

        if (Map.class.isAssignableFrom(type)) {
            return new TsType.StringMap(TsType.UnknownKeyword);
        }

        if (isUserType(type)) {
            List<TsType> rawArguments = Arrays.stream(type.getTypeParameters())
                .map(parameter -> TsType.UnknownKeyword)
                .toList();
            return new TsType.Reference(references.register(type), rawArguments);
        }

        return unknown(type.getName());
    }

    private TsType mapParameterized(ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        Type[] arguments = parameterized.getActualTypeArguments();

        if (Wrappers.contains(raw.getName())) {
            return map(arguments[0]);
        }

        if (raw.getName().equals("java.util.Optional")) {
            return TsType.nullable(map(arguments[0]));
        }

        if (Collection.class.isAssignableFrom(raw)) {
            return new TsType.Array(map(arguments[0]));
        }

        if (Map.class.isAssignableFrom(raw)) {
            return mapMap(arguments[0], arguments[1]);
        }

        if (isUserType(raw)) {
            List<TsType> mappedArguments = Arrays.stream(arguments).map(this::map).toList();
            return new TsType.Reference(references.register(raw), mappedArguments);
        }

        return unknown(raw.getName());
    }

    private TsType mapMap(Type keyType, Type valueType) {
        TsType value = map(valueType);
        TsType key = map(keyType);

        if (key == TsType.StringKeyword) {
            return new TsType.StringMap(value);
        }

        if (key == TsType.NumberKeyword) {
            return new TsType.NumberMap(value);
        }

        Class<?> keyClass = rawClass(keyType);
        if (keyClass != null && keyClass.isEnum()) {
            return key instanceof TsType.Reference
                ? new TsType.EnumMap(key, value)
                : new TsType.StringMap(value);
        }

        throw new GenerationException(
            "Unsupported map key type " + keyType.getTypeName() + " in " + context
                + ": keys must be strings, numbers or enums"
        );
    }

    private TsType mapWildcard(WildcardType wildcard) {
        Type[] upperBounds = wildcard.getUpperBounds();

        if (wildcard.getLowerBounds().length == 0 && upperBounds.length == 1 && upperBounds[0] != Object.class) {
            return map(upperBounds[0]);
        }

        return unknown(wildcard.getTypeName());
    }

    private boolean isUserType(Class<?> type) {
        return ClassScanner.inPackages(type.getName(), packages);
    }

    private TsType unknown(String typeName) {
        warnings.add("Type " + typeName + " in " + context + " is outside the configured packages and is mapped to unknown");
        return TsType.UnknownKeyword;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass;
        }

        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }

        return null;
    }
}
