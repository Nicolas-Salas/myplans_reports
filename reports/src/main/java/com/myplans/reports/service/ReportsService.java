package com.myplans.reports.service;

import com.myplans.reports.client.AuditClient;
import com.myplans.reports.client.CoreClient;
import com.myplans.reports.dto.HistorialDTO;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.dto.TagDTO;
import com.myplans.reports.exception.BusinessException;
import com.myplans.reports.exception.UpstreamServiceException;
import com.myplans.reports.util.ExcelReportsGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportsService {

    private static final Logger log = LoggerFactory.getLogger(ReportsService.class);
    private static final String ESTADO_CERRADO = "CERRADO";

    private final CoreClient coreClient;
    private final AuditClient auditClient;
    private final ExcelReportsGenerator excelGenerator;

    public byte[] generarReporteExcel(Integer idPlano) {
        log.info("Generando reporte Excel del plano idPlano={}", idPlano);

        PlanoDTO plano = coreClient.getPlano(idPlano);

        if (plano.status() == null || !ESTADO_CERRADO.equalsIgnoreCase(plano.status())) {
            throw new BusinessException(
                    "Solo se pueden exportar planos en estado CERRADO. " +
                            "Estado actual del plano " + idPlano + ": " + plano.status());
        }

        List<TagDTO> tags = coreClient.getTagsByPlano(idPlano);
        log.debug("Plano {} tiene {} TAGs", idPlano, tags.size());

        Map<Integer, List<HistorialDTO>> historialPorTag = new HashMap<>();
        for (TagDTO tag : tags) {
            List<HistorialDTO> historial = auditClient.getHistorialPorTag(tag.idTag());
            historialPorTag.put(tag.idTag(), historial);
        }

        try {
            byte[] xlsx = excelGenerator.generate(plano, tags, historialPorTag);
            log.info("Reporte del plano {} generado: {} bytes, {} TAGs, {} eventos",
                    idPlano, xlsx.length, tags.size(),
                    historialPorTag.values().stream().mapToInt(List::size).sum());
            return xlsx;
        } catch (IOException ex) {
            log.error("Error generando archivo Excel del plano {}", idPlano, ex);
            throw new UpstreamServiceException("Error generando archivo Excel", ex);
        }
    }

    public String buildFilename(Integer idPlano, PlanoDTO plano) {
        String base = plano == null || plano.codigoPlano() == null || plano.codigoPlano().isBlank()
                ? ("plano-" + idPlano)
                : plano.codigoPlano().replaceAll("[^A-Za-z0-9_.-]", "_");
        return "matriz-" + base + ".xlsx";
    }
}