package io.github.inertia4j.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Resolves special prop values ({@link DeferredProp}, {@link MergeProp} and lazy {@link Supplier} values)
 * according to the incoming request, and computes the merge and deferred props metadata of the page object.
 */
class PropsResolver {
    private final boolean partial;
    private final List<String> only;
    private final List<String> except;
    private final List<String> reset;

    final Map<String, Object> resolvedProps = new LinkedHashMap<>();
    final List<String> mergeProps = new ArrayList<>();
    final List<String> prependProps = new ArrayList<>();
    final List<String> deepMergeProps = new ArrayList<>();
    final List<String> matchPropsOn = new ArrayList<>();
    final Map<String, List<String>> deferredProps = new LinkedHashMap<>();

    PropsResolver(HttpRequest request, Map<String, Object> props) {
        this.partial = request.getHeader("X-Inertia-Partial-Component") != null;
        this.only = parseHeaderList(request.getHeader("X-Inertia-Partial-Data"));
        this.except = parseHeaderList(request.getHeader("X-Inertia-Partial-Except"));
        this.reset = parseHeaderList(request.getHeader("X-Inertia-Reset"));

        props.forEach(this::resolve);
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

    private void resolve(String key, Object value) {
        if (!partial && value instanceof DeferredProp) {
            String group = ((DeferredProp<?>) value).getGroup();
            deferredProps.computeIfAbsent(group, k -> new ArrayList<>()).add(key);
            collectMergeMetadata(key, value);
            return;
        }

        if (!isIncluded(key)) {
            return;
        }

        resolvedProps.put(key, resolveValue(value));
        collectMergeMetadata(key, value);
    }

    private boolean isIncluded(String key) {
        if (!partial) {
            return true;
        }

        if (!only.isEmpty() && !only.contains(key)) {
            return false;
        }

        return !except.contains(key);
    }

    private void collectMergeMetadata(String key, Object value) {
        if (!(value instanceof MergeableProp)) {
            return;
        }

        MergeableProp<?> prop = (MergeableProp<?>) value;
        if (!prop.shouldMerge()) {
            return;
        }

        if (reset.contains(key)) {
            return;
        }

        prop.matchOnFields().forEach(field -> matchPropsOn.add(key + "." + field));

        if (prop.shouldDeepMerge()) {
            deepMergeProps.add(key);
            return;
        }

        if (prop.appendsAtRoot()) {
            mergeProps.add(key);
        }
        prop.appendPaths().forEach(path -> mergeProps.add(key + "." + path));

        if (prop.prependsAtRoot()) {
            prependProps.add(key);
        }
        prop.prependPaths().forEach(path -> prependProps.add(key + "." + path));
    }

    private static Object resolveValue(Object value) {
        if (value instanceof DeferredProp) {
            return resolveValue(((DeferredProp<?>) value).resolve());
        }

        if (value instanceof MergeProp) {
            return resolveValue(((MergeProp<?>) value).getValue());
        }

        if (value instanceof Supplier) {
            return resolveValue(((Supplier<?>) value).get());
        }

        return value;
    }
}
