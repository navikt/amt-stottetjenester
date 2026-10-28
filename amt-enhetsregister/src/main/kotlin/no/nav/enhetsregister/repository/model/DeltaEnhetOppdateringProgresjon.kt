package no.nav.enhetsregister.repository.model

import no.nav.enhetsregister.domain.EnhetType

data class DeltaEnhetOppdateringProgresjon(
    val oppdateringId: Int,
    val enhetType: EnhetType,
)
