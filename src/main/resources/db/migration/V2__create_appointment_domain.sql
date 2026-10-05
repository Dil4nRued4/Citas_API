ALTER TABLE users
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'PATIENT';

CREATE TABLE sites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_sites_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE specialties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    duration_minutes INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_specialties_name UNIQUE (name),
    CONSTRAINT ck_specialties_duration CHECK (duration_minutes IN (30, 60))
) ENGINE=InnoDB;

CREATE TABLE professional_profiles (
    user_id BIGINT NOT NULL,
    doctor_type VARCHAR(24) NOT NULL,
    description VARCHAR(500) NOT NULL,
    license_code VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_professional_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_professional_profiles_type CHECK (doctor_type IN ('GENERAL', 'ESPECIALISTA'))
) ENGINE=InnoDB;

CREATE TABLE professional_specialties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    primary_assignment BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_professional_specialty UNIQUE (professional_id, specialty_id),
    CONSTRAINT fk_professional_specialties_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    CONSTRAINT fk_professional_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id)
) ENGINE=InnoDB;

CREATE TABLE professional_sites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_professional_site UNIQUE (professional_id, site_id),
    CONSTRAINT fk_professional_sites_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    CONSTRAINT fk_professional_sites_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE=InnoDB;

CREATE TABLE availability_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_availability_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    CONSTRAINT fk_availability_site FOREIGN KEY (site_id) REFERENCES sites (id),
    INDEX ix_availability_professional_time (professional_id, starts_at, ends_at)
) ENGINE=InnoDB;

CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    appointment_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    duration_minutes INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    rejection_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    CONSTRAINT fk_appointments_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id),
    CONSTRAINT fk_appointments_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT ck_appointments_type CHECK (appointment_type IN ('GENERAL', 'ESPECIALISTA')),
    CONSTRAINT ck_appointments_status CHECK (status IN ('APPROVED', 'REQUESTED', 'REJECTED')),
    CONSTRAINT ck_appointments_duration CHECK (duration_minutes IN (30, 60)),
    INDEX ix_appointments_patient_date (patient_id, starts_at),
    INDEX ix_appointments_status_date (status, starts_at)
) ENGINE=InnoDB;

CREATE TABLE appointment_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    slot_starts_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_appointment_reserved_slot UNIQUE (professional_id, slot_starts_at),
    CONSTRAINT fk_appointment_slots_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_slots_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    INDEX ix_appointment_slots_appointment (appointment_id)
) ENGINE=InnoDB;
