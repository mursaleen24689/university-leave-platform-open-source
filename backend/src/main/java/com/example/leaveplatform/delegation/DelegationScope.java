package com.example.leaveplatform.delegation;

/**
 * Which approving step one delegation covers. The two steps have different
 * authorities and a person can hold one without the other, so handing over one
 * never hands over the other. See {@code V016}.
 */
public enum DelegationScope {

    SUPERVISOR("Recommending"),
    DECISION("Final decision");

    private final String label;

    DelegationScope(String label) {
        this.label = label;
    }

    /** What a screen calls this kind of handing-over. */
    public String label() {
        return label;
    }
}
