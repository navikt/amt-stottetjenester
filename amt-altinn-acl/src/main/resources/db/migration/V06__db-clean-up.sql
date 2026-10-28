DROP TABLE IF EXISTS rettigheter_cache;

-- Indeksopprydding basert på målinger mot prod 2026-09-26.

-- person.last_synchronized manglet indeks. getUnsynchronizedPersons filtrerer
-- og sorterer på kolonnen, og gjorde seq scan: 8220 scan leste 48,2 millioner
-- rader. Indeksen dekker både filter og sortering.
-- Ikke partiell: predikatet bruker now(), som ikke er immutable.
CREATE INDEX person_last_synchronized_idx ON person (last_synchronized);

-- person_norsk_ident (V04) duplerer person_norsk_ident_key fra UNIQUE-
-- constrainten: to identiske btree-indekser på samme kolonne, 344 kB hver.
-- Duplikatet droppes, ikke unique-indeksen — den bakker constrainten.
-- person_norsk_ident_key overtar oppslagene med samme plan.
--
-- Uten IF EXISTS med vilje: V04 oppretter indeksen, så mangler den, har
-- skjemaet driftet og migrasjonen skal feile synlig.
DROP INDEX person_norsk_ident;
