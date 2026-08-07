import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import {
  getMyAssignmentThresholds,
  updateMyAssignmentThresholds,
  type Thresholds,
} from '../../api/faculty';
import { AssignmentPickerField, useAssignmentPicker } from '../../components/AssignmentPicker';

const defaultThresholds: Thresholds = { coIndividual: 60, poIndividual: 40, coCohort: 50, poCohort: 50 };

const ManageCourseThresholds = () => {
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { assignments, selectedKey, setSelectedKey, selectedAssignment } = useAssignmentPicker(
    (text) => setMessage({ type: 'error', text }),
  );
  const [thresholds, setThresholds] = useState<Thresholds>(defaultThresholds);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!selectedAssignment) {
      setThresholds(defaultThresholds);
      return;
    }
    const { courseCode, programme, academicYear, department } = selectedAssignment;
    getMyAssignmentThresholds(courseCode, programme, academicYear, department)
      .then((res) => setThresholds(res.data))
      .catch((error) => {
        console.error('Failed to load thresholds', error);
        setMessage({ type: 'error', text: 'Failed to load thresholds for this assignment.' });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey]);

  const handleSave = async () => {
    if (!selectedAssignment) return;
    setSaving(true);
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const res = await updateMyAssignmentThresholds(courseCode, programme, academicYear, department, thresholds);
      setThresholds(res.data);
      setMessage({ type: 'success', text: 'Thresholds updated successfully.' });
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to update thresholds.';
      setMessage({ type: 'error', text });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Course Thresholds</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        The CO/PO attainment cutoffs used when generating reports for one of your course assignments. An admin
        sets an initial value when assigning you the course; you can adjust it here at any time.
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <AssignmentPickerField assignments={assignments} selectedKey={selectedKey} onChange={setSelectedKey} />

      {selectedAssignment && (
        <Paper sx={{ p: 2.5, maxWidth: 620 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5 }}>
            <TextField
              label="CO Individual %"
              type="number"
              size="small"
              value={thresholds.coIndividual}
              onChange={(e) => setThresholds({ ...thresholds, coIndividual: Number(e.target.value) })}
              helperText="Min. % of a CO's marks a student needs to attain it"
            />
            <TextField
              label="PO Individual %"
              type="number"
              size="small"
              value={thresholds.poIndividual}
              onChange={(e) => setThresholds({ ...thresholds, poIndividual: Number(e.target.value) })}
              helperText="Min. % of a PO's marks a student needs to attain it"
            />
            <TextField
              label="CO Cohort %"
              type="number"
              size="small"
              value={thresholds.coCohort}
              onChange={(e) => setThresholds({ ...thresholds, coCohort: Number(e.target.value) })}
              helperText="Min. % of students who must attain a CO"
            />
            <TextField
              label="PO Cohort %"
              type="number"
              size="small"
              value={thresholds.poCohort}
              onChange={(e) => setThresholds({ ...thresholds, poCohort: Number(e.target.value) })}
              helperText="Min. % of students who must attain a PO"
            />
          </Box>
          <Box sx={{ mt: 2.5 }}>
            <Button variant="contained" onClick={handleSave} disabled={saving}>
              {saving ? 'Saving...' : 'Save Thresholds'}
            </Button>
          </Box>
        </Paper>
      )}
    </Box>
  );
};

export default ManageCourseThresholds;
