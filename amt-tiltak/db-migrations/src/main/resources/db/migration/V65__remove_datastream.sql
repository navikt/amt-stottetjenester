-- Koordiner manuell stopp av Datastream rett før denne migrasjonen kjøres; migrasjonen stopper
-- ikke Datastream. Fjerning av slot kan frigi WAL-retensjon og kan ikke rulles tilbake. Hvis
-- migrasjonen feiler etter slot-fjerningen, kan Flyway-historikken kreve manuell repair.
DO
$$
DECLARE
    replication_slot record;
    publication_owner oid;
BEGIN
    SELECT slot_type, database, active
    INTO replication_slot
    FROM pg_replication_slots
    WHERE slot_name = 'ds_replication';

    IF FOUND THEN
        IF replication_slot.slot_type <> 'logical' THEN
            RAISE EXCEPTION 'Replication slot ds_replication must be logical';
        END IF;

        IF replication_slot.database IS DISTINCT FROM current_database() THEN
            RAISE EXCEPTION 'Replication slot ds_replication must belong to the current database';
        END IF;

        IF replication_slot.active THEN
            RAISE EXCEPTION 'Replication slot ds_replication must be inactive';
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_roles
            WHERE rolname = current_user
              AND (rolsuper OR rolreplication)
        ) THEN
            RAISE EXCEPTION 'Current user must be a superuser or have REPLICATION to drop slot ds_replication';
        END IF;
    END IF;

    SELECT pubowner
    INTO publication_owner
    FROM pg_publication
    WHERE pubname = 'ds_publication';

    IF FOUND
       AND NOT EXISTS (
           SELECT 1
           FROM pg_roles
           WHERE rolname = current_user
             AND rolsuper
       )
       AND NOT pg_has_role(current_user, publication_owner, 'USAGE') THEN
        RAISE EXCEPTION 'Current user must own publication ds_publication or be a superuser';
    END IF;
END
$$;

SELECT pg_drop_replication_slot('ds_replication')
WHERE EXISTS (
    SELECT 1
    FROM pg_replication_slots
    WHERE slot_name = 'ds_replication'
);

DROP PUBLICATION IF EXISTS ds_publication;
