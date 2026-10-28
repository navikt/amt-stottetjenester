package no.nav.arrangor.arrangor.model

import no.nav.arrangor.domain.Arrangor
import java.util.UUID

data class ArrangorMedOverordnetArrangor(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangor: Arrangor?,
)
