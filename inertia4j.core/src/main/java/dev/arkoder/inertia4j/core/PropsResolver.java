package dev.arkoder.inertia4j.core;

import dev.arkoder.inertia4j.spi.OncePropMetadata;
import dev.arkoder.inertia4j.spi.ScrollPropMetadata;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Resolves props according to the incoming request, following the Inertia prop evaluation model,
 * and computes the prop metadata of the page object.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#prop-evaluation-model">Inertia prop evaluation model</a>
 */
class PropsResolver {
    static final String ErrorsKey = "errors";

    private final boolean inertia;
    private final boolean partial;
    private final List<String> only;
    private final List<String> except;
    private final List<String> reset;
    private final List<String> loadedOnceProps;
    private final boolean prependScrollItems;
    private final Clock clock;
    private final Consumer<RuntimeException> exceptionReporter;

    final Map<String, Object> resolvedProps;
    final List<String> mergeProps = new ArrayList<>();
    final List<String> prependProps = new ArrayList<>();
    final List<String> deepMergeProps = new ArrayList<>();
    final List<String> matchPropsOn = new ArrayList<>();
    final Map<String, ScrollPropMetadata> scrollProps = new LinkedHashMap<>();
    final Map<String, List<String>> deferredProps = new LinkedHashMap<>();
    final List<String> rescuedProps = new ArrayList<>();
    final Map<String, OncePropMetadata> onceProps = new LinkedHashMap<>();
    final List<String> sharedProps = new ArrayList<>();

    PropsResolver(
        HttpRequest request,
        InertiaRenderingOptions options,
        Clock clock,
        Consumer<RuntimeException> exceptionReporter,
        boolean exposeSharedPropKeys
    ) {
        this.inertia = InertiaHeaders.isInertia(request);
        this.partial = options.componentName.equals(request.getHeader(InertiaHeaders.PartialComponent));
        this.only = parseHeaderListOrNull(request.getHeader(InertiaHeaders.PartialData));
        this.except = parseHeaderList(request.getHeader(InertiaHeaders.PartialExcept));
        this.reset = parseHeaderList(request.getHeader(InertiaHeaders.Reset));
        this.loadedOnceProps = parseHeaderList(request.getHeader(InertiaHeaders.ExceptOnceProps));
        this.prependScrollItems = "prepend".equals(request.getHeader(InertiaHeaders.InfiniteScrollMergeIntent));
        this.clock = clock;
        this.exceptionReporter = exceptionReporter;

        Map<String, Object> shared = unpackDotProps(options.sharedProps);
        shared.putIfAbsent(ErrorsKey, errors(request, options.errors));

        if (exposeSharedPropKeys) {
            sharedProps.addAll(shared.keySet());
        }

        Map<String, Object> props = new LinkedHashMap<>(shared);
        props.putAll(unpackDotProps(options.props));
        props.computeIfPresent(ErrorsKey, (key, value) -> asAlwaysProp(value));

        this.resolvedProps = resolveProps(props, "", false);
    }

