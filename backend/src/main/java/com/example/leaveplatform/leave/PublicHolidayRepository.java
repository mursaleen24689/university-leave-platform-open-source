package com.example.leaveplatform.leave;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    @Query("select h.date from PublicHoliday h where h.date between :start and :end")
    List<LocalDate> findDatesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<PublicHoliday> findByDateIsBetweenOrderByDateAsc(LocalDate start, LocalDate end);

    boolean existsByDate(LocalDate date);
}
