package com.myplans.reports.dto;

import java.time.LocalDateTime;

public record HistorialDTO(
        Long idHistorial,
        Integer idTag,
        Integer idUsuario,
        String estadoAnterior,
        String estadoNuevo,
        String observaciones,
        LocalDateTime fechaActualizado
) {
}
