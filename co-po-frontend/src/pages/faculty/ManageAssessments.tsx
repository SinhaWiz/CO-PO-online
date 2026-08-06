import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  OutlinedInput,
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
import { getQuestions, createQuestion, deleteQuestion, getSectionsForCourse, resolveAssessmentInstance } from '../../api/faculty';
import type { AssessmentQuestion, CourseSection } from '../../api/faculty';
import { getPOs, type ProgramOutcome } from '../../api/admin';

const emptyForm: AssessmentQuestion = { assessmentId: 1, title: '', marks: 0, coId: undefined, poIds: [] };
const ACADEMIC_YEAR_REGEX = /^\d{4}-\d{4}$/;

const ManageAssessments = () => {
  const [assessmentId, setAssessmentId] = useState<number>(1);
  const [questions, setQuestions] = useState<AssessmentQuestion[]>([]);
  const [pos, setPos] = useState<ProgramOutcome[]>([]);
  const [form, setForm] = useState<AssessmentQuestion>(emptyForm);

  // Picker: replaces having to already know a raw assessment id. Still needs the
  // faculty's course code/programme typed in by hand - a full "my assignments" picker
  // needs the assignment-awareness plumbing that Phase 2.2 builds.
  const [courseCode, setCourseCode] = useState('');
  const [programme, setProgramme] = useState('');
  const [academicYear, setAcademicYear] = useState('');
  const [courseSections, setCourseSections] = useState<CourseSection[]>([]);
  const [selectedSectionId, setSelectedSectionId] = useState<number | ''>('');
  const [pickerMessage, setPickerMessage] = useState('');

  const poById = new Map(pos.map((po) => [po.id, po.poNumber]));

  useEffect(() => {
    getPOs()
      .then((res) => setPos(res.data))
      .catch((error) => console.error('Failed to fetch POs', error));
  }, []);

  const handleLoadSections = async () => {
    if (!courseCode.trim() || !programme.trim()) {
      setPickerMessage('Enter both course code and programme.');
      return;
    }
    try {
      const res = await getSectionsForCourse(courseCode.trim(), programme.trim());
      setCourseSections(res.data);
      setSelectedSectionId('');
      setPickerMessage(res.data.length === 0 ? 'This course has no sections defined yet - ask an admin to add some.' : '');
    } catch (error) {
      console.error('Failed to load sections', error);
      setPickerMessage('Failed to load sections for that course.');
    }
  };

  const handleResolveAssessment = async () => {
    if (!selectedSectionId || !ACADEMIC_YEAR_REGEX.test(academicYear)) {
      setPickerMessage('Pick a section and enter an academic year like 2024-2025.');
      return;
    }
    try {
      const res = await resolveAssessmentInstance(selectedSectionId, academicYear);
      setAssessmentId(res.data.id);
      setPickerMessage(`Using assessment #${res.data.id} for this section/year.`);
      fetchQuestionsFor(res.data.id);
    } catch (error) {
      console.error('Failed to resolve assessment instance', error);
      setPickerMessage('Failed to resolve an assessment for that section/year.');
    }
  };

  const fetchQuestionsFor = async (id: number) => {
    try {
      const response = await getQuestions(id);
      setQuestions(response.data);
    } catch (error) {
      console.error('Failed to fetch questions', error);
    }
  };

  const fetchQuestions = async () => {
    await fetchQuestionsFor(assessmentId);
  };

  const handleAdd = async () => {
    try {
      await createQuestion({ ...form, assessmentId });
      setForm({ ...emptyForm, assessmentId });
      fetchQuestions();
    } catch (error) {
      console.error('Failed to add question', error);
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) return;
    try {
      await deleteQuestion(id);
      fetchQuestions();
    } catch (error) {
      console.error('Failed to delete question', error);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Course Questions</Typography>

      <Paper sx={{ p: 2, mb: 2 }}>
      <Typography sx={{ fontWeight: 700, mb: 1 }}>Find Your Assessment</Typography>
      <Box sx={{ mb: 1, display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField label="Course Code" placeholder="CSE 4107" size="small" value={courseCode} onChange={(e) => setCourseCode(e.target.value)} />
        <TextField label="Programme" placeholder="BSc in CSE" size="small" value={programme} onChange={(e) => setProgramme(e.target.value)} />
        <Button variant="outlined" onClick={handleLoadSections}>Load Sections</Button>
        <FormControl size="small" sx={{ minWidth: 160 }} disabled={courseSections.length === 0}>
          <InputLabel>Section</InputLabel>
          <Select
            label="Section"
            value={selectedSectionId}
            onChange={(e) => setSelectedSectionId(e.target.value as number)}
          >
            {courseSections.map((section) => (
              <MenuItem key={section.id} value={section.id}>{section.displayName}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField label="Academic Year" placeholder="2024-2025" size="small" value={academicYear} onChange={(e) => setAcademicYear(e.target.value)} />
        <Button variant="contained" onClick={handleResolveAssessment}>Use This Assessment</Button>
      </Box>
      {pickerMessage && <Typography sx={{ color: '#64748b', fontSize: 13, mb: 1 }}>{pickerMessage}</Typography>}

      <Box sx={{ mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          label="Assessment ID"
          type="number"
          value={assessmentId}
          onChange={(e) => setAssessmentId(Number(e.target.value))}
          size="small"
          helperText="Filled in automatically above, or type one directly if you already know it"
        />
        <Button variant="outlined" onClick={fetchQuestions}>Load Questions</Button>
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
        <TextField label="Question Title (e.g. 1a)" value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} size="small" />
        <TextField label="Marks" type="number" value={form.marks} onChange={(e) => setForm({...form, marks: Number(e.target.value)})} size="small" />
        <TextField label="CO ID (opt)" type="number" value={form.coId || ''} onChange={(e) => setForm({...form, coId: Number(e.target.value)})} size="small" />
        <FormControl size="small">
          <InputLabel>PO Mapping (opt)</InputLabel>
          <Select
            multiple
            label="PO Mapping (opt)"
            value={form.poIds ?? []}
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
            {pos.map((po) => (
              <MenuItem key={po.id} value={po.id}>
                {po.poNumber}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Box>
      <Box sx={{ mt: 2 }}>
        <Button variant="contained" onClick={handleAdd}>Add Question</Button>
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
              <TableRow key={q.id}>
                <TableCell>{q.title}</TableCell>
                <TableCell>{q.marks}</TableCell>
                <TableCell>{q.coId || '-'}</TableCell>
                <TableCell>
                  {q.poIds && q.poIds.length > 0
                    ? q.poIds.map((id) => poById.get(id) ?? id).join(', ')
                    : '-'}
                </TableCell>
                <TableCell>
                  <Button color="error" onClick={() => handleDelete(q.id)}>Delete</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ManageAssessments;
