package com.example.leaveplatform.organization;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    public List<DepartmentResponse> list(@RequestParam(required = false) Long facultyId) {
        return departments.listActive(facultyId);
    }
}
