package com.example.leaveplatform.organization;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    List<Department> findAllByOrderByNameAsc();

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findByActiveTrueAndFacultyIdOrderByNameAsc(Long facultyId);
}
