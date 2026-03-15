package io.github.dornol.filekit.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FILEKIT_EXAMPLE_TEST", matches = "true")
class ExampleApplicationTests {

    @Test
    void contextLoads() {
    }

}
