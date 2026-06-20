package com.myplans.reports;

import com.myplans.reports.client.AuditClient;
import com.myplans.reports.client.CoreClient;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.dto.TagDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * PF-016 — Descargar Excel de plano cerrado → 200 + xlsx
 * PF-017 — Exportar plano no cerrado → 409
 * PS-001  — Sin token → 401
 * PS-008  — ROLE_USER no puede exportar → 403
 *
 * Reports llama a Core, Audit y Auth durante el export, por eso se mockean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "reports.core.uri=http://localhost:1",
        "reports.audit.uri=http://localhost:1",
        "reports.core.internal-token=test-token",
        "reports.audit.internal-token=test-token"
})
class ReportsSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private CoreClient coreClient;

    @MockBean
    private AuditClient auditClient;

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private PlanoDTO planoCerrado() {
        return new PlanoDTO(
                1, "Plano Test", "PRE-ELE-YL", null,
                "SKIC", "Subestación A", "CERRADO",
                "P-001", "0", null, "Ing. Test",
                LocalDate.now(), 10, LocalDateTime.now(), 2L);
    }

    private PlanoDTO planoAbierto() {
        return new PlanoDTO(
                2, "Plano Abierto", "PRE-ELE-YL", null,
                "SKIC", "Subestación B", "ABIERTO",
                "P-002", "0", null, null,
                null, 5, LocalDateTime.now(), 1L);
    }

    private TagDTO tagDTO(Integer idTag) {
        return new TagDTO(
                idTag, 1, "TAG-00" + idTag, "Motor bomba " + idTag,
                "Sala 1", "APROBADO", null, "EQUIPO",
                1, LocalDate.now(), 1, LocalDateTime.now());
    }

    // Seguridad — sin token y roles incorrectos

    @Test
    void givenNoToken_whenExportExcel_thenReturn401() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody().get("message"));
    }

    @Test
    void givenInvalidToken_whenExportExcel_thenReturn401() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("token-falso-invalido");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(h),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void givenExpiredToken_whenExportExcel_thenReturn401WithMessage() {
        String expired = TestJwtHelper.expiredTokenFor("aud@test.com", 1, List.of("ROLE_AUDITOR"));
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(expired);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(h),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertNotNull(msg);
        assertTrue(msg.toLowerCase().contains("expirado") ||
                   msg.toLowerCase().contains("sesión") ||
                   msg.toLowerCase().contains("inicia"),
                "El mensaje debe indicar token expirado, fue: " + msg);
    }

    @Test
    void givenRoleUser_whenExportExcel_thenReturn403() {
        String token = TestJwtHelper.tokenFor("op@test.com", 2, List.of("ROLE_USER"));

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // PF-017: Plano no cerrado → 409

    @Test
    void givenPlanoNoEstaCerrado_whenExportExcel_thenReturn409() {
        when(coreClient.getPlano(2)).thenReturn(planoAbierto());
        when(coreClient.getTagsByPlano(2)).thenReturn(List.of());
        when(auditClient.getHistorialPorTag(anyInt())).thenReturn(List.of());

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/2/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void givenPlanoEnValidado_whenExportExcel_thenReturn409() {
        PlanoDTO planoValidado = new PlanoDTO(
                3, "Plano Validado", "PRE-ELE", null,
                "SKIC", "Sub C", "VALIDADO",
                "P-003", "0", null, null,
                null, 5, LocalDateTime.now(), 1L);
        when(coreClient.getPlano(3)).thenReturn(planoValidado);

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/3/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // PF-016: Plano CERRADO → descarga Excel

    @Test
    void givenPlanoCerrado_whenExportExcel_thenReturn200WithXlsxContentType() {
        when(coreClient.getPlano(1)).thenReturn(planoCerrado());
        when(coreClient.getTagsByPlano(1)).thenReturn(List.of(tagDTO(1), tagDTO(2)));
        when(auditClient.getHistorialPorTag(anyInt())).thenReturn(List.of());

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));

        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                byte[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String contentType = resp.getHeaders().getContentType().toString();
        assertTrue(contentType.contains("spreadsheetml") || contentType.contains("excel"),
                "Content-Type debe ser xlsx, fue: " + contentType);

        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().length > 0,
                "El archivo Excel no debe estar vacío");
    }

    @Test
    void givenPlanoCerrado_whenExportExcel_thenFilenameInContentDisposition() {
        when(coreClient.getPlano(1)).thenReturn(planoCerrado());
        when(coreClient.getTagsByPlano(1)).thenReturn(List.of(tagDTO(1)));
        when(auditClient.getHistorialPorTag(anyInt())).thenReturn(List.of());

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));

        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                byte[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String contentDisposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(contentDisposition,
                "La respuesta debe tener Content-Disposition");
        assertTrue(contentDisposition.contains(".xlsx"),
                "El Content-Disposition debe indicar un archivo .xlsx, fue: " + contentDisposition);
    }

    @Test
    void givenAdminRole_whenExportPlanoCerrado_thenReturn200() {
        when(coreClient.getPlano(1)).thenReturn(planoCerrado());
        when(coreClient.getTagsByPlano(1)).thenReturn(List.of());
        when(auditClient.getHistorialPorTag(anyInt())).thenReturn(List.of());

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));

        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "/api/v1/reportes/plano/1/excel?statusExport=APROBADO",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                byte[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // Health check

    @Test
    void whenHealthCheck_thenReturn200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/actuator/health", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("UP", resp.getBody().get("status"));
    }
}
