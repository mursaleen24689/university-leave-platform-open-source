package com.example.leaveplatform.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "department_type", nullable = false, length = 20)
    private DepartmentType type;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "successor_id")
    private Long successorId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Department() {
    }

    public Department(Faculty faculty, String code, String name, DepartmentType type) {
        this.faculty = faculty;
        this.code = code;
        this.name = name;
        this.type = type;
        this.active = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(Faculty faculty) {
        this.faculty = faculty;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public DepartmentType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public Long getSuccessorId() {
        return successorId;
    }
}
