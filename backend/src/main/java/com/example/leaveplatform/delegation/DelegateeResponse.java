package com.example.leaveplatform.delegation;

/** One colleague the delegation form can name, as the picker and the API show them. */
public record DelegateeResponse(Long id, String fullName, String employeeNo, String designation) {

    public static DelegateeResponse of(com.example.leaveplatform.identity.Employee employee) {
        return new DelegateeResponse(employee.getId(), employee.getFullName(),
                employee.getEmployeeNo(), employee.getDesignation());
    }

    /** The line the picker shows; a name alone is not enough to pick the right Ali. */
    public String label() {
        return fullName + (employeeNo == null ? "" : " (" + employeeNo + ")") + " - " + designation;
    }
}
