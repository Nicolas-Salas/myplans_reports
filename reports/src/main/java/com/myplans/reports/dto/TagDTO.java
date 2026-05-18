package com.myplans.reports.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TagDTO(
        Integer idTag,
        Integer idPlano,
        String codigo,
        String descripcion,
        String area,
        String estadoActual,
        String comentario,
        String tipo,
        Integer idUsuarioIngreso,
        LocalDate fechaIngreso,
        Integer idUsuarioActualizacion,
        LocalDateTime ultimaModificacion
) {
}
