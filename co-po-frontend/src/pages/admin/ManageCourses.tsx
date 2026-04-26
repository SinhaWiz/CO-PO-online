import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Paper,
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
  deleteCourse,
  getCourses,
  updateCourse,
  type Course,
} from '../../api/admin';

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
  const [form, setForm] = useState<Course>(defaultForm);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

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

  useEffect(() => {
    fetchCourses();
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
        setMessage({ type: 'success', text: 'Course updated successfully.' });
      } else {
        await createCourse(payload);
        setMessage({ type: 'success', text: 'Course added successfully.' });
      }

      clearForm();
      fetchCourses();
    } catch (error) {
      console.error('Failed to save course', error);
      setMessage({ type: 'error', text: 'Failed to save course. Check duplicate key or invalid data.' });
    }
  };

  const handleEdit = (course: Course) => {
    setForm(course);
    setEditingKey(`${course.courseCode}||${course.programme}`);
    setMessage(null);
  };

  const handleDelete = async (course: Course) => {
    const ok = window.confirm(
      `Remove course ${course.courseCode} (${course.programme})? This action cannot be undone.`,
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

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleAddOrUpdate}>
            {editingKey ? 'Update Course' : 'Add Course'}
          </Button>
          <Button variant="outlined" onClick={clearForm}>
            Clear
          </Button>
        </Box>
      </Paper>

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
    </Box>
  );
};

export default ManageCourses;
