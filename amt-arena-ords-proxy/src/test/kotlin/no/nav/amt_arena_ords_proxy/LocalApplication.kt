package no.nav.amt_arena_ords_proxy

import org.springframework.boot.SpringApplication

fun main(args: Array<String>) {
	val application = SpringApplication(Application::class.java)
	application.setAdditionalProfiles("local")
	application.run(*args)
}
