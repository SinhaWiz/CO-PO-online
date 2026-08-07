package org.example.copo.service;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Ported near-verbatim from the desktop app's utility of the same name. Reads the
 * first sheet of an uploaded workbook into header-name -> value maps, with headers
 * normalized (trim, lowercase, spaces/line-breaks collapsed to underscores) so a
 * column titled "Course Code", "course_code", or "  Course   Code  " all resolve the
 * same way. Every entity's bulk importer builds on this rather than parsing POI
 * directly.
 */
public final class ExcelImportUtils {
    private ExcelImportUtils() {}

    public static List<Map<String, String>> readSheetAsMaps(InputStream inputStream) throws IOException {
        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) return List.of();
            DataFormatter formatter = new DataFormatter();
            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) return List.of();
            Row headerRow = it.next();
            int maxCell = headerRow.getLastCellNum();
            Map<Integer, String> headers = new LinkedHashMap<>();
            for (int c = 0; c < maxCell; c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String key = cell == null ? null : normalizeHeader(formatter.formatCellValue(cell));
                if (key != null && !key.isBlank()) headers.put(c, key);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext()) {
                Row row = it.next();
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> e : headers.entrySet()) {
                    int c = e.getKey();
                    String key = e.getValue();
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = cell == null ? "" : formatter.formatCellValue(cell);
                    map.put(key, val == null ? "" : val.trim());
                }
                boolean allBlank = map.values().stream().allMatch(String::isBlank);
                if (!allBlank) rows.add(map);
            }
            return rows;
        }
    }

    public static String normalizeHeader(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace("\r", " ").replace("\n", " ").replace('\t', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replace(' ', '_');
        return s;
    }

    public static String get(Map<String, String> row, String... candidates) {
        for (String c : candidates) {
            String k = normalizeHeader(c);
            if (row.containsKey(k)) return emptyToNull(row.get(k));
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