    static List<String> parseHeaderList(String header) {
        if (header == null) {
            return List.of();
        }

        return Arrays.stream(header.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static List<String> parseHeaderListOrNull(String header) {
        List<String> values = parseHeaderList(header);

        return values.isEmpty() ? null : values;
    }

    private static Object errors(HttpRequest request, Map<String, ?> errors) {
        String errorBag = request.getHeader(InertiaHeaders.ErrorBag);

        if (errors.isEmpty()) {
            return Map.of();
        }

        if (errorBag == null || errorBag.isEmpty()) {
            return errors;
        }

        return Map.of(errorBag, errors);
    }

    private static Object asAlwaysProp(Object value) {
        if (value instanceof InertiaProp) {
            return ((InertiaProp<?>) value).always();
        }

        return InertiaProps.always(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unpackDotProps(Map<String, Object> props) {
        Map<String, Object> unpacked = new LinkedHashMap<>();

        props.forEach((key, value) -> {
            if (!key.contains(".")) {
                unpacked.put(key, value);
                return;
            }

            String[] segments = key.split("\\.");
            Map<String, Object> parent = unpacked;
            for (int i = 0; i < segments.length - 1; i++) {
                Object child = resolveLazy(parent.get(segments[i]));
                Map<String, Object> childMap = child instanceof Map
                    ? new LinkedHashMap<>((Map<String, Object>) child)
                    : new LinkedHashMap<>();
                parent.put(segments[i], childMap);
                parent = childMap;
            }
            parent.put(segments[segments.length - 1], value);
        });

        return unpacked;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveProps(Map<String, Object> props, String prefix, boolean parentWasResolved) {
        Map<String, Object> resolved = new LinkedHashMap<>();

        props.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            resolveProp(resolved, key, path, value, parentWasResolved);
        });

        return resolved;
    }

    @SuppressWarnings("unchecked")
    private void resolveProp(Map<String, Object> resolved, String key, String path, Object value, boolean parentWasResolved) {
        InertiaProp<?> prop = value instanceof InertiaProp ? (InertiaProp<?>) value : null;

        if (!shouldIncludeInPartialResponse(prop, path, parentWasResolved)) {
            return;
        }

        if (prop != null && excludeFromInitialResponse(prop, path)) {
            return;
        }

        Object resolvedValue;
        try {
            resolvedValue = resolveLazy(prop != null ? prop.value() : value);
        } catch (RuntimeException exception) {
            if (prop == null || !prop.isRescuable() || !prop.isDeferred()) {
                throw exception;
            }

            exceptionReporter.accept(exception);
            rescuedProps.add(path);
            return;
        }

        if (resolvedValue instanceof InertiaProp) {
            resolveProp(resolved, key, path, resolvedValue, parentWasResolved);
            return;
        }

        if (prop != null && (!partial || isIncludedInPartialMetadata(path))) {
            collectMetadata(prop, path, resolvedValue);
        }

        if (hasStringKeys(resolvedValue)) {
            boolean childrenWereResolved = parentWasResolved || !(value instanceof Map);
            resolvedValue = resolveProps((Map<String, Object>) resolvedValue, path, childrenWereResolved);
        }

        resolved.put(key, resolvedValue);
    }

    private static boolean hasStringKeys(Object value) {
        if (!(value instanceof Map)) {
            return false;
        }

        return ((Map<?, ?>) value).keySet().stream().allMatch(key -> key instanceof String);
    }

    private static Object resolveLazy(Object value) {
        Object resolved = value;
        while (resolved instanceof Supplier) {
            resolved = ((Supplier<?>) resolved).get();
        }

        return resolved;
    }

    private boolean shouldIncludeInPartialResponse(InertiaProp<?> prop, String path, boolean parentWasResolved) {
        if (!partial) {
            return true;
        }

        if (prop != null && prop.isAlways()) {
            return true;
        }

        if (parentWasResolved) {
            return true;
        }

        if (only != null && !matchesOnly(path) && !leadsToOnly(path)) {
            return false;
        }

        return !matchesExcept(path);
    }

    private boolean isIncludedInPartialMetadata(String path) {
        if (only != null && !matchesOnly(path)) {
            return false;
        }

        return !matchesExcept(path);
    }

    private boolean matchesOnly(String path) {
        return only.stream().anyMatch(selected -> isSameOrDescendant(path, selected));
    }

    private boolean leadsToOnly(String path) {
        return only.stream().anyMatch(selected -> selected.startsWith(path + "."));
    }

    private boolean matchesExcept(String path) {
        return except.stream().anyMatch(excluded -> isSameOrDescendant(path, excluded));
    }

    private static boolean isSameOrDescendant(String path, String ancestor) {
        return path.equals(ancestor) || path.startsWith(ancestor + ".");
    }

    private boolean excludeFromInitialResponse(InertiaProp<?> prop, String path) {
        if (partial) {
            return false;
        }

        if (prop.isOptional() || prop.isDeferred()) {
            collectIgnoredPropMetadata(prop, path);
            return true;
        }

        if (inertia && wasAlreadyLoadedByClient(prop, path)) {
            collectOnceMetadata(prop, path);
            return true;
        }

        return false;
    }

    private void collectIgnoredPropMetadata(InertiaProp<?> prop, String path) {
        if (prop.isDeferred() && !wasAlreadyLoadedByClient(prop, path)) {
            deferredProps.computeIfAbsent(prop.group(), group -> new ArrayList<>()).add(path);
            collectMergeMetadata(prop, path, prop.isScroll());
        }

        if (prop.isOnce()) {
            collectOnceMetadata(prop, path);
        }
    }

    private boolean wasAlreadyLoadedByClient(InertiaProp<?> prop, String path) {
        return prop.isOnce() && !prop.isFresh() && loadedOnceProps.contains(onceKey(prop, path));
    }

    private void collectMetadata(InertiaProp<?> prop, String path, Object resolvedValue) {
        if (prop.isOnce()) {
            collectOnceMetadata(prop, path);
        }

        collectMergeMetadata(prop, path, prop.isScroll());

        if (prop.isScroll()) {
            ScrollMetadata metadata = prop.scrollMetadata(resolvedValue);
            scrollProps.put(path, new ScrollPropMetadata(
                metadata.getPageName(),
                metadata.getPreviousPage(),
                metadata.getNextPage(),
                metadata.getCurrentPage(),
                reset.contains(path)
            ));
        }
    }

    private void collectOnceMetadata(InertiaProp<?> prop, String path) {
        onceProps.put(onceKey(prop, path), new OncePropMetadata(path, expiresAt(prop)));
    }

    private static String onceKey(InertiaProp<?> prop, String path) {
        return prop.onceKey() != null ? prop.onceKey() : path;
    }

    private Long expiresAt(InertiaProp<?> prop) {
        if (prop.expiresAt() != null) {
            return prop.expiresAt().toEpochMilli();
        }

        if (prop.expiresIn() != null) {
            return clock.instant().plus(prop.expiresIn()).toEpochMilli();
        }

        return null;
    }

    private void collectMergeMetadata(InertiaProp<?> prop, String path, boolean mergeScrollItems) {
        if (!prop.shouldMerge()) {
            return;
        }

        if (reset.contains(path)) {
            return;
        }

        prop.matchOnFields().forEach(field -> matchPropsOn.add(path + "." + field));

        if (prop.shouldDeepMerge()) {
            deepMergeProps.add(path);
            return;
        }

        List<String> appendPaths = new ArrayList<>(prop.appendPaths());
        List<String> prependPaths = new ArrayList<>(prop.prependPaths());
        if (mergeScrollItems) {
            (prependScrollItems ? prependPaths : appendPaths).add(prop.scrollWrapper());
        }

        InertiaProp.RootStrategy rootStrategy = prop.rootStrategy();
        boolean appendsAtRoot = rootStrategy == InertiaProp.RootStrategy.APPEND
            || (rootStrategy == null && appendPaths.isEmpty() && prependPaths.isEmpty());

        if (appendsAtRoot) {
            mergeProps.add(path);
        }

        if (rootStrategy == InertiaProp.RootStrategy.PREPEND) {
            prependProps.add(path);
        }

        appendPaths.forEach(appendPath -> mergeProps.add(path + "." + appendPath));
        prependPaths.forEach(prependPath -> prependProps.add(path + "." + prependPath));
    }
}
