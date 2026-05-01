package com.example.leetcodeportal.dto;

public record ReportResponse(
        String reportId,
        String downloadUrl,
        int processedStudents,
        String message
) {
}
