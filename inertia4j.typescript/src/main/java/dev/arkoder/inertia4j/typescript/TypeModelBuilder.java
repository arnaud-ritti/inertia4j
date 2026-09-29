package dev.arkoder.inertia4j.typescript;

import dev.arkoder.inertia4j.core.Property;
import dev.arkoder.inertia4j.core.PropertyIntrospector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds the {@link TsModel} for the role classes found by the {@link ClassScanner},
 * emitting every referenced user type transitively.
 */
final class TypeModelBuilder {
    private static final Set<String> ReservedNames = Set.of("InertiaPages", "PageProps");
    private static final Set<String> OmitNullInclusions = Set.of("NON_NULL", "NON_ABSENT", "NON_EMPTY");

    private final GeneratorOptions options;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Class<?>> classesByName = new HashMap<>();
    private final Map<Class<?>, String> namesByClass = new HashMap<>();
    private final Deque<Class<?>> pending = new ArrayDeque<>();
    private final Map<Class<?>, Optional<Set<String>>> kotlinNullableAccessorsByClass = new HashMap<>();
    private final TypeMapper mapper;

    TypeModelBuilder(GeneratorOptions options) {
        this.options = options;
        this.mapper = new TypeMapper(options.packages(), this::register, warnings);
    }

    List<String> warnings() {
        return List.copyOf(new LinkedHashSet<>(warnings));
    }

    TsModel build(List<Class<?>> roleClasses) {
        roleClasses.forEach(this::register);

        List<TsDeclaration> declarations = new ArrayList<>();
        while (!pending.isEmpty()) {
            declarations.add(declare(pending.poll()));
        }

        return new TsModel(
            declarations,
            pages(roleClasses),
            typesAnnotatedWith(roleClasses, Annotations.InertiaShared),
            typesAnnotatedWith(roleClasses, Annotations.InertiaFlash),
            options.errorValueType()
        );
    }

    private String register(Class<?> type) {
        String existing = namesByClass.get(type);
        if (existing != null) {
            return existing;
        }

        String name = typeScriptName(type);

        if (ReservedNames.contains(name)) {
            throw new GenerationException(
                "Type name '" + name + "' of " + type.getName() + " is reserved by the generated file. Rename it with @TypeScriptName."
            );
        }

        Class<?> other = classesByName.get(name);
        if (other != null) {
            throw new GenerationException(
                "Type name '" + name + "' is used by both " + other.getName() + " and " + type.getName()
                    + ". Rename one of them with @TypeScriptName."
            );
        }

        classesByName.put(name, type);
        namesByClass.put(type, name);
        pending.add(type);

        return name;
    }

    private static String typeScriptName(Class<?> type) {
        return Annotations.find(type, Annotations.TypeScriptName)
            .map(annotation -> (String) Annotations.value(annotation, "value"))
            .orElse(type.getSimpleName());
    }

    private TsDeclaration declare(Class<?> type) {
        String name = namesByClass.get(type);

        if (type.isEnum()) {
            return new TsDeclaration.Enum(name, enumValues(type));
        }

        List<String> typeParameters = Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName).toList();
        List<TsDeclaration.Property> properties = new ArrayList<>();

        boolean flash = Annotations.has(type, Annotations.InertiaFlash);

        for (Property property : PropertyIntrospector.properties(type, options.propertyNaming())) {
            TsDeclaration.Property declared = declareProperty(type, property);

            properties.add(flash ? declared.asOptional() : declared);
        }

