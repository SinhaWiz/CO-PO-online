import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Radio,
  RadioGroup,
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
  clearCourseReportDraft,
  downloadFacultyReport,
  generateCourseReport,
  getCourseReportContext,
  getMyAssignments,
  saveCourseReportDraft,
  type ActionPlanRow,
  type CoAttainmentRow,
  type CourseReportForm,
  type CourseReportSeed,
  type MyAssignment,
  type TopicRow,
} from '../../api/faculty';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;
const AUTOSAVE_DELAY_MS = 1500;

const emptyForm = (seed: CourseReportSeed): CourseReportForm => ({
  lectureHours: seed.credits != null ? String(seed.credits) : '',
  tutorialHours: '0',
  practicalHours: '0',
  instructors: seed.instructorDefault,
  hodName: '',
  studentsCompleting: '',
  percentPassed: '',
  percentFailed: '',
  gradeDistribution: seed.letterGrades.map((letter) => ({ letter, count: 0 })),
  coAttainment: seed.coCodes.map((coCode) => ({ coCode, maxMarks: 0, studentsAttained: 0, attainmentPercent: 0, remarks: '' })),
  topics: [],
  coverageLevel: '',
  topicDeviation: '',
  methodLectures: false,
  methodLab: false,
  methodSeminar: false,
  methodActivity: false,
  methodCaseStudy: false,
  methodAssignment: false,
  otherMethods: '',
  assessmentMethods: seed.assessmentMethodDefaults,
  facilitiesLevel: '',
  inadequacies: '',
  adminConstraints: '',
  studentCriticism: '',
  moderatorComments: '',
  externalComments: '',
  enhancementProgress: '',
  actionPlan: [],
  thresholdOverride: String(seed.coCohortThresholdDefault),
});

// The course's configured CO/grade/assessment-method label sets can drift after a
// draft was last saved (an admin adds a CO, say) - so row STRUCTURE always comes
// fresh from the seed, and only per-row VALUES are overlaid from the saved draft by
// matching key (coCode / letter / method name). Free-form lists (topics, action plan)
// have no fixed key set, so they're restored as full-list replacements instead.
// This mirrors CourseReportDraftManager's own restore semantics on the desktop side.
const buildInitialForm = (seed: CourseReportSeed, draft: CourseReportForm | null): CourseReportForm => {
  if (!draft) return emptyForm(seed);

  const gradeByLetter = new Map(draft.gradeDistribution.map((r) => [r.letter, r]));
  const gradeDistribution = seed.letterGrades.map((letter) => ({ letter, count: gradeByLetter.get(letter)?.count ?? 0 }));

  const coByCode = new Map(draft.coAttainment.map((r) => [r.coCode, r]));
  const coAttainment = seed.coCodes.map(
    (coCode) => coByCode.get(coCode) ?? { coCode, maxMarks: 0, studentsAttained: 0, attainmentPercent: 0, remarks: '' },
  );

  const methodByName = new Map(draft.assessmentMethods.map((r) => [r.method, r]));
  const assessmentMethods = seed.assessmentMethodDefaults.map((def) => {
    const existing = methodByName.get(def.method);
    return existing ? { ...def, percent: existing.percent } : def;
  });

  return { ...draft, gradeDistribution, coAttainment, assessmentMethods };
};

