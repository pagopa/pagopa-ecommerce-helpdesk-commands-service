package it.pagopa.helpdeskcommands

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertTrue


@SpringBootTest
@TestPropertySource(locations = ["classpath:application.properties"])
class HelpDeskCommandsApplicationTest {

    @Test
    fun contextLoads() {
        assertTrue { true }
    }
}
