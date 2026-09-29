package dev.arkoder.inertia4j.typescript.fixtures.springroutes;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UsersController {
    @GetMapping
    public String index() {
        return "";
    }

    @GetMapping("/{id:\\d+}")
    public String show(@PathVariable long id) {
        return "";
    }

    @PostMapping
    public String store() {
        return "";
    }

    @RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public String update(@PathVariable long id) {
        return "";
    }

    @DeleteMapping("{id}/")
    public String destroy(@PathVariable long id) {
        return "";
    }

    @GetMapping("/{id}/files/{*path}")
    public String file(@PathVariable long id, @PathVariable String path) {
        return "";
    }

    @GetMapping("/legacy/**")
    public String legacy() {
        return "";
    }

    @GetMapping("/search")
    public String search() {
        return "";
    }

    @GetMapping("/search/{term}")
    public String search(@PathVariable String term) {
        return "";
    }

    public String helper() {
        return "";
    }
}
