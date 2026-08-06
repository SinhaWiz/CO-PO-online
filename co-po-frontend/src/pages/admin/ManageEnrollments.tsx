import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
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
  createEnrollment,
  deleteEnrollment,
  getCourses,
  getEnrollments,
  getStudents,
  type Course,
  type Enrollment,
  type Student,
} from '../../api/admin';
import { useConfirmDialog } from '../../components/ConfirmDialog';

const ACADEMIC_YEAR_REGEX = /^\d{4}-\d{4}$/;

const isValidAcademicYear = (value: string) => {
  if (!ACADEMIC_YEAR_REGEX.test(value)) return false;
  const y1 = Number(value.slice(0, 4));
  const y2 = Number(value.slice(5, 9));
  return y2 === y1 + 1;
};

const ManageEnrollments = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);

  const [batchFilter, setBatchFilter] = useState<number | 'ALL'>('ALL');
  const [departmentFilter, setDepartmentFilter] = useState<string>('ALL');
  const [programmeFilter, setProgrammeFilter] = useState<string>('ALL');

  const [selectedCourseKey, setSelectedCourseKey] = useState<string>('');
  const [academicYear, setAcademicYear] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const loadData = async () => {
    try {
      const [studentsRes, coursesRes, enrollmentsRes] = await Promise.all([
        getStudents(),
        getCourses(),
        getEnrollments(),
      ]);
      setStudents(studentsRes.data);
      setCourses(coursesRes.data);
      setEnrollments(enrollmentsRes.data);
    } catch (error) {
      console.error('Failed to load enrollments page data', error);
      setMessage({ type: 'error', text: 'Failed to load enrollments data.' });
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const availableBatches = useMemo(
    () => [...new Set(students.map((s) => s.batch))].sort((a, b) => a - b),
    [students],
  );
  const availableDepartments = useMemo(
    () => [...new Set(students.map((s) => s.department).filter(Boolean))].sort(),
    [students],
  );
  const availableProgrammes = useMemo(
    () => [...new Set(students.map((s) => s.programme).filter(Boolean))].sort(),
    [students],
  );

  const filteredStudents = useMemo(
    () =>
      students.filter((student) => {
        if (batchFilter !== 'ALL' && student.batch !== batchFilter) return false;
        if (departmentFilter !== 'ALL' && student.department !== departmentFilter) return false;
        if (programmeFilter !== 'ALL' && student.programme !== programmeFilter) return false;
        return true;
      }),
    [students, batchFilter, departmentFilter, programmeFilter],
  );

  const selectedCourse = useMemo(() => {
    if (!selectedCourseKey) return null;
    const [courseCode, programme] = selectedCourseKey.split('||');
    return courses.find((course) => course.courseCode === courseCode && course.programme === programme) ?? null;
  }, [selectedCourseKey, courses]);

  const enrolledSet = useMemo(() => {
    if (!selectedCourse || !isValidAcademicYear(academicYear)) return new Set<string>();

    return new Set(
      enrollments
        .filter(
          (enrollment) =>
            enrollment.courseId === selectedCourse.courseCode &&
            enrollment.programme === selectedCourse.programme &&
            enrollment.academicYear === academicYear,
        )
        .map((enrollment) => enrollment.studentId),
    );
  }, [selectedCourse, academicYear, enrollments]);

  const toggleSelect = (studentId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(studentId)) next.delete(studentId);
      else next.add(studentId);
      return next;
    });
  };

  const handleSelectAllFiltered = () => {
    const next = new Set(selectedIds);
    const allSelected = filteredStudents.every((student) => next.has(student.id));

    if (allSelected) {
      filteredStudents.forEach((student) => next.delete(student.id));
    } else {
      filteredStudents.forEach((student) => next.add(student.id));
    }

    setSelectedIds(next);
  };

  const clearFilters = () => {
    setBatchFilter('ALL');
    setDepartmentFilter('ALL');
    setProgrammeFilter('ALL');
  };

  const validateSelection = () => {
    if (!selectedCourse) {
      setMessage({ type: 'error', text: 'Select a course first.' });
      return false;
    }
    if (!isValidAcademicYear(academicYear)) {
      setMessage({ type: 'error', text: 'Academic year must be in YYYY-YYYY format (e.g., 2024-2025).' });
      return false;
    }
    if (selectedIds.size === 0) {
      setMessage({ type: 'error', text: 'Select at least one student.' });
      return false;
    }
    return true;
  };

  const handleEnrollSelected = async () => {
    if (!validateSelection() || !selectedCourse) return;

    const selectedStudents = students.filter((student) => selectedIds.has(student.id));
    const eligible = selectedStudents.filter(
      (student) =>
        student.department === selectedCourse.department &&
        student.programme === selectedCourse.programme,
    );

    if (eligible.length === 0) {
      setMessage({
        type: 'error',
        text: `No selected students match ${selectedCourse.department}/${selectedCourse.programme}.`,
      });
      return;
    }

    let created = 0;
    let skipped = 0;

    for (const student of eligible) {
      if (enrolledSet.has(student.id)) {
        skipped += 1;
        continue;
      }

      try {
        await createEnrollment({
          studentId: student.id,
          courseId: selectedCourse.courseCode,
          programme: selectedCourse.programme,
          academicYear,
        });
        created += 1;
      } catch (error) {
        console.error('Enrollment create failed', error);
        skipped += 1;
      }
    }

    await loadData();
    setMessage({
      type: 'success',
      text: `Enrollment complete. Added: ${created}, Skipped: ${skipped}.`,
    });
  };

  const handleUnenrollSelected = async () => {
    if (!validateSelection() || !selectedCourse) return;

    const selectedStudents = students.filter((student) => selectedIds.has(student.id));
    const enrolledSelected = selectedStudents.filter((student) => enrolledSet.has(student.id));

    if (enrolledSelected.length === 0) {
      setMessage({ type: 'error', text: 'None of the selected students are enrolled for the selected course/year.' });
      return;
    }

    const ok = await confirm(
      `Unenroll ${enrolledSelected.length} students from ${selectedCourse.courseCode} (${academicYear})?`,
      { title: 'Unenroll Students', confirmLabel: 'Unenroll', confirmColor: 'error' },
    );
    if (!ok) return;

    let removed = 0;
    let failed = 0;

    for (const student of enrolledSelected) {
      try {
        await deleteEnrollment(student.id, selectedCourse.courseCode, selectedCourse.programme, academicYear);
        removed += 1;
      } catch (error) {
        console.error('Enrollment delete failed', error);
        failed += 1;
      }
    }

    await loadData();
    setMessage({ type: 'success', text: `Unenrollment complete. Removed: ${removed}, Failed: ${failed}.` });
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Enrollments</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(6, 1fr)' }, gap: 1.5 }}>
          <FormControl size="small">
            <InputLabel>Batch</InputLabel>
            <Select
              label="Batch"
              value={batchFilter}
              onChange={(e) => setBatchFilter(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
            >
              <MenuItem value="ALL">All Batches</MenuItem>
              {availableBatches.map((batch) => (
                <MenuItem key={batch} value={batch}>
                  {batch}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small">
            <InputLabel>Department</InputLabel>
            <Select
              label="Department"
              value={departmentFilter}
              onChange={(e) => setDepartmentFilter(e.target.value)}
            >
              <MenuItem value="ALL">All Departments</MenuItem>
              {availableDepartments.map((department) => (
                <MenuItem key={department} value={department}>
                  {department}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small">
            <InputLabel>Programme</InputLabel>
            <Select
              label="Programme"
              value={programmeFilter}
              onChange={(e) => setProgrammeFilter(e.target.value)}
            >
              <MenuItem value="ALL">All Programmes</MenuItem>
              {availableProgrammes.map((programme) => (
                <MenuItem key={programme} value={programme}>
                  {programme}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small">
            <InputLabel>Course</InputLabel>
            <Select
              label="Course"
              value={selectedCourseKey}
              onChange={(e) => setSelectedCourseKey(e.target.value)}
            >
              {courses
                .slice()
                .sort((a, b) => a.courseCode.localeCompare(b.courseCode))
                .map((course) => (
                  <MenuItem key={`${course.courseCode}-${course.programme}`} value={`${course.courseCode}||${course.programme}`}>
                    {course.courseCode} - {course.courseName} - {course.department} - {course.programme}
                  </MenuItem>
                ))}
            </Select>
          </FormControl>

          <TextField
            label="Academic Year"
            placeholder="2024-2025"
            size="small"
            value={academicYear}
            onChange={(e) => setAcademicYear(e.target.value)}
          />

          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button variant="outlined" onClick={clearFilters}>
              Clear Filters
            </Button>
            <Button variant="outlined" onClick={loadData}>
              Refresh
            </Button>
          </Box>
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleEnrollSelected}>
            Enroll Selected
          </Button>
          <Button variant="contained" color="error" onClick={handleUnenrollSelected}>
            Unenroll Selected
          </Button>
          <Button variant="outlined" onClick={handleSelectAllFiltered}>
            Toggle Select Filtered
          </Button>
        </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  checked={filteredStudents.length > 0 && filteredStudents.every((student) => selectedIds.has(student.id))}
                  indeterminate={
                    filteredStudents.some((student) => selectedIds.has(student.id)) &&
                    !filteredStudents.every((student) => selectedIds.has(student.id))
                  }
                  onChange={handleSelectAllFiltered}
                />
              </TableCell>
              <TableCell>S/N</TableCell>
              <TableCell>Student ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Batch</TableCell>
              <TableCell>Department</TableCell>
              <TableCell>Programme</TableCell>
              <TableCell>Enrollment Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredStudents.map((student, index) => {
              const enrolled = enrolledSet.has(student.id);
              return (
                <TableRow key={student.id} hover>
                  <TableCell padding="checkbox">
                    <Checkbox checked={selectedIds.has(student.id)} onChange={() => toggleSelect(student.id)} />
                  </TableCell>
                  <TableCell>{index + 1}</TableCell>
                  <TableCell>{student.id}</TableCell>
                  <TableCell>{student.name}</TableCell>
                  <TableCell>{student.batch}</TableCell>
                  <TableCell>{student.department}</TableCell>
                  <TableCell>{student.programme}</TableCell>
                  <TableCell sx={{ color: enrolled ? '#15803d' : '#64748b', fontWeight: enrolled ? 700 : 500 }}>
                    {selectedCourse && isValidAcademicYear(academicYear) ? (enrolled ? '✓ Enrolled' : 'Not Enrolled') : '-'}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {ConfirmDialog}
    </Box>
  );
};

export default ManageEnrollments;
