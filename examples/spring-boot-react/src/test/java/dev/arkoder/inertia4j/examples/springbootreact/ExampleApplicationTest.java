package dev.arkoder.inertia4j.examples.springbootreact;

import dev.arkoder.inertia4j.springboot3.VersionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "inertia.vite.hot-file=build/no-dev-server.hot")
@AutoConfigureMockMvc
class ExampleApplicationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    VersionProvider versionProvider;

    @Test
    void homePage_loadsBuiltViteEntry() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(matchesPattern(
                "(?s).*<script type=\"module\" src=\"/build/assets/main-[\\w-]+\\.js\"></script>.*"
            )));
    }

    @Test
    void aboutPage_rendersAboutComponent() throws Exception {
        mvc.perform(get("/about").header("X-Inertia", "true").header("X-Inertia-Version", versionProvider.get()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.component").value("About"));
    }
}
