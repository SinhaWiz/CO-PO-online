import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import {
  getCourseResults,
  getMyAssignments,
  type CourseResultData,
  type MyAssignment,
} from '../../api/faculty';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;

const ViewResults = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');
  const [data, setData] = useState<CourseResultData | null>(null);
  const [issue, setIssue] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((err) => {
      console.error('Failed to load assignments', err);
      setError('Failed to load your course assignments.');
    });
  }, []);

  useEffect(() => {
    setData(null);
    setIssue(null);
    if (!selectedAssignment) return;
    const { courseCode, programme, academicYear, department } = selectedAssignment;
    setLoading(true);
    getCourseResults(courseCode, programme, academicYear, department)
      .then((res) => {
        setData(res.data.data);
        setIssue(res.data.issue);
      })
      .catch((err) => {
        console.error('Failed to load results', err);
        setError('Failed to load results.');
      })
      .finally(() => setLoading(false));
  }, [selectedKey]);

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>View Results</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        Weighted final results for one of your theory-course assignments - best-3 quiz/assignment average, mid,
        final, and (for older batches) attendance, combined per the course's weighting scheme.
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <FormControl size="small" fullWidth>
          <InputLabel>Course Assignment</InputLabel>
          <Select label="Course Assignment" value={selectedKey} onChange={(e) => setSelectedKey(e.target.value)}>
            {assignments.map((a) => (
              <MenuItem key={assignmentKey(a)} value={assignmentKey(a)}>
                {a.courseCode} - {a.courseName} ({a.programme}, {a.academicYear})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {assignments.length === 0 && (
          <Typography sx={{ color: '#94a3b8', fontSize: 13, mt: 1 }}>
            You have no course assignments yet - an admin needs to assign you a course first.
          </Typography>
        )}
      </Paper>

      {loading && <Typography sx={{ color: '#64748b' }}>Loading...</Typography>}

      {!loading && issue && (
        <Alert severity="warning" sx={{ mb: 2 }}>{issue}</Alert>
      )}

      {!loading && data && !issue && data.results.length === 0 && (
        <Alert severity="info">No students are enrolled in this course for {selectedAssignment?.academicYear}.</Alert>
      )}

      {!loading && data && !issue && data.results.length > 0 && (
        <>
          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
              <Chip label={`Majority Batch: ${data.majorityBatch}`} />
              <Chip
                label={data.batchBelow23 ? 'Weighting: Attendance 10% + Quiz/Assignment 15% + Mid 25% + Final 50%' : 'Weighting: Quiz/Assignment 20% + Mid 40% + Final 40%'}
                variant="outlined"
              />
            </Box>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>Statistics</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 1.5, mb: 2 }}>
              <Stat label="Total Students" value={String(data.statistics.totalStudents)} />
              <Stat label="Passed" value={`${data.statistics.passedCount} (${data.statistics.passPercentage.toFixed(1)}%)`} color="#16a34a" />
              <Stat label="Failed" value={`${data.statistics.failedCount} (${data.statistics.failPercentage.toFixed(1)}%)`} color="#dc2626" />
              <Stat label="Average" value={`${data.statistics.averagePercentage.toFixed(2)}% / GPA ${data.statistics.averageGpa.toFixed(2)}`} />
            </Box>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Grade Distribution</Typography>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    {data.statistics.gradeDistribution.map((g) => <TableCell key={g.letterGrade}>{g.letterGrade}</TableCell>)}
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    {data.statistics.gradeDistribution.map((g) => (
                      <TableCell key={g.letterGrade}>{g.count} ({g.percentage.toFixed(1)}%)</TableCell>
                    ))}
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 4 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>Student Results</Typography>
            <TableContainer sx={{ maxHeight: 480, overflow: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Student</TableCell>
                    <TableCell align="right">Batch</TableCell>
                    {data.batchBelow23 && <TableCell align="right">Attendance</TableCell>}
                    <TableCell align="right">Quiz/Assignment</TableCell>
                    <TableCell align="right">Mid</TableCell>
                    <TableCell align="right">Final</TableCell>
                    <TableCell align="right">Total %</TableCell>
                    <TableCell align="right">Grade</TableCell>
                    <TableCell align="right">GPA</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.results.map((row) => (
                    <TableRow key={row.studentId}>
                      <TableCell>{row.studentName}</TableCell>
                      <TableCell align="right">{row.batch}</TableCell>
                      {data.batchBelow23 && <TableCell align="right">{row.attendanceWeighted.toFixed(2)}</TableCell>}
                      <TableCell align="right">{row.quizAssignmentWeighted.toFixed(2)}</TableCell>
                      <TableCell align="right">{row.midWeighted.toFixed(2)}</TableCell>
                      <TableCell align="right">{row.finalWeighted.toFixed(2)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>{row.totalPercentage.toFixed(2)}</TableCell>
                      <TableCell align="right">{row.letterGrade}</TableCell>
                      <TableCell align="right">{row.gradePoint.toFixed(2)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={row.passed ? 'Pass' : 'Fail'} color={row.passed ? 'success' : 'error'} variant="outlined" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}
    </Box>
  );
};

const Stat = ({ label, value, color }: { label: string; value: string; color?: string }) => (
  <Box>
    <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>{label}</Typography>
    <Typography sx={{ fontSize: 18, fontWeight: 700, color: color ?? '#1e293b' }}>{value}</Typography>
  </Box>
);

export default ViewResults;
