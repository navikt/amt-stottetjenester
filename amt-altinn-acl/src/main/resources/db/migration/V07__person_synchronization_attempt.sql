ALTER TABLE person
    ADD COLUMN synchronization_attempt BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN applied_synchronization_attempt BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT person_synchronization_attempt_order
        CHECK (
            applied_synchronization_attempt >= 0
            AND applied_synchronization_attempt <= synchronization_attempt
        );

-- Manuell rollback:
-- ALTER TABLE person DROP CONSTRAINT person_synchronization_attempt_order;
-- ALTER TABLE person DROP COLUMN applied_synchronization_attempt, DROP COLUMN synchronization_attempt;
