package io.github.inertia4j.springboot3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        InertiaFilterDisabledMockMvcTest.FakeApplication.class,
        InertiaFilterDisabledMockMvcTest.FakeController.class
    },
    properties = "inertia.filter.enabled=false"
)
@AutoConfigureMockMvc
class InertiaFilterDisabledMockMvcTest {
    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {}

    @Controller
    static class FakeController {
        @GetMapping("/empty")
        ResponseEntity<Void> showNothing() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/text")
        @ResponseBody
        String text() {
            return "plain";
        }
    }

    @Test
    void emptyInertiaResponse_isKeptWhenTheFilterIsDisabled() throws Exception {
        mvc.perform(get("/empty").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void responses_haveNoVaryHeaderWhenTheFilterIsDisabled() throws Exception {
        mvc.perform(get("/text"))
            .andExpect(header().doesNotExist("Vary"));
    }
}
