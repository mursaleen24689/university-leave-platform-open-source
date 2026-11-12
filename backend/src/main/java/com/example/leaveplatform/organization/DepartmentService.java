package com.example.leaveplatform.organization;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departments;

    public DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listActive(Long facultyId) {
        List<Department> found = facultyId == null
                ? departments.findByActiveTrueOrderByNameAsc()
                : departments.findByActiveTrueAndFacultyIdOrderByNameAsc(facultyId);
        return found.stream().map(DepartmentService::toResponse).toList();
    }

    private static DepartmentResponse toResponse(Department department) {
        Faculty faculty = department.getFaculty();
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getType(),
                faculty.getCode(),
                faculty.getName(),
                department.isActive());
    }
}
