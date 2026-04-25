package org.example.copo.entity.keys;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseId implements Serializable {
    private String courseCode;
    private String programme;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseId courseId = (CourseId) o;
        return Objects.equals(courseCode, courseId.courseCode) &&
                Objects.equals(programme, courseId.programme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseCode, programme);
    }
}
