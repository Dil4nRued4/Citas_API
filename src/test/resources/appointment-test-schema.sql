CREATE TABLE IF NOT EXISTS sites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(16) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS specialties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    duration_minutes INT NOT NULL,
    category VARCHAR(24) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (duration_minutes IN (30, 60)),
    CHECK (category IN ('GENERAL', 'ESPECIALISTA'))
);

CREATE TABLE IF NOT EXISTS professional_profiles (
    user_id BIGINT PRIMARY KEY,
    doctor_type VARCHAR(24) NOT NULL,
    description VARCHAR(500) NOT NULL,
    license_code VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS professional_specialties (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    primary_assignment BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (professional_id, specialty_id),
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    FOREIGN KEY (specialty_id) REFERENCES specialties (id)
);

CREATE TABLE IF NOT EXISTS professional_sites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    UNIQUE (professional_id, site_id),
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    FOREIGN KEY (site_id) REFERENCES sites (id)
);

CREATE TABLE IF NOT EXISTS availability_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    seed_key VARCHAR(96) UNIQUE,
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    FOREIGN KEY (site_id) REFERENCES sites (id)
);

CREATE TABLE IF NOT EXISTS professional_weekly_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    iso_day_of_week TINYINT NOT NULL,
    starts_at_local TIME NOT NULL,
    ends_at_local TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    seed_key VARCHAR(96) UNIQUE,
    UNIQUE (professional_id, iso_day_of_week, site_id),
    CHECK (iso_day_of_week BETWEEN 1 AND 5),
    CHECK (starts_at_local < ends_at_local),
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    FOREIGN KEY (site_id) REFERENCES sites (id)
);

CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    specialty_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    appointment_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    duration_minutes INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    seed_key VARCHAR(96) UNIQUE,
    FOREIGN KEY (patient_id) REFERENCES users (id),
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    FOREIGN KEY (specialty_id) REFERENCES specialties (id),
    FOREIGN KEY (site_id) REFERENCES sites (id)
);

CREATE TABLE IF NOT EXISTS appointment_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    slot_starts_at TIMESTAMP(6) NOT NULL,
    UNIQUE (professional_id, slot_starts_at),
    FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE,
    FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id)
);
