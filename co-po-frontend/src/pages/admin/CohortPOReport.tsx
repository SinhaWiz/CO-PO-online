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
  Typography,
} from '@mui/material';
import {
  downloadAdminReport,
  generateCohortPoReport,
  getCohortBatches,
  getCohortProgrammes,
  getCohortStudents,
  type CohortPoRow,
  type CohortStudent,
} from '../../api/reports';

const CohortPOReport = () => {
  const [programmes, setProgrammes] = useState<string[]>([]);
  const [programme, setProgramme] = useState('');
  const [batches, setBatches] = useState<number[]>([]);
  const [batch, setBatch] = useState<number | ''>('');

  const [students, setStudents] = useState<CohortStudent[]>([]);
  const [rows, setRows] = useState<CohortPoRow[]>([]);
  const [statusMessages, setStatusMessages] = useState<string[]>([]);

  const [isGenerating, setIsGenerating] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadProgrammes = async () => {
    try {
      const res = await getCohortProgrammes();
      setProgrammes(res.data);
      if (res.data.length > 0 && !res.data.includes(programme)) {
        setProgramme(res.data[0]);
      }
    } catch (error) {
      console.error('Failed to load programmes', error);
      setMessage({ type: 'error', text: 'Failed to load programmes.' });
    }
  };

  useEffect(() => {
    loadProgrammes();
  }, []);

  useEffect(() => {
    const loadBatches = async () => {
      if (!programme) return;
      try {
        const res = await getCohortBatches(programme);
        setBatches(res.data);
        if (res.data.length > 0 && (batch === '' || !res.data.includes(batch))) {
          setBatch(res.data[0]);
        }
      } catch (error) {
        console.error('Failed to load batches', error);
        setMessage({ type: 'error', text: 'Failed to load batches.' });
      }
    };

    loadBatches();
    setStudents([]);
    setRows([]);
    setStatusMessages([]);
  }, [programme]);

  useEffect(() => {
    const loadCohort = async () => {
      if (!programme || batch === '') return;
      try {
        const res = await getCohortStudents(programme, batch);
        setStudents(res.data);
      } catch (error) {
        console.error('Failed to load cohort students', error);
        setMessage({ type: 'error', text: 'Failed to load graduating cohort students.' });
      }
    };

    loadCohort();
    setRows([]);
    setStatusMessages([]);
  }, [programme, batch]);

  const sortedRows = useMemo(
    () => [...rows].sort((a, b) => Number(a.po.replace(/\D/g, '')) - Number(b.po.replace(/\D/g, ''))),
    [rows],
  );

  const handleGenerate = async () => {
    if (!programme || batch === '') {
      setMessage({ type: 'error', text: 'Select programme and batch first.' });
      return;
    }

    setIsGenerating(true);
    setMessage(null);
    try {
      const res = await generateCohortPoReport(programme, batch);
      const result = res.data;

      setRows(result.rows ?? []);
      setStatusMessages(result.statusMessages ?? []);

      if (result.pdfFileName) {
        setMessage({ type: 'success', text: `Report generated: ${result.pdfFileName}` });
      } else {
        setMessage({ type: 'error', text: 'Report generation finished with validation issues.' });
      }
    } catch (error) {
      console.error('Failed to generate cohort report', error);
      setMessage({ type: 'error', text: 'Failed to generate cohort PO report.' });
    } finally {
      setIsGenerating(false);
    }
  };

  const handleDownloadLatest = async () => {
    const fileLine = statusMessages.find((line) => line.toLowerCase().includes('report generated'));
    const generatedName = fileLine?.split(':').at(-1)?.trim();
    if (!generatedName) {
      setMessage({ type: 'error', text: 'No generated report found in current session.' });
      return;
    }

    try {
      const blob = await downloadAdminReport('Cohort PO', generatedName);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = generatedName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Download failed', error);
      setMessage({ type: 'error', text: 'Failed to download generated report.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Cohort PO Report</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr auto auto' }, gap: 1.5 }}>
          <FormControl size="small">
            <InputLabel>Programme</InputLabel>
            <Select label="Programme" value={programme} onChange={(e) => setProgramme(e.target.value)}>
              {programmes.map((p) => (
                <MenuItem key={p} value={p}>{p}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" disabled={!programme || batches.length === 0}>
            <InputLabel>Batch</InputLabel>
            <Select label="Batch" value={batch} onChange={(e) => setBatch(Number(e.target.value))}>
              {batches.map((b) => (
                <MenuItem key={b} value={b}>{b}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <Button variant="contained" onClick={handleGenerate} disabled={isGenerating || !programme || batch === ''}>
            {isGenerating ? 'Generating...' : 'Generate Report'}
          </Button>

          <Button variant="outlined" onClick={handleDownloadLatest}>
            Download Latest PDF
          </Button>
        </Box>
      </Paper>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 1.5 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>Graduating Cohort Students ({students.length})</Typography>
          <TableContainer sx={{ maxHeight: 360 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>S/N</TableCell>
                  <TableCell>ID</TableCell>
                  <TableCell>Name</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {students.map((s, i) => (
                  <TableRow key={s.id}>
                    <TableCell>{i + 1}</TableCell>
                    <TableCell>{s.id}</TableCell>
                    <TableCell>{s.name}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>

        <Paper sx={{ p: 1.5 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>PO Attainment Result</Typography>
          <TableContainer sx={{ maxHeight: 360 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>PO</TableCell>
                  <TableCell>Attainment %</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedRows.map((r) => (
                  <TableRow key={r.po}>
                    <TableCell>{r.po}</TableCell>
                    <TableCell>{r.percent.toFixed(2)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      </Box>

      <Paper sx={{ p: 1.5, mt: 2 }}>
        <Typography sx={{ fontWeight: 700, mb: 1 }}>Status</Typography>
        <Box component="ul" sx={{ m: 0, pl: 2 }}>
          {statusMessages.map((line, idx) => (
            <Typography component="li" key={`${idx}-${line}`} sx={{ color: '#475569', fontSize: 14 }}>
              {line}
            </Typography>
          ))}
        </Box>
      </Paper>
    </Box>
  );
};

export default CohortPOReport;
