package dev.arkoder.inertia4j.examples.springbootreact;

import dev.arkoder.inertia4j.springboot3.Inertia;
import org.springframework.boot.SpringBootVersion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PagesController {
    private final Inertia inertia;

    public PagesController(Inertia inertia) {
        this.inertia = inertia;
    }

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return inertia.render("Home", Map.of("message", "Hello from Spring Boot"));
    }

    @GetMapping("/about")
    public ResponseEntity<String> about() {
        return inertia.render("About", Map.of("springBootVersion", SpringBootVersion.getVersion()));
    }
}
