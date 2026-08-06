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
  Tabs,
  Tab,
  TextField,
  Typography,
} from '@mui/material';
import { getMyAssignments, getSectionsForCourse, resolveAssessmentInstance } from '../../api/faculty';
import type { CourseSection, MyAssignment } from '../../api/faculty';
import { getRoster, saveMarksBatch } from '../../api/gradebook';
import type { MarkEntry, Roster } from '../../api/gradebook';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;
const cellKey = (studentId: string, questionId: number) => `${studentId}::${questionId}`;

const EnterMarks = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');

  const [sections, setSections] = useState<CourseSection[]>([]);
  const [activeSectionId, setActiveSectionId] = useState<number | ''>('');
  const [assessmentId, setAssessmentId] = useState<number | null>(null);

  const [roster, setRoster] = useState<Roster | null>(null);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  const dirtyCount = Object.keys(edits).length;

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
      setMessage({ type: 'error', text: 'Failed to load your course assignments.' });
    });
  }, []);

  useEffect(() => {
    if (!selectedAssignment) {
      setSections([]);
      setActiveSectionId('');
      return;
    }
    getSectionsForCourse(selectedAssignment.courseCode, selectedAssignment.programme)
      .then((res) => {
        setSections(res.data);
        setActiveSectionId(res.data.length > 0 ? res.data[0].id : '');
      })
      .catch((error) => {
        console.error('Failed to load sections', error);
        setMessage({ type: 'error', text: 'Failed to load sections for this course.' });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey]);

  useEffect(() => {
    if (!selectedAssignment || activeSectionId === '') {
      setAssessmentId(null);
      setRoster(null);
      return;
    }
    resolveAssessmentInstance(activeSectionId, selectedAssignment.academicYear)
      .then((res) => {
        setAssessmentId(res.data.id);
        return getRoster(res.data.id);
      })
      .then((res) => {
        setRoster(res?.data ?? null);
        setEdits({});
      })
      .catch((error) => {
        console.error('Failed to resolve/load roster', error);
        setMessage({ type: 'error', text: 'Failed to load this section.' });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSectionId, selectedKey]);

  // Warn on tab close/refresh with unsaved edits - mirrors the desktop app's
  // unsaved-changes guard on the marks entry screen.
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirtyCount > 0) {
        e.preventDefault();
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirtyCount]);

  const savedValue = (studentId: string, questionId: number): number | null => {
    const row = roster?.rows.find((r) => r.studentId === studentId);
    return row?.cells.find((c) => c.questionId === questionId)?.marksObtained ?? null;
  };

  const displayValue = (studentId: string, questionId: number): string => {
    const key = cellKey(studentId, questionId);
    if (key in edits) return edits[key];
    const saved = savedValue(studentId, questionId);
    return saved === null ? '' : String(saved);
  };

  const handleCellChange = (studentId: string, questionId: number, value: string) => {
    setEdits((prev) => ({ ...prev, [cellKey(studentId, questionId)]: value }));
  };

  const switchSection = (sectionId: number) => {
    if (dirtyCount > 0) {
      const proceed = window.confirm(`You have ${dirtyCount} unsaved cell(s). Switch sections and discard them?`);
      if (!proceed) return;
    }
    setActiveSectionId(sectionId);
  };

  const handleSaveAll = async () => {
    if (!assessmentId || dirtyCount === 0) return;

    const entries: MarkEntry[] = [];
    const badEntries: string[] = [];
    for (const [key, raw] of Object.entries(edits)) {
      const [studentId, questionIdStr] = key.split('::');
      const questionId = Number(questionIdStr);
      const value = raw.trim() === '' ? null : Number(raw);
      if (value === null || Number.isNaN(value)) {
        badEntries.push(`${studentId} / question ${questionId}`);
        continue;
      }
      entries.push({ studentId, questionId, marksObtained: value });
    }

    if (badEntries.length > 0) {
      setMessage({ type: 'error', text: `Invalid (non-numeric) marks for: ${badEntries.join(', ')}` });
      return;
    }

    setSaving(true);
    try {
      const res = await saveMarksBatch(assessmentId, entries);
      const roster2 = await getRoster(assessmentId);
      setRoster(roster2.data);
      setEdits({});
      setMessage(
        res.data.errors.length > 0
          ? { type: 'error', text: `Saved ${res.data.saved}, ${res.data.errors.length} rejected: ${res.data.errors.join('; ')}` }
          : { type: 'success', text: `Saved ${res.data.saved} mark(s).` },
      );
    } catch (error: any) {
      console.error('Failed to save marks', error);
      setMessage({ type: 'error', text: error?.response?.data?.message || 'Failed to save marks.' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Student Marks</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <FormControl size="small" fullWidth>
          <InputLabel>Course Assignment</InputLabel>
          <Select
            label="Course Assignment"
            value={selectedKey}
            onChange={(e) => {
              if (dirtyCount > 0 && !window.confirm(`You have ${dirtyCount} unsaved cell(s). Switch assignments and discard them?`)) {
                return;
              }
              setSelectedKey(e.target.value);
            }}
          >
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

      {selectedAssignment && (
        <>
          <Paper sx={{ mb: 2 }}>
            <Tabs
              value={activeSectionId}
              onChange={(_, value) => switchSection(value)}
              variant="scrollable"
              scrollButtons="auto"
            >
              {sections.map((section) => (
                <Tab key={section.id} label={section.displayName} value={section.id} />
              ))}
            </Tabs>
            {sections.length === 0 && (
              <Typography sx={{ color: '#94a3b8', fontSize: 13, p: 2 }}>
                This course has no assessment sections defined yet - ask an admin to add some under Manage Courses.
              </Typography>
            )}
          </Paper>

          {roster && (
            <>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
                <Button variant="contained" onClick={handleSaveAll} disabled={dirtyCount === 0 || saving}>
                  {saving ? 'Saving...' : `Save ${dirtyCount > 0 ? `(${dirtyCount} unsaved)` : 'All'}`}
                </Button>
                {dirtyCount > 0 && (
                  <Typography sx={{ color: '#b45309', fontSize: 13 }}>
                    {dirtyCount} cell(s) not yet saved.
                  </Typography>
                )}
              </Box>

              {roster.questions.length === 0 ? (
                <Alert severity="info">This section has no questions yet - add some under Course Questions first.</Alert>
              ) : roster.rows.length === 0 ? (
                <Alert severity="info">No students are enrolled in this course for {selectedAssignment.academicYear} yet.</Alert>
              ) : (
                <TableContainer component={Paper}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Student ID</TableCell>
                        <TableCell>Name</TableCell>
                        {roster.questions.map((q) => (
                          <TableCell key={q.id} align="center">
                            {q.title} <Typography component="span" sx={{ color: '#94a3b8', fontSize: 12 }}>/{q.maxMarks}</Typography>
                          </TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {roster.rows.map((row) => (
                        <TableRow key={row.studentId} hover>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>{row.studentId}</TableCell>
                          <TableCell>{row.studentName}</TableCell>
                          {roster.questions.map((q) => {
                            const key = cellKey(row.studentId, q.id);
                            const isDirty = key in edits;
                            return (
                              <TableCell key={q.id} align="center">
                                <TextField
                                  type="number"
                                  size="small"
                                  value={displayValue(row.studentId, q.id)}
                                  onChange={(e) => handleCellChange(row.studentId, q.id, e.target.value)}
                                  sx={{
                                    width: 80,
                                    '& .MuiInputBase-input': { textAlign: 'center' },
                                    ...(isDirty && { '& .MuiOutlinedInput-notchedOutline': { borderColor: '#f59e0b', borderWidth: 2 } }),
                                  }}
                                  slotProps={{ htmlInput: { min: 0, max: q.maxMarks, step: 0.5 } }}
                                />
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </>
          )}
        </>
      )}
    </Box>
  );
};

export default EnterMarks;
