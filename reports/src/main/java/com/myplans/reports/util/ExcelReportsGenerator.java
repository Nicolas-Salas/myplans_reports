package com.myplans.reports.util;

import com.myplans.reports.dto.HistorialDTO;
import com.myplans.reports.dto.PlanoDTO;
import com.myplans.reports.dto.TagDTO;
import org.apache.poi.ss.usermodel.*;
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

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // Columnas de la Planilla de Amarillado (34 columnas reales para los 27 ítems del doc)
    private static final String[] HEADERS = {
        "ITEM",                     //  0
        "COD.UNICO",                //  1
        "FORMULARIO",               //  2
        "DESC. FORMULARIO",         //  3
        "NRO",                      //  4
        "ALCANCE",                  //  5
        "ELEMENTO",                 //  6
        "TAG ELEMENTO",             //  7
        "SUBSISTEMA",               //  8
        "ESTADO",                   //  9
        "PLANO REFERENCIA",         // 10
        "REV",                      // 11
        "COMENTARIO",               // 12
        "STATUS",                   // 13
        "OBSERVACIONES",            // 14
        "RESPONSABLE",              // 15
        "FIRMA RESPONSABLE",        // 16
        "DIGITAL",                  // 17
        "FECHA",                    // 18
        "NRO PÁGINAS",              // 19
        "CARPETA",                  // 20
        "CARPETA ARCHIVO",          // 21
        "TRAMITTAL",                // 22
        "NOMBRE DOCUMENTO",         // 23
        "FECHA INGRESO",            // 24
        "NOMBRE INGRESO",           // 25
        "FECHA ACTUALIZACIÓN",      // 26
        "NOMBRE ACTUALIZACIÓN",     // 27
        "VERIFICADOR",              // 28
        "TIPO",                     // 29
        "NRO TIPO",                 // 30
        "PROGRAMA PEM",             // 31
        "VERIFICADOR 2",            // 32
        "URL"                       // 33
    };

    public byte[] generate(PlanoDTO plano, List<TagDTO> tags,
                           Map<Integer, List<HistorialDTO>> historialPorTag,
                           String statusExport, String observaciones,
                           Map<Integer, String> userNames) throws IOException {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle dataStyle   = buildDataStyle(wb);
            CellStyle grayStyle   = buildGrayStyle(wb);

            buildSheetPlanilla(wb, plano, tags, statusExport, observaciones, userNames,
                               headerStyle, dataStyle, grayStyle);
            buildSheetHistorial(wb, tags, historialPorTag, userNames, headerStyle, dataStyle);

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private void buildSheetPlanilla(Workbook wb, PlanoDTO plano, List<TagDTO> tags,
                                    String statusExport, String observaciones,
                                    Map<Integer, String> userNames,
                                    CellStyle headerStyle, CellStyle dataStyle, CellStyle grayStyle) {

        Sheet sheet = wb.createSheet("Planilla Amarillado");

        // Fila de cabeceras
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(headerStyle);
        }

        // Datos fijos del plano
        String formulario  = safe(plano.formulario(), "PRE-ELE-YL");
        String nro         = plano.idPlano() == null ? "" : String.valueOf(plano.idPlano());
        String codUnico    = formulario + nro;
        String alcance     = safe(plano.alcance());
        String subsistema  = safe(plano.subsistema());
        String codigoPlano = safe(plano.codigoPlano());
        String rev         = safe(plano.rev());
        String responsable = safe(plano.responsable());
        String fecha       = plano.fechaCreacion() == null ? "" : plano.fechaCreacion().format(DATE_FMT);
        String nroPaginas  = plano.nroPaginas() == null ? "" : String.valueOf(plano.nroPaginas());
        String urlPlano    = safe(plano.urlS3());
        String obsExport   = observaciones != null && !observaciones.isBlank()
                             ? observaciones : safe(plano.observaciones());

        int rowIdx = 1;
        int item = 1;
        for (TagDTO tag : tags) {
            Row row = sheet.createRow(rowIdx++);

            String nombreIngreso   = tag.idUsuarioIngreso() == null ? ""
                    : userNames.getOrDefault(tag.idUsuarioIngreso(), String.valueOf(tag.idUsuarioIngreso()));
            String nombreActualizacion = tag.idUsuarioActualizacion() == null ? ""
                    : userNames.getOrDefault(tag.idUsuarioActualizacion(), String.valueOf(tag.idUsuarioActualizacion()));

            String nombreDoc = codUnico + "_" + responsable;

            // Columnas calculadas o vacías según el doc
            String[] values = {
                String.valueOf(item++),                                      //  0 ITEM
                codUnico,                                                    //  1 COD.UNICO
                formulario,                                                  //  2 FORMULARIO
                "YELLOW LINE",                                               //  3 DESC. FORMULARIO
                nro,                                                         //  4 NRO
                alcance,                                                     //  5 ALCANCE
                safe(tag.codigo()),                                          //  6 ELEMENTO
                safe(tag.codigo()).toUpperCase(),                            //  7 TAG ELEMENTO
                subsistema,                                                  //  8 SUBSISTEMA
                safe(tag.estadoActual()),                                    //  9 ESTADO
                codigoPlano,                                                 // 10 PLANO REFERENCIA
                rev,                                                         // 11 REV
                safe(tag.comentario()),                                      // 12 COMENTARIO
                safe(statusExport),                                          // 13 STATUS
                obsExport,                                                   // 14 OBSERVACIONES
                responsable,                                                 // 15 RESPONSABLE
                responsable,                                                 // 16 FIRMA RESPONSABLE
                "DIGITAL",                                                   // 17 DIGITAL
                fecha,                                                       // 18 FECHA
                nroPaginas,                                                  // 19 NRO PÁGINAS
                "",                                                          // 20 CARPETA
                "",                                                          // 21 CARPETA ARCHIVO
                "",                                                          // 22 TRAMITTAL
                nombreDoc,                                                   // 23 NOMBRE DOCUMENTO
                tag.fechaIngreso() == null ? "" : tag.fechaIngreso().format(DATE_FMT),   // 24 FECHA INGRESO
                nombreIngreso,                                               // 25 NOMBRE INGRESO
                tag.ultimaModificacion() == null ? "" : tag.ultimaModificacion().format(DATETIME_FMT), // 26 FECHA ACT.
                nombreActualizacion,                                         // 27 NOMBRE ACT.
                "",                                                          // 28 VERIFICADOR
                safe(tag.tipo()),                                            // 29 TIPO
                "",                                                          // 30 NRO TIPO
                "",                                                          // 31 PROGRAMA PEM
                "",                                                          // 32 VERIFICADOR 2
                urlPlano                                                     // 33 URL
            };

            // Columnas fijas (vacías o constantes) van con estilo gris
            int[] colsGris = {20, 21, 22, 28, 30, 31, 32};
            java.util.Set<Integer> grisSet = new java.util.HashSet<>();
            for (int g : colsGris) grisSet.add(g);

            for (int i = 0; i < values.length; i++) {
                Cell c = row.createCell(i);
                c.setCellValue(values[i]);
                c.setCellStyle(grisSet.contains(i) ? grayStyle : dataStyle);
            }
        }

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            // Cap máximo para no generar columnas enormes
            if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
        }
    }

    private void buildSheetHistorial(Workbook wb, List<TagDTO> tags,
                                     Map<Integer, List<HistorialDTO>> historialPorTag,
                                     Map<Integer, String> userNames,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Historial");

        String[] headers = {
            "ITEM", "CÓDIGO TAG", "FECHA CAMBIO",
            "USUARIO", "ESTADO ANTERIOR", "ESTADO NUEVO", "OBSERVACIONES"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        Map<Integer, String> codigoPorTag = new HashMap<>();
        for (TagDTO t : tags) codigoPorTag.put(t.idTag(), t.codigo());

        int rowIdx = 1;
        int item = 1;
        for (TagDTO tag : tags) {
            for (HistorialDTO h : historialPorTag.getOrDefault(tag.idTag(), List.of())) {
                Row row = sheet.createRow(rowIdx++);
                set(row, 0, String.valueOf(item++), dataStyle);
                set(row, 1, safe(codigoPorTag.get(h.idTag())), dataStyle);
                set(row, 2, h.fechaActualizado() == null ? "" : h.fechaActualizado().format(DATETIME_FMT), dataStyle);
                set(row, 3, h.idUsuario() == null ? "" : userNames.getOrDefault(h.idUsuario(), "Usuario " + h.idUsuario()), dataStyle);
                set(row, 4, safe(h.estadoAnterior()), dataStyle);
                set(row, 5, safe(h.estadoNuevo()), dataStyle);
                set(row, 6, safe(h.observaciones()), dataStyle);
            }
        }

        if (rowIdx == 1) {
            sheet.createRow(1).createCell(0).setCellValue("(Sin registros de auditoría)");
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    // ── helpers ──────────────────────────────────────────────

    private void set(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String safe(String s, String fallback) { return (s == null || s.isBlank()) ? fallback : s; }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setWrapText(true);
        return s;
    }

    private CellStyle buildDataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildGrayStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }
}
