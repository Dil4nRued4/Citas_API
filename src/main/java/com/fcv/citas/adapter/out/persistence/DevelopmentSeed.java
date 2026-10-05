package com.fcv.citas.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class DevelopmentSeed implements ApplicationRunner {
    private static final ZoneId SEED_TIME_ZONE = ZoneId.of("America/Bogota");
    private static final List<SeedSpecialty> SPECIALTIES = List.of(
            new SeedSpecialty("Medicina General", 30, "GENERAL"),
            new SeedSpecialty("Medicina Familiar", 60, "GENERAL"),
            new SeedSpecialty("Consulta Particular", 30, "GENERAL"),
            new SeedSpecialty("Cardiología", 30, "ESPECIALISTA"),
            new SeedSpecialty("Dermatología", 30, "ESPECIALISTA"),
            new SeedSpecialty("Pediatría", 30, "ESPECIALISTA"),
            new SeedSpecialty("Ginecología", 60, "ESPECIALISTA"),
            new SeedSpecialty("Ortopedia y traumatología", 60, "ESPECIALISTA"));
    private static final List<SeedSite> SITES = List.of(
            new SeedSite("HIC", "Clínica Demostrativa Norte", "Dirección ficticia 100, Ciudad Ejemplo"),
            new SeedSite("ICV", "Centro Médico Demostrativo Sur", "Dirección ficticia 200, Ciudad Ejemplo"));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final boolean enabled;
    private final boolean weeklySchedulesEnabled;
    private final String environment;
    private final String defaultPassword;

    public DevelopmentSeed(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            Clock clock,
            @Value("${app.seed.on-start:false}") boolean enabled,
            @Value("${app.seed.weekly-schedules-on-start:false}") boolean weeklySchedulesEnabled,
            @Value("${app.environment:production}") String environment,
            @Value("${app.seed.default-password:}") String defaultPassword) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
        this.enabled = enabled;
        this.weeklySchedulesEnabled = weeklySchedulesEnabled;
        this.environment = environment;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enabled) {
            populate();
        } else if (weeklySchedulesEnabled) {
            populateWeeklySchedules();
        }
    }

    @Transactional
    public void populate() {
        requireDevelopmentEnvironment();
        if (defaultPassword == null || defaultPassword.length() < 12 || defaultPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("SEED_DEFAULT_PASSWORD must contain 12 to 72 UTF-8 bytes");
        }

        upsertUser("admin@example.com", "Administración Demo", "ADMIN");
        for (int patient = 1; patient <= 5; patient++) {
            upsertUser("paciente" + patient + "@example.com", "Paciente Demo " + patient, "PATIENT");
        }
        for (int doctor = 1; doctor <= 8; doctor++) {
            upsertUser("doctor" + doctor + "@example.com", doctorName(doctor), "PROFESSIONAL");
        }

        for (SeedSite site : SITES) {
                Long id = jdbc.query("SELECT id FROM sites WHERE code = ?", rs -> rs.next() ? rs.getLong(1) : null, site.code());
                if (id == null) {
                    jdbc.update("INSERT INTO sites (code, name, address, active) VALUES (?, ?, ?, TRUE)",
                            site.code(), site.name(), site.address());
                } else {
                    jdbc.update("UPDATE sites SET name = ?, address = ?, active = TRUE WHERE id = ?",
                            site.name(), site.address(), id);
                }
            }
            for (SeedSpecialty specialty : SPECIALTIES) {
                Long id = jdbc.query("SELECT id FROM specialties WHERE name = ?", rs -> rs.next() ? rs.getLong(1) : null,
                        specialty.name());
                if (id == null) {
                    jdbc.update("""
                            INSERT INTO specialties (name, duration_minutes, category, active)
                            VALUES (?, ?, ?, TRUE)
                            """, specialty.name(), specialty.durationMinutes(), specialty.category());
                } else {
                    jdbc.update("""
                            UPDATE specialties SET duration_minutes = ?, category = ?, active = TRUE WHERE id = ?
                            """, specialty.durationMinutes(), specialty.category(), id);
                }
        }

        createProfessional(1, "GENERAL", "Atención médica general de 30 minutos.", "DEMO-GEN-001",
                List.of("Medicina General"), List.of("HIC"));
        createProfessional(2, "GENERAL", "Atención de medicina familiar de 60 minutos.", "DEMO-GEN-002",
                List.of("Medicina Familiar"), List.of("ICV"));
        createProfessional(3, "GENERAL", "Consulta particular familiar con duración según especialidad.", "DEMO-GEN-003",
                List.of("Consulta Particular", "Medicina Familiar"), List.of("HIC", "ICV"));
        createProfessional(4, "ESPECIALISTA", "Consulta ficticia de cardiología.", "DEMO-ESP-004",
                List.of("Cardiología"), List.of("HIC"));
        createProfessional(5, "ESPECIALISTA", "Consulta ficticia de dermatología.", "DEMO-ESP-005",
                List.of("Dermatología"), List.of("ICV"));
        createProfessional(6, "ESPECIALISTA", "Consulta ficticia de pediatría.", "DEMO-ESP-006",
                List.of("Pediatría"), List.of("HIC", "ICV"));
        createProfessional(7, "ESPECIALISTA", "Consulta ficticia de ginecología.", "DEMO-ESP-007",
                List.of("Ginecología"), List.of("ICV"));
        createProfessional(8, "ESPECIALISTA", "Consulta ficticia de ortopedia y traumatología.", "DEMO-ESP-008",
                List.of("Ortopedia y traumatología"), List.of("HIC"));

        List<LocalDate> businessDays = nextBusinessDays(14);
        refreshSeedWeeklySchedules();
        refreshSeedAppointments(businessDays);
    }

    @Transactional
    public void populateWeeklySchedules() {
        requireDevelopmentEnvironment();
        refreshSeedWeeklySchedules();
    }

    private void requireDevelopmentEnvironment() {
        if (!environment.equalsIgnoreCase("development")) {
            throw new IllegalStateException("Development seed is disabled outside APP_ENV=development");
        }
    }

    private void upsertUser(String email, String name, String role) {
        String passwordHash = passwords.encode(defaultPassword);
        jdbc.update("""
                INSERT INTO users (name, email, password_hash, role, created_at)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = ?)
                """, name, email, passwordHash, role, clock.instant(), email);
        jdbc.update("""
                UPDATE users SET name = ?, password_hash = ?, role = ? WHERE email = ?
                """, name, passwordHash, role, email);
    }

    private void createProfessional(
            int number,
            String type,
            String description,
            String license,
            List<String> specialties,
            List<String> sites) {
        long userId = userId("doctor" + number + "@example.com");
        jdbc.update("""
                INSERT INTO professional_profiles (user_id, doctor_type, description, license_code, active)
                SELECT ?, ?, ?, ?, TRUE
                WHERE NOT EXISTS (SELECT 1 FROM professional_profiles WHERE user_id = ?)
                """, userId, type, description, license, userId);
        jdbc.update("""
                UPDATE professional_profiles
                SET doctor_type = ?, description = ?, license_code = ?, active = TRUE
                WHERE user_id = ?
                """, type, description, license, userId);
        jdbc.update("DELETE FROM professional_specialties WHERE professional_id = ?", userId);
        boolean primary = true;
        for (String name : specialties) {
            jdbc.update("""
                    INSERT INTO professional_specialties (professional_id, specialty_id, primary_assignment)
                    VALUES (?, ?, ?)
                    """, userId, specialtyId(name), primary);
            primary = false;
        }
        jdbc.update("DELETE FROM professional_sites WHERE professional_id = ?", userId);
        for (String code : sites) {
            jdbc.update("INSERT INTO professional_sites (professional_id, site_id) VALUES (?, ?)", userId, siteId(code));
        }
    }

    private void refreshSeedWeeklySchedules() {
        jdbc.update("DELETE FROM availability_blocks WHERE seed_key LIKE 's3-block-%'");
        jdbc.update("DELETE FROM professional_weekly_schedules WHERE seed_key LIKE 's3-weekly-%'");
        for (int doctor = 1; doctor <= 8; doctor++) {
            long userId = userId("doctor" + doctor + "@example.com");
            String siteCode = scheduleSiteCode(doctor);
            long siteId = siteId(siteCode);
            for (int day = 1; day <= 5; day++) {
                String key = "s3-weekly-" + doctor + "-" + day;
                jdbc.update("""
                        INSERT INTO professional_weekly_schedules
                            (professional_id, site_id, iso_day_of_week, starts_at_local, ends_at_local, time_zone, seed_key)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, userId, siteId, day, LocalTime.of(7, 0), LocalTime.of(16, 0), SEED_TIME_ZONE.getId(), key);
            }
        }
    }

    private String scheduleSiteCode(int doctor) {
        return switch (doctor) {
            case 2, 5, 6, 7 -> "ICV";
            case 1, 3, 4, 8 -> "HIC";
            default -> throw new IllegalArgumentException("Unsupported seeded doctor number: " + doctor);
        };
    }

    private void refreshSeedAppointments(List<LocalDate> days) {
        jdbc.update("""
                DELETE FROM appointment_slots
                WHERE appointment_id IN (SELECT id FROM appointments WHERE seed_key LIKE 's3-appointment-%')
                """);
        LocalDate first = days.getFirst();
        LocalDate second = days.get(1);
        LocalDate third = days.get(2);
        LocalDate fourth = days.get(3);
        seedAppointment("s3-appointment-general-1", "paciente1@example.com", "doctor1@example.com",
                "Medicina General", "HIC", first, 9, 0, "APPROVED", null);
        seedAppointment("s3-appointment-general-2", "paciente2@example.com", "doctor2@example.com",
                "Medicina Familiar", "ICV", second, 10, 0, "APPROVED", null);
        seedAppointment("s3-appointment-general-3", "paciente3@example.com", "doctor3@example.com",
                "Consulta Particular", "HIC", first, 11, 0, "APPROVED", null);
        seedAppointment("s3-appointment-requested-1", "paciente1@example.com", "doctor4@example.com",
                "Cardiología", "HIC", first, 8, 30, "REQUESTED", null);
        seedAppointment("s3-appointment-requested-2", "paciente2@example.com", "doctor5@example.com",
                "Dermatología", "ICV", second, 10, 30, "REQUESTED", null);
        seedAppointment("s3-appointment-requested-3", "paciente3@example.com", "doctor6@example.com",
                "Pediatría", "ICV", third, 12, 0, "REQUESTED", null);
        seedAppointment("s3-appointment-rejected-1", "paciente4@example.com", "doctor7@example.com",
                "Ginecología", "ICV", fourth, 14, 30, "REJECTED", "Horario no disponible en la sede.");
    }

    private void seedAppointment(
            String seedKey,
            String patientEmail,
            String doctorEmail,
            String specialtyName,
            String siteCode,
            LocalDate date,
            int hour,
            int minute,
            String status,
            String reason) {
        long patientId = userId(patientEmail);
        long professionalId = userId(doctorEmail);
        long specialtyId = specialtyId(specialtyName);
        int duration = jdbc.queryForObject("SELECT duration_minutes FROM specialties WHERE id = ?", Integer.class, specialtyId);
        Instant startsAt = date.atTime(hour, minute).atZone(SEED_TIME_ZONE).toInstant();
        Instant endsAt = startsAt.plusSeconds(duration * 60L);
        Long appointmentId = jdbc.query("""
                SELECT id FROM appointments WHERE seed_key = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, seedKey);
        if (appointmentId == null) {
            jdbc.update("""
                        INSERT INTO appointments
                            (patient_id, professional_id, specialty_id, site_id, appointment_type, status,
                             duration_minutes, starts_at, ends_at, rejection_reason, created_at, seed_key)
                        SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                        WHERE NOT EXISTS (SELECT 1 FROM appointments WHERE seed_key = ?)
                        """, patientId, professionalId, specialtyId, siteId(siteCode),
                        jdbc.queryForObject("SELECT doctor_type FROM professional_profiles WHERE user_id = ?", String.class, professionalId),
                        status, duration, startsAt, endsAt, reason, clock.instant(), seedKey, seedKey);
            appointmentId = jdbc.queryForObject("SELECT id FROM appointments WHERE seed_key = ?", Long.class, seedKey);
        } else {
            jdbc.update("""
                    UPDATE appointments SET patient_id = ?, professional_id = ?, specialty_id = ?, site_id = ?,
                        appointment_type = (SELECT doctor_type FROM professional_profiles WHERE user_id = ?),
                        status = ?, duration_minutes = ?, starts_at = ?, ends_at = ?, rejection_reason = ?
                    WHERE id = ?
                    """, patientId, professionalId, specialtyId, siteId(siteCode), professionalId, status,
                    duration, startsAt, endsAt, reason, appointmentId);
        }
        if (!status.equals("REJECTED")) {
            for (int slot = 0; slot < duration / 30; slot++) {
                jdbc.update("""
                        INSERT INTO appointment_slots (appointment_id, professional_id, slot_starts_at)
                        VALUES (?, ?, ?)
                        """, appointmentId, professionalId, startsAt.plusSeconds(slot * 1800L));
            }
        }
    }

    private List<LocalDate> nextBusinessDays(int amount) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate date = LocalDate.now(clock).plusDays(1);
        while (result.size() < amount) {
            if (date.getDayOfWeek().getValue() <= 5) {
                result.add(date);
            }
            date = date.plusDays(1);
        }
        return result;
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long specialtyId(String name) {
        return jdbc.queryForObject("SELECT id FROM specialties WHERE name = ?", Long.class, name);
    }

    private long siteId(String code) {
        return jdbc.queryForObject("SELECT id FROM sites WHERE code = ?", Long.class, code);
    }

    private static String doctorName(int number) {
        return switch (number) {
            case 1 -> "Dr. Mateo Silvestre Fernández";
            case 2 -> "Dra. Valeria Sofía Montenegro";
            case 3 -> "Dr. Damián Alejandro Cruz";
            case 4 -> "Dra. Lucía Beatriz Navarro";
            case 5 -> "Dr. Esteban Gabriel Pineda";
            case 6 -> "Dra. Camila Victoria Sotomayor";
            case 7 -> "Dra. Mariana Cruz";
            case 8 -> "Dr. Daniel León";
            default -> throw new IllegalArgumentException("Unsupported seeded doctor number: " + number);
        };
    }

    private record SeedSpecialty(String name, int durationMinutes, String category) {}
    private record SeedSite(String code, String name, String address) {}
}
