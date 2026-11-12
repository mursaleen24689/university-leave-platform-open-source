package com.example.leaveplatform.leave;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByCodeAndActiveTrue(String code);
}
