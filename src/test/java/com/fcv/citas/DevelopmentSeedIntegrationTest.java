package com.fcv.citas;

import com.fcv.citas.adapter.out.persistence.DevelopmentSeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.environment=development",
        "app.seed.default-password=Test-Only-Seed-Password-2026"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Sql(scripts = "/appointment-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DevelopmentSeedIntegrationTest {
    @Autowired
    private DevelopmentSeed seed;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedCanRunTwiceWithoutAddingDuplicates() {
        seed.populate();
        seed.populate();

        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'PATIENT'")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'PROFESSIONAL'")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM professional_profiles WHERE doctor_type = 'GENERAL'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM professional_profiles WHERE doctor_type = 'ESPECIALISTA'")).isEqualTo(5);
        assertThat(jdbc.queryForList("""
                SELECT name FROM users
                WHERE email IN ('doctor1@example.com', 'doctor2@example.com', 'doctor3@example.com',
                                'doctor4@example.com', 'doctor5@example.com', 'doctor6@example.com')
                ORDER BY email
                """, String.class)).containsExactly(
                "Dr. Mateo Silvestre Fernández",
                "Dra. Valeria Sofía Montenegro",
                "Dr. Damián Alejandro Cruz",
                "Dra. Lucía Beatriz Navarro",
                "Dr. Esteban Gabriel Pineda",
                "Dra. Camila Victoria Sotomayor");
        assertThat(count("""
                SELECT COUNT(*) FROM users u
                JOIN professional_profiles p ON p.user_id = u.id
                WHERE (u.email IN ('doctor1@example.com', 'doctor2@example.com', 'doctor3@example.com')
                       AND p.doctor_type = 'GENERAL')
                   OR (u.email IN ('doctor4@example.com', 'doctor5@example.com', 'doctor6@example.com')
                       AND p.doctor_type = 'ESPECIALISTA')
                """)).isEqualTo(6);
        assertThat(jdbc.queryForList("""
                SELECT COUNT(*) FROM professional_weekly_schedules w
                JOIN users u ON u.id = w.professional_id
                WHERE u.email IN ('doctor1@example.com', 'doctor2@example.com', 'doctor3@example.com',
                                  'doctor4@example.com', 'doctor5@example.com', 'doctor6@example.com',
                                  'doctor7@example.com', 'doctor8@example.com')
                GROUP BY w.professional_id
                """, Integer.class)).containsExactly(5, 5, 5, 5, 5, 5, 5, 5);
        assertThat(count("""
                SELECT COUNT(*) FROM professional_profiles p
                JOIN users u ON u.id = p.user_id
                JOIN professional_specialties ps ON ps.professional_id = p.user_id
                JOIN specialties s ON s.id = ps.specialty_id
                WHERE (u.email = 'doctor4@example.com' AND s.name = 'Cardiología')
                   OR (u.email = 'doctor5@example.com' AND s.name = 'Dermatología')
                   OR (u.email = 'doctor6@example.com' AND s.name = 'Pediatría')
                """)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM appointments WHERE status = 'APPROVED' AND appointment_type = 'GENERAL'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM appointments WHERE status = 'REQUESTED'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM appointments WHERE status = 'REJECTED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM availability_blocks")).isZero();
        assertThat(count("SELECT COUNT(*) FROM professional_weekly_schedules")).isEqualTo(8 * 5);
        assertThat(count("""
                SELECT COUNT(*) FROM professional_weekly_schedules
                WHERE iso_day_of_week BETWEEN 1 AND 5
                  AND starts_at_local = '07:00:00'
                  AND ends_at_local = '16:00:00'
                  AND time_zone = 'America/Bogota'
                """)).isEqualTo(8 * 5);
        assertThat(count("""
                SELECT COUNT(*) FROM professional_weekly_schedules w
                JOIN users u ON u.id = w.professional_id
                JOIN sites s ON s.id = w.site_id
                WHERE (u.email = 'doctor1@example.com' AND s.code = 'HIC')
                   OR (u.email = 'doctor2@example.com' AND s.code = 'ICV')
                   OR (u.email = 'doctor3@example.com' AND s.code = 'HIC')
                   OR (u.email = 'doctor4@example.com' AND s.code = 'HIC')
                   OR (u.email = 'doctor5@example.com' AND s.code = 'ICV')
                   OR (u.email = 'doctor6@example.com' AND s.code = 'ICV')
                   OR (u.email = 'doctor7@example.com' AND s.code = 'ICV')
                   OR (u.email = 'doctor8@example.com' AND s.code = 'HIC')
                """)).isEqualTo(8 * 5);
        assertThat(count("""
                SELECT COUNT(*) FROM appointment_slots s
                JOIN appointments a ON a.id = s.appointment_id
                WHERE a.status = 'REQUESTED'
                """)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = 'paciente5@example.com'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM appointments a JOIN users u ON u.id = a.patient_id WHERE u.email = 'paciente5@example.com'"))
                .isZero();
    }

    @Test
    void weeklyScheduleRefreshDoesNotResetDemoAccountsOrAppointments() {
        seed.populate();
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'doctor1@example.com'", String.class);
        int appointments = count("SELECT COUNT(*) FROM appointments");

        seed.populateWeeklySchedules();
        seed.populateWeeklySchedules();

        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'doctor1@example.com'", String.class))
                .isEqualTo(passwordHash);
        assertThat(count("SELECT COUNT(*) FROM appointments")).isEqualTo(appointments);
        assertThat(count("SELECT COUNT(*) FROM professional_weekly_schedules")).isEqualTo(8 * 5);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
