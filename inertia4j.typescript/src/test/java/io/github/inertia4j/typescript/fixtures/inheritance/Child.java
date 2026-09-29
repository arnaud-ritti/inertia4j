package io.github.inertia4j.typescript.fixtures.inheritance;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public class Child extends Base {
    private String name;

    public String getName() {
        return name;
    }
}
