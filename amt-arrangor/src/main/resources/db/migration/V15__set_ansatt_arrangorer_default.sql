-- Appen skriver ikke lenger ansatt.arrangorer; relasjonene ligger i ansatt_arrangor-tabellene.
-- Kolonnen beholdes til alle gamle pods er borte, og de krever en ikke-null JSON-verdi ved lesing.
-- Kolonnen droppes i en senere migrering.
-- Rollback: ALTER TABLE ansatt ALTER COLUMN arrangorer DROP DEFAULT;
ALTER TABLE ansatt
    ALTER COLUMN arrangorer SET DEFAULT '[]'::jsonb;
