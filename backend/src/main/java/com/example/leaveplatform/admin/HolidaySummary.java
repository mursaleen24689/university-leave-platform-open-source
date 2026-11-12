package com.example.leaveplatform.admin;

import java.time.LocalDate;

/** A day in the university holiday calendar as the screen shows it. */
public record HolidaySummary(Long id, LocalDate date, String name) {
}
