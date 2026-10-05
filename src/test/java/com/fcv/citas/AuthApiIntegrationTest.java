package com.fcv.citas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerReturnsUserWithoutPassword() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Pérez","email":"ana@example.test","password":"Password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailReturnsConflictIgnoringCase() throws Exception {
        register("duplicate@example.test");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"DUPLICATE@example.test","password":"Password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {
        register("login@example.test");

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.test","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        String accessToken = body.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(body.path("refreshToken").asText()).isNotBlank();
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        "test-only-secret-that-is-long-enough-for-hmac-sha256".getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();
        assertThat(claims.getSubject()).isNotBlank();
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getExpiration()).isAfter(Date.from(Instant.now()));
    }

    @Test
    void invalidLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.test","password":"WrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void incorrectPasswordReturnsUnauthorized() throws Exception {
        register("wrong-password@example.test");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrong-password@example.test","password":"NotThePassword123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meRequiresValidAccessToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        register("me@example.test");
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"me@example.test","password":"Password123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(response).path("accessToken").asText();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void basicAuthenticationDoesNotAuthorizeProtectedRoutes() throws Exception {
        mockMvc.perform(get("/auth/me").header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotAccessAdminAppointmentQueue() throws Exception {
        register("patient-role@example.test");
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"patient-role@example.test","password":"Password123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(response).path("accessToken").asText();

        mockMvc.perform(get("/api/admin/appointments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void invalidRegistrationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsPasswordLongerThanBcryptUtf8Limit() throws Exception {
        String multibytePassword = "ñ".repeat(37);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Test User",
                                "email", "long-password@example.test",
                                "password", multibytePassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must not exceed 72 UTF-8 bytes"));
    }

    @Test
    void refreshRotatesRefreshToken() throws Exception {
        register("refresh@example.test");
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh@example.test","password":"Password123"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(response).path("refreshToken").asText();

        String refreshed = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(refreshed).path("refreshToken").asText()).isNotEqualTo(refreshToken);
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Test User",
                                "email", email,
                                "password", "Password123"))))
                .andExpect(status().isCreated());
    }
}
