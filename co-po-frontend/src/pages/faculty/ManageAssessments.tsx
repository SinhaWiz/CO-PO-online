import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  OutlinedInput,
  Paper,
  Select,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import {
  createQuestion,
  deleteQuestion,
  getFacultyCOs,
  getFacultyCourseOutcomes,
  getFacultyPOs,
  getMyAssignments,
  getQuestions,
  getSectionsForCourse,
  resolveAssessmentInstance,
  updateQuestion,
  type AssessmentQuestion,
  type CourseSection,
  type MyAssignment,
} from '../../api/faculty';
import { useConfirmDialog } from '../../components/ConfirmDialog';

type Outcome = { id: number; coNumber?: string; poNumber?: string };

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;
const emptyQuestionForm = { title: '', marks: 0, coId: undefined as number | undefined, poIds: [] as number[] };

const ManageAssessments = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');

  const [sections, setSections] = useState<CourseSection[]>([]);
  const [activeSectionId, setActiveSectionId] = useState<number | ''>('');
  const [assessmentId, setAssessmentId] = useState<number | null>(null);

  const [allCos, setAllCos] = useState<Outcome[]>([]);
  const [allPos, setAllPos] = useState<Outcome[]>([]);
  const [courseOutcomeIds, setCourseOutcomeIds] = useState<{ coIds: number[]; poIds: number[] }>({ coIds: [], poIds: [] });

  const [questions, setQuestions] = useState<AssessmentQuestion[]>([]);
  const [form, setForm] = useState(emptyQuestionForm);
  const [editingQuestionId, setEditingQuestionId] = useState<number | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  const coById = useMemo(() => new Map(allCos.map((co) => [co.id, co.coNumber])), [allCos]);
  const poById = useMemo(() => new Map(allPos.map((po) => [po.id, po.poNumber])), [allPos]);
  const scopedCos = useMemo(() => allCos.filter((co) => courseOutcomeIds.coIds.includes(co.id)), [allCos, courseOutcomeIds]);
  const scopedPos = useMemo(() => allPos.filter((po) => courseOutcomeIds.poIds.includes(po.id)), [allPos, courseOutcomeIds]);

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
      setMessage({ type: 'error', text: 'Failed to load your course assignments.' });
    });
    getFacultyCOs().then((res) => setAllCos(res.data)).catch((error) => console.error('Failed to load COs', error));
    getFacultyPOs().then((res) => setAllPos(res.data)).catch((error) => console.error('Failed to load POs', error));
  }, []);

  // Selecting an assignment loads its course's sections and outcome allow-list, and
  // picks the first section as the active tab.
  useEffect(() => {
    if (!selectedAssignment) {
      setSections([]);
      setActiveSectionId('');
      setCourseOutcomeIds({ coIds: [], poIds: [] });
      return;
    }

    const { courseCode, programme } = selectedAssignment;
    Promise.all([getSectionsForCourse(courseCode, programme), getFacultyCourseOutcomes(courseCode, programme)])
      .then(([sectionsRes, outcomesRes]) => {
        setSections(sectionsRes.data);
        setCourseOutcomeIds(outcomesRes.data);
        setActiveSectionId(sectionsRes.data.length > 0 ? sectionsRes.data[0].id : '');
      })
      .catch((error) => {
        console.error('Failed to load course setup', error);
        setMessage({ type: 'error', text: 'Failed to load sections/outcomes for this course.' });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey]);

  // Picking a section tab resolves that section's assessment instance for this
  // assignment's academic year, then loads its questions.
  useEffect(() => {
    if (!selectedAssignment || activeSectionId === '') {
      setAssessmentId(null);
      setQuestions([]);
      return;
    }

    resolveAssessmentInstance(activeSectionId, selectedAssignment.academicYear)
      .then((res) => {
        setAssessmentId(res.data.id);
        return getQuestions(res.data.id);
      })
      .then((res) => setQuestions(res?.data ?? []))
      .catch((error) => {
        console.error('Failed to resolve/load assessment', error);
        setMessage({ type: 'error', text: 'Failed to load this section.' });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSectionId, selectedKey]);

  const reloadQuestions = async () => {
    if (!assessmentId) return;
    try {
      const res = await getQuestions(assessmentId);
      setQuestions(res.data);
    } catch (error) {
      console.error('Failed to reload questions', error);
    }
  };

  const clearQuestionForm = () => {
    setForm(emptyQuestionForm);
    setEditingQuestionId(null);
  };

  const handleEditClick = (q: AssessmentQuestion) => {
    setEditingQuestionId(q.id ?? null);
    setForm({ title: q.title, marks: q.marks, coId: q.coId, poIds: q.poIds ?? [] });
  };

  const handleSubmit = async () => {
    if (!assessmentId) {
      setMessage({ type: 'error', text: 'Pick an assignment and section first.' });
      return;
    }
    if (!form.title.trim()) {
      setMessage({ type: 'error', text: 'Question title is required.' });
      return;
    }

    try {
      if (editingQuestionId) {
        await updateQuestion(editingQuestionId, form);
        setMessage({ type: 'success', text: 'Question updated.' });
      } else {
        await createQuestion({ ...form, assessmentId });
        setMessage({ type: 'success', text: 'Question added.' });
      }
      clearQuestionForm();
      reloadQuestions();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to save question.';
      setMessage({ type: 'error', text });
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) return;
    const ok = await confirm('Remove this question? Any marks recorded against it go with it.', {
      title: 'Remove Question',
      confirmLabel: 'Remove',
      confirmColor: 'error',
    });
    if (!ok) return;

    try {
      await deleteQuestion(id);
      if (editingQuestionId === id) clearQuestionForm();
      reloadQuestions();
    } catch (error) {
      console.error('Failed to delete question', error);
      setMessage({ type: 'error', text: 'Failed to delete question.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Course Questions</Typography>

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
              setSelectedKey(e.target.value);
              clearQuestionForm();
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
              onChange={(_, value) => {
                setActiveSectionId(value);
                clearQuestionForm();
              }}
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

          {assessmentId && (
            <>
              <Paper sx={{ p: 2, mb: 2 }}>
                <Typography sx={{ fontWeight: 700, mb: 1.5 }}>
                  {editingQuestionId ? 'Edit Question' : 'Add Question'}
                </Typography>
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
                  <TextField
                    label="Question Title (e.g. 1a)"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    size="small"
                  />
                  <TextField
                    label="Marks"
                    type="number"
                    value={form.marks}
                    onChange={(e) => setForm({ ...form, marks: Number(e.target.value) })}
                    size="small"
                  />
                  <FormControl size="small">
                    <InputLabel>CO Mapping (opt)</InputLabel>
                    <Select
                      label="CO Mapping (opt)"
                      value={form.coId ?? ''}
                      onChange={(e) => {
                        const value = e.target.value as number | '';
                        setForm({ ...form, coId: value === '' ? undefined : Number(value) });
                      }}
                    >
                      <MenuItem value="">
                        <em>None</em>
                      </MenuItem>
                      {scopedCos.map((co) => (
                        <MenuItem key={co.id} value={co.id}>{co.coNumber}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <FormControl size="small">
                    <InputLabel>PO Mapping (opt)</InputLabel>
                    <Select
                      multiple
                      label="PO Mapping (opt)"
                      value={form.poIds}
                      onChange={(e) => setForm({ ...form, poIds: e.target.value as number[] })}
                      input={<OutlinedInput label="PO Mapping (opt)" />}
                      renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                          {(selected as number[]).map((id) => (
                            <Chip key={id} label={poById.get(id) ?? id} size="small" />
                          ))}
                        </Box>
                      )}
                    >
                      {scopedPos.map((po) => (
                        <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Box>
                {(scopedCos.length === 0 || scopedPos.length === 0) && (
                  <Typography sx={{ color: '#94a3b8', fontSize: 13, mt: 1 }}>
                    This course has no CO/PO outcomes scoped to it yet - ask an admin to set that up under Manage Courses.
                  </Typography>
                )}
                <Box sx={{ mt: 2, display: 'flex', gap: 1.25 }}>
                  <Button variant="contained" onClick={handleSubmit}>
                    {editingQuestionId ? 'Update Question' : 'Add Question'}
                  </Button>
                  {editingQuestionId && (
                    <Button variant="outlined" onClick={clearQuestionForm}>
                      Cancel Edit
                    </Button>
                  )}
                </Box>
              </Paper>

              <TableContainer component={Paper}>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Title</TableCell>
                      <TableCell>Marks</TableCell>
                      <TableCell>CO MAP</TableCell>
                      <TableCell>PO MAP</TableCell>
                      <TableCell>Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {questions.map((q) => (
                      <TableRow key={q.id} selected={q.id === editingQuestionId}>
                        <TableCell>{q.title}</TableCell>
                        <TableCell>{q.marks}</TableCell>
                        <TableCell>{q.coId ? coById.get(q.coId) ?? q.coId : '-'}</TableCell>
                        <TableCell>
                          {q.poIds && q.poIds.length > 0 ? q.poIds.map((id) => poById.get(id) ?? id).join(', ') : '-'}
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            <Button size="small" onClick={() => handleEditClick(q)}>Edit</Button>
                            <Button size="small" color="error" onClick={() => handleDelete(q.id)}>Delete</Button>
                          </Box>
                        </TableCell>
                      </TableRow>
                    ))}
                    {questions.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} sx={{ color: '#94a3b8', textAlign: 'center' }}>
                          No questions yet for this section.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}
        </>
      )}

      {ConfirmDialog}
    </Box>
  );
};

export default ManageAssessments;
