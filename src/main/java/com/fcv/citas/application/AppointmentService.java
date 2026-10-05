package com.fcv.citas.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

@Service
public class AppointmentService {
    private static final Duration SLOT_SIZE = Duration.ofMinutes(30);
    private static final RowMapper<Professional> PROFESSIONAL_ROW = (rs, row) -> new Professional(
            rs.getLong("user_id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("doctor_type"),
            rs.getString("description"),
            rs.getString("license_code"),
            rs.getBoolean("active"));
    private static final RowMapper<Appointment> APPOINTMENT_ROW = (rs, row) -> new Appointment(
            rs.getLong("id"),
            rs.getLong("patient_id"),
            rs.getLong("professional_id"),
            rs.getString("professional_name"),
            rs.getLong("specialty_id"),
            rs.getString("specialty_name"),
            rs.getLong("site_id"),
            rs.getString("site_name"),
            rs.getString("appointment_type"),
            rs.getString("status"),
            rs.getInt("duration_minutes"),
            rs.getTimestamp("starts_at").toInstant(),
            rs.getTimestamp("ends_at").toInstant(),
            rs.getString("rejection_reason"));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AppointmentService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Specialty> specialties() {
        return jdbc.query("""
                SELECT id, name, duration_minutes, category
                FROM specialties
                WHERE active = TRUE
                ORDER BY name
                """, (rs, row) -> new Specialty(rs.getLong("id"), rs.getString("name"),
                rs.getInt("duration_minutes"), rs.getString("category")));
    }

    @Transactional(readOnly = true)
    public List<Site> sites() {
        return jdbc.query("""
                SELECT id, code, name, address
                FROM sites
                WHERE active = TRUE
                ORDER BY name
                """, (rs, row) -> new Site(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address")));
    }

    @Transactional(readOnly = true)
    public List<ProfessionalView> professionals(String type, Long specialtyId) {
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        if (normalizedType != null && !Set.of("GENERAL", "ESPECIALISTA").contains(normalizedType)) {
            throw badRequest("type must be GENERAL or ESPECIALISTA");
        }
        String query = """
                SELECT p.user_id, u.name, u.email, p.doctor_type, p.description, p.license_code, p.active
                FROM professional_profiles p
                JOIN users u ON u.id = p.user_id
                WHERE p.active = TRUE
                  AND (? IS NULL OR p.doctor_type = ?)
                  AND (? IS NULL OR EXISTS (
                    SELECT 1 FROM professional_specialties ps
                    WHERE ps.professional_id = p.user_id AND ps.specialty_id = ?
                  ))
                ORDER BY u.name
                """;
        return jdbc.query(query, PROFESSIONAL_ROW, normalizedType, normalizedType, specialtyId, specialtyId)
                .stream().map(this::professionalView).toList();
    }

    @Transactional
    public ProfessionalView createProfessional(ProfessionalRequest request) {
        validateProfessionalRequest(request, true);
        Long userId = request.userId();
        String userRole;
        try {
            userRole = jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw notFound("User not found");
        }
        if (!Set.of("PATIENT", "PROFESSIONAL").contains(userRole)) {
            throw conflict("An ADMIN account cannot be assigned as a professional");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM professional_profiles WHERE user_id = ?", Integer.class, userId) > 0) {
            throw conflict("Professional profile already exists");
        }
        jdbc.update("UPDATE users SET role = 'PROFESSIONAL' WHERE id = ?", userId);
        jdbc.update("""
                INSERT INTO professional_profiles (user_id, doctor_type, description, license_code, active)
                VALUES (?, ?, ?, ?, TRUE)
                """, userId, request.type(), request.description().trim(), request.licenseCode().trim());
        replaceAssignments(userId, request.specialtyIds(), request.siteIds());
        return findProfessional(userId);
    }

    @Transactional
    public ProfessionalView updateProfessional(long userId, ProfessionalRequest request) {
        validateProfessionalRequest(request, false);
        lockProfessional(userId);
        requireProfessional(userId);
        Integer appointmentsInProgress = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments
                WHERE professional_id = ? AND status IN ('APPROVED', 'REQUESTED')
                """, Integer.class, userId);
        if (appointmentsInProgress != null && appointmentsInProgress > 0) {
            throw conflict("A professional with committed appointments cannot change assignments");
        }
        if (request.userId() != userId) {
            throw badRequest("userId cannot be changed");
        }
        String licenseCode = request.licenseCode() == null || request.licenseCode().isBlank()
                ? findProfessionalRecord(userId).licenseCode()
                : request.licenseCode().trim();
        jdbc.update("""
                UPDATE professional_profiles
                SET doctor_type = ?, description = ?, license_code = ?
                WHERE user_id = ?
                """, request.type(), request.description().trim(), licenseCode, userId);
        replaceAssignments(userId, request.specialtyIds(), request.siteIds());
        return findProfessional(userId);
    }

    @Transactional
    public void deactivateProfessional(long userId) {
        lockProfessional(userId);
        requireProfessional(userId);
        Integer appointmentsInProgress = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments
                WHERE professional_id = ? AND status IN ('APPROVED', 'REQUESTED')
                """, Integer.class, userId);
        if (appointmentsInProgress != null && appointmentsInProgress > 0) {
            throw conflict("A professional with committed appointments cannot be deactivated");
        }
        jdbc.update("UPDATE professional_profiles SET active = FALSE WHERE user_id = ?", userId);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlock> ownBlocks(long professionalId, Instant from, Instant to) {
        validateRange(from, to);
        requireActiveProfessional(professionalId);
        return jdbc.query("""
                SELECT b.id, b.professional_id, b.site_id, s.name AS site_name, b.starts_at, b.ends_at
                FROM availability_blocks b JOIN sites s ON s.id = b.site_id
                WHERE b.professional_id = ? AND b.starts_at < ? AND b.ends_at > ?
                ORDER BY b.starts_at
                """, (rs, row) -> block(rs), professionalId, to, from);
    }

    @Transactional
    public AvailabilityBlock createBlock(long professionalId, BlockRequest request) {
        validateBlock(request);
        lockProfessional(professionalId);
        requireActiveProfessional(professionalId);
        requireSiteAssignment(professionalId, request.siteId());
        rejectBlockOverlap(professionalId, request.startsAt(), request.endsAt(), null);
        long id = insertId("""
                INSERT INTO availability_blocks
                    (professional_id, site_id, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, professionalId, request.siteId(), request.startsAt(), request.endsAt(), clock.instant());
        return findBlock(id);
    }

    @Transactional
    public AvailabilityBlock updateBlock(long professionalId, long blockId, BlockRequest request) {
        validateBlock(request);
        lockProfessional(professionalId);
        requireActiveProfessional(professionalId);
        AvailabilityBlock current = findBlock(blockId);
        if (current.professionalId() != professionalId) {
            throw forbidden("Availability belongs to another professional");
        }
        requireNoCommittedAppointments(current.id());
        requireSiteAssignment(professionalId, request.siteId());
        rejectBlockOverlap(professionalId, request.startsAt(), request.endsAt(), blockId);
        jdbc.update("""
                UPDATE availability_blocks SET site_id = ?, starts_at = ?, ends_at = ?
                WHERE id = ? AND professional_id = ?
                """, request.siteId(), request.startsAt(), request.endsAt(), blockId, professionalId);
        return findBlock(blockId);
    }

    @Transactional
    public void deleteBlock(long professionalId, long blockId) {
        lockProfessional(professionalId);
        requireActiveProfessional(professionalId);
        AvailabilityBlock current = findBlock(blockId);
        if (current.professionalId() != professionalId) {
            throw forbidden("Availability belongs to another professional");
        }
        requireNoCommittedAppointments(blockId);
        jdbc.update("DELETE FROM availability_blocks WHERE id = ?", blockId);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlot> availableSlots(long professionalId, long specialtyId, long siteId, Instant from, Instant to) {
        validateRange(from, to);
        Professional professional = findProfessionalRecord(professionalId);
        if (!professional.active()) {
            throw notFound("Professional is not active");
        }
        Specialty specialty = findSpecialty(specialtyId);
        requireSpecialtyAssignment(professionalId, specialtyId);
        requireSiteAssignment(professionalId, siteId);
        int duration = specialty.durationMinutes();
        List<AvailabilityWindow> windows = jdbc.query("""
                SELECT b.id, b.professional_id, b.site_id, s.name AS site_name, b.starts_at, b.ends_at
                FROM availability_blocks b JOIN sites s ON s.id = b.site_id
                WHERE b.professional_id = ? AND b.site_id = ?
                  AND b.starts_at < ? AND b.ends_at > ?
                ORDER BY b.starts_at
                """, (rs, row) -> {
                    AvailabilityBlock block = block(rs);
                    return new AvailabilityWindow(block.siteId(), block.siteName(), block.startsAt(), block.endsAt());
                }, professionalId, siteId, to, from);
        windows.addAll(recurringWindows(professionalId, siteId, from, to));
        windows.sort(Comparator.comparing(AvailabilityWindow::startsAt));

        TreeMap<Instant, AvailableSlot> available = new TreeMap<>();
        int slotCount = duration / 30;
        Instant now = clock.instant();
        for (AvailabilityWindow window : windows) {
            Instant start = ceilToHalfHour(max(window.startsAt(), from));
            for (Instant candidate = start; candidate.plus(Duration.ofMinutes(duration)).compareTo(window.endsAt()) <= 0
                    && candidate.plus(Duration.ofMinutes(duration)).compareTo(to) <= 0;
                 candidate = candidate.plus(SLOT_SIZE)) {
                Instant end = candidate.plus(Duration.ofMinutes(duration));
                if (!candidate.isAfter(now)) {
                    continue;
                }
                boolean reserved = false;
                for (int slot = 0; slot < slotCount; slot++) {
                    Instant slotStart = candidate.plus(SLOT_SIZE.multipliedBy(slot));
                    if (jdbc.queryForObject("""
                            SELECT COUNT(*) FROM appointment_slots
                            WHERE professional_id = ? AND slot_starts_at = ?
                            """, Integer.class, professionalId, slotStart) > 0) {
                        reserved = true;
                        break;
                    }
                }
                if (!reserved) {
                    available.putIfAbsent(candidate, new AvailableSlot(professionalId, professional.name(), specialtyId,
                            specialty.name(), siteId, window.siteName(), candidate, end, duration, professional.type()));
                }
            }
        }
        return new ArrayList<>(available.values());
    }

    @Transactional
    public Appointment book(long patientId, BookingRequest request) {
        Instant start = request.startsAt();
        if (!start.isAfter(clock.instant()) || !isHalfHourAligned(start)) {
            throw badRequest("Appointment must start in the future on a 30-minute boundary");
        }
        lockProfessional(request.professionalId());
        Professional professional = findProfessionalRecord(request.professionalId());
        if (!professional.active()) {
            throw notFound("Professional is not active");
        }
        Specialty specialty = findSpecialty(request.specialtyId());
        requireSpecialtyAssignment(request.professionalId(), request.specialtyId());
        requireSiteAssignment(request.professionalId(), request.siteId());
        int duration = specialty.durationMinutes();
        Instant end = start.plus(Duration.ofMinutes(duration));
        Integer contained = jdbc.queryForObject("""
                SELECT COUNT(*) FROM availability_blocks
                WHERE professional_id = ? AND site_id = ? AND starts_at <= ? AND ends_at >= ?
                """, Integer.class, request.professionalId(), request.siteId(), start, end);
        if ((contained == null || contained == 0)
                && !isInsideWeeklySchedule(request.professionalId(), request.siteId(), start, end)) {
            throw conflict("Requested time is outside an available block");
        }
        String status = professional.type().equals("GENERAL") ? "APPROVED" : "REQUESTED";
        long appointmentId = insertId("""
                INSERT INTO appointments
                    (patient_id, professional_id, specialty_id, site_id, appointment_type, status,
                     duration_minutes, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, patientId, request.professionalId(), request.specialtyId(), request.siteId(),
                professional.type(), status, duration, start, end, clock.instant());
        try {
            int slots = duration / 30;
            for (int slot = 0; slot < slots; slot++) {
                jdbc.update("""
                        INSERT INTO appointment_slots (appointment_id, professional_id, slot_starts_at)
                        VALUES (?, ?, ?)
                        """, appointmentId, request.professionalId(), start.plus(SLOT_SIZE.multipliedBy(slot)));
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("This appointment slot has already been reserved");
        }
        return findAppointment(appointmentId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> patientAppointments(long patientId) {
        return jdbc.query(appointmentSelect() + " WHERE a.patient_id = ? ORDER BY a.starts_at DESC",
                APPOINTMENT_ROW, patientId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> adminAppointments(String status) {
        if (status != null && !Set.of("APPROVED", "REQUESTED", "REJECTED").contains(status)) {
            throw badRequest("status is invalid");
        }
        return jdbc.query(appointmentSelect() + " WHERE (? IS NULL OR a.status = ?) ORDER BY a.starts_at",
                APPOINTMENT_ROW, status, status);
    }

    @Transactional
    public Appointment decide(long appointmentId, DecisionRequest request) {
        String decision = request.decision().trim().toUpperCase();
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw badRequest("decision must be APPROVE or REJECT");
        }
        Appointment current = findAppointmentForUpdate(appointmentId);
        if (!current.type().equals("ESPECIALISTA") || !current.status().equals("REQUESTED")) {
            throw conflict("Only a REQUESTED specialized appointment can be decided");
        }
        if (decision.equals("REJECT") && (request.reason() == null || request.reason().isBlank())) {
            throw badRequest("A rejection reason is required");
        }
        String nextStatus = decision.equals("APPROVE") ? "APPROVED" : "REJECTED";
        jdbc.update("UPDATE appointments SET status = ?, rejection_reason = ? WHERE id = ?",
                nextStatus, decision.equals("REJECT") ? request.reason().trim() : null, appointmentId);
        if (nextStatus.equals("REJECTED")) {
            jdbc.update("DELETE FROM appointment_slots WHERE appointment_id = ?", appointmentId);
        }
        return findAppointment(appointmentId);
    }

    private void validateProfessionalRequest(ProfessionalRequest request, boolean requireLicense) {
        if (!Set.of("GENERAL", "ESPECIALISTA").contains(request.type())) {
            throw badRequest("type must be GENERAL or ESPECIALISTA");
        }
        if (request.description() == null || request.description().isBlank() || request.description().length() > 500
                || (requireLicense && (request.licenseCode() == null || request.licenseCode().isBlank()))
                || (request.licenseCode() != null && request.licenseCode().length() > 80)) {
            throw badRequest("description is required and licenseCode is required for new professionals");
        }
        if (request.specialtyIds() == null || request.specialtyIds().isEmpty()
                || request.siteIds() == null || request.siteIds().isEmpty()) {
            throw badRequest("At least one specialty and site are required");
        }
        requireAllExist("SELECT COUNT(*) FROM specialties WHERE id IN (%s)".formatted(placeholders(request.specialtyIds().size())),
                request.specialtyIds(), "Specialty not found");
        Integer compatibleSpecialties = jdbc.queryForObject(
                "SELECT COUNT(*) FROM specialties WHERE id IN (%s) AND category = ?".formatted(placeholders(request.specialtyIds().size())),
                Integer.class, append(request.specialtyIds(), request.type()));
        if (compatibleSpecialties == null || compatibleSpecialties != new HashSet<>(request.specialtyIds()).size()) {
            throw badRequest("Specialties must match the professional type");
        }
        requireAllExist("SELECT COUNT(*) FROM sites WHERE id IN (%s)".formatted(placeholders(request.siteIds().size())),
                request.siteIds(), "Site not found");
    }

    private void replaceAssignments(long professionalId, List<Long> specialtyIds, List<Long> siteIds) {
        Set<Long> uniqueSpecialties = new HashSet<>(specialtyIds);
        Set<Long> uniqueSites = new HashSet<>(siteIds);
        if (uniqueSpecialties.size() != specialtyIds.size() || uniqueSites.size() != siteIds.size()) {
            throw badRequest("Duplicate assignment");
        }
        jdbc.update("DELETE FROM professional_specialties WHERE professional_id = ?", professionalId);
        boolean primary = true;
        for (Long specialtyId : specialtyIds) {
            jdbc.update("""
                    INSERT INTO professional_specialties (professional_id, specialty_id, primary_assignment)
                    VALUES (?, ?, ?)
                    """, professionalId, specialtyId, primary);
            primary = false;
        }
        jdbc.update("DELETE FROM professional_sites WHERE professional_id = ?", professionalId);
        for (Long siteId : siteIds) {
            jdbc.update("INSERT INTO professional_sites (professional_id, site_id) VALUES (?, ?)", professionalId, siteId);
        }
    }

    private ProfessionalView professionalView(Professional professional) {
        List<Specialty> assignedSpecialties = jdbc.query("""
                SELECT s.id, s.name, s.duration_minutes, s.category
                FROM professional_specialties ps JOIN specialties s ON s.id = ps.specialty_id
                WHERE ps.professional_id = ? AND s.active = TRUE
                ORDER BY ps.primary_assignment DESC, s.name
                """, (rs, row) -> new Specialty(rs.getLong("id"), rs.getString("name"),
                rs.getInt("duration_minutes"), rs.getString("category")),
                professional.id());
        List<Site> assignedSites = jdbc.query("""
                SELECT s.id, s.code, s.name, s.address
                FROM professional_sites ps JOIN sites s ON s.id = ps.site_id
                WHERE ps.professional_id = ? AND s.active = TRUE
                ORDER BY s.name
                """, (rs, row) -> new Site(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address")),
                professional.id());
        return new ProfessionalView(professional.id(), professional.name(), professional.type(),
                professional.description(), professional.active(), assignedSpecialties, assignedSites);
    }

    private ProfessionalView findProfessional(long id) {
        return professionalView(findProfessionalRecord(id));
    }

    private Professional findProfessionalRecord(long id) {
        List<Professional> result = jdbc.query("""
                SELECT p.user_id, u.name, u.email, p.doctor_type, p.description, p.license_code, p.active
                FROM professional_profiles p JOIN users u ON u.id = p.user_id
                WHERE p.user_id = ?
                """, PROFESSIONAL_ROW, id);
        if (result.isEmpty()) {
            throw notFound("Professional not found");
        }
        return result.getFirst();
    }

    private Specialty findSpecialty(long id) {
        List<Specialty> result = jdbc.query("""
                SELECT id, name, duration_minutes, category FROM specialties WHERE id = ? AND active = TRUE
                """, (rs, row) -> new Specialty(rs.getLong("id"), rs.getString("name"),
                rs.getInt("duration_minutes"), rs.getString("category")), id);
        if (result.isEmpty()) {
            throw notFound("Specialty not found");
        }
        return result.getFirst();
    }

    private void requireProfessional(long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM professional_profiles WHERE user_id = ?", Integer.class, id) == 0) {
            throw notFound("Professional not found");
        }
    }

    private void requireActiveProfessional(long id) {
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM professional_profiles WHERE user_id = ? AND active = TRUE", Integer.class, id);
        if (active == null || active == 0) {
            throw forbidden("Professional account is inactive");
        }
    }

    private void requireSpecialtyAssignment(long professionalId, long specialtyId) {
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM professional_specialties ps
                JOIN specialties s ON s.id = ps.specialty_id
                WHERE ps.professional_id = ? AND ps.specialty_id = ? AND s.active = TRUE
                """, Integer.class, professionalId, specialtyId) == 0) {
            throw notFound("Specialty is not assigned to this professional");
        }
    }

    private void requireSiteAssignment(long professionalId, long siteId) {
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM professional_sites ps
                JOIN sites s ON s.id = ps.site_id
                WHERE ps.professional_id = ? AND ps.site_id = ? AND s.active = TRUE
                """, Integer.class, professionalId, siteId) == 0) {
            throw forbidden("Professional is not assigned to this site");
        }
    }

    private void rejectBlockOverlap(long professionalId, Instant start, Instant end, Long excludedId) {
        Integer overlap = jdbc.queryForObject("""
                SELECT COUNT(*) FROM availability_blocks
                WHERE professional_id = ? AND starts_at < ? AND ends_at > ?
                  AND (? IS NULL OR id <> ?)
                """, Integer.class, professionalId, end, start, excludedId, excludedId);
        if (overlap != null && overlap > 0) {
            throw conflict("Availability blocks cannot overlap");
        }
    }

    private void requireNoCommittedAppointments(long blockId) {
        Integer committed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a JOIN availability_blocks b
                  ON a.professional_id = b.professional_id AND a.site_id = b.site_id
                WHERE b.id = ? AND a.status IN ('APPROVED', 'REQUESTED')
                  AND a.starts_at < b.ends_at AND a.ends_at > b.starts_at
                """, Integer.class, blockId);
        if (committed != null && committed > 0) {
            throw conflict("Availability has committed appointments and cannot be changed");
        }
    }

    private void lockProfessional(long professionalId) {
        List<Long> rows = jdbc.query("SELECT user_id FROM professional_profiles WHERE user_id = ? AND active = TRUE FOR UPDATE",
                (rs, row) -> rs.getLong(1), professionalId);
        if (rows.isEmpty()) {
            throw notFound("Professional not found");
        }
    }

    private void validateBlock(BlockRequest request) {
        if (request.startsAt() == null || request.endsAt() == null || request.siteId() <= 0) {
            throw badRequest("startsAt, endsAt and siteId are required");
        }
        if (!request.startsAt().isAfter(clock.instant())) {
            throw badRequest("Availability cannot be in the past");
        }
        if (!isHalfHourAligned(request.startsAt()) || !isHalfHourAligned(request.endsAt())
                || !request.endsAt().isAfter(request.startsAt())
                || Duration.between(request.startsAt(), request.endsAt()).toMinutes() % 30 != 0) {
            throw badRequest("Availability must align to the 30-minute grid");
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).toDays() > 31) {
            throw badRequest("A valid date range of at most 31 days is required");
        }
    }

    private void requireExists(String sql, long id, String message) {
        if (jdbc.queryForObject(sql, Integer.class, id) == 0) {
            throw notFound(message);
        }
    }

    private void requireAllExist(String sql, List<Long> ids, String message) {
        Integer count = jdbc.queryForObject(sql, Integer.class, ids.toArray());
        if (count == null || count != new HashSet<>(ids).size()) {
            throw notFound(message);
        }
    }

    private static Object[] append(List<Long> values, Object last) {
        Object[] args = new Object[values.size() + 1];
        for (int index = 0; index < values.size(); index++) {
            args[index] = values.get(index);
        }
        args[values.size()] = last;
        return args;
    }

    private AvailabilityBlock findBlock(long id) {
        List<AvailabilityBlock> result = jdbc.query("""
                SELECT b.id, b.professional_id, b.site_id, s.name AS site_name, b.starts_at, b.ends_at
                FROM availability_blocks b JOIN sites s ON s.id = b.site_id WHERE b.id = ?
                """, (rs, row) -> block(rs), id);
        if (result.isEmpty()) {
            throw notFound("Availability block not found");
        }
        return result.getFirst();
    }

    private static AvailabilityBlock block(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AvailabilityBlock(rs.getLong("id"), rs.getLong("professional_id"), rs.getLong("site_id"),
                rs.getString("site_name"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant());
    }

    private List<AvailabilityWindow> recurringWindows(long professionalId, long siteId, Instant from, Instant to) {
        List<WeeklySchedule> schedules = jdbc.query("""
                SELECT w.site_id, s.name AS site_name, w.iso_day_of_week, w.starts_at_local, w.ends_at_local, w.time_zone
                FROM professional_weekly_schedules w
                JOIN sites s ON s.id = w.site_id
                WHERE w.professional_id = ? AND w.site_id = ?
                ORDER BY w.iso_day_of_week, w.starts_at_local
                """, (rs, row) -> new WeeklySchedule(
                rs.getLong("site_id"),
                rs.getString("site_name"),
                rs.getInt("iso_day_of_week"),
                rs.getObject("starts_at_local", LocalTime.class),
                rs.getObject("ends_at_local", LocalTime.class),
                ZoneId.of(rs.getString("time_zone"))), professionalId, siteId);
        List<AvailabilityWindow> windows = new ArrayList<>();
        for (WeeklySchedule schedule : schedules) {
            LocalDate date = from.atZone(schedule.timeZone()).toLocalDate();
            LocalDate lastDate = to.atZone(schedule.timeZone()).toLocalDate();
            while (!date.isAfter(lastDate)) {
                if (date.getDayOfWeek().getValue() == schedule.isoDayOfWeek()) {
                    Instant startsAt = date.atTime(schedule.startsAt()).atZone(schedule.timeZone()).toInstant();
                    Instant endsAt = date.atTime(schedule.endsAt()).atZone(schedule.timeZone()).toInstant();
                    if (startsAt.isBefore(to) && endsAt.isAfter(from)) {
                        windows.add(new AvailabilityWindow(schedule.siteId(), schedule.siteName(), startsAt, endsAt));
                    }
                }
                date = date.plusDays(1);
            }
        }
        return windows;
    }

    private boolean isInsideWeeklySchedule(long professionalId, long siteId, Instant startsAt, Instant endsAt) {
        List<WeeklySchedule> schedules = jdbc.query("""
                SELECT w.site_id, s.name AS site_name, w.iso_day_of_week, w.starts_at_local, w.ends_at_local, w.time_zone
                FROM professional_weekly_schedules w
                JOIN sites s ON s.id = w.site_id
                WHERE w.professional_id = ? AND w.site_id = ?
                ORDER BY w.iso_day_of_week, w.starts_at_local
                """, (rs, row) -> new WeeklySchedule(
                rs.getLong("site_id"),
                rs.getString("site_name"),
                rs.getInt("iso_day_of_week"),
                rs.getObject("starts_at_local", LocalTime.class),
                rs.getObject("ends_at_local", LocalTime.class),
                ZoneId.of(rs.getString("time_zone"))), professionalId, siteId);
        for (WeeklySchedule schedule : schedules) {
            ZonedDateTime localStart = startsAt.atZone(schedule.timeZone());
            ZonedDateTime localEnd = endsAt.atZone(schedule.timeZone());
            if (localStart.getDayOfWeek().getValue() == schedule.isoDayOfWeek()
                    && localStart.toLocalDate().equals(localEnd.toLocalDate())
                    && !localStart.toLocalTime().isBefore(schedule.startsAt())
                    && !localEnd.toLocalTime().isAfter(schedule.endsAt())) {
                return true;
            }
        }
        return false;
    }

    private Appointment findAppointment(long id) {
        List<Appointment> result = jdbc.query(appointmentSelect() + " WHERE a.id = ?", APPOINTMENT_ROW, id);
        if (result.isEmpty()) {
            throw notFound("Appointment not found");
        }
        return result.getFirst();
    }

    private Appointment findAppointmentForUpdate(long id) {
        List<Long> ids = jdbc.query("SELECT id FROM appointments WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getLong("id"), id);
        if (ids.isEmpty()) {
            throw notFound("Appointment not found");
        }
        return findAppointment(id);
    }

    private static String appointmentSelect() {
        return """
                SELECT a.id, a.patient_id, a.professional_id, u.name AS professional_name,
                       a.specialty_id, sp.name AS specialty_name, a.site_id, st.name AS site_name,
                       a.appointment_type, a.status, a.duration_minutes, a.starts_at, a.ends_at,
                       a.rejection_reason
                FROM appointments a
                JOIN users u ON u.id = a.professional_id
                JOIN specialties sp ON sp.id = a.specialty_id
                JOIN sites st ON st.id = a.site_id
                """;
    }

    private long insertId(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated identifier");
        }
        return key.longValue();
    }

    private static Instant ceilToHalfHour(Instant instant) {
        Instant floor = instant.truncatedTo(ChronoUnit.HOURS)
                .plus(Duration.ofMinutes(30L * ((instant.getEpochSecond() / 1800) % 2)));
        return floor.isBefore(instant) ? floor.plus(SLOT_SIZE) : floor;
    }

    private static Instant max(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static boolean isHalfHourAligned(Instant instant) {
        return instant.getEpochSecond() % 1800 == 0 && instant.getNano() == 0;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static DomainException badRequest(String message) {
        return new DomainException(400, message);
    }

    private static DomainException forbidden(String message) {
        return new DomainException(403, message);
    }

    private static DomainException notFound(String message) {
        return new DomainException(404, message);
    }

    private static DomainException conflict(String message) {
        return new DomainException(409, message);
    }

    public record Specialty(long id, String name, int durationMinutes, String category) {}
    public record Site(long id, String code, String name, String address) {}
    public record Professional(long id, String name, String email, String type, String description,
                               String licenseCode, boolean active) {}
    public record ProfessionalView(long id, String name, String type, String description, boolean active,
                                   List<Specialty> specialties, List<Site> sites) {}
    public record ProfessionalRequest(long userId, String type, String description, String licenseCode,
                                      List<Long> specialtyIds, List<Long> siteIds) {}
    public record BlockRequest(long siteId, Instant startsAt, Instant endsAt) {}
    public record AvailabilityBlock(long id, long professionalId, long siteId, String siteName,
                                    Instant startsAt, Instant endsAt) {}
    private record AvailabilityWindow(long siteId, String siteName, Instant startsAt, Instant endsAt) {}
    private record WeeklySchedule(long siteId, String siteName, int isoDayOfWeek, LocalTime startsAt,
                                  LocalTime endsAt, ZoneId timeZone) {}
    public record BookingRequest(long professionalId, long specialtyId, long siteId, Instant startsAt) {}
    public record AvailableSlot(long professionalId, String professionalName, long specialtyId, String specialtyName,
                                long siteId, String siteName, Instant startsAt, Instant endsAt, int durationMinutes,
                                String appointmentType) {}
    public record Appointment(long id, long patientId, long professionalId, String professionalName, long specialtyId,
                              String specialtyName, long siteId, String siteName, String type, String status,
                              int durationMinutes, Instant startsAt, Instant endsAt, String rejectionReason) {}
    public record DecisionRequest(String decision, String reason) {}

    public static class DomainException extends RuntimeException {
        private final int status;

        public DomainException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
