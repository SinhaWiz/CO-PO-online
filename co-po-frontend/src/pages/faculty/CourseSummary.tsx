import { useEffect, useState } from 'react';
import { Box, Chip, Paper, Typography } from '@mui/material';
import { getCourseSummary, type AssessmentSectionSummary, type CourseSummary as CourseSummaryData } from '../../api/faculty';
import { AssignmentPickerField, useAssignmentPicker } from '../../components/AssignmentPicker';

const sectionStatus = (section: AssessmentSectionSummary) => {
  if (section.questionCount === 0) {
    return { label: 'Not Setup', color: '#9ca3af', bg: '#f3f4f6' };
  }
  if (section.marksEntered === 0) {
    return { label: 'No Marks', color: '#dc2626', bg: '#fee2e2' };
  }
  if (section.marksEntered < section.totalPossibleEntries) {
    const pct = (section.marksEntered * 100) / section.totalPossibleEntries;
    return { label: `${pct.toFixed(0)}% Done`, color: '#d97706', bg: '#fef3c7' };
  }
  return { label: 'Complete', color: '#059669', bg: '#d1fae5' };
};

// Ports the desktop app's Course Summary screen - a setup/grading-completeness
// dashboard, one card per assessment section. Purely a status view (question counts,
// marks totals, completion %) of data managed elsewhere (Course Questions, Student
// Marks) - nothing here is editable.
const CourseSummary = () => {
  const [error, setError] = useState<string | null>(null);
  const { assignments, selectedKey, setSelectedKey, selectedAssignment } = useAssignmentPicker(setError);
  const [summary, setSummary] = useState<CourseSummaryData | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setSummary(null);
    if (!selectedAssignment) return;
    const { courseCode, programme, academicYear } = selectedAssignment;
    setLoading(true);
    getCourseSummary(courseCode, programme, academicYear)
      .then((res) => setSummary(res.data))
      .catch((err) => {
        console.error('Failed to load course summary', err);
        setError('Failed to load course summary.');
      })
      .finally(() => setLoading(false));
  }, [selectedKey]);

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Course Summary</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        A quick look at how set up and how graded each assessment section is for one of your course assignments.
      </Typography>

      {error && <Typography sx={{ color: '#dc2626', mb: 2 }}>{error}</Typography>}

      <AssignmentPickerField assignments={assignments} selectedKey={selectedKey} onChange={setSelectedKey} />

      {loading && <Typography sx={{ color: '#64748b' }}>Loading...</Typography>}

      {!loading && summary && (
        <>
          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontSize: 20, fontWeight: 700, color: '#1e293b' }}>
              {summary.courseCode} - {summary.courseName}
            </Typography>
            <Typography sx={{ color: '#64748b', mb: 2 }}>
              {summary.programme} - {summary.academicYear}
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 2 }}>
              <Stat label="Enrolled Students" value={String(summary.enrolledStudents)} />
              <Stat label="Total Questions" value={String(summary.totalQuestions)} />
              <Stat label="Total Marks" value={summary.totalMarks.toFixed(0)} />
              <Stat label="Completion" value={`${summary.completionPercentage.toFixed(1)}%`} />
            </Box>
          </Paper>

          {summary.sections.length === 0 ? (
            <Paper sx={{ p: 2.5 }}>
              <Typography sx={{ color: '#64748b' }}>
                No assessment sections configured for this course. Contact an administrator to set them up.
              </Typography>
            </Paper>
          ) : (
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)', md: 'repeat(4, 1fr)' }, gap: 2 }}>
              {summary.sections.map((section) => {
                const status = sectionStatus(section);
                return (
                  <Paper key={section.sectionName} sx={{ p: 2, border: '2px solid #d1fae5' }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#059669', mb: 0.5 }}>
                      {section.sectionName}
                    </Typography>
                    <Typography sx={{ fontSize: 22, fontWeight: 700, color: '#10b981' }}>
                      {section.questionCount} question{section.questionCount !== 1 ? 's' : ''}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: '#6b7280', mb: 1 }}>
                      Total: {section.totalMarks.toFixed(0)} marks
                    </Typography>
                    <Chip
                      size="small"
                      label={status.label}
                      sx={{ bgcolor: status.bg, color: status.color, fontWeight: 700, fontSize: 11 }}
                    />
                  </Paper>
                );
              })}
            </Box>
          )}
        </>
      )}
    </Box>
  );
};

const Stat = ({ label, value }: { label: string; value: string }) => (
  <Box>
    <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>{label}</Typography>
    <Typography sx={{ fontSize: 22, fontWeight: 700, color: '#1e293b' }}>{value}</Typography>
  </Box>
);

export default CourseSummary;
