import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Typography,
} from '@mui/material';
import {
  downloadFacultyReport,
  generateConsolidatedMarksReport,
  generateDetailedMarksReport,
  getMyAssignments,
  getSectionsForCourse,
  type CourseSection,
  type MyAssignment,
} from '../../api/faculty';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;

type GenState = { generating: boolean; file: string | null; message: { type: 'success' | 'error'; text: string } | null };
const idleState: GenState = { generating: false, file: null, message: null };

// Both reports are read-only computed dumps, not editable forms - so unlike CO/PO
// Report, Course Report, or Summary Report, this page is just a section picker plus
// two generate actions, matching desktop's own dialogs (which are pickers, not
// on-screen previews). Bundled into one page since both pull from the exact same
// assignment + section picker rather than making faculty pick sections twice.
const MarksReports = () => {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');
  const [sections, setSections] = useState<CourseSection[]>([]);
  const [selectedSectionIds, setSelectedSectionIds] = useState<number[]>([]);
  const [includeOverall, setIncludeOverall] = useState(true);
  const [loading, setLoading] = useState(false);
  const [detailed, setDetailed] = useState<GenState>(idleState);
  const [consolidated, setConsolidated] = useState<GenState>(idleState);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
    });
  }, []);

  useEffect(() => {
    setDetailed(idleState);
    setConsolidated(idleState);
    setSelectedSectionIds([]);
    if (!selectedAssignment) {
      setSections([]);
      return;
    }
    setLoading(true);
    getSectionsForCourse(selectedAssignment.courseCode, selectedAssignment.programme)
      .then((res) => {
        setSections(res.data);
        setSelectedSectionIds(res.data.map((s) => s.id));
      })
      .catch((error) => {
        console.error('Failed to load sections', error);
      })
      .finally(() => setLoading(false));
  }, [selectedKey]);

  const toggleSection = (id: number) => {
    setSelectedSectionIds((prev) => (prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]));
  };

  const downloadResult = async (type: 'Detailed' | 'Consolidated', file: string) => {
    try {
      const blob = await downloadFacultyReport(type, file);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Download failed', error);
    }
  };

  const handleGenerateDetailed = async () => {
    if (!selectedAssignment) return;
    setDetailed({ generating: true, file: null, message: null });
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const res = await generateDetailedMarksReport(courseCode, programme, academicYear, department, selectedSectionIds, includeOverall);
      if (res.data.fileName) {
        setDetailed({ generating: false, file: res.data.fileName, message: { type: 'success', text: 'PDF generated.' } });
      } else {
        setDetailed({ generating: false, file: null, message: { type: 'error', text: res.data.issues[0] || 'Failed to generate report.' } });
      }
    } catch (error: any) {
      setDetailed({ generating: false, file: null, message: { type: 'error', text: error?.response?.data?.message || 'Failed to generate report.' } });
    }
  };

  const handleGenerateConsolidated = async () => {
    if (!selectedAssignment) return;
    setConsolidated({ generating: true, file: null, message: null });
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const res = await generateConsolidatedMarksReport(courseCode, programme, academicYear, department, selectedSectionIds);
      if (res.data.fileName) {
        setConsolidated({ generating: false, file: res.data.fileName, message: { type: 'success', text: 'Workbook generated.' } });
      } else {
        setConsolidated({ generating: false, file: null, message: { type: 'error', text: res.data.issues[0] || 'Failed to generate report.' } });
      }
    } catch (error: any) {
      setConsolidated({ generating: false, file: null, message: { type: 'error', text: error?.response?.data?.message || 'Failed to generate report.' } });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Marks Reports</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        Pick which assessment sections to include, then generate a Detailed Marks Report (PDF - raw marks and
        per-section CO attainment) or a Consolidated Marks Report (Excel - CO and PO percentages grouped by
        assessment type).
      </Typography>

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

      {!loading && selectedAssignment && (
        <>
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography sx={{ fontWeight: 600, mb: 1 }}>Assessment Sections</Typography>
            {sections.length === 0 ? (
              <Typography sx={{ color: '#94a3b8', fontSize: 13 }}>
                This course has no assessment sections configured yet.
              </Typography>
            ) : (
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                {sections.map((s) => (
                  <FormControlLabel
                    key={s.id}
                    control={<Checkbox size="small" checked={selectedSectionIds.includes(s.id)} onChange={() => toggleSection(s.id)} />}
                    label={s.displayName}
                  />
                ))}
              </Box>
            )}
          </Paper>

          <Paper sx={{ p: 2.5, mb: 2 }}>
            <Typography sx={{ fontWeight: 700, mb: 1 }}>Detailed Marks Report (PDF)</Typography>
            <Typography sx={{ color: '#64748b', fontSize: 13, mb: 1 }}>
              Requires every selected section to have questions, students to be enrolled, and all marks graded.
            </Typography>
            <FormControlLabel
              control={<Checkbox size="small" checked={includeOverall} onChange={(e) => setIncludeOverall(e.target.checked)} />}
              label="Include Overall CO Attainment Summary page"
              sx={{ display: 'block', mb: 1 }}
            />
            {detailed.message && (
              <Alert severity={detailed.message.type} sx={{ mb: 1 }} onClose={() => setDetailed({ ...detailed, message: null })}>
                {detailed.message.text}
              </Alert>
            )}
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <Button variant="contained" onClick={handleGenerateDetailed} disabled={detailed.generating || selectedSectionIds.length === 0}>
                {detailed.generating ? 'Generating...' : 'Generate PDF'}
              </Button>
              {detailed.file && (
                <Button variant="outlined" onClick={() => downloadResult('Detailed', detailed.file!)}>
                  Download {detailed.file}
                </Button>
              )}
            </Box>
          </Paper>

          <Paper sx={{ p: 2.5, mb: 4 }}>
            <Typography sx={{ fontWeight: 700, mb: 1 }}>Consolidated Marks Report (Excel)</Typography>
            <Typography sx={{ color: '#64748b', fontSize: 13, mb: 1 }}>
              Works with partial data - missing or ungraded marks show as "-" rather than blocking generation.
            </Typography>
            {consolidated.message && (
              <Alert severity={consolidated.message.type} sx={{ mb: 1 }} onClose={() => setConsolidated({ ...consolidated, message: null })}>
                {consolidated.message.text}
              </Alert>
            )}
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <Button variant="contained" onClick={handleGenerateConsolidated} disabled={consolidated.generating || selectedSectionIds.length === 0}>
                {consolidated.generating ? 'Generating...' : 'Generate Excel'}
              </Button>
              {consolidated.file && (
                <Button variant="outlined" onClick={() => downloadResult('Consolidated', consolidated.file!)}>
                  Download {consolidated.file}
                </Button>
              )}
            </Box>
          </Paper>
        </>
      )}
    </Box>
  );
};

export default MarksReports;
