-- Platform module catalog: modules, their features and per-module settings. Module codes match institution_modules.module_code.
CREATE TABLE platform_modules (
    code        VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    icon        VARCHAR(30)  NOT NULL,
    record_noun VARCHAR(30)  NOT NULL DEFAULT 'Record',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    position    INT          NOT NULL
);
CREATE TABLE platform_features (
    module_code     VARCHAR(40)  NOT NULL REFERENCES platform_modules (code) ON DELETE CASCADE,
    code            VARCHAR(60)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL,
    default_enabled BOOLEAN      NOT NULL,
    position        INT          NOT NULL,
    PRIMARY KEY (module_code, code)
);
CREATE TABLE platform_module_settings (
    module_code   VARCHAR(40) NOT NULL REFERENCES platform_modules (code) ON DELETE CASCADE,
    setting_key   VARCHAR(40) NOT NULL,
    setting_value VARCHAR(100) NOT NULL,
    PRIMARY KEY (module_code, setting_key)
);

INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('STUDENT_INFORMATION', 'Student Management', 'CORE', 'Manage student profiles, admissions, academic records etc.', 'users', 'Student', TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'STUDENT_REGISTRATION', 'Student Registration', 'Create and manage student profiles', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'DOCUMENT_MANAGEMENT', 'Document Management', 'Upload and manage student documents', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'CLASS_ENROLLMENT', 'Class Enrollment', 'Assign students to classes', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'TRANSFER_PROMOTION', 'Transfer & Promotion', 'Manage student transfer and promotion', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'PROFILE_MANAGEMENT', 'Profile Management', 'View and update student information', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'BULK_IMPORT', 'Bulk Import', 'Import student data via CSV/Excel', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'ID_CARD_GENERATION', 'ID Card Generation', 'Generate student ID cards', FALSE, FALSE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('STUDENT_INFORMATION', 'STUDENT_ARCHIVE', 'Student Archive', 'Archive inactive student records', TRUE, TRUE, 7);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('TEACHER_MANAGEMENT', 'Teacher Management', 'CORE', 'Manage teacher profiles, subjects, assignments etc.', 'user', 'Teacher', TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'TEACHER_REGISTRATION', 'Teacher Registration', 'Create and manage teacher profiles', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'SUBJECT_ASSIGNMENT', 'Subject Assignment', 'Assign subjects and classes to teachers', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'WORKLOAD_MANAGEMENT', 'Workload Management', 'Track teaching load and free periods', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'TEACHER_DOCUMENTS', 'Teacher Documents', 'Store qualifications and documents', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'TEACHER_ATTENDANCE', 'Teacher Attendance', 'Record teacher attendance', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TEACHER_MANAGEMENT', 'PERFORMANCE_REVIEWS', 'Performance Reviews', 'Record periodic performance reviews', TRUE, TRUE, 5);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('PARENT_PORTAL', 'Parent Portal', 'CORE', 'Parent access to student information and communication.', 'users-round', 'Parent', TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('PARENT_PORTAL', 'PARENT_LOGIN', 'Parent Login', 'Secure sign-in for parents and guardians', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('PARENT_PORTAL', 'CHILD_PROGRESS_VIEW', 'Child Progress View', 'View attendance, marks and reports', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('PARENT_PORTAL', 'FEE_PAYMENT_RECEIPTS', 'Fee Payment & Receipts', 'View dues and download receipts', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('PARENT_PORTAL', 'TEACHER_MESSAGING', 'Teacher Messaging', 'Message teachers from the portal', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('PARENT_PORTAL', 'NOTIFICATIONS', 'Notifications', 'Receive alerts and announcements', TRUE, TRUE, 4);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('ACADEMICS', 'Academic Management', 'ACADEMIC', 'Classes, subjects, timetable, exams and grading.', 'graduation-cap', 'Record', TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'ACADEMIC_YEARS', 'Academic Years', 'Set up academic years', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'TERMS_SEMESTERS', 'Terms & Semesters', 'Define terms and grading periods', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'CLASSES_SECTIONS', 'Classes & Sections', 'Manage classes and sections', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'SUBJECTS', 'Subjects', 'Manage subjects and electives', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'TIMETABLE', 'Timetable', 'Build class and teacher timetables', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'SYLLABUS_PLANNING', 'Syllabus Planning', 'Plan the syllabus by term', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'LESSON_PLANS', 'Lesson Plans', 'Teachers prepare lesson plans', TRUE, TRUE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'HOMEWORK_ASSIGNMENTS', 'Homework & Assignments', 'Assign and track homework', TRUE, TRUE, 7);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'CLASS_PROMOTION', 'Class Promotion', 'Promote students between years', TRUE, TRUE, 8);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'CURRICULUM_MAPPING', 'Curriculum Mapping', 'Map subjects to curriculum standards', TRUE, TRUE, 9);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'ACADEMIC_CALENDAR', 'Academic Calendar', 'Share dates and holidays', FALSE, FALSE, 10);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ACADEMICS', 'CO_CURRICULAR_RECORDS', 'Co-curricular Records', 'Record clubs and activities', FALSE, FALSE, 11);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('EXAMS_RESULTS', 'Examination & Grading', 'ACADEMIC', 'Manage exams, results, report cards.', 'file-text', 'Record', TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'EXAM_SCHEDULING', 'Exam Scheduling', 'Schedule exams and invigilation', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'HALL_TICKETS', 'Hall Tickets', 'Generate hall tickets', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'MARK_ENTRY', 'Mark Entry', 'Enter and verify marks', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'GRADING_SCHEMES', 'Grading Schemes', 'Configure grades and GPA', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'REPORT_CARDS', 'Report Cards', 'Generate report cards', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'RESULT_PUBLISHING', 'Result Publishing', 'Publish results to portals', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'GRACE_MARKS', 'Grace Marks', 'Apply grace marks rules', TRUE, TRUE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'EXAM_ANALYTICS', 'Exam Analytics', 'Analyse class and subject results', TRUE, TRUE, 7);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'RE_EVALUATION_REQUESTS', 'Re-evaluation Requests', 'Handle re-evaluation requests', FALSE, FALSE, 8);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('EXAMS_RESULTS', 'QUESTION_BANK', 'Question Bank', 'Maintain a question bank', FALSE, FALSE, 9);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('ATTENDANCE', 'Attendance Management', 'ACADEMIC', 'Student, teacher and staff attendance.', 'calendar-check', 'Record', TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'DAILY_ATTENDANCE', 'Daily Attendance', 'Mark daily attendance', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'PERIOD_WISE_ATTENDANCE', 'Period-wise Attendance', 'Mark attendance per period', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'STAFF_ATTENDANCE', 'Staff Attendance', 'Record staff attendance', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'LEAVE_REQUESTS', 'Leave Requests', 'Apply for and approve leave', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'ABSENCE_NOTIFICATIONS', 'Absence Notifications', 'Notify parents of absence', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'ATTENDANCE_REPORTS', 'Attendance Reports', 'Reports by class and student', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'BIOMETRIC_INTEGRATION', 'Biometric Integration', 'Import biometric device data', FALSE, FALSE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('ATTENDANCE', 'GEO_FENCED_CHECK_IN', 'Geo-fenced Check-in', 'Check-in within a campus boundary', FALSE, FALSE, 7);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('FEES_PAYMENTS', 'Fee & Payment Management', 'FINANCE', 'Fee collection, receipts, payment gateway integration.', 'indian-rupee', 'Record', TRUE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'FEE_STRUCTURE', 'Fee Structure', 'Define fee heads and structures', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'FEE_COLLECTION', 'Fee Collection', 'Collect fees at the counter', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'RECEIPTS', 'Receipts', 'Issue and reprint receipts', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'CONCESSIONS_SCHOLARSHIPS', 'Concessions & Scholarships', 'Apply discounts and scholarships', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'FEE_REMINDERS', 'Fee Reminders', 'Remind parents of dues', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'PAYMENT_GATEWAY', 'Payment Gateway', 'Accept online payments', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'FEE_REPORTS', 'Fee Reports', 'Collection and dues reports', TRUE, TRUE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'REFUNDS', 'Refunds', 'Process fee refunds', TRUE, TRUE, 7);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'INSTALLMENT_PLANS', 'Installment Plans', 'Split fees into installments', FALSE, FALSE, 8);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('FEES_PAYMENTS', 'LATE_FEE_RULES', 'Late Fee Rules', 'Charge late fees automatically', FALSE, FALSE, 9);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('LIBRARY', 'Library Management', 'ACADEMIC', 'Manage books, catalog, issue/return.', 'book-open', 'Record', FALSE, 7);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'BOOK_CATALOG', 'Book Catalog', 'Maintain the book catalog', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'ISSUE_RETURN', 'Issue & Return', 'Issue and return books', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'FINES', 'Fines', 'Calculate overdue fines', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'RESERVATIONS', 'Reservations', 'Reserve books in advance', FALSE, FALSE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'E_BOOKS', 'E-Books', 'Share digital books', FALSE, FALSE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('LIBRARY', 'BARCODE_SCANNING', 'Barcode Scanning', 'Scan barcodes to issue', FALSE, FALSE, 5);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('TRANSPORT', 'Transport Management', 'ADMINISTRATION', 'Routes, GPS tracking, transport attendance.', 'bus', 'Record', TRUE, 8);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'ROUTES_STOPS', 'Routes & Stops', 'Manage routes and stops', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'VEHICLE_MANAGEMENT', 'Vehicle Management', 'Manage the vehicle fleet', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'DRIVER_MANAGEMENT', 'Driver Management', 'Manage drivers and licences', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'STUDENT_TRANSPORT_ASSIGNMENT', 'Student Transport Assignment', 'Assign students to routes', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'TRANSPORT_ATTENDANCE', 'Transport Attendance', 'Mark boarding attendance', TRUE, TRUE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'TRANSPORT_FEES', 'Transport Fees', 'Bill transport fees', TRUE, TRUE, 5);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'GPS_TRACKING', 'GPS Tracking', 'Track vehicles live', FALSE, FALSE, 6);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('TRANSPORT', 'PARENT_ALERTS', 'Parent Alerts', 'Alert parents on arrival', FALSE, FALSE, 7);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('HOSTEL', 'Hostel Management', 'ADMINISTRATION', 'Manage hostel allocation, room details.', 'building', 'Record', FALSE, 9);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'ROOM_ALLOCATION', 'Room Allocation', 'Allocate rooms and beds', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'HOSTEL_ATTENDANCE', 'Hostel Attendance', 'Record night attendance', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'MESS_MANAGEMENT', 'Mess Management', 'Plan menus and mess billing', FALSE, FALSE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'VISITOR_LOG', 'Visitor Log', 'Log visitors', FALSE, FALSE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'HOSTEL_FEES', 'Hostel Fees', 'Bill hostel fees', FALSE, FALSE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HOSTEL', 'MAINTENANCE_REQUESTS', 'Maintenance Requests', 'Track maintenance requests', FALSE, FALSE, 5);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('HEALTH_MEDICAL', 'Health & Medical', 'ADMINISTRATION', 'Student medical records and health tracking.', 'heart', 'Record', FALSE, 10);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HEALTH_MEDICAL', 'HEALTH_RECORDS', 'Health Records', 'Keep student health records', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HEALTH_MEDICAL', 'MEDICAL_ALERTS', 'Medical Alerts', 'Flag allergies and conditions', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HEALTH_MEDICAL', 'VACCINATION_TRACKING', 'Vaccination Tracking', 'Track vaccinations', FALSE, FALSE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('HEALTH_MEDICAL', 'INFIRMARY_VISITS', 'Infirmary Visits', 'Log infirmary visits', FALSE, FALSE, 3);
INSERT INTO platform_modules (code, name, category, description, icon, record_noun, enabled, position) VALUES ('COMMUNICATIONS', 'Communication', 'COMMUNICATION', 'Announcements, notifications, messaging.', 'megaphone', 'Record', TRUE, 11);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'ANNOUNCEMENTS', 'Announcements', 'Publish announcements', TRUE, TRUE, 0);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'EMAIL_NOTIFICATIONS', 'Email Notifications', 'Send email notifications', TRUE, TRUE, 1);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'SMS_NOTIFICATIONS', 'SMS Notifications', 'Send SMS notifications', TRUE, TRUE, 2);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'PUSH_NOTIFICATIONS', 'Push Notifications', 'Send app notifications', TRUE, TRUE, 3);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'MESSAGE_TEMPLATES', 'Message Templates', 'Reusable message templates', FALSE, FALSE, 4);
INSERT INTO platform_features (module_code, code, name, description, enabled, default_enabled, position) VALUES ('COMMUNICATIONS', 'SCHEDULED_MESSAGES', 'Scheduled Messages', 'Schedule messages for later', FALSE, FALSE, 5);
