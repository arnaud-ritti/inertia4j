package dev.arkoder.inertia4j.typescript.fixtures.genericinheritance;

import dev.arkoder.inertia4j.annotations.InertiaForm;

@InertiaForm
public class ConcreteChild extends GenericBase<String> {
    private String name;

    public String getName() {
        return name;
    }
}
