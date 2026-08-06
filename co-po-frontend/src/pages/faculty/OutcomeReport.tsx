import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
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
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  downloadFacultyReport,
  generateCoReport,
  generatePoReport,
  getCoAttainment,
  getMyAssignments,
  getPoAttainment,
  type AttainmentResult,
  type MyAssignment,
} from '../../api/faculty';

const assignmentKey = (a: MyAssignment) => `${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`;

interface OutcomeReportProps {
  kind: 'co' | 'po';
}

// Shared by COReport.tsx and POReport.tsx - the two report types are structurally
// identical (same picker, same chart, same editable comment table), they just point
// at different attainment/generate endpoints, so it didn't make sense to fork the file.
const OutcomeReport = ({ kind }: OutcomeReportProps) => {
  const label = kind === 'co' ? 'CO' : 'PO';
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [selectedKey, setSelectedKey] = useState('');
  const [attainment, setAttainment] = useState<AttainmentResult | null>(null);
  const [comments, setComments] = useState<Record<string, { comment: string; suggestions: string }>>({});
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generatedFile, setGeneratedFile] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => assignmentKey(a) === selectedKey) ?? null,
    [assignments, selectedKey],
  );

  useEffect(() => {
    getMyAssignments().then((res) => setAssignments(res.data)).catch((error) => {
      console.error('Failed to load assignments', error);
      setMessage({ type: 'error', text: 'Failed to load your course assignments.' });
    });
  }, []);

  useEffect(() => {
    setGeneratedFile(null);
    setComments({});
    if (!selectedAssignment) {
      setAttainment(null);
      return;
    }
    const { courseCode, programme, academicYear, department } = selectedAssignment;
    setLoading(true);
    const fetchAttainment = kind === 'co' ? getCoAttainment : getPoAttainment;
    fetchAttainment(courseCode, programme, academicYear, department)
      .then((res) => setAttainment(res.data))
      .catch((error) => {
        console.error(`Failed to load ${label} attainment`, error);
        setMessage({ type: 'error', text: `Failed to load ${label} attainment.` });
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedKey, kind]);

  const updateComment = (code: string, field: 'comment' | 'suggestions', value: string) => {
    setComments((prev) => ({
      ...prev,
      [code]: { comment: prev[code]?.comment ?? '', suggestions: prev[code]?.suggestions ?? '', [field]: value },
    }));
  };

  const handleGenerate = async () => {
    if (!selectedAssignment || !attainment) return;
    setGenerating(true);
    setMessage(null);
    try {
      const { courseCode, programme, academicYear, department } = selectedAssignment;
      const payload = attainment.rows.map((row) => ({
        code: row.code,
        comment: comments[row.code]?.comment ?? '',
        suggestions: comments[row.code]?.suggestions ?? '',
      }));
      const generate = kind === 'co' ? generateCoReport : generatePoReport;
      const res = await generate(courseCode, programme, academicYear, department, payload);
      if (res.data.pdfFileName) {
        setGeneratedFile(res.data.pdfFileName);
        setMessage({ type: 'success', text: 'Report generated successfully.' });
      } else {
        setMessage({ type: 'error', text: res.data.issues[0] || 'Failed to generate report.' });
      }
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to generate report.';
      setMessage({ type: 'error', text });
    } finally {
      setGenerating(false);
    }
  };

  const handleDownload = async () => {
    if (!generatedFile) return;
    try {
      const blob = await downloadFacultyReport(label, generatedFile);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = generatedFile;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Download failed', error);
      setMessage({ type: 'error', text: 'Failed to download the generated PDF.' });
    }
  };

  const rows = attainment?.rows ?? [];
  const issues = attainment?.issues ?? [];

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>{label} Report</Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        {kind === 'co'
          ? 'Course Outcome attainment for one of your assignments, computed fresh from graded marks. Add a comment and suggestion for each CO, then generate a PDF.'
          : 'Programme Outcome attainment for one of your assignments, computed fresh from graded marks via the CO-to-PO mapping. Add a comment and suggestion for each PO, then generate a PDF.'}
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

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

      {loading && <Typography sx={{ color: '#64748b' }}>Loading attainment...</Typography>}

      {!loading && selectedAssignment && issues.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {label} attainment isn't available yet:
          <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
            {issues.map((issue) => (
              <li key={issue}>{issue}</li>
            ))}
          </ul>
        </Alert>
      )}

      {!loading && rows.length > 0 && (
        <>
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography sx={{ fontWeight: 600, mb: 1 }}>Attainment %</Typography>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={rows}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="code" />
                <YAxis domain={[0, 100]} />
                <Tooltip formatter={(value) => `${Number(value).toFixed(2)}%`} />
                <Bar dataKey="attainedPercent" fill="#2563eb" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Paper>

          <TableContainer component={Paper} sx={{ mb: 2 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{label}</TableCell>
                  <TableCell align="right">Attainment %</TableCell>
                  <TableCell>Comment</TableCell>
                  <TableCell>Suggestions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.code}>
                    <TableCell sx={{ fontWeight: 600 }}>{row.code}</TableCell>
                    <TableCell align="right">{row.attainedPercent.toFixed(2)}%</TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        fullWidth
                        variant="standard"
                        value={comments[row.code]?.comment ?? ''}
                        onChange={(e) => updateComment(row.code, 'comment', e.target.value.slice(0, 80))}
                        placeholder="Optional comment"
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        fullWidth
                        variant="standard"
                        value={comments[row.code]?.suggestions ?? ''}
                        onChange={(e) => updateComment(row.code, 'suggestions', e.target.value.slice(0, 80))}
                        placeholder="Optional suggestion"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button variant="contained" onClick={handleGenerate} disabled={generating}>
              {generating ? 'Generating...' : 'Generate PDF Report'}
            </Button>
            {generatedFile && (
              <Button variant="outlined" onClick={handleDownload}>
                Download {generatedFile}
              </Button>
            )}
          </Box>
        </>
      )}
    </Box>
  );
};

export default OutcomeReport;
