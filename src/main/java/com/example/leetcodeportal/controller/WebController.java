package com.example.leetcodeportal.controller;

import com.example.leetcodeportal.dto.ReportResponse;
import com.example.leetcodeportal.service.ReportService;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@Validated
public class WebController {

    private final ReportService reportService;

    public WebController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("sampleFormat", "Name,LeetCode ID");
        return "index";
    }

    @PostMapping(path = "/api/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ReportResponse generate(@RequestParam("file") MultipartFile file) throws IOException {
        return reportService.generateReport(file);
    }

    @GetMapping("/download/{reportId}")
    public ResponseEntity<Resource> download(@PathVariable @NotBlank String reportId) throws IOException {
        Resource resource = reportService.loadReport(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leetcode-student-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }
}
