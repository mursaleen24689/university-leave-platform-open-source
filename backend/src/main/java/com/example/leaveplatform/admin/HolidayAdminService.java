package com.example.leaveplatform.admin;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.leave.PublicHoliday;
import com.example.leaveplatform.leave.PublicHolidayRepository;

/**
 * The holiday calendar the day counter reads. The earlier application kept the same list in
 * its admin panel; without a screen here a new holiday would need a migration.
 *
 * <p>Editing the calendar does not re-count requests that were already made:
 * each request keeps the number of days the server worked out when it was
 * submitted, so a correction changes future counting only.
 */
@Service
public class HolidayAdminService {

    private static final int NAME_MAX = 150;

    private final PublicHolidayRepository holidays;
    private final AuditService audit;

    public HolidayAdminService(PublicHolidayRepository holidays, AuditService audit) {
        this.holidays = holidays;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<HolidaySummary> list(int year) {
        checkYear(year);
        return holidays.findByDateIsBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .map(HolidayAdminService::toSummary)
                .toList();
    }

    /** A mistyped year is a bad request, not a server error from LocalDate.of. */
    private static void checkYear(int year) {
        if (year < 1900 || year > 9999) {
            throw new AdminException("Enter a year between 1900 and 9999.");
        }
    }

    @Transactional
    public HolidaySummary add(LocalDate date, String name) {
        if (date == null) {
            throw new AdminException("Choose the date of the holiday.");
        }
        if (!StringUtils.hasText(name)) {
            throw new AdminException("Give the holiday a name, for example Eid-ul-Fitr (2nd day).");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new AdminException("The holiday name must be " + NAME_MAX + " characters or fewer.");
        }
        if (holidays.existsByDate(date)) {
            throw new AdminException("There is already a holiday on " + date + ".");
        }
        HolidaySummary created = toSummary(holidays.save(new PublicHoliday(date, trimmed)));
        audit.record(AuditAction.HOLIDAY_ADDED, "PUBLIC_HOLIDAY", created.id(),
                "Added " + created.date() + " (" + created.name() + ") to the holiday calendar.");
        return created;
    }

    @Transactional
    public void remove(Long id) {
        PublicHoliday holiday = holidays.findById(id)
                .orElseThrow(() -> new AdminException("That holiday is no longer in the calendar."));
        holidays.delete(holiday);
        audit.record(AuditAction.HOLIDAY_REMOVED, "PUBLIC_HOLIDAY", id,
                "Removed " + holiday.getDate() + " (" + holiday.getName() + ") from the holiday calendar.");
    }

    private static HolidaySummary toSummary(PublicHoliday holiday) {
        return new HolidaySummary(holiday.getId(), holiday.getDate(), holiday.getName());
    }
}
