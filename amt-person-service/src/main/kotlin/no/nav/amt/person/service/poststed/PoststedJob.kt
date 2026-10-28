package no.nav.amt.person.service.poststed

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.amt.person.service.clients.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PoststedJob(
    val kodeverkClient: KodeverkClient,
    val poststedRepository: PoststedRepository,
    @Value($$"${app.poststed.run-on-startup:false}")
    private val runOnStartup: Boolean,
) : InitializingBean {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        if (runOnStartup) {
            log.info("Running PoststedJob on startup")
            run()
        }
    }

    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "PoststedJob", lockAtMostFor = "30m")
    fun run() {
        val sporingsId = UUID.randomUUID()
        log.info("Oppdaterer database med postnummer og poststed, $sporingsId")
        val postnummerListe = kodeverkClient.hentKodeverk()

        poststedRepository.oppdaterPoststed(
            oppdatertePostnummer = postnummerListe.toSet(),
            sporingsId = sporingsId,
        )

        log.info("Ferdig med å oppdatere poststed i database, $sporingsId")
    }
}
