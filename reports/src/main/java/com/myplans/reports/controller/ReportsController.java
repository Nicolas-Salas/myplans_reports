package com.myplans.reports.controller;

import com.myplans.reports.client.CoreClient;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.service.ReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Generación de entregables Excel del plano (CU-18, RF-21, RF-25)")
public class ReportsController {

    private final ReportsService reportService;
    private final CoreClient coreClient;

    @GetMapping("/plano/{idPlano}/excel")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    @Operation(summary = "Exportar Planilla de Amarillado a Excel",
               description = "Genera el .xlsx con el formato oficial de la Planilla de Amarillado. " +
                             "Solo disponible si el plano está en estado CERRADO.")
    public ResponseEntity<ByteArrayResource> exportarPlanoExcel(
            @PathVariable Integer idPlano,
            @RequestParam String statusExport,
            @RequestParam(required = false, defaultValue = "") String observaciones) {

        byte[] xlsx = reportService.generarReporteExcel(idPlano, statusExport, observaciones);

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
