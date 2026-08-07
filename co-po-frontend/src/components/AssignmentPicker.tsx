import { useEffect, useMemo, useState } from 'react';
import { FormControl, InputLabel, MenuItem, Paper, Select, Typography } from '@mui/material';
import { getMyAssignments, type MyAssignment } from '../api/faculty';

export const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;

// Every faculty report/tool page starts the same way: load "my assignments", let the
// faculty member pick one, resolve the picked key back to the full assignment object.
// This was copy-pasted near-identically across 7 pages (OutcomeReport, CourseReport,
// SummaryReport, MarksReports, ViewResults, ExportImportMarks, ManageCourseThresholds)
// before this extraction - same pattern as useConfirmDialog: a hook bundling the state
// with a ready-to-render field, so a page just does
// `const { selectedAssignment, ... } = useAssignmentPicker(); <AssignmentPickerField .../>`
// instead of re-deriving all of this itself.
export function useAssignmentPicker(onError?: (message: string) => void) {
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
      onError?.('Failed to load your course assignments.');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { assignments, selectedKey, setSelectedKey, selectedAssignment };
}

interface AssignmentPickerFieldProps {
  assignments: MyAssignment[];
  selectedKey: string;
  onChange: (key: string) => void;
}

export const AssignmentPickerField = ({ assignments, selectedKey, onChange }: AssignmentPickerFieldProps) => (
  <Paper sx={{ p: 2, mb: 2 }}>
    <FormControl size="small" fullWidth>
      <InputLabel>Course Assignment</InputLabel>
      <Select label="Course Assignment" value={selectedKey} onChange={(e) => onChange(e.target.value)}>
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
);
