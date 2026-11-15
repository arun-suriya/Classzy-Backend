package com.centram.classzy.academic.promotion;

public enum SubjectScope {
    ALL_SUBJECTS("all subjects"),
    CORE_SUBJECTS("core subjects"),
    ANY_SUBJECT("any subject");

    private final String label;

    SubjectScope(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
