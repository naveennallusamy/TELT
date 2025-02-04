package com.telt.entity.reference;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "ref_course_details", uniqueConstraints = {@UniqueConstraint(columnNames = {"gradType", "courseType", "courseName"})})
@Getter
@Setter
public class CourseDetails extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gradType;

    private String courseType;

    private String courseName;

    @ColumnDefault("'Active'")
    private String status;
}

