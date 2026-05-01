package com.example.leetcodeportal.model;

public record StudentStats(
        String name,
        String leetCodeId,
        int maxStreak,
        int totalSolved,
        int contestsAttended,
        String status
) {
}
