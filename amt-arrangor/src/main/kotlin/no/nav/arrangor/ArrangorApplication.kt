package no.nav.arrangor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ArrangorApplication

fun main(args: Array<String>) {
    runApplication<ArrangorApplication>(*args)
}
