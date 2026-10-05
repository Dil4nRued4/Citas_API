package com.fcv.citas.adapter.in.web;

import com.fcv.citas.application.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/catalog/specialties")
    public List<AppointmentService.Specialty> specialties() {
        return appointments.specialties();
    }

    @GetMapping("/catalog/sites")
    public List<AppointmentService.Site> sites() {
        return appointments.sites();
    }

    @GetMapping("/professionals")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentService.ProfessionalView> professionals(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long specialtyId) {
        return appointments.professionals(type, specialtyId);
    }

    @GetMapping("/admin/professionals")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppointmentService.ProfessionalView> allProfessionals(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long specialtyId) {
        return appointments.professionals(type, specialtyId);
    }

    @PostMapping("/admin/professionals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentService.ProfessionalView> createProfessional(
            @Valid @RequestBody ProfessionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.createProfessional(request.toService()));
    }

    @PutMapping("/admin/professionals/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentService.ProfessionalView updateProfessional(
            @PathVariable long userId,
            @Valid @RequestBody ProfessionalRequest request) {
        return appointments.updateProfessional(userId, request.toService());
    }

    @DeleteMapping("/admin/professionals/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateProfessional(@PathVariable long userId) {
        appointments.deactivateProfessional(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/professional/availability")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public List<AppointmentService.AvailabilityBlock> ownAvailability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return appointments.ownBlocks(user.id(), from, to);
    }

    @PostMapping("/professional/availability")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public ResponseEntity<AppointmentService.AvailabilityBlock> createAvailability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointments.createBlock(user.id(), request.toService()));
    }

    @PutMapping("/professional/availability/{id}")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AppointmentService.AvailabilityBlock updateAvailability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id,
            @Valid @RequestBody BlockRequest request) {
        return appointments.updateBlock(user.id(), id, request.toService());
    }

    @DeleteMapping("/professional/availability/{id}")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public ResponseEntity<Void> deleteAvailability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id) {
        appointments.deleteBlock(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentService.AvailableSlot> availableSlots(
            @RequestParam long professionalId,
            @RequestParam long specialtyId,
            @RequestParam long siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return appointments.availableSlots(professionalId, specialtyId, siteId, from, to);
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentService.Appointment> book(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.book(user.id(), request.toService()));
    }

    @GetMapping("/appointments/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<AppointmentService.Appointment> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return appointments.patientAppointments(user.id());
    }

    @GetMapping("/admin/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppointmentService.Appointment> allAppointments(@RequestParam(required = false) String status) {
        return appointments.adminAppointments(status);
    }

    @PostMapping("/admin/appointments/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentService.Appointment decide(
            @PathVariable long id,
            @Valid @RequestBody DecisionRequest request) {
        return appointments.decide(id, request.toService());
    }

    public record ProfessionalRequest(
            @Positive long userId,
            @NotBlank @Size(max = 24) String type,
            @NotBlank @Size(max = 500) String description,
            @Size(max = 80) String licenseCode,
            @NotNull List<@Positive Long> specialtyIds,
            @NotNull List<@Positive Long> siteIds) {
        AppointmentService.ProfessionalRequest toService() {
            return new AppointmentService.ProfessionalRequest(
                    userId, type.trim().toUpperCase(), description, licenseCode, specialtyIds, siteIds);
        }
    }

    public record BlockRequest(
            @Positive long siteId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt) {
        AppointmentService.BlockRequest toService() {
            return new AppointmentService.BlockRequest(siteId, startsAt, endsAt);
        }
    }

    public record BookingRequest(
            @Positive long professionalId,
            @Positive long specialtyId,
            @Positive long siteId,
            @NotNull Instant startsAt) {
        AppointmentService.BookingRequest toService() {
            return new AppointmentService.BookingRequest(professionalId, specialtyId, siteId, startsAt);
        }
    }

    public record DecisionRequest(
            @NotBlank @Size(max = 16) String decision,
            @Size(max = 500) String reason) {
        AppointmentService.DecisionRequest toService() {
            return new AppointmentService.DecisionRequest(decision, reason);
        }
    }
}
