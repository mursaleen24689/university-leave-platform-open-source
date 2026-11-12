package com.example.leaveplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.util.StringUtils;
import com.example.leaveplatform.organization.Department;

/**
 * University employee. Only the name, designation and department are required;
 * the staff number, e-mail and phone are optional because staff data reaches
 * the university in stages. See V008.
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", unique = true, length = 30)
    private String employeeNo;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(unique = true, length = 150)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 100)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "supervisor_employee_id")
    private Long supervisorEmployeeId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(String employeeNo, String fullName, String email, String phone,
            String designation, Department department) {
        this.employeeNo = blankToNull(employeeNo);
        this.fullName = fullName;
        this.email = blankToNull(email);
        this.phone = blankToNull(phone);
        this.designation = designation;
        this.department = department;
        this.active = true;
    }

    /**
     * Replaces the editable fields. Called by the administration screens when a
     * record is completed or corrected.
     */
    public void updateDetails(String employeeNo, String fullName, String email, String phone,
            String designation) {
        this.employeeNo = blankToNull(employeeNo);
        this.fullName = fullName;
        this.email = blankToNull(email);
        this.phone = blankToNull(phone);
        this.designation = designation;
    }

    /**
     * One field at a time, for the cases where somebody is right about one part
     * of a record and the rest must not be touched. {@link #updateDetails} writes
     * all five whether or not it was given them, which is how the earlier import lost
     * numbers when a profile change was approved; these cannot.
     *
     * <p>{@link com.example.leaveplatform.profile.ProfileField} holds the pair for each of
     * them, so a proposed correction and the column it lands on are decided in one
     * place rather than matched up by name at run time.
     */
    public void changeFullName(String fullName) {
        this.fullName = fullName;
    }

    public void changeEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public void changeDesignation(String designation) {
        this.designation = designation;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void moveTo(Department department) {
        this.department = department;
    }

    public void assignSupervisor(Long supervisorEmployeeId) {
        this.supervisorEmployeeId = supervisorEmployeeId;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getDesignation() {
        return designation;
    }

    public Department getDepartment() {
        return department;
    }

    public Long getSupervisorEmployeeId() {
        return supervisorEmployeeId;
    }

    public boolean isActive() {
        return active;
    }
}