        return new TsDeclaration.Interface(name, typeParameters, properties);
    }

    private TsDeclaration.Property declareProperty(Class<?> owner, Property property) {
        mapper.setOwner(owner);
        mapper.setContext(owner.getSimpleName() + "." + property.getName());

        Type type = property.getGenericType();
        TsType tsType = mapper.map(type);
        boolean optional = TypeMapper.isInertiaProp(type);

        if (isPrimitive(type) || !isNullable(property)) {
            return new TsDeclaration.Property(property.getName(), tsType, optional);
        }

        if (omitsNulls(owner, property)) {
            return new TsDeclaration.Property(property.getName(), tsType, true);
        }

        return new TsDeclaration.Property(property.getName(), TsType.nullable(tsType), optional);
    }

    private boolean isNullable(Property property) {
        Class<?> declaringClass = property.getDeclaringClass();
        Optional<Set<String>> kotlinNullable = kotlinNullableAccessors(declaringClass);
        if (kotlinNullable.isPresent()) {
            return kotlinNullable.get().contains(property.getAccessorName());
        }

        if (property.hasAnnotation("Nullable")) {
            return true;
        }

        if (!options.nullableByDefault()) {
            return false;
        }

        if (property.hasAnnotation("NonNull") || property.hasAnnotation("NotNull")) {
            return false;
        }

        return !isNullMarked(declaringClass);
    }

    private static boolean isNullMarked(Class<?> owner) {
        for (Class<?> current = owner; current != null; current = current.getEnclosingClass()) {
            if (Annotations.hasSimpleName(current, "NullMarked")) {
                return true;
            }
        }

        Package ownerPackage = owner.getPackage();

        return ownerPackage != null && Annotations.hasSimpleName(ownerPackage, "NullMarked");
    }

    private static boolean omitsNulls(Class<?> owner, Property property) {
        Optional<Annotation> include = property.findAnnotation("JsonInclude")
            .or(() -> Annotations.findSimpleName(owner, "JsonInclude"));

        return include
            .map(annotation -> ((Enum<?>) Annotations.value(annotation, "value")).name())
            .filter(OmitNullInclusions::contains)
            .isPresent();
    }

    private static boolean isPrimitive(Type type) {
        return type instanceof Class<?> typeClass && typeClass.isPrimitive();
    }

    private static List<String> enumValues(Class<?> type) {
        Optional<Method> jsonValue = Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Annotations.hasSimpleName(method, "JsonValue"))
            .findFirst();

        List<String> values = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            values.add(jsonValue.isPresent() ? String.valueOf(invoke(jsonValue.get(), constant)) : ((Enum<?>) constant).name());
        }

        return values;
    }

    private static Object invoke(Method method, Object target) {
        try {
            method.trySetAccessible();
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new GenerationException("Could not read @JsonValue of " + target.getClass().getName(), e);
        }
    }

    private SortedMap<String, String> pages(List<Class<?>> roleClasses) {
        SortedMap<String, String> pages = new TreeMap<>();
        Map<String, Class<?>> pageClasses = new HashMap<>();

        for (Class<?> type : roleClasses) {
            Optional<Annotation> page = Annotations.find(type, Annotations.InertiaPage);
            if (page.isEmpty()) {
                continue;
            }

            if (type.getTypeParameters().length > 0) {
                throw new GenerationException(
                    "@InertiaPage class " + type.getName() + " has type parameters, which a page cannot be typed with."
                        + " Use a non-generic page class."
                );
            }

            String component = (String) Annotations.value(page.get(), "value");
            if (component.isBlank()) {
                throw new GenerationException("@InertiaPage of " + type.getName() + " has a blank component name");
            }

            Class<?> other = pageClasses.put(component, type);
            if (other != null) {
                throw new GenerationException(
                    "Component '" + component + "' is declared by both " + other.getName() + " and " + type.getName()
                );
            }

            pages.put(component, namesByClass.get(type));
        }

        return pages;
    }

    private List<String> typesAnnotatedWith(List<Class<?>> roleClasses, String annotation) {
        return roleClasses.stream()
            .filter(type -> Annotations.has(type, annotation))
            .map(namesByClass::get)
            .sorted()
            .toList();
    }

    private Optional<Set<String>> kotlinNullableAccessors(Class<?> type) {
        return kotlinNullableAccessorsByClass.computeIfAbsent(
            type,
            key -> Optional.ofNullable(KotlinNullability.nullableAccessors(key))
        );
    }
}
