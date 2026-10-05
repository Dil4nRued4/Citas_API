CREATE TABLE professional_weekly_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    professional_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    iso_day_of_week TINYINT NOT NULL,
    starts_at_local TIME NOT NULL,
    ends_at_local TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    seed_key VARCHAR(96) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_weekly_schedule_professional_day_site UNIQUE (professional_id, iso_day_of_week, site_id),
    CONSTRAINT uq_weekly_schedule_seed_key UNIQUE (seed_key),
    CONSTRAINT fk_weekly_schedule_professional FOREIGN KEY (professional_id) REFERENCES professional_profiles (user_id),
    CONSTRAINT fk_weekly_schedule_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT ck_weekly_schedule_day CHECK (iso_day_of_week BETWEEN 1 AND 5),
    CONSTRAINT ck_weekly_schedule_time CHECK (starts_at_local < ends_at_local),
    INDEX ix_weekly_schedule_professional_day (professional_id, iso_day_of_week)
) ENGINE=InnoDB;
