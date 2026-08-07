import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
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
  TextField,
  Typography,
} from '@mui/material';
import {
  downloadFacultyReport,
  generateSummaryReport,
  getMyAssignments,
  getSummaryReportPreview,
  type MyAssignment,
  type SummaryReportPreview,
} from '../../api/faculty';
import { summarizeFeedbackWithAI } from '../../api/reports';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;

const FEEDBACK_LIMITS = { feedback1: 500, feedback2: 700, improvementPlan: 1000 } as const;

const SummaryReport = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');
  const [preview, setPreview] = useState<SummaryReportPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [feedback1, setFeedback1] = useState('');
  const [feedback2, setFeedback2] = useState('');
  const [improvementPlan, setImprovementPlan] = useState('');
  const [rawFeedback, setRawFeedback] = useState('');
  const [summarizing, setSummarizing] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generatedFile, setGeneratedFile] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
      setMessage({ type: 'error', text: 'Failed to load your course assignments.' });
    });
  }, []);

  useEffect(() => {
    setGeneratedFile(null);
    setFeedback1('');
    setFeedback2('');
    setImprovementPlan('');
    setRawFeedback('');
    if (!selectedAssignment) {
      setPreview(null);
      return;
    }
    const { courseCode, programme, academicYear, department } = selectedAssignment;
    setLoading(true);
    getSummaryReportPreview(courseCode, programme, academicYear, department)
      .then((res) => setPreview(res.data))
      .catch((error) => {
        console.error('Failed to load summary report preview', error);
        setMessage({ type: 'error', text: 'Failed to load the summary report preview.' });
      })
      .finally(() => setLoading(false));
  }, [selectedKey]);

  const handleSummarize = async () => {
    if (!rawFeedback.trim()) return;
    setSummarizing(true);
    setMessage(null);
    try {
      const res = await summarizeFeedbackWithAI(rawFeedback);
      setFeedback1(res.data.summary.slice(0, FEEDBACK_LIMITS.feedback1));
    } catch (error: any) {
      setMessage({ type: 'error', text: error?.response?.data?.error || 'Failed to summarize feedback.' });
    } finally {
      setSummarizing(false);
    }
  };

  const handleGenerate = async () => {
    if (!selectedAssignment || !preview) return;
    setGenerating(true);
    setMessage(null);
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const res = await generateSummaryReport(courseCode, programme, academicYear, department, {
        feedback1, feedback2, improvementPlan,
      });
      if (res.data.pdfFileName) {
        setGeneratedFile(res.data.pdfFileName);
        setMessage({ type: 'success', text: 'Report generated successfully.' });
      } else {
        setMessage({ type: 'error', text: res.data.issues[0] || 'Failed to generate report.' });
      }
    } catch (error: any) {
      setMessage({ type: 'error', text: error?.response?.data?.message || 'Failed to generate report.' });
    } finally {
      setGenerating(false);
    }
  };

  const handleDownload = async () => {
    if (!generatedFile) return;
    try {
      const blob = await downloadFacultyReport('Summary', generatedFile);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = generatedFile;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Download failed', error);
      setMessage({ type: 'error', text: 'Failed to download the generated PDF.' });
    }
  };

  const bucketLabels = ['Below 40%', '40% and above', '50% and above', '60% and above', '70% and above', '80% and above', '90% and above'];

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Summary Report</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        A per-student CO evaluation sheet and attainment breakdown for one of your assignments, computed fresh from
        graded marks, plus space for your own feedback and an improvement plan.
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
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

      {!loading && preview && preview.issues.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Summary Report isn't available yet:
          <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
            {preview.issues.map((issue) => <li key={issue}>{issue}</li>)}
          </ul>
        </Alert>
      )}

      {!loading && preview && preview.issues.length === 0 && (
        <>
          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 1.5 }}>
              <TextField label="Course" size="small" value={`${selectedAssignment?.courseCode ?? ''} - ${preview.courseName}`} disabled />
              <TextField label="Semester" size="small" value={preview.semester} disabled />
              <TextField label="Batch" size="small" value={preview.majorityBatch || 'N/A'} disabled />
              <TextField label="Students" size="small" value={preview.totalStudents} disabled />
            </Box>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>1. Evaluation Sheet for COs</Typography>
            <TableContainer sx={{ maxHeight: 420, overflow: 'auto' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>Student</TableCell>
                    {preview.coCodes.map((co) => (
                      <TableCell key={co} align="right">{co} (/{preview.coMaxMarks[co]})</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.evaluationSheet.map((row) => (
                    <TableRow key={row.studentId}>
                      <TableCell>{row.studentName}</TableCell>
                      {preview.coCodes.map((co) => (
                        <TableCell key={co} align="right">{(row.obtainedByCo[co] ?? 0).toFixed(1)}</TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>2. Statistical Analysis: CO Attainment Levels</Typography>
            <TableContainer sx={{ mb: 2, overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>CO</TableCell>
                    {bucketLabels.map((label) => <TableCell key={label} align="right">{label}</TableCell>)}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {preview.histogram.map((row) => (
                    <TableRow key={row.coCode}>
                      <TableCell sx={{ fontWeight: 600 }}>{row.coCode}</TableCell>
                      {row.bucketCounts.map((count, i) => <TableCell key={i} align="right">{count}</TableCell>)}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Attainment Status</Typography>
            <Typography sx={{ color: '#64748b', fontSize: 13, mb: 1 }}>
              Rule: a CO is attained by a student at {preview.coIndividualThreshold}% of its marks; the course meets
              its target when {preview.coCohortThreshold}% of students attain it.
            </Typography>
            {preview.thresholdInStandardRange ? (
              preview.attainmentStatus.map((row) => (
                <Typography key={row.coCode} sx={{ fontSize: 14, mb: 0.5 }}>
                  <b>{row.coCode}</b> - {row.satisfied ? 'Attainment criteria satisfied' : 'Attainment criteria not satisfied'}
                  {' '}({row.achievedPercent.toFixed(1)}% of students)
                </Typography>
              ))
            ) : (
              <Typography sx={{ color: '#94a3b8', fontSize: 13 }}>
                Could not determine attainment status - the CO individual threshold ({preview.coIndividualThreshold}%)
                isn't one of the standard 40/50/60/70/80/90 brackets.
              </Typography>
            )}
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>3. Feedback & Comments</Typography>

            <Box sx={{ mb: 2, p: 1.5, border: '1px dashed #cbd5e1', borderRadius: 1 }}>
              <Typography sx={{ fontWeight: 600, fontSize: 13, mb: 1 }}>
                Paste raw student feedback to summarize with AI (optional)
              </Typography>
              <TextField
                placeholder="Paste feedback collected from students here..." multiline minRows={3} size="small" fullWidth
                value={rawFeedback} onChange={(e) => setRawFeedback(e.target.value)}
                sx={{ mb: 1 }}
              />
              <Button size="small" variant="outlined" onClick={handleSummarize} disabled={summarizing || !rawFeedback.trim()}>
                {summarizing ? 'Summarizing...' : '🤖 Summarize with AI'}
              </Button>
              <Typography sx={{ color: '#94a3b8', fontSize: 12, mt: 0.5 }}>
                Fills in "Summary of feedback from student" below - review and edit before generating.
              </Typography>
            </Box>

            <Box sx={{ display: 'grid', gap: 2 }}>
              <TextField
                label="Summary of feedback from student" multiline minRows={3} size="small"
                value={feedback1} onChange={(e) => setFeedback1(e.target.value.slice(0, FEEDBACK_LIMITS.feedback1))}
                helperText={`${feedback1.length}/${FEEDBACK_LIMITS.feedback1}`}
              />
              <TextField
                label="Feedback and comments from course teacher" multiline minRows={4} size="small"
                value={feedback2} onChange={(e) => setFeedback2(e.target.value.slice(0, FEEDBACK_LIMITS.feedback2))}
                helperText={`${feedback2.length}/${FEEDBACK_LIMITS.feedback2}`}
              />
              <TextField
                label="Improvement Plan" multiline minRows={5} size="small"
                value={improvementPlan} onChange={(e) => setImprovementPlan(e.target.value.slice(0, FEEDBACK_LIMITS.improvementPlan))}
                helperText={`${improvementPlan.length}/${FEEDBACK_LIMITS.improvementPlan}`}
              />
            </Box>
          </Paper>

          <Box sx={{ display: 'flex', gap: 1.5, mb: 4 }}>
            <Button variant="contained" onClick={handleGenerate} disabled={generating}>
              {generating ? 'Generating...' : 'Generate PDF Report'}
            </Button>
            {generatedFile && (
              <Button variant="outlined" onClick={handleDownload}>
                Download {generatedFile}
              </Button>
            )}
          </Box>
        </>
      )}
    </Box>
  );
};

export default SummaryReport;
