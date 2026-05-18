package com.myplans.reports.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanoDTO(
        Integer idPlano,
        String nombre,
        String formulario,
        String urlS3,
        String alcance,
        String subsistema,
        String status,
        String codigoPlano,
        String rev,
        String observaciones,
        String responsable,
        LocalDate fechaFirma,
        Integer nroPaginas,
        LocalDateTime fechaCreacion,
        Long cantidadTags
) {
}
