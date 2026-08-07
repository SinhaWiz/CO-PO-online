package org.example.copo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where generated report/export files (PDFs, Excel workbooks) live on disk. Every
 * report-generating service used to have its own private copy of this exact method -
 * CourseReportService, SummaryReportService, MarksReportService, FacultyReportService,
 * AdminReportService, and MarksExcelService all duplicated the identical
 * "prefer the legacy desktop app's folder if it's sitting right there, else use a
 * local folder" logic independently. Consolidated here so there's one place that
 * understands "where do reports go," not six.
 *
 * The storage APPROACH this implements deliberately stays local disk, not a move to
 * S3/cloud object storage - nothing in this migration's brief specifies a deployment
 * target or gives this app credentials for one, and inventing that infrastructure
 * without being asked would be scope creep, not a real decision. What actually
 * changes here: the base directory is now an externalized, configurable property
 * (app.report-storage.base-dir) instead of a hardcoded relative path baked into six
 * different classes, so a real deployment can point it at a mounted volume - or, if a
 * later phase does move this to object storage, there's exactly one class to change
 * instead of six.
 */
@Service
public class ReportStorageService {

    @Value("${app.report-storage.base-dir:}")
    private String configuredBaseDir;

    @Value("${app.report-storage.legacy-desktop-dir:../CO_PO_Assessment}")
    private String legacyDesktopDir;

    public Path resolveReportDir(String dirName) {
        if (configuredBaseDir != null && !configuredBaseDir.isBlank()) {
            return Paths.get(configuredBaseDir, dirName).normalize();
        }

        // No base dir configured (the default, e.g. in dev) - fall back to the
        // pre-phase-10.4 behavior: prefer the legacy desktop app's own output folder
        // if it's present on the same disk, so reports from both apps show up in one
        // place during the migration period, otherwise a local folder relative to
        // wherever the backend process is running.
        Path legacy = Paths.get(legacyDesktopDir, dirName).normalize();
        if (Files.exists(legacy.getParent())) {
            return legacy;
        }
        return Paths.get(dirName).normalize();
    }
}
