package org.example.copo.service;

import java.util.List;

/**
 * Percentage -> letter grade / GPA, ported from the desktop app's GradeProcessor.
 * Stateless on purpose (matches the source, which is a static utility class too).
 */
public final class GradeProcessor {

    private GradeProcessor() {}

    public record Grade(String letter, double gradePoint, double minPercentage, double maxPercentage) {
        public boolean isInRange(double percentage) {
            if (letter.equals("A+")) return percentage >= minPercentage;
            if (letter.equals("F")) return percentage < maxPercentage;
            return percentage >= minPercentage && percentage < maxPercentage;
        }
    }

    // Ordered highest to lowest - order matters, getLetterGrade returns the first match.
    private static final List<Grade> GRADING_SCALE = List.of(
        new Grade("A+", 4.00, 80.0, 100.0),
        new Grade("A", 3.75, 75.0, 80.0),
        new Grade("A-", 3.50, 70.0, 75.0),
        new Grade("B+", 3.25, 65.0, 70.0),
        new Grade("B", 3.00, 60.0, 65.0),
        new Grade("B-", 2.75, 55.0, 60.0),
        new Grade("C+", 2.50, 50.0, 55.0),
        new Grade("C", 2.25, 45.0, 50.0),
        new Grade("D", 2.00, 40.0, 45.0),
        new Grade("F", 0.00, 0.0, 40.0)
    );

    public static final double PASSING_PERCENTAGE = 40.0;

    public static List<Grade> getGradingScale() {
        return GRADING_SCALE;
    }

    public static List<String> getAllLetterGrades() {
        return GRADING_SCALE.stream().map(Grade::letter).toList();
    }

    public static String getLetterGrade(double percentage) {
        for (Grade grade : GRADING_SCALE) {
            if (grade.isInRange(percentage)) return grade.letter();
        }
        return "F";
    }

    public static double getGradePoint(String letterGrade) {
        return GRADING_SCALE.stream()
            .filter(g -> g.letter().equals(letterGrade))
            .findFirst()
            .map(Grade::gradePoint)
            .orElse(0.0);
    }

    public static boolean isPassing(double percentage) {
        return percentage >= PASSING_PERCENTAGE;
    }
}
