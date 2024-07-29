package it.pagopa.helpdeskcommands

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.properties"])
class HelpDeskCommandsApplicationTest {

    @Test
    fun contextLoads() {
        assertTrue { true }
    }
}
