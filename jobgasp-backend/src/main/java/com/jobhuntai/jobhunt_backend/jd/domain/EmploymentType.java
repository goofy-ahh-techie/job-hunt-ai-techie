package com.jobhuntai.jobhunt_backend.jd.domain;

/**
 * Employment arrangement extracted from a job description. The intelligence service
 * normalises free-text phrasing ("Full-time", "full time") onto exactly these
 * constants or null before it ever reaches the backend, so an out-of-range value
 * cannot land in the {@code employment_type} column.
 */
public enum EmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP
}
