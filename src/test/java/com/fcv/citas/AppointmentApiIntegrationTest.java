package com.fcv.citas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/appointment-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AppointmentApiIntegrationTest {
    private static final String PASSWORD = "Test-Appointment-Password-2026";
    private static final String ADMIN_EMAIL = "appointment-admin@example.test";
    private static final String PATIENT_EMAIL = "appointment-patient@example.test";
    private static final String DOCTOR_EMAIL = "appointment-doctor@example.test";
    private static final String OTHER_DOCTOR_EMAIL = "appointment-other-doctor@example.test";
    private static final ZoneId CLINIC_TIME_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String patientToken;
    private String adminToken;
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        clearFixtures();
        long adminId = createUser(ADMIN_EMAIL, "ADMIN");
        long patientId = createUser(PATIENT_EMAIL, "PATIENT");
        long professionalId = createUser(DOCTOR_EMAIL, "PROFESSIONAL");
        long otherProfessionalId = createUser(OTHER_DOCTOR_EMAIL, "PROFESSIONAL");
        jdbc.update("INSERT INTO sites (code, name, address, active) VALUES ('TEST', 'Test clinic', 'Test address', TRUE)");
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        jdbc.update("""
                INSERT INTO specialties (name, duration_minutes, category, active)
                VALUES ('Test General', 30, 'GENERAL', TRUE), ('Test Specialty', 60, 'ESPECIALISTA', TRUE)
                """);
        long generalId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        createProfessional(professionalId, "GENERAL", "Test general practitioner", "TEST-1");
        createProfessional(otherProfessionalId, "ESPECIALISTA", "Test specialist", "TEST-2");
        jdbc.update("INSERT INTO professional_specialties (professional_id, specialty_id, primary_assignment) VALUES (?, ?, TRUE)",
                professionalId, generalId);
        jdbc.update("INSERT INTO professional_specialties (professional_id, specialty_id, primary_assignment) VALUES (?, ?, TRUE)",
                otherProfessionalId, specialtyId);
        jdbc.update("INSERT INTO professional_sites (professional_id, site_id) VALUES (?, ?)", professionalId, siteId);
        jdbc.update("INSERT INTO professional_sites (professional_id, site_id) VALUES (?, ?)", otherProfessionalId, siteId);

        Instant tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC);
        insertBlock(professionalId, siteId, tomorrow, tomorrow.plusSeconds(4 * 3600L));
        insertBlock(otherProfessionalId, siteId, tomorrow.plusSeconds(2 * 3600L), tomorrow.plusSeconds(6 * 3600L));

        patientToken = token(PATIENT_EMAIL);
        adminToken = token(ADMIN_EMAIL);
        doctorToken = token(DOCTOR_EMAIL);
    }

    @Test
    void bookingInfersGeneralApprovalAndRejectsDuplicateSlot() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC);
        String requestBody = booking(professionalId, specialtyId, siteId, startsAt);

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.type").value("GENERAL"))
                .andExpect(jsonPath("$.durationMinutes").value(30));

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This appointment slot has already been reserved"));
    }

    @Test
    void concurrentRequestsCannotReserveTheSameSlot() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(9, 30).toInstant(ZoneOffset.UTC);
        String body = booking(professionalId, specialtyId, siteId, startsAt);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/appointments")
                                    .header("Authorization", bearer(patientToken))
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE starts_at = ?",
                    Integer.class, Timestamp.from(startsAt))).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void specializedBookingIsRequestedAndAdminDecisionIsOneWay() throws Exception {
        long professionalId = userId(OTHER_DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(10, 0).toInstant(ZoneOffset.UTC);
        MvcResult booked = mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andReturn();
        long appointmentId = objectMapper.readTree(booked.getResponse().getContentAsString()).path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slots WHERE appointment_id = ?",
                Integer.class, appointmentId)).isEqualTo(2);

        mockMvc.perform(post("/api/admin/appointments/" + appointmentId + "/decision")
                        .header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/appointments/" + appointmentId + "/decision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/admin/appointments/" + appointmentId + "/decision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reason\":\"No longer needed\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRejectionReleasesReservedSlots() throws Exception {
        long professionalId = userId(OTHER_DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC);
        MvcResult booked = mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();
        long appointmentId = objectMapper.readTree(booked.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/admin/appointments/" + appointmentId + "/decision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"reason\":\"Cambio de agenda\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Cambio de agenda"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slots WHERE appointment_id = ?",
                Integer.class, appointmentId)).isZero();
    }

    @Test
    void rejectsInvalidGridAndOverlappingBlock() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC);

        mockMvc.perform(post("/api/professional/availability")
                        .header("Authorization", bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlockRequest(
                                siteId, tomorrow.plusSeconds(15 * 60L), tomorrow.plusSeconds(90 * 60L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Availability must align to the 30-minute grid"));

        mockMvc.perform(post("/api/professional/availability")
                        .header("Authorization", bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlockRequest(
                                siteId, tomorrow.plusSeconds(3 * 3600L), tomorrow.plusSeconds(5 * 3600L)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Availability blocks cannot overlap"));
    }

    @Test
    void rejectsPastAvailabilityAndAppointmentStarts() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant past = LocalDate.now(ZoneOffset.UTC).minusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC);

        mockMvc.perform(post("/api/professional/availability")
                        .header("Authorization", bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlockRequest(
                                siteId, past, past.plusSeconds(2 * 3600L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Availability cannot be in the past"));

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, past)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Appointment must start in the future on a 30-minute boundary"));
    }

    @Test
    void availabilityRangeNeverReturnsIncompleteSixtyMinuteSlots() throws Exception {
        long professionalId = userId(OTHER_DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = startsAt.plusSeconds(30 * 60L);
        MvcResult result = mockMvc.perform(get("/api/availability")
                        .header("Authorization", bearer(patientToken))
                        .param("professionalId", Long.toString(professionalId))
                        .param("specialtyId", Long.toString(specialtyId))
                        .param("siteId", Long.toString(siteId))
                        .param("from", startsAt.toString())
                        .param("to", rangeEnd.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size()).isZero();
    }

    @Test
    void weeklyAvailabilityUsesBogotaWeekdaysAndRespectsSpecialtyDuration() throws Exception {
        long generalId = userId(DOCTOR_EMAIL);
        long specialistId = userId(OTHER_DOCTOR_EMAIL);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        long generalSpecialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        LocalDate generalDate = futureWeekday();
        LocalDate specialistDate = generalDate.plusDays(7);
        insertWeekdaySchedules(generalId, siteId);
        insertWeekdaySchedules(specialistId, siteId);

        JsonNode generalSlots = availableSlots(generalId, generalSpecialtyId, siteId, generalDate);
        assertThat(generalSlots.size()).isEqualTo(18);
        assertThat(Instant.parse(generalSlots.get(0).path("startsAt").asText())
                .atZone(CLINIC_TIME_ZONE).toLocalTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(Instant.parse(generalSlots.get(generalSlots.size() - 1).path("startsAt").asText())
                .atZone(CLINIC_TIME_ZONE).toLocalTime()).isEqualTo(LocalTime.of(15, 30));

        JsonNode specialistSlots = availableSlots(specialistId, specialtyId, siteId, specialistDate);
        assertThat(specialistSlots.size()).isEqualTo(17);
        assertThat(Instant.parse(specialistSlots.get(specialistSlots.size() - 1).path("startsAt").asText())
                .atZone(CLINIC_TIME_ZONE).toLocalTime()).isEqualTo(LocalTime.of(15, 0));

        LocalDate saturday = specialistDate.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY));
        assertThat(availableSlots(specialistId, specialtyId, siteId, saturday).size()).isZero();
    }

    @Test
    void bookingFromWeeklyScheduleAppearsInPatientsAppointments() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        LocalDate date = futureWeekday();
        insertWeekdaySchedules(professionalId, siteId);
        Instant startsAt = date.atTime(7, 0).atZone(CLINIC_TIME_ZONE).toInstant();

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startsAt").value(startsAt.toString()));

        MvcResult mine = mockMvc.perform(get("/api/appointments/mine")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode appointments = objectMapper.readTree(mine.getResponse().getContentAsString());
        assertThat(appointments).hasSize(1);
        assertThat(appointments.get(0).path("professionalName").asText()).isEqualTo("Synthetic PROFESSIONAL");
        assertThat(appointments.get(0).path("status").asText()).isEqualTo("APPROVED");
        assertThat(appointments.get(0).path("startsAt").asText()).isEqualTo(startsAt.toString());
    }

    @Test
    void rejectsWeeklyBookingOutsideLocalWorkHours() throws Exception {
        long professionalId = userId(DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        LocalDate date = futureWeekday();
        insertWeekdaySchedules(professionalId, siteId);
        Instant startsAt = date.atTime(16, 0).atZone(CLINIC_TIME_ZONE).toInstant();

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, startsAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Requested time is outside an available block"));
    }

    @Test
    void adminCanCreateAndUpdateProfessionalAssignments() throws Exception {
        long userId = userId(PATIENT_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test General'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        String createBody = """
                {"userId":%d,"type":"GENERAL","description":"Initial description","licenseCode":"DEMO-LICENSE",
                 "specialtyIds":[%d],"siteIds":[%d]}
                """.formatted(userId, specialtyId, siteId);

        mockMvc.perform(post("/api/admin/professionals")
                        .header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/professionals")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.description").value("Initial description"));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("PROFESSIONAL");

        mockMvc.perform(put("/api/admin/professionals/" + userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"type":"GENERAL","description":"Updated description",
                                 "specialtyIds":[%d],"siteIds":[%d]}
                                """.formatted(userId, specialtyId, siteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"));
        assertThat(jdbc.queryForObject("SELECT license_code FROM professional_profiles WHERE user_id = ?",
                String.class, userId)).isEqualTo("DEMO-LICENSE");
    }

    @Test
    void professionalCannotEditAnotherProfessionalsBlock() throws Exception {
        long otherProfessionalId = userId(OTHER_DOCTOR_EMAIL);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(10, 0).toInstant(ZoneOffset.UTC);
        long otherBlockId = insertBlock(otherProfessionalId, siteId, tomorrow, tomorrow.plusSeconds(2 * 3600L));

        mockMvc.perform(put("/api/professional/availability/" + otherBlockId)
                        .header("Authorization", bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlockRequest(
                                siteId, tomorrow, tomorrow.plusSeconds(2 * 3600L)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rejectsBookingOutsideAvailabilityBlock() throws Exception {
        long professionalId = userId(OTHER_DOCTOR_EMAIL);
        long specialtyId = jdbc.queryForObject("SELECT id FROM specialties WHERE name = 'Test Specialty'", Long.class);
        long siteId = jdbc.queryForObject("SELECT id FROM sites WHERE code = 'TEST'", Long.class);
        Instant startsAt = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(16, 0).toInstant(ZoneOffset.UTC);

        mockMvc.perform(post("/api/appointments").header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking(professionalId, specialtyId, siteId, startsAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Requested time is outside an available block"));
    }

    private void clearFixtures() {
        jdbc.update("DELETE FROM appointment_slots");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM professional_weekly_schedules");
        jdbc.update("DELETE FROM availability_blocks");
        jdbc.update("DELETE FROM professional_specialties");
        jdbc.update("DELETE FROM professional_sites");
        jdbc.update("DELETE FROM professional_profiles");
        jdbc.update("DELETE FROM specialties");
        jdbc.update("DELETE FROM sites");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    private long createUser(String email, String role) {
        jdbc.update("INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
                "Synthetic " + role, email, passwordEncoder.encode(PASSWORD), role, Timestamp.from(Instant.now()));
        return userId(email);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void createProfessional(long userId, String type, String description, String license) {
        jdbc.update("""
                INSERT INTO professional_profiles (user_id, doctor_type, description, license_code, active)
                VALUES (?, ?, ?, ?, TRUE)
                """, userId, type, description, license);
    }

    private long insertBlock(long professionalId, long siteId, Instant start, Instant end) {
        jdbc.update("""
                INSERT INTO availability_blocks (professional_id, site_id, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, professionalId, siteId, Timestamp.from(start), Timestamp.from(end), Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT MAX(id) FROM availability_blocks", Long.class);
    }

    private JsonNode availableSlots(long professionalId, long specialtyId, long siteId, LocalDate date) throws Exception {
        Instant from = date.atStartOfDay(CLINIC_TIME_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(CLINIC_TIME_ZONE).toInstant();
        MvcResult result = mockMvc.perform(get("/api/availability")
                        .header("Authorization", bearer(patientToken))
                        .param("professionalId", Long.toString(professionalId))
                        .param("specialtyId", Long.toString(specialtyId))
                        .param("siteId", Long.toString(siteId))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void insertWeekdaySchedules(long professionalId, long siteId) {
        IntStream.rangeClosed(1, 5).forEach(day -> jdbc.update("""
                INSERT INTO professional_weekly_schedules
                    (professional_id, site_id, iso_day_of_week, starts_at_local, ends_at_local, time_zone)
                VALUES (?, ?, ?, ?, ?, ?)
                """, professionalId, siteId, day, LocalTime.of(7, 0), LocalTime.of(16, 0), CLINIC_TIME_ZONE.getId()));
    }

    private LocalDate futureWeekday() {
        LocalDate date = LocalDate.now(CLINIC_TIME_ZONE).plusDays(8);
        while (date.getDayOfWeek().getValue() > 5) {
            date = date.plusDays(1);
        }
        return date;
    }

    private String token(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("accessToken").asText();
    }

    private String booking(long professionalId, long specialtyId, long siteId, Instant startsAt) throws Exception {
        return objectMapper.writeValueAsString(new BookingRequest(professionalId, specialtyId, siteId, startsAt));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record BookingRequest(long professionalId, long specialtyId, long siteId, Instant startsAt) {}
    private record BlockRequest(long siteId, Instant startsAt, Instant endsAt) {}
}
