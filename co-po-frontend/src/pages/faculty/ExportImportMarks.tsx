import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Paper, Typography } from '@mui/material';
import {
  exportMarksExcel,
  importMarksExcel,
  type MarksImportResult,
} from '../../api/faculty';
import { AssignmentPickerField, useAssignmentPicker } from '../../components/AssignmentPicker';

// Ports the desktop app's "Export Marks" / "Import Marks" sidebar buttons - a full
// course roster (one sheet per assessment section, plus an Attendance sheet for
// legacy offerings) exported pre-filled with existing marks so grading can happen
// offline, then the same file brought back in to save it. No on-screen preview here,
// same as desktop - it's a file round-trip, not an editable form.
const ExportImportMarks = () => {
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { assignments, selectedKey, setSelectedKey, selectedAssignment } = useAssignmentPicker(
    (text) => setMessage({ type: 'error', text }),
  );
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<MarksImportResult | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setResult(null);
    setMessage(null);
  }, [selectedKey]);

  const handleExport = async () => {
    if (!selectedAssignment) return;
    setExporting(true);
    setMessage(null);
    try {
      const { courseCode, programme, academicYear } = selectedAssignment;
      const blob = await exportMarksExcel(courseCode, programme, academicYear);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Marks_${courseCode}_${academicYear}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Export failed', error);
      setMessage({ type: 'error', text: 'Failed to export marks.' });
    } finally {
      setExporting(false);
    }
  };

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !selectedAssignment) return;
    setImporting(true);
    setMessage(null);
    setResult(null);
    try {
      const { courseCode, programme, academicYear } = selectedAssignment;
      const res = await importMarksExcel(courseCode, programme, academicYear, file);
      setResult(res.data);
      setMessage({ type: 'success', text: 'Import complete.' });
    } catch (error: any) {
      setMessage({ type: 'error', text: error?.response?.data?.message || 'Failed to import marks.' });
    } finally {
      setImporting(false);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Export / Import Marks</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        Export a course's full roster to Excel - one sheet per assessment section, pre-filled with any marks
        already entered - grade it offline, then import the same file back to save your changes.
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <AssignmentPickerField assignments={assignments} selectedKey={selectedKey} onChange={setSelectedKey} />

      {selectedAssignment && (
        <Paper sx={{ p: 2.5, mb: 2 }}>
          <input ref={fileInputRef} type="file" accept=".xlsx,.xls" hidden onChange={handleFileSelected} />
          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button variant="contained" onClick={handleExport} disabled={exporting}>
              {exporting ? 'Exporting...' : '📤 Export Marks'}
            </Button>
            <Button variant="outlined" onClick={() => fileInputRef.current?.click()} disabled={importing}>
              {importing ? 'Importing...' : '📥 Import Marks'}
            </Button>
          </Box>

          {result && (
            <Box sx={{ mt: 2 }}>
              <Typography sx={{ fontSize: 14 }}>
                Saved {result.marksSaved} mark(s){result.attendanceSaved > 0 ? ` and ${result.attendanceSaved} attendance row(s)` : ''}.
              </Typography>
              {result.errors.length > 0 && (
                <Box sx={{ mt: 1, maxHeight: 280, overflow: 'auto', bgcolor: '#fef2f2', borderRadius: 1, p: 1.5 }}>
                  {result.errors.map((err, i) => (
                    <Typography key={i} sx={{ fontSize: 13, color: '#991b1b' }}>{err}</Typography>
                  ))}
                </Box>
              )}
            </Box>
          )}
        </Paper>
      )}
    </Box>
  );
};

export default ExportImportMarks;
