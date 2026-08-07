package org.example.copo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grading scale is the one piece of business logic every other report/result
 * feature built this migration ultimately renders through (letter grade, GPA, pass/
 * fail) - worth pinning down exactly, including the exact boundary values, since a
 * single off-by-one at a cutoff (e.g. exactly 80.0%) silently changes a student's grade.
 */
class GradeProcessorTest {

    @ParameterizedTest
    @CsvSource({
        "100.0, A+",
        "80.0, A+",
        "79.999, A",
        "75.0, A",
        "74.999, A-",
        "70.0, A-",
        "69.999, B+",
        "65.0, B+",
        "64.999, B",
        "60.0, B",
        "59.999, B-",
        "55.0, B-",
        "54.999, C+",
        "50.0, C+",
        "49.999, C",
        "45.0, C",
        "44.999, D",
        "40.0, D",
        "39.999, F",
        "0.0, F",
    })
    void getLetterGrade_matchesExactBoundaries(double percentage, String expectedLetter) {
        assertThat(GradeProcessor.getLetterGrade(percentage)).isEqualTo(expectedLetter);
    }

    @Test
    void getLetterGrade_belowZero_fallsBackToF() {
        assertThat(GradeProcessor.getLetterGrade(-5.0)).isEqualTo("F");
    }

    @Test
    void getLetterGrade_above100_isStillAPlus() {
        // Weighted percentages in this app are always <=100 in practice, but the
        // scale itself has no upper bound check - A+ is "80 and up," not "80-100."
        assertThat(GradeProcessor.getLetterGrade(150.0)).isEqualTo("A+");
    }

    @ParameterizedTest
    @CsvSource({
        "A+, 4.00", "A, 3.75", "A-, 3.50", "B+, 3.25", "B, 3.00",
        "B-, 2.75", "C+, 2.50", "C, 2.25", "D, 2.00", "F, 0.00",
    })
    void getGradePoint_matchesEachLetter(String letter, double expectedPoint) {
        assertThat(GradeProcessor.getGradePoint(letter)).isEqualTo(expectedPoint);
    }

    @Test
    void getGradePoint_unknownLetter_defaultsToZero() {
        assertThat(GradeProcessor.getGradePoint("Z")).isEqualTo(0.0);
    }

    @ParameterizedTest
    @CsvSource({ "40.0, true", "39.999, false", "0.0, false", "100.0, true" })
    void isPassing_usesFortyPercentCutoff(double percentage, boolean expectedPass) {
        assertThat(GradeProcessor.isPassing(percentage)).isEqualTo(expectedPass);
    }

    @Test
    void getAllLetterGrades_returnsAllTenGradesHighestFirst() {
        assertThat(GradeProcessor.getAllLetterGrades())
            .containsExactly("A+", "A", "A-", "B+", "B", "B-", "C+", "C", "D", "F");
    }
}
