package dev.arkoder.inertia4j.typescript.fixtures.inheritance;

import dev.arkoder.inertia4j.annotations.InertiaForm;

@InertiaForm
public class Child extends Base {
    private String name;

    public String getName() {
        return name;
    }
}
