package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faculty/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}")
    public ResponseEntity<AttendanceService.AttendanceStatusDto> getAttendance(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable String academicYear
    ) {
        return ResponseEntity.ok(attendanceService.getAttendanceStatus(authentication.getName(), courseCode, programme, academicYear));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}")
    public ResponseEntity<AttendanceService.AttendanceSaveResult> saveAttendance(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable String academicYear,
        @Valid @RequestBody AttendanceService.AttendanceSaveRequest request
    ) {
        return ResponseEntity.ok(attendanceService.saveAttendance(authentication.getName(), courseCode, programme, academicYear, request));
    }
}
