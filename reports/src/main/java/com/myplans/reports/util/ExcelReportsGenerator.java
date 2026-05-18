package com.myplans.reports.util;

import com.myplans.reports.dto.HistorialDTO;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.dto.TagDTO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExcelReportsGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generate(PlanoDTO plano, List<TagDTO> tags, Map<Integer, List<HistorialDTO>> historialPorTag)
            throws IOException {

        try (Workbook wb = new XSSFWorkbook();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle dataStyle = buildDataStyle(wb);

            buildSheetMatriz(wb, plano, tags, headerStyle, dataStyle);
            buildSheetHistorial(wb, tags, historialPorTag, headerStyle, dataStyle);

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private void buildSheetMatriz(Workbook wb, PlanoDTO plano, List<TagDTO> tags,
            CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Matriz TAGs");

        // Bloque de metadatos del plano (filas 0-2)
        Row metaTitle = sheet.createRow(0);
        metaTitle.createCell(0).setCellValue("Plano:");
        metaTitle.createCell(1).setCellValue(plano.nombre() == null ? "" : plano.nombre());
        metaTitle.createCell(2).setCellValue("Formulario:");
        metaTitle.createCell(3).setCellValue(plano.formulario() == null ? "" : plano.formulario());
        metaTitle.createCell(4).setCellValue("Status:");
        metaTitle.createCell(5).setCellValue(plano.status() == null ? "" : plano.status());

        Row meta2 = sheet.createRow(1);
        meta2.createCell(0).setCellValue("Código:");
        meta2.createCell(1).setCellValue(plano.codigoPlano() == null ? "" : plano.codigoPlano());
        meta2.createCell(2).setCellValue("Rev:");
        meta2.createCell(3).setCellValue(plano.rev() == null ? "" : plano.rev());
        meta2.createCell(4).setCellValue("Responsable:");
        meta2.createCell(5).setCellValue(plano.responsable() == null ? "" : plano.responsable());

        // Headers en fila 3
        String[] headers = {
                "ID TAG", "Código", "Tipo", "Descripción", "Área", "Estado actual",
                "Comentario", "Usuario ingreso", "Fecha ingreso",
                "Usuario última act.", "Última modificación"
        };
        Row headerRow = sheet.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Datos
        int rowIdx = 4;
        for (TagDTO tag : tags) {
            Row row = sheet.createRow(rowIdx++);
            setCell(row, 0, tag.idTag() == null ? "" : tag.idTag().toString(), dataStyle);
            setCell(row, 1, nullSafe(tag.codigo()), dataStyle);
            setCell(row, 2, nullSafe(tag.tipo()), dataStyle);
            setCell(row, 3, nullSafe(tag.descripcion()), dataStyle);
            setCell(row, 4, nullSafe(tag.area()), dataStyle);
            setCell(row, 5, nullSafe(tag.estadoActual()), dataStyle);
            setCell(row, 6, nullSafe(tag.comentario()), dataStyle);
            setCell(row, 7, tag.idUsuarioIngreso() == null ? "" : tag.idUsuarioIngreso().toString(), dataStyle);
            setCell(row, 8, tag.fechaIngreso() == null ? "" : tag.fechaIngreso().format(DATE_FMT), dataStyle);
            setCell(row, 9, tag.idUsuarioActualizacion() == null ? "" : tag.idUsuarioActualizacion().toString(),
                    dataStyle);
            setCell(row, 10, tag.ultimaModificacion() == null ? "" : tag.ultimaModificacion().format(DATETIME_FMT),
                    dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildSheetHistorial(Workbook wb, List<TagDTO> tags,
            Map<Integer, List<HistorialDTO>> historialPorTag,
            CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Historial");

        String[] headers = {
                "ID TAG", "Código TAG", "Fecha cambio",
                "ID Usuario", "Estado anterior", "Estado nuevo", "Observaciones"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Resolver código de TAG por id para no repetir lookups
        Map<Integer, String> codigoByIdTag = new HashMap<>();
        for (TagDTO tag : tags) {
            codigoByIdTag.put(tag.idTag(), tag.codigo());
        }

        int rowIdx = 1;
        for (TagDTO tag : tags) {
            List<HistorialDTO> historial = historialPorTag.getOrDefault(tag.idTag(), List.of());
            for (HistorialDTO h : historial) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, h.idTag() == null ? "" : h.idTag().toString(), dataStyle);
                setCell(row, 1, nullSafe(codigoByIdTag.get(h.idTag())), dataStyle);
                setCell(row, 2, h.fechaActualizado() == null ? "" : h.fechaActualizado().format(DATETIME_FMT),
                        dataStyle);
                setCell(row, 3, h.idUsuario() == null ? "" : h.idUsuario().toString(), dataStyle);
                setCell(row, 4, nullSafe(h.estadoAnterior()), dataStyle);
                setCell(row, 5, nullSafe(h.estadoNuevo()), dataStyle);
                setCell(row, 6, nullSafe(h.observaciones()), dataStyle);
            }
        }

        // Si no hubo historial, dejar un mensaje
        if (rowIdx == 1) {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("(No hay registros de auditoría para este plano)");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle buildDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}