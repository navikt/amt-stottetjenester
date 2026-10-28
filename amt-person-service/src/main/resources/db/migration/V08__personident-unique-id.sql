alter table personident
    add constraint personident_ident_person_id_key unique (ident, person_id);
