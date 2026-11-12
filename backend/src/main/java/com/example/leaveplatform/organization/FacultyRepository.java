package com.example.leaveplatform.organization;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByCode(String code);

    boolean existsByCode(String code);

    List<Faculty> findAllByOrderByNameAsc();
}
