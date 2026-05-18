package com.myplans.reports.controller;

import com.myplans.reports.client.CoreClient;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.service.ReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Generación de entregables Excel del plano (CU-18, RF-21, RF-25)")
public class ReportsController {

    private final ReportsService reportService;
    private final CoreClient coreClient;

    @GetMapping("/plano/{idPlano}/excel")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Exportar matriz del plano + historial completo a Excel",
            description = "Genera un .xlsx con dos hojas: matriz de TAGs y historial de cambios. " +
                    "Solo disponible si el plano está en estado CERRADO (RF-25).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo Excel generado"),
            @ApiResponse(responseCode = "400", description = "El plano no está en estado CERRADO"),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "Solo SUPERVISOR y ADMIN pueden exportar"),
            @ApiResponse(responseCode = "404", description = "Plano no encontrado"),
            @ApiResponse(responseCode = "502", description = "Core o Audit no disponibles")
    })
    public ResponseEntity<ByteArrayResource> exportarPlanoExcel(@PathVariable Integer idPlano) {
        byte[] xlsx = reportService.generarReporteExcel(idPlano);

        // Buscar nombre del archivo (segunda llamada al Core, pero ya está cacheada por la primera del service;
        // si quieres optimizar, se puede modificar el service para que devuelva también el plano)
        PlanoDTO plano = coreClient.getPlano(idPlano);
        String filename = reportService.buildFilename(idPlano, plano);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(xlsx.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(xlsx));
    }
}