const CourseReport = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');
  const [seed, setSeed] = useState<CourseReportSeed | null>(null);
  const [form, setForm] = useState<CourseReportForm | null>(null);
  const [loading, setLoading] = useState(false);
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved'>('idle');
  const [dirty, setDirty] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generatedFile, setGeneratedFile] = useState<string | null>(null);
  const [issues, setIssues] = useState<string[]>([]);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const saveTimerRef = useRef<number | null>(null);
  const skipAutosaveRef = useRef(false);

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
    setIssues([]);
    setDirty(false);
    if (!selectedAssignment) {
      setSeed(null);
      setForm(null);
      return;
    }
    const { courseCode, programme, academicYear, department } = selectedAssignment;
    setLoading(true);
    getCourseReportContext(courseCode, programme, academicYear, department)
      .then((res) => {
        skipAutosaveRef.current = true;
        setSeed(res.data.seed);
        setForm(buildInitialForm(res.data.seed, res.data.draft));
      })
      .catch((error) => {
        console.error('Failed to load course report', error);
        setMessage({ type: 'error', text: 'Failed to load the course report form.' });
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey]);

  // Desktop autosaves this form on every single keystroke. Debouncing it here avoids
  // hammering the API while someone is actively typing a paragraph into one of the
  // free-text fields, while still saving automatically without an explicit button.
  useEffect(() => {
    if (!form || !selectedAssignment) return;
    if (skipAutosaveRef.current) {
      skipAutosaveRef.current = false;
      return;
    }
    setDirty(true);
    if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);
    saveTimerRef.current = window.setTimeout(async () => {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      setSaveStatus('saving');
      try {
        await saveCourseReportDraft(courseCode, programme, academicYear, department, form);
        setSaveStatus('saved');
        setDirty(false);
      } catch (error) {
        console.error('Autosave failed', error);
        setSaveStatus('idle');
      }
    }, AUTOSAVE_DELAY_MS);
    return () => {
      if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form]);

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const updateForm = (patch: Partial<CourseReportForm>) => setForm((prev) => (prev ? { ...prev, ...patch } : prev));

  const updatePassPercent = (value: string) => {
    const num = Number(value);
    updateForm(Number.isNaN(num) || value.trim() === ''
      ? { percentPassed: value }
      : { percentPassed: value, percentFailed: String(Math.min(100, Math.max(0, 100 - num))) });
  };
  const updateFailPercent = (value: string) => {
    const num = Number(value);
    updateForm(Number.isNaN(num) || value.trim() === ''
      ? { percentFailed: value }
      : { percentFailed: value, percentPassed: String(Math.min(100, Math.max(0, 100 - num))) });
  };

  const updateGradeCount = (index: number, count: number) => {
    setForm((prev) => prev ? { ...prev, gradeDistribution: prev.gradeDistribution.map((r, i) => (i === index ? { ...r, count } : r)) } : prev);
  };

  const updateCoRow = (index: number, patch: Partial<CoAttainmentRow>) => {
    setForm((prev) => {
      if (!prev) return prev;
      const coAttainment = prev.coAttainment.map((r, i) => {
        if (i !== index) return r;
        const updated = { ...r, ...patch };
        // Mirrors desktop: editing "Students Attained" alone re-derives a default
        // Attainment % - a convenience, not authoritative, freely overridable after.
        if ('studentsAttained' in patch && seed && seed.totalStudents > 0) {
          updated.attainmentPercent = Math.round((updated.studentsAttained / seed.totalStudents) * 10000) / 100;
        }
        return updated;
      });
      return { ...prev, coAttainment };
    });
  };

  const updateAssessmentPercent = (index: number, percent: number) => {
    setForm((prev) => prev ? { ...prev, assessmentMethods: prev.assessmentMethods.map((r, i) => (i === index ? { ...r, percent } : r)) } : prev);
  };

  const addTopic = () => setForm((prev) => prev ? { ...prev, topics: [...prev.topics, { topic: '', hours: 0, instructor: seed?.instructorDefault ?? '' }] } : prev);
  const updateTopic = (index: number, patch: Partial<TopicRow>) =>
    setForm((prev) => prev ? { ...prev, topics: prev.topics.map((r, i) => (i === index ? { ...r, ...patch } : r)) } : prev);
  const removeTopic = (index: number) => setForm((prev) => prev ? { ...prev, topics: prev.topics.filter((_, i) => i !== index) } : prev);

  const addAction = () => setForm((prev) => prev ? { ...prev, actionPlan: [...prev.actionPlan, { action: '', completionDate: '', responsible: '' }] } : prev);
  const updateAction = (index: number, patch: Partial<ActionPlanRow>) =>
    setForm((prev) => prev ? { ...prev, actionPlan: prev.actionPlan.map((r, i) => (i === index ? { ...r, ...patch } : r)) } : prev);
  const removeAction = (index: number) => setForm((prev) => prev ? { ...prev, actionPlan: prev.actionPlan.filter((_, i) => i !== index) } : prev);

  const handleGenerate = async () => {
    if (!form || !selectedAssignment) return;
    setGenerating(true);
    setMessage(null);
    setIssues([]);
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const res = await generateCourseReport(courseCode, programme, academicYear, department, form);
      if (res.data.pdfFileName) {
        setGeneratedFile(res.data.pdfFileName);
        setDirty(false);
        setMessage({ type: 'success', text: 'Report generated - your draft has been cleared.' });
      } else {
        setIssues(res.data.issues);
        setMessage({ type: 'error', text: 'Fix the issues below, then generate again.' });
      }
    } catch (error: any) {
      setMessage({ type: 'error', text: error?.response?.data?.message || 'Failed to generate report.' });
    } finally {
      setGenerating(false);
    }
  };

  const handleDiscardDraft = async () => {
    if (!selectedAssignment || !seed) return;
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      await clearCourseReportDraft(courseCode, programme, academicYear, department);
      skipAutosaveRef.current = true;
      setForm(emptyForm(seed));
      setDirty(false);
      setMessage({ type: 'success', text: 'Draft discarded.' });
    } catch (error) {
      console.error('Failed to discard draft', error);
      setMessage({ type: 'error', text: 'Failed to discard the draft.' });
    }
  };

  const handleDownload = async () => {
    if (!generatedFile) return;
    try {
      const blob = await downloadFacultyReport('Course', generatedFile);
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

  const creditTotal = form ? (Number(form.lectureHours) || 0) + (Number(form.tutorialHours) || 0) + (Number(form.practicalHours) || 0) : 0;
  const gradePercent = (count: number) => (seed && seed.totalStudents > 0 ? ((count / seed.totalStudents) * 100).toFixed(2) : '0.00');
  const coStatus = (row: CoAttainmentRow) => {
    const threshold = Number(form?.thresholdOverride);
    if (!form || Number.isNaN(threshold)) return '';
    return row.attainmentPercent >= threshold ? 'Attained' : 'Not Attained';
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Course Report</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        A self-assessment report for one of your course assignments - most fields here are yours to fill in, not
        computed automatically. Your progress saves automatically as you type.
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

      {!loading && form && seed && (
        <>
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
            <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>
              {saveStatus === 'saving' ? 'Saving draft...' : dirty ? 'Unsaved changes' : 'Draft saved'}
            </Typography>
          </Box>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>1. Basic Information</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, mb: 1.5 }}>
              <TextField label="Programme" size="small" value={selectedAssignment?.programme ?? ''} disabled />
              <TextField label="Course" size="small" value={`${selectedAssignment?.courseCode ?? ''} - ${seed.courseName}`} disabled />
              <TextField label="Academic Year" size="small" value={selectedAssignment?.academicYear ?? ''} disabled />
              <TextField label="Semester" size="small" value={seed.semester} disabled />
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 1.5, mb: 1.5 }}>
              <TextField label="Lecture Hours" size="small" value={form.lectureHours} onChange={(e) => updateForm({ lectureHours: e.target.value })} />
              <TextField label="Tutorial Hours" size="small" value={form.tutorialHours} onChange={(e) => updateForm({ tutorialHours: e.target.value })} />
              <TextField label="Practical Hours" size="small" value={form.practicalHours} onChange={(e) => updateForm({ practicalHours: e.target.value })} />
              <TextField
                label="Total"
                size="small"
                value={creditTotal}
                disabled
                helperText={seed.credits != null ? `Course credits: ${seed.credits}` : undefined}
              />
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5 }}>
              <TextField label="Instructor(s)" size="small" value={form.instructors} onChange={(e) => updateForm({ instructors: e.target.value })} />
              <TextField label="Head of Department" size="small" value={form.hodName} onChange={(e) => updateForm({ hodName: e.target.value })} />
            </Box>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>2. Statistical Information</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 1.5, mb: 2 }}>
              <TextField label="Students Attending" size="small" value={seed.totalStudents} disabled />
              <TextField
                label="Students Completing"
                size="small"
                value={form.studentsCompleting}
                onChange={(e) => updateForm({ studentsCompleting: e.target.value })}
              />
              <TextField
                label="% Completing"
                size="small"
                value={
                  seed.totalStudents > 0 && form.studentsCompleting !== ''
                    ? ((Number(form.studentsCompleting) / seed.totalStudents) * 100).toFixed(2)
                    : '0.00'
                }
                disabled
              />
              <TextField label="Passed %" size="small" value={form.percentPassed} onChange={(e) => updatePassPercent(e.target.value)} />
              <TextField label="Failed %" size="small" value={form.percentFailed} onChange={(e) => updateFailPercent(e.target.value)} />
              <TextField
                label="CO Cohort Threshold %"
                size="small"
                value={form.thresholdOverride}
                onChange={(e) => updateForm({ thresholdOverride: e.target.value })}
              />
            </Box>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Distribution of Grades</Typography>
            <TableContainer sx={{ mb: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Grade</TableCell>
                    <TableCell align="right">Count</TableCell>
                    <TableCell align="right">%</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {form.gradeDistribution.map((row, index) => (
                    <TableRow key={row.letter}>
                      <TableCell>{row.letter}</TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small"
                          type="number"
                          variant="standard"
                          value={row.count}
                          onChange={(e) => updateGradeCount(index, Math.max(0, Number(e.target.value)))}
                          sx={{ width: 80 }}
                        />
                      </TableCell>
                      <TableCell align="right">{gradePercent(row.count)}%</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>CO Attainment</Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>CO</TableCell>
                    <TableCell align="right">Max Marks</TableCell>
                    <TableCell align="right">Students Attained</TableCell>
                    <TableCell align="right">Attainment %</TableCell>
                    <TableCell>Remarks</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {form.coAttainment.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} sx={{ color: '#94a3b8' }}>
                        This course has no COs configured yet - an admin sets that up under Manage Courses.
                      </TableCell>
                    </TableRow>
                  )}
                  {form.coAttainment.map((row, index) => (
                    <TableRow key={row.coCode}>
                      <TableCell sx={{ fontWeight: 600 }}>{row.coCode}</TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small" type="number" variant="standard" value={row.maxMarks}
                          onChange={(e) => updateCoRow(index, { maxMarks: Math.max(0, Number(e.target.value)) })}
                          sx={{ width: 80 }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small" type="number" variant="standard" value={row.studentsAttained}
                          onChange={(e) => updateCoRow(index, { studentsAttained: Math.max(0, Number(e.target.value)) })}
                          sx={{ width: 80 }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small" type="number" variant="standard" value={row.attainmentPercent}
                          onChange={(e) => updateCoRow(index, { attainmentPercent: Number(e.target.value) })}
                          sx={{ width: 80 }}
                        />
                      </TableCell>
                      <TableCell>
                        <TextField
                          size="small" variant="standard" value={row.remarks} placeholder="Optional, max 100 chars"
                          onChange={(e) => updateCoRow(index, { remarks: e.target.value.slice(0, 100) })}
                          fullWidth
                        />
                      </TableCell>
                      <TableCell sx={{ color: coStatus(row) === 'Attained' ? '#16a34a' : '#dc2626', fontWeight: 600 }}>
                        {coStatus(row)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1.5 }}>3. Course Related Information</Typography>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Details of Course Taught</Typography>
            <TableContainer sx={{ mb: 1 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Topic</TableCell>
                    <TableCell align="right">Hours</TableCell>
                    <TableCell>Instructor</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {form.topics.map((row, index) => (
                    <TableRow key={index}>
                      <TableCell>
                        <TextField size="small" variant="standard" fullWidth value={row.topic} onChange={(e) => updateTopic(index, { topic: e.target.value })} />
                      </TableCell>
                      <TableCell align="right">
                        <TextField size="small" type="number" variant="standard" sx={{ width: 80 }} value={row.hours} onChange={(e) => updateTopic(index, { hours: Number(e.target.value) })} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" variant="standard" fullWidth value={row.instructor} onChange={(e) => updateTopic(index, { instructor: e.target.value })} />
                      </TableCell>
                      <TableCell>
                        <IconButton size="small" onClick={() => removeTopic(index)} title="Remove topic">✕</IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Button size="small" onClick={addTopic} sx={{ mb: 2 }}>+ Add Topic</Button>

            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mb: 2 }}>
              <Box>
                <Typography sx={{ fontWeight: 600, mb: 0.5 }}>Coverage</Typography>
                <RadioGroup row value={form.coverageLevel} onChange={(e) => updateForm({ coverageLevel: e.target.value })}>
                  <FormControlLabel value="high" control={<Radio size="small" />} label=">90%" />
                  <FormControlLabel value="medium" control={<Radio size="small" />} label="70-90%" />
                  <FormControlLabel value="low" control={<Radio size="small" />} label="<70%" />
                </RadioGroup>
              </Box>
              <TextField
                label="Deviation from plan (if any)" size="small" multiline minRows={2}
                value={form.topicDeviation} onChange={(e) => updateForm({ topicDeviation: e.target.value })}
              />
            </Box>

            <Typography sx={{ fontWeight: 600, mb: 0.5 }}>Teaching and Learning Methods</Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
              {([
                ['methodLectures', 'Lectures'], ['methodLab', 'Lab'], ['methodSeminar', 'Seminar'],
                ['methodActivity', 'Activity'], ['methodCaseStudy', 'Case Study'], ['methodAssignment', 'Assignment'],
              ] as const).map(([key, label]) => (
                <FormControlLabel
                  key={key}
                  control={<Checkbox size="small" checked={form[key]} onChange={(e) => updateForm({ [key]: e.target.checked } as Partial<CourseReportForm>)} />}
                  label={label}
                />
              ))}
            </Box>
            <TextField
              label="Other methods" size="small" fullWidth sx={{ mb: 2 }}
              value={form.otherMethods} onChange={(e) => updateForm({ otherMethods: e.target.value })}
            />

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Student Assessment</Typography>
            <TableContainer sx={{ mb: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Method</TableCell>
                    <TableCell align="right">%</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {form.assessmentMethods.map((row, index) => (
                    <TableRow key={row.method}>
                      <TableCell>{row.method}</TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small" type="number" variant="standard" sx={{ width: 80 }}
                          value={row.percent} onChange={(e) => updateAssessmentPercent(index, Number(e.target.value))}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mb: 2 }}>
              <Box>
                <Typography sx={{ fontWeight: 600, mb: 0.5 }}>Facilities and Teaching Materials</Typography>
                <RadioGroup row value={form.facilitiesLevel} onChange={(e) => updateForm({ facilitiesLevel: e.target.value })}>
                  <FormControlLabel value="adequate" control={<Radio size="small" />} label="Adequate" />
                  <FormControlLabel value="partial" control={<Radio size="small" />} label="Partial" />
                  <FormControlLabel value="inadequate" control={<Radio size="small" />} label="Inadequate" />
                </RadioGroup>
              </Box>
              <TextField
                label="Inadequacies (if any)" size="small" multiline minRows={2}
                value={form.inadequacies} onChange={(e) => updateForm({ inadequacies: e.target.value })}
              />
            </Box>

            <Divider sx={{ mb: 2 }} />

            <Box sx={{ display: 'grid', gap: 2, mb: 2 }}>
              <TextField
                label="Administrative/Organizational Constraints" multiline minRows={2} size="small"
                value={form.adminConstraints} onChange={(e) => updateForm({ adminConstraints: e.target.value })}
              />
              <TextField
                label="Student Evaluation / Criticism" multiline minRows={2} size="small"
                value={form.studentCriticism} onChange={(e) => updateForm({ studentCriticism: e.target.value })}
              />
              <TextField
                label="Moderator Comments" multiline minRows={2} size="small"
                value={form.moderatorComments} onChange={(e) => updateForm({ moderatorComments: e.target.value })}
              />
              <TextField
                label="External Evaluator Comments" multiline minRows={2} size="small"
                value={form.externalComments} onChange={(e) => updateForm({ externalComments: e.target.value })}
              />
              <TextField
                label="Course Enhancement Progress" multiline minRows={2} size="small"
                value={form.enhancementProgress} onChange={(e) => updateForm({ enhancementProgress: e.target.value })}
              />
            </Box>

            <Typography sx={{ fontWeight: 600, mb: 1 }}>Action Plan for {seed.nextAcademicYear}</Typography>
            <TableContainer sx={{ mb: 1 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Action</TableCell>
                    <TableCell>Completion Date</TableCell>
                    <TableCell>Responsible</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {form.actionPlan.map((row, index) => (
                    <TableRow key={index}>
                      <TableCell>
                        <TextField size="small" variant="standard" fullWidth value={row.action} onChange={(e) => updateAction(index, { action: e.target.value })} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" variant="standard" fullWidth value={row.completionDate} onChange={(e) => updateAction(index, { completionDate: e.target.value })} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" variant="standard" fullWidth value={row.responsible} onChange={(e) => updateAction(index, { responsible: e.target.value })} />
                      </TableCell>
                      <TableCell>
                        <IconButton size="small" onClick={() => removeAction(index)} title="Remove row">✕</IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Button size="small" onClick={addAction}>+ Add Action</Button>
          </Paper>

          {issues.length > 0 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {issues.map((issue) => <li key={issue}>{issue}</li>)}
              </ul>
            </Alert>
          )}

          <Box sx={{ display: 'flex', gap: 1.5, mb: 4 }}>
            <Button variant="contained" onClick={handleGenerate} disabled={generating}>
              {generating ? 'Generating...' : 'Generate PDF Report'}
            </Button>
            {generatedFile && (
              <Button variant="outlined" onClick={handleDownload}>
                Download {generatedFile}
              </Button>
            )}
            <Button color="inherit" onClick={handleDiscardDraft}>
              Discard Draft
            </Button>
          </Box>
        </>
      )}
    </Box>
  );
};

export default CourseReport;
