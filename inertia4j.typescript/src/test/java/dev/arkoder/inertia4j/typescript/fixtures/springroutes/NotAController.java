package dev.arkoder.inertia4j.typescript.fixtures.springroutes;

import org.springframework.web.bind.annotation.GetMapping;

public class NotAController {
    @GetMapping("/ignored")
    public String ignored() {
        return "";
    }
}
