package com.centram.classzy.institution;

/** Small enums used by the Institution API. Stored as strings. */
public final class InstitutionEnums {
    private InstitutionEnums() {
    }

    public enum Lifecycle { DRAFT, ACTIVE, ARCHIVED }

    public enum GradingSystemType { PERCENTAGE, GRADE, CGPA, CUSTOM }

    public enum PlanType { TRIAL, BASIC, STANDARD, PREMIUM, PROFESSIONAL, ENTERPRISE }

    /** Shown in the list. Derived from lifecycle + plan + subscription dates, never stored. */
    public enum Status { DRAFT, ACTIVE, TRIAL, EXPIRING_SOON, EXPIRED, ARCHIVED }

    public enum ModuleCode {
        STUDENT_INFORMATION, ACADEMICS, ATTENDANCE, ASSIGNMENTS, EXAMS_RESULTS, FEES_PAYMENTS, LEAVE_MANAGEMENT,
        CLUBS_ACTIVITIES, COMMUNICATIONS, TRANSPORT, LIBRARY, HOSTEL, INVENTORY, ALUMNI,
        TEACHER_MANAGEMENT, PARENT_PORTAL, HEALTH_MEDICAL
    }

    public enum ImageKind {
        LOGO(2 * 1024 * 1024), BANNER(5 * 1024 * 1024);

        public final int maxBytes;

        ImageKind(int maxBytes) {
            this.maxBytes = maxBytes;
        }
    }
}
