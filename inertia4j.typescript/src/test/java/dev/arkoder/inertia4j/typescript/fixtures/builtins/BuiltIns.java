package dev.arkoder.inertia4j.typescript.fixtures.builtins;

import dev.arkoder.inertia4j.annotations.InertiaForm;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@InertiaForm
public record BuiltIns(
    String text,
    char letter,
    UUID id,
    URI uri,
    int count,
    long big,
    double ratio,
    BigDecimal price,
    boolean active,
    Boolean flag,
    Instant createdAt,
    LocalDate day,
    Duration timeout,
    byte[] data,
    Object anything
) {}
