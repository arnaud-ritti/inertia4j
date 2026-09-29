package dev.arkoder.inertia4j.typescript.fixtures.springroutes;

import dev.arkoder.inertia4j.annotations.TypeScriptName;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@InertiaController
@TypeScriptName("Pages")
public class PagesController {
    @GetMapping("/")
    public String home() {
        return "";
    }

    @RequestMapping("/contact")
    public String contact() {
        return "";
    }
}
