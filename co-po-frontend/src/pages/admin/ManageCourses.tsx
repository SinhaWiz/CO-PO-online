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
  createCourse,
  createCourseSection,
  deleteCourse,
  deleteCourseSection,
  getCOs,
  getCourseOutcomes,
  getCourseSections,
  getCourses,
  getPOs,
  updateCourse,
  updateCourseOutcomes,
  updateCourseSection,
  type Course,
  type CourseOutcome,
  type CourseSection,
  type ProgramOutcome,
} from '../../api/admin';
import { useConfirmDialog } from '../../components/ConfirmDialog';

const COURSE_CODE_REGEX = /^[A-Z]{3}\s\d{4}$/;
const DEPARTMENT_REGEX = /^[A-Z]{3}$/;
const PROGRAMME_REGEX = /^(BSc|MSc|PhD) in [A-Z]{2,3}$/;

const defaultForm: Course = {
  courseCode: '',
  programme: '',
  courseName: '',
  credits: 3,
  department: '',
};

const ManageCourses = () => {
  const [courses, setCourses] = useState<Course[]>([]);
  const [cos, setCos] = useState<CourseOutcome[]>([]);
  const [pos, setPos] = useState<ProgramOutcome[]>([]);
  const [form, setForm] = useState<Course>(defaultForm);
  const [selectedCoIds, setSelectedCoIds] = useState<number[]>([]);
  const [selectedPoIds, setSelectedPoIds] = useState<number[]>([]);
  const [sections, setSections] = useState<CourseSection[]>([]);
  const [newSectionName, setNewSectionName] = useState('');
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const coById = useMemo(() => new Map(cos.map((co) => [co.id, co.coNumber])), [cos]);
  const poById = useMemo(() => new Map(pos.map((po) => [po.id, po.poNumber])), [pos]);

  const sortedCourses = useMemo(
    () =>
      [...courses].sort((a, b) => {
        const codeCmp = a.courseCode.localeCompare(b.courseCode);
        if (codeCmp !== 0) return codeCmp;
        return a.programme.localeCompare(b.programme);
      }),
    [courses],
  );

  const fetchCourses = async () => {
    try {
      const response = await getCourses();
      setCourses(response.data);
    } catch (error) {
      console.error('Failed to fetch courses', error);
      setMessage({ type: 'error', text: 'Failed to fetch courses.' });
    }
  };

  const fetchOutcomeMasterLists = async () => {
    try {
      const [coRes, poRes] = await Promise.all([getCOs(), getPOs()]);
      setCos(coRes.data);
      setPos(poRes.data);
    } catch (error) {
      console.error('Failed to fetch CO/PO master lists', error);
    }
  };

  useEffect(() => {
    fetchCourses();
    fetchOutcomeMasterLists();
  }, []);

  const validate = (payload: Course) => {
    if (!COURSE_CODE_REGEX.test(payload.courseCode.trim())) {
      return 'Course code must follow format like CSE 4107.';
    }
    if (!payload.courseName.trim()) {
      return 'Course name is required.';
    }
    if (!Number.isFinite(payload.credits) || payload.credits <= 0 || Math.round(payload.credits * 2) !== payload.credits * 2) {
      return 'Credits must be a positive number in 0.5 steps.';
    }
    if (!DEPARTMENT_REGEX.test(payload.department.trim())) {
      return 'Department must be 3 uppercase letters (e.g., CSE).';
    }
    if (!PROGRAMME_REGEX.test(payload.programme.trim())) {
      return 'Programme must be like BSc in CSE / MSc in CSE / PhD in CSE.';
    }
    return null;
  };

  const clearForm = () => {
    setForm(defaultForm);
    setSelectedCoIds([]);
    setSelectedPoIds([]);
    setSections([]);
    setNewSectionName('');
    setEditingKey(null);
  };

  const handleAddOrUpdate = async () => {
    const payload: Course = {
      courseCode: form.courseCode.trim(),
      courseName: form.courseName.trim(),
      credits: Number(form.credits),
      department: form.department.trim(),
      programme: form.programme.trim(),
    };

    const validationError = validate(payload);
    if (validationError) {
      setMessage({ type: 'error', text: validationError });
      return;
    }

    try {
      if (editingKey) {
        const [courseCode, programme] = editingKey.split('||');
        await updateCourse(courseCode, programme, payload);
      } else {
        await createCourse(payload);
      }

      await updateCourseOutcomes(payload.courseCode, payload.programme, selectedCoIds, selectedPoIds);
      setMessage({ type: 'success', text: editingKey ? 'Course updated successfully.' : 'Course added successfully.' });

      clearForm();
      fetchCourses();
    } catch (error) {
      console.error('Failed to save course', error);
      setMessage({ type: 'error', text: 'Failed to save course. Check duplicate key or invalid data.' });
    }
  };

  const handleEdit = async (course: Course) => {
    setForm(course);
    setEditingKey(`${course.courseCode}||${course.programme}`);
    setMessage(null);

    try {
      const res = await getCourseOutcomes(course.courseCode, course.programme);
      setSelectedCoIds(res.data.coIds);
      setSelectedPoIds(res.data.poIds);
    } catch (error) {
      console.error('Failed to load course outcome scoping', error);
      setSelectedCoIds([]);
      setSelectedPoIds([]);
    }

    await reloadSections(course.courseCode, course.programme);
  };

  const reloadSections = async (courseCode: string, programme: string) => {
    try {
      const res = await getCourseSections(courseCode, programme);
      setSections(res.data);
    } catch (error) {
      console.error('Failed to load assessment sections', error);
      setSections([]);
    }
  };

  const handleAddSection = async () => {
    if (!editingKey || !newSectionName.trim()) return;
    const [courseCode, programme] = editingKey.split('||');

    try {
      const nextOrder = sections.length === 0 ? 1 : Math.max(...sections.map((s) => s.sectionOrder)) + 1;
      await createCourseSection(courseCode, programme, newSectionName.trim(), nextOrder);
      setNewSectionName('');
      await reloadSections(courseCode, programme);
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to add section. Names must be unique within a course.';
      setMessage({ type: 'error', text });
    }
  };

  const handleDeleteSection = async (section: CourseSection) => {
    if (!editingKey) return;
    const [courseCode, programme] = editingKey.split('||');

    const ok = await confirm(`Remove section "${section.displayName}"? Any questions/marks under it will be removed too.`, {
      title: 'Remove Section',
      confirmLabel: 'Remove',
      confirmColor: 'error',
    });
    if (!ok) return;

    try {
      await deleteCourseSection(courseCode, programme, section.id);
      await reloadSections(courseCode, programme);
    } catch (error) {
      console.error('Failed to delete section', error);
      setMessage({ type: 'error', text: 'Failed to delete section.' });
    }
  };

  const handleMoveSection = async (index: number, direction: -1 | 1) => {
    if (!editingKey) return;
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= sections.length) return;

    const [courseCode, programme] = editingKey.split('||');
    const a = sections[index];
    const b = sections[targetIndex];

    try {
      await Promise.all([
        updateCourseSection(courseCode, programme, a.id, a.displayName, b.sectionOrder),
        updateCourseSection(courseCode, programme, b.id, b.displayName, a.sectionOrder),
      ]);
      await reloadSections(courseCode, programme);
    } catch (error) {
      console.error('Failed to reorder sections', error);
      setMessage({ type: 'error', text: 'Failed to reorder sections.' });
    }
  };

  const handleDelete = async (course: Course) => {
    const ok = await confirm(
      `Remove course ${course.courseCode} (${course.programme})? This action cannot be undone.`,
      { title: 'Remove Course', confirmLabel: 'Remove', confirmColor: 'error' },
    );
    if (!ok) return;

    try {
      await deleteCourse(course.courseCode, course.programme);
      setMessage({ type: 'success', text: 'Course removed successfully.' });
      fetchCourses();
    } catch (error) {
      console.error('Failed to delete course', error);
      setMessage({ type: 'error', text: 'Failed to delete course. It may be referenced by enrollments/assignments.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Courses</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(5, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="Course Code"
            placeholder="CSE 4107"
            value={form.courseCode}
            onChange={(e) => setForm({ ...form, courseCode: e.target.value })}
            size="small"
            disabled={Boolean(editingKey)}
          />
          <TextField
            label="Course Name"
            value={form.courseName}
            onChange={(e) => setForm({ ...form, courseName: e.target.value })}
            size="small"
          />
          <TextField
            label="Credits"
            type="number"
            value={form.credits}
            onChange={(e) => setForm({ ...form, credits: Number(e.target.value) })}
            size="small"
            slotProps={{ htmlInput: { step: 0.5 } }}
          />
          <TextField
            label="Department"
            placeholder="CSE"
            value={form.department}
            onChange={(e) => setForm({ ...form, department: e.target.value })}
            size="small"
          />
          <TextField
            label="Programme"
            placeholder="BSc in CSE"
            value={form.programme}
            onChange={(e) => setForm({ ...form, programme: e.target.value })}
            size="small"
            disabled={Boolean(editingKey)}
          />
        </Box>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1.5, mt: 1.5 }}>
          <FormControl size="small">
            <InputLabel>Course Outcomes (CO)</InputLabel>
            <Select
              multiple
              label="Course Outcomes (CO)"
              value={selectedCoIds}
              onChange={(e) => setSelectedCoIds(e.target.value as number[])}
              input={<OutlinedInput label="Course Outcomes (CO)" />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {(selected as number[]).map((id) => (
                    <Chip key={id} label={coById.get(id) ?? id} size="small" />
                  ))}
                </Box>
              )}
            >
              {cos.map((co) => (
                <MenuItem key={co.id} value={co.id}>
                  {co.coNumber}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small">
            <InputLabel>Program Outcomes (PO)</InputLabel>
            <Select
              multiple
              label="Program Outcomes (PO)"
              value={selectedPoIds}
              onChange={(e) => setSelectedPoIds(e.target.value as number[])}
              input={<OutlinedInput label="Program Outcomes (PO)" />}
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

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleAddOrUpdate}>
            {editingKey ? 'Update Course' : 'Add Course'}
          </Button>
          <Button variant="outlined" onClick={clearForm}>
            Clear
          </Button>
        </Box>
      </Paper>

      {editingKey && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Typography sx={{ fontWeight: 700, mb: 0.5 }}>Assessment Sections</Typography>
          <Typography sx={{ color: '#64748b', fontSize: 13, mb: 1.5 }}>
            The ordered list of assessments this course is graded on (e.g. "Quiz 1", "Mid", "Final") - defined
            per course, not fixed to any set list.
          </Typography>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 2 }}>
            {sections.map((section, index) => (
              <Box
                key={section.id}
                sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1, bgcolor: '#f8fafc', borderRadius: 1 }}
              >
                <Typography sx={{ minWidth: 24, color: '#94a3b8', fontSize: 13 }}>{index + 1}.</Typography>
                <Typography sx={{ flexGrow: 1 }}>{section.displayName}</Typography>
                <Button size="small" disabled={index === 0} onClick={() => handleMoveSection(index, -1)}>
                  ↑
                </Button>
                <Button size="small" disabled={index === sections.length - 1} onClick={() => handleMoveSection(index, 1)}>
                  ↓
                </Button>
                <Button size="small" color="error" onClick={() => handleDeleteSection(section)}>
                  Remove
                </Button>
              </Box>
            ))}
            {sections.length === 0 && <Typography sx={{ color: '#94a3b8' }}>No sections defined yet.</Typography>}
          </Box>

          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              label="New Section Name"
              placeholder="Quiz 1"
              size="small"
              value={newSectionName}
              onChange={(e) => setNewSectionName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddSection()}
              fullWidth
            />
            <Button variant="contained" onClick={handleAddSection}>
              Add Section
            </Button>
          </Box>
        </Paper>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>S/N</TableCell>
              <TableCell>Course Code</TableCell>
              <TableCell>Course Name</TableCell>
              <TableCell>Credits</TableCell>
              <TableCell>Department</TableCell>
              <TableCell>Programme</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedCourses.map((course, index) => (
              <TableRow key={`${course.courseCode}-${course.programme}`}>
                <TableCell>{index + 1}</TableCell>
                <TableCell>{course.courseCode}</TableCell>
                <TableCell>{course.courseName}</TableCell>
                <TableCell>{course.credits}</TableCell>
                <TableCell>{course.department}</TableCell>
                <TableCell>{course.programme}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Button variant="outlined" onClick={() => handleEdit(course)}>
                      Edit
                    </Button>
                    <Button color="error" variant="contained" onClick={() => handleDelete(course)}>
                      Remove
                    </Button>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {ConfirmDialog}
    </Box>
  );
};

export default ManageCourses;
