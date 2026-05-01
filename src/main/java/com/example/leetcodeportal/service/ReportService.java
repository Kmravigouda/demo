package com.example.leetcodeportal.service;

import com.example.leetcodeportal.dto.ReportResponse;
import com.example.leetcodeportal.model.StudentInput;
import com.example.leetcodeportal.model.StudentStats;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReportService {

    private static final String HEADER = "Rank,Name,LeetCode ID,Max Streak,Total Problems Solved,Total Contests Attended,Status";

    private final LeetCodeClient leetCodeClient;
    private final Path outputDirectory;

    public ReportService(LeetCodeClient leetCodeClient) throws IOException {
        this.leetCodeClient = leetCodeClient;
        this.outputDirectory = Path.of("generated-reports");
        Files.createDirectories(this.outputDirectory);
    }

    public ReportResponse generateReport(MultipartFile file) throws IOException {
        validateFile(file);
        List<StudentInput> students = parseStudents(file.getInputStream());
        if (students.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file does not contain any student records.");
        }

        List<StudentStats> collected = students.stream()
                .map(leetCodeClient::fetchStudentStats)
                .sorted(Comparator
                        .comparingInt(StudentStats::totalSolved).reversed()
                        .thenComparing(Comparator.comparingInt(StudentStats::maxStreak).reversed())
                        .thenComparing(Comparator.comparingInt(StudentStats::contestsAttended).reversed())
                        .thenComparing(StudentStats::leetCodeId, String.CASE_INSENSITIVE_ORDER))
                .toList();

        String reportId = UUID.randomUUID().toString();
        Path reportFile = outputDirectory.resolve(reportId + ".csv");
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        int rank = 1;
        for (StudentStats stats : collected) {
            lines.add(toCsvLine(rank++, stats));
        }

        Files.write(reportFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new ReportResponse(reportId, "/download/" + reportId, collected.size(),
                "Report generated successfully.");
    }

    public Resource loadReport(String reportId) throws IOException {
        Path reportFile = outputDirectory.resolve(reportId + ".csv");
        if (!Files.exists(reportFile)) {
            throw new IOException("Report not found.");
        }
        return new FileSystemResource(reportFile);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a CSV or TXT file with student names and LeetCode IDs.");
        }
    }

    private List<StudentInput> parseStudents(InputStream inputStream) throws IOException {
        List<String> lines = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));

        if (!lines.isEmpty() && isHeaderRow(lines.getFirst())) {
            lines.removeFirst();
        }

        return lines.stream()
                .map(this::parseStudent)
                .filter(student -> StringUtils.hasText(student.leetCodeId()))
                .collect(Collectors.toList());
    }

    private boolean isHeaderRow(String line) {
        String normalized = line.toLowerCase();
        return normalized.contains("leetcode") || normalized.contains("name");
    }

    private StudentInput parseStudent(String line) {
        String[] parts = line.split("[,\t]");
        if (parts.length == 1) {
            String idOnly = cleanValue(parts[0]);
            return new StudentInput(idOnly, idOnly);
        }

        String name = cleanValue(parts[0]);
        String leetCodeId = cleanValue(parts[1]);
        return new StudentInput(name.isBlank() ? leetCodeId : name, leetCodeId);
    }

    private String cleanValue(String raw) {
        return raw == null ? "" : raw.replace("\"", "").trim();
    }

    private String toCsvLine(int rank, StudentStats stats) {
        return String.join(",",
                String.valueOf(rank),
                csvEscape(stats.name()),
                csvEscape(stats.leetCodeId()),
                String.valueOf(stats.maxStreak()),
                String.valueOf(stats.totalSolved()),
                String.valueOf(stats.contestsAttended()),
                csvEscape(stats.status()));
    }

    private String csvEscape(String value) {
        String safe = StringUtils.hasText(value) ? value : "-";
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
