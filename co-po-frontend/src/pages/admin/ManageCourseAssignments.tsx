import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
  createCourseAssignment,
  deleteCourseAssignment,
  getAssignmentThresholds,
  getCourseAssignments,
  getCourses,
  getFaculties,
  importCourseAssignments,
  updateAssignmentThresholds,
  type Course,
  type CourseAssignment,
  type Faculty,
  type Thresholds,
} from '../../api/admin';
import { useConfirmDialog } from '../../components/ConfirmDialog';
import BulkImportButton from '../../components/BulkImportButton';

const defaultThresholds: Thresholds = { coIndividual: 60, poIndividual: 40, coCohort: 50, poCohort: 50 };

const ACADEMIC_YEAR_REGEX = /^\d{4}-\d{4}$/;

const isValidAcademicYear = (value: string) => {
  if (!ACADEMIC_YEAR_REGEX.test(value)) return false;
  const y1 = Number(value.slice(0, 4));
  const y2 = Number(value.slice(5, 9));
  return y2 === y1 + 1;
};

type AssignmentRow = CourseAssignment & {
  courseName: string;
  facultyName: string;
};

const ManageCourseAssignments = () => {
  const [assignments, setAssignments] = useState<CourseAssignment[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [faculties, setFaculties] = useState<Faculty[]>([]);

  const [search, setSearch] = useState('');
  const [yearFilter, setYearFilter] = useState('');
  const [programmeFilter, setProgrammeFilter] = useState('ALL');

  const [selectedCourseKey, setSelectedCourseKey] = useState('');
  const [selectedFacultyId, setSelectedFacultyId] = useState('');
  const [academicYear, setAcademicYear] = useState('');
  const [initialThresholds, setInitialThresholds] = useState<Thresholds>(defaultThresholds);

  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const [thresholdDialogRow, setThresholdDialogRow] = useState<CourseAssignment | null>(null);
  const [editingThresholds, setEditingThresholds] = useState<Thresholds>(defaultThresholds);
  const [thresholdsSaving, setThresholdsSaving] = useState(false);

  const loadData = async () => {
    try {
      const [assignmentRes, courseRes, facultyRes] = await Promise.all([
        getCourseAssignments(),
        getCourses(),
        getFaculties(),
      ]);
      setAssignments(assignmentRes.data);
      setCourses(courseRes.data);
      setFaculties(facultyRes.data);
    } catch (error) {
      console.error('Failed to load assignment data', error);
      setMessage({ type: 'error', text: 'Failed to load course assignments.' });
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const courseMap = useMemo(
    () =>
      new Map<string, Course>(
        courses.map((course) => [`${course.courseCode}||${course.programme}`, course]),
      ),
    [courses],
  );

  const facultyMap = useMemo(
    () => new Map(faculties.map((faculty) => [faculty.id, faculty] as const)),
    [faculties],
  );

  const rowData = useMemo<AssignmentRow[]>(
    () =>
      assignments.map((assignment) => {
        const course = courseMap.get(`${assignment.courseCode}||${assignment.programme}`);
        const faculty = facultyMap.get(assignment.facultyId);

        return {
          ...assignment,
          courseName: course?.courseName ?? '-',
          facultyName: faculty ? `${faculty.fullName}${faculty.shortname ? ` (${faculty.shortname})` : ''}` : assignment.facultyId,
        };
      }),
    [assignments, courseMap, facultyMap],
  );

  const filteredRows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const byYear = isValidAcademicYear(yearFilter) ? yearFilter : '';
    const byProgramme = programmeFilter !== 'ALL' ? programmeFilter : '';

    return rowData
      .filter((row) => {
        if (byYear && row.academicYear !== byYear) return false;
        if (byProgramme && row.programme !== byProgramme) return false;

        if (!q) return true;
        return (
          row.courseCode.toLowerCase().includes(q) ||
          row.courseName.toLowerCase().includes(q)
        );
      })
      .sort((a, b) => {
        const codeCmp = a.courseCode.localeCompare(b.courseCode);
        if (codeCmp !== 0) return codeCmp;
        const progCmp = a.programme.localeCompare(b.programme);
        if (progCmp !== 0) return progCmp;
        return a.academicYear.localeCompare(b.academicYear);
      });
  }, [rowData, search, yearFilter, programmeFilter]);

  const availableProgrammes = useMemo(
    () => [...new Set(rowData.map((row) => row.programme))].sort(),
    [rowData],
  );

  const selectedCourse = useMemo(() => courseMap.get(selectedCourseKey) ?? null, [courseMap, selectedCourseKey]);

  const clearAssignForm = () => {
    setSelectedCourseKey('');
    setSelectedFacultyId('');
    setAcademicYear('');
    setInitialThresholds(defaultThresholds);
  };

  const handleAssign = async () => {
    if (!selectedCourse) {
      setMessage({ type: 'error', text: 'Select a course.' });
      return;
    }
    if (!selectedFacultyId) {
      setMessage({ type: 'error', text: 'Select a faculty member.' });
      return;
    }
    if (!isValidAcademicYear(academicYear)) {
      setMessage({ type: 'error', text: 'Academic year must be in YYYY-YYYY format (e.g., 2024-2025).' });
      return;
    }

    const isDuplicate = assignments.some(
      (assignment) =>
        assignment.courseCode === selectedCourse.courseCode &&
        assignment.programme === selectedCourse.programme &&
        assignment.department === selectedCourse.department &&
        assignment.academicYear === academicYear,
    );

    if (isDuplicate) {
      setMessage({ type: 'error', text: 'This course is already assigned for the selected academic year.' });
      return;
    }

    try {
      await createCourseAssignment({
        facultyId: selectedFacultyId,
        courseCode: selectedCourse.courseCode,
        programme: selectedCourse.programme,
        academicYear,
        department: selectedCourse.department,
      });
      // Creating the assignment already seeds default (60/40/50/50) thresholds
      // server-side - this just overwrites them with whatever the admin chose here,
      // in the same action rather than a separate edit step afterward.
      await updateAssignmentThresholds(
        selectedCourse.courseCode, selectedCourse.programme, academicYear, selectedCourse.department, initialThresholds,
      );
      setMessage({ type: 'success', text: 'Course assignment created successfully.' });
      clearAssignForm();
      loadData();
    } catch (error) {
      console.error('Failed to assign course', error);
      setMessage({ type: 'error', text: 'Failed to assign course. Please check duplicates and inputs.' });
    }
  };

  const openThresholdDialog = async (row: CourseAssignment) => {
    setThresholdDialogRow(row);
    try {
      const res = await getAssignmentThresholds(row.courseCode, row.programme, row.academicYear, row.department);
      setEditingThresholds(res.data);
    } catch (error) {
      console.error('Failed to load thresholds', error);
      setEditingThresholds(defaultThresholds);
    }
  };

  const closeThresholdDialog = () => {
    setThresholdDialogRow(null);
    setEditingThresholds(defaultThresholds);
  };

  const handleSaveThresholds = async () => {
    if (!thresholdDialogRow) return;
    setThresholdsSaving(true);
    try {
      await updateAssignmentThresholds(
        thresholdDialogRow.courseCode, thresholdDialogRow.programme,
        thresholdDialogRow.academicYear, thresholdDialogRow.department,
        editingThresholds,
      );
      setMessage({ type: 'success', text: 'Thresholds updated successfully.' });
      closeThresholdDialog();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to update thresholds.';
      setMessage({ type: 'error', text });
    } finally {
      setThresholdsSaving(false);
    }
  };

  const handleRemove = async (row: CourseAssignment) => {
    const ok = await confirm(
      `Remove assignment for ${row.courseCode} (${row.programme}) in ${row.academicYear}?`,
      { title: 'Remove Assignment', confirmLabel: 'Remove', confirmColor: 'error' },
    );
    if (!ok) return;

    try {
      await deleteCourseAssignment(row.courseCode, row.programme, row.academicYear, row.department);
      setMessage({ type: 'success', text: 'Course assignment removed successfully.' });
      loadData();
    } catch (error) {
      console.error('Failed to remove assignment', error);
      setMessage({ type: 'error', text: 'Failed to remove assignment.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Course Assignments</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography sx={{ fontSize: 16, fontWeight: 700, mb: 1.25 }}>Assign Course to Faculty</Typography>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
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

          <FormControl size="small">
            <InputLabel>Faculty</InputLabel>
            <Select
              label="Faculty"
              value={selectedFacultyId}
              onChange={(e) => setSelectedFacultyId(e.target.value)}
            >
              {faculties
                .slice()
                .sort((a, b) => a.fullName.localeCompare(b.fullName))
                .map((faculty) => (
                  <MenuItem key={faculty.id} value={faculty.id}>
                    {faculty.fullName} {faculty.shortname ? `(${faculty.shortname})` : ''}
                  </MenuItem>
                ))}
            </Select>
          </FormControl>

          <TextField
            label="Academic Year"
            size="small"
            placeholder="2024-2025"
            value={academicYear}
            onChange={(e) => setAcademicYear(e.target.value)}
          />

          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button variant="contained" onClick={handleAssign}>
              Assign Course
            </Button>
            <Button variant="outlined" onClick={clearAssignForm}>
              Clear
            </Button>
            <BulkImportButton label="Bulk Import (Excel)" onImport={importCourseAssignments} onComplete={loadData} />
          </Box>
        </Box>

        <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#64748b', mt: 2, mb: 1 }}>
          INITIAL THRESHOLDS (the assigned faculty can adjust these later)
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="CO Individual %"
            type="number"
            size="small"
            value={initialThresholds.coIndividual}
            onChange={(e) => setInitialThresholds({ ...initialThresholds, coIndividual: Number(e.target.value) })}
          />
          <TextField
            label="PO Individual %"
            type="number"
            size="small"
            value={initialThresholds.poIndividual}
            onChange={(e) => setInitialThresholds({ ...initialThresholds, poIndividual: Number(e.target.value) })}
          />
          <TextField
            label="CO Cohort %"
            type="number"
            size="small"
            value={initialThresholds.coCohort}
            onChange={(e) => setInitialThresholds({ ...initialThresholds, coCohort: Number(e.target.value) })}
          />
          <TextField
            label="PO Cohort %"
            type="number"
            size="small"
            value={initialThresholds.poCohort}
            onChange={(e) => setInitialThresholds({ ...initialThresholds, poCohort: Number(e.target.value) })}
          />
        </Box>
      </Paper>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography sx={{ fontSize: 16, fontWeight: 700, mb: 1.25 }}>Filter Assignments</Typography>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="Search by course code/name"
            size="small"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <TextField
            label="Academic Year"
            size="small"
            placeholder="2024-2025"
            value={yearFilter}
            onChange={(e) => setYearFilter(e.target.value)}
          />

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

          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="outlined"
              onClick={() => {
                setSearch('');
                setYearFilter('');
                setProgrammeFilter('ALL');
              }}
            >
              Clear Filters
            </Button>
            <Button variant="outlined" onClick={loadData}>
              Refresh
            </Button>
          </Box>
        </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>S/N</TableCell>
              <TableCell>Course Code</TableCell>
              <TableCell>Course Name</TableCell>
              <TableCell>Faculty</TableCell>
              <TableCell>Academic Year</TableCell>
              <TableCell>Department</TableCell>
              <TableCell>Programme</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredRows.map((row, index) => (
              <TableRow key={`${row.courseCode}-${row.programme}-${row.academicYear}-${row.department}`}>
                <TableCell>{index + 1}</TableCell>
                <TableCell>{row.courseCode}</TableCell>
                <TableCell>{row.courseName}</TableCell>
                <TableCell>{row.facultyName}</TableCell>
                <TableCell>{row.academicYear}</TableCell>
                <TableCell>{row.department}</TableCell>
                <TableCell>{row.programme}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Button variant="outlined" onClick={() => openThresholdDialog(row)}>
                      Thresholds
                    </Button>
                    <Button color="error" variant="contained" onClick={() => handleRemove(row)}>
                      Remove
                    </Button>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={thresholdDialogRow !== null} onClose={closeThresholdDialog} maxWidth="xs" fullWidth>
        <DialogTitle>
          Thresholds{thresholdDialogRow ? ` - ${thresholdDialogRow.courseCode} (${thresholdDialogRow.academicYear})` : ''}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, pt: 1 }}>
          <TextField
            label="CO Individual %"
            type="number"
            size="small"
            value={editingThresholds.coIndividual}
            onChange={(e) => setEditingThresholds({ ...editingThresholds, coIndividual: Number(e.target.value) })}
          />
          <TextField
            label="PO Individual %"
            type="number"
            size="small"
            value={editingThresholds.poIndividual}
            onChange={(e) => setEditingThresholds({ ...editingThresholds, poIndividual: Number(e.target.value) })}
          />
          <TextField
            label="CO Cohort %"
            type="number"
            size="small"
            value={editingThresholds.coCohort}
            onChange={(e) => setEditingThresholds({ ...editingThresholds, coCohort: Number(e.target.value) })}
          />
          <TextField
            label="PO Cohort %"
            type="number"
            size="small"
            value={editingThresholds.poCohort}
            onChange={(e) => setEditingThresholds({ ...editingThresholds, poCohort: Number(e.target.value) })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeThresholdDialog}>Cancel</Button>
          <Button onClick={handleSaveThresholds} variant="contained" disabled={thresholdsSaving}>
            {thresholdsSaving ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      {ConfirmDialog}
    </Box>
  );
};

export default ManageCourseAssignments;
