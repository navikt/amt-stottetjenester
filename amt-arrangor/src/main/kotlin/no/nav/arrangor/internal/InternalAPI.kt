package no.nav.arrangor.internal

import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.kafka.ProducerService
import no.nav.common.job.JobRunner
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal")
class InternalAPI(
    private val ansattService: AnsattService,
    private val producerService: ProducerService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(InternalAPI::class.java)

    @GetMapping("/ansatte/republiser")
    fun republiserAnsatte(
        @RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
    ) {
        JobRunner.runAsync("republiser-ansatte") {
            republiserAlleAnsatte(startFromOffset ?: 0)
        }
    }

    private fun republiserAlleAnsatte(startOffset: Int = 0) {
        var offset = startOffset
        var ansatte: List<Ansatt>

        do {
            ansatte = ansattService.getAll(offset, 500)
            ansatte.forEach { ansatt ->
                transactionTemplate.executeWithoutResult {
                    producerService.publishAnsatt(ansatt)
                }
            }

            log.info("Republiserte ansatte fra offset $offset til ${offset + ansatte.size}")
            offset += ansatte.size
        } while (ansatte.isNotEmpty())
    }
}
