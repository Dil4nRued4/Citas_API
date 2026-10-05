ALTER TABLE specialties
    ADD COLUMN category VARCHAR(24) NOT NULL DEFAULT 'ESPECIALISTA';

ALTER TABLE specialties
    ADD CONSTRAINT ck_specialties_category CHECK (category IN ('GENERAL', 'ESPECIALISTA'));

ALTER TABLE availability_blocks
    ADD COLUMN seed_key VARCHAR(96) NULL;

ALTER TABLE availability_blocks
    ADD CONSTRAINT uq_availability_seed_key UNIQUE (seed_key);

ALTER TABLE appointments
    ADD COLUMN seed_key VARCHAR(96) NULL;

ALTER TABLE appointments
    ADD CONSTRAINT uq_appointments_seed_key UNIQUE (seed_key);
