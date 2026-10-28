package no.nav.amt.person.service.clients.pdl

object PdlClientTestData {
    const val NULL_ERROR = "- data i respons er null \n"
    const val ERROR_PREFIX = "Feilmeldinger i respons fra pdl:\n"

    val minimalFeilRespons =
        """
        {
            "errors": [
                {
                    "message": "Ikke tilgang til å se person",
                    "locations": [
                        {
                            "line": 2,
                            "column": 5
                        }
                    ],
                    "path": [
                        "hentPerson"
                    ],
                    "extensions": {
                        "code": "unauthorized",
                        "details": {
                            "type": "abac-deny",
                            "cause": "cause-0001-manglerrolle",
                            "policy": "adressebeskyttelse_strengt_fortrolig_adresse"
                        },
                        "classification": "ExecutionAborted"
                    }
                }
            ]
        }
        """.trimIndent()

    val flereFeilRespons =
        """
        {
            "errors": [
                {
                    "message": "Ikke tilgang til å se person",
                    "extensions": {
                        "code": "unauthorized",
                        "details": {
                            "type": "abac-deny",
                            "cause": "cause-0001-manglerrolle",
                            "policy": "adressebeskyttelse_strengt_fortrolig_adresse"
                        }
                    }
                },
                {
                    "message": "Test",
                    "extensions": {
                        "code": "unauthorized",
                        "details": {
                            "type": "abac-deny",
                            "cause": "cause-0001-manglerrolle",
                            "policy": "adressebeskyttelse_strengt_fortrolig_adresse"
                        }
                    }
                }
            ]
        }
        """.trimIndent()

    val gyldigRespons =
        """
        {
        	"errors": null,
        	"data": {
        		"hentPerson": {
        			"navn": [
        				{
        					"fornavn": "Tester",
        					"mellomnavn": "Test",
        					"etternavn": "Testersen"
        				}
        			],
        			"telefonnummer": [
        				{
        					"landskode": "+47",
        					"nummer": "98765432",
        					"prioritet": 2
        				},
        				{
        					"landskode": "+47",
        					"nummer": "12345678",
        					"prioritet": 1
        				}
        			],
        			"adressebeskyttelse": [
        				{
        					"gradering": "FORTROLIG"
        				}
        			],
        			"bostedsadresse": [
        				{
        					"matrikkeladresse": {
        						"tilleggsnavn": "Storgården",
        						"postnummer": "0484"
        					}
        				}
        			],
        			"oppholdsadresse": [],
        			"kontaktadresse": [
        				{
        					"postboksadresse": {
        						"postboks": "Postboks 1234",
        						"postnummer": "0484"
        					}
        				}
        			]
        		},
        		"hentIdenter": {
        		  "identer": [
        			{
        			  "ident": "00000000000",
        			  "historisk": false,
        			  "gruppe": "FOLKEREGISTERIDENT"
        			}
        		  ]
        		}
        	}
        }
        """.trimIndent()

    val fodselsarRespons =
        """
        {
        	"errors": null,
        	"data": {
        		"hentPerson": {
        			"foedselsdato": [
        				{
        		  			"foedselsaar": 1976
        				}
        			]
        		}
        	}
        }
        """.trimIndent()

    val telefonResponse =
        """
        {
          "data": {
            "hentPerson": {
        		"telefonnummer": [
        			{
        				"landskode": "+47",
        				"nummer": "98765432",
        				"prioritet": 2
        			},
        			{
        				"landskode": "+47",
        				"nummer": "12345678",
        				"prioritet": 1
        			}
        		]
            }
          },
          "extensions": {
            "warnings": [
              {
                "query": "hentPerson",
                "id": "tilgangsstyring",
                "message": "behandlingsnummer_header_missing",
                "details": "header for 'behandlingsnummer' mangler"
              }
            ]
          }
        }
        """.trimIndent()
}
