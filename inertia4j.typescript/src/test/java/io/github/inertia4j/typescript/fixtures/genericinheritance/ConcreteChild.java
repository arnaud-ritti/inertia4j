package io.github.inertia4j.typescript.fixtures.genericinheritance;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public class ConcreteChild extends GenericBase<String> {
    private String name;

    public String getName() {
        return name;
    }
}
