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
  Typography,
} from '@mui/material';
import { downloadAdminReport, listAdminReports, type ReportFileItem } from '../../api/reports';

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(2)} MB`;
};

const ViewReports = () => {
  const [reports, setReports] = useState<ReportFileItem[]>([]);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [loading, setLoading] = useState(false);

  const loadReports = async () => {
    setLoading(true);
    try {
      const res = await listAdminReports();
      setReports(res.data);
      if (res.data.length === 0) {
        setMessage({ type: 'error', text: 'No reports found.' });
      } else {
        setMessage(null);
      }
    } catch (error) {
      console.error('Failed to load reports', error);
      setMessage({ type: 'error', text: 'Failed to load reports.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, []);

  const sortedReports = useMemo(
    () => [...reports].sort((a, b) => a.type.localeCompare(b.type) || a.name.localeCompare(b.name)),
    [reports],
  );

  const handleDownload = async (item: ReportFileItem) => {
    try {
      const blob = await downloadAdminReport(item.type, item.name);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = item.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setMessage({ type: 'success', text: `Downloaded ${item.name}` });
    } catch (error) {
      console.error('Download failed', error);
      setMessage({ type: 'error', text: `Failed to download ${item.name}` });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>View Reports</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography sx={{ color: '#64748b' }}>
          {loading ? 'Loading reports...' : `Total reports: ${sortedReports.length}`}
        </Typography>
        <Button variant="outlined" onClick={loadReports}>Refresh</Button>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Type</TableCell>
              <TableCell>File Name</TableCell>
              <TableCell>Size</TableCell>
              <TableCell>Modified</TableCell>
              <TableCell>Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedReports.map((item) => (
              <TableRow key={`${item.type}-${item.name}`}>
                <TableCell>{item.type}</TableCell>
                <TableCell>{item.name}</TableCell>
                <TableCell>{formatBytes(item.sizeBytes)}</TableCell>
                <TableCell>{item.lastModified}</TableCell>
                <TableCell>
                  <Button variant="contained" onClick={() => handleDownload(item)}>
                    Open / Download
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ViewReports;
