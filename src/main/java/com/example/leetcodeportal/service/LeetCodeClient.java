package com.example.leetcodeportal.service;

import com.example.leetcodeportal.model.StudentInput;
import com.example.leetcodeportal.model.StudentStats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LeetCodeClient {

    private static final String LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql";
    private static final String GRAPHQL_QUERY = """
            query studentDashboard($username: String!) {
              matchedUser(username: $username) {
                username
                submitStats {
                  acSubmissionNum {
                    difficulty
                    count
                  }
                }
                userCalendar {
                  streak
                  submissionCalendar
                }
              }
              userContestRanking(username: $username) {
                attendedContestsCount
              }
            }
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LeetCodeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public StudentStats fetchStudentStats(StudentInput studentInput) {
        String username = normalizeUsername(studentInput.leetCodeId());
        if (username.isBlank()) {
            return new StudentStats(studentInput.name(), studentInput.leetCodeId(), 0, 0, 0, "Invalid LeetCode ID");
        }

        try {
            JsonNode root = executeQuery(username);
            JsonNode data = root.path("data");
            JsonNode matchedUser = data.path("matchedUser");
            if (matchedUser.isMissingNode() || matchedUser.isNull()) {
                return new StudentStats(studentInput.name(), username, 0, 0, 0, "User not found");
            }

            int totalSolved = extractTotalSolved(matchedUser.path("submitStats").path("acSubmissionNum"));
            int contestsAttended = data.path("userContestRanking").path("attendedContestsCount").asInt(0);
            JsonNode userCalendar = matchedUser.path("userCalendar");
            int maxStreak = userCalendar.path("streak").asInt(0);
            if (maxStreak == 0) {
                maxStreak = calculateMaxStreak(userCalendar.path("submissionCalendar").asText(""));
            }

            return new StudentStats(studentInput.name(), username, maxStreak, totalSolved, contestsAttended, "OK");
        } catch (Exception exception) {
            return new StudentStats(studentInput.name(), username, 0, 0, 0, "Unable to fetch data");
        }
    }

    private JsonNode executeQuery(String username) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
                "query", GRAPHQL_QUERY,
                "variables", Map.of("username", username)
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LEETCODE_GRAPHQL_URL))
                .header("Content-Type", "application/json")
                .header("Referer", "https://leetcode.com")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("LeetCode API returned " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String normalizeUsername(String rawInput) {
        if (rawInput == null) {
            return "";
        }

        String candidate = rawInput.trim();
        if (candidate.isBlank()) {
            return "";
        }

        int leetCodeIndex = candidate.toLowerCase().indexOf("leetcode.com/");
        if (leetCodeIndex >= 0) {
            String profilePart = candidate.substring(leetCodeIndex);
            profilePart = profilePart.replaceFirst("(?i)^https?://", "");
            profilePart = profilePart.replaceFirst("(?i)^www\\.", "");

            String[] segments = profilePart.split("/");
            for (int index = 0; index < segments.length; index++) {
                if ("u".equalsIgnoreCase(segments[index]) || "users".equalsIgnoreCase(segments[index])) {
                    if (index + 1 < segments.length) {
                        return sanitizeUsername(segments[index + 1]);
                    }
                }
            }
        }

        return sanitizeUsername(candidate);
    }

    private String sanitizeUsername(String candidate) {
        String cleaned = candidate.trim();

        int whitespaceIndex = cleaned.indexOf(' ');
        if (whitespaceIndex >= 0) {
            cleaned = cleaned.substring(0, whitespaceIndex);
        }

        int questionMarkIndex = cleaned.indexOf('?');
        if (questionMarkIndex >= 0) {
            cleaned = cleaned.substring(0, questionMarkIndex);
        }

        int hashIndex = cleaned.indexOf('#');
        if (hashIndex >= 0) {
            cleaned = cleaned.substring(0, hashIndex);
        }

        cleaned = cleaned.replaceAll("^/+", "").replaceAll("/+$", "");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned;
    }

    private int extractTotalSolved(JsonNode submissionStats) {
        if (!submissionStats.isArray()) {
            return 0;
        }

        for (JsonNode stat : submissionStats) {
            if ("All".equalsIgnoreCase(stat.path("difficulty").asText())) {
                return stat.path("count").asInt(0);
            }
        }
        return 0;
    }

    private int calculateMaxStreak(String calendarJson) throws IOException {
        if (calendarJson == null || calendarJson.isBlank()) {
            return 0;
        }

        JsonNode submissionMap = objectMapper.readTree(calendarJson);
        if (!submissionMap.isObject()) {
            return 0;
        }

        List<LocalDate> activeDays = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = submissionMap.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().asInt(0) > 0) {
                long epochSeconds = Long.parseLong(field.getKey());
                activeDays.add(Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate());
            }
        }

        if (activeDays.isEmpty()) {
            return 0;
        }

        activeDays.sort(Comparator.naturalOrder());
        int best = 1;
        int current = 1;

        for (int index = 1; index < activeDays.size(); index++) {
            LocalDate previous = activeDays.get(index - 1);
            LocalDate currentDate = activeDays.get(index);
            if (previous.plusDays(1).equals(currentDate)) {
                current++;
                best = Math.max(best, current);
            } else if (!previous.equals(currentDate)) {
                current = 1;
            }
        }
        return best;
    }
}
