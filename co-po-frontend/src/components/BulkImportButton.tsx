import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material';
import type { AxiosResponse } from 'axios';
import type { ImportResult } from '../api/admin';

interface BulkImportButtonProps {
  label?: string;
  onImport: (file: File) => Promise<AxiosResponse<ImportResult>>;
  onComplete?: (result: ImportResult) => void;
}

// Shared by all 5 admin "bulk import from Excel" screens (Students, Faculties,
// Courses, Enrollments, Course Assignments) - same file picker, same
// inserted/skipped/errors result dialog every time, just pointed at a different
// endpoint. Callers that need extra context (Enrollments' default course/programme/
// year) close over their own state in the onImport callback rather than this
// component needing to know about it.
const BulkImportButton = ({ label = 'Import from Excel', onImport, onComplete }: BulkImportButtonProps) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleFileSelected = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setImporting(true);
    setError(null);
    try {
      const res = await onImport(file);
      setResult(res.data);
      onComplete?.(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Import failed - check the file and try again.');
    } finally {
      setImporting(false);
    }
  };

  const close = () => {
    setResult(null);
    setError(null);
  };

  return (
    <>
      <input ref={inputRef} type="file" accept=".xlsx,.xls" hidden onChange={handleFileSelected} />
      <Button variant="outlined" onClick={() => inputRef.current?.click()} disabled={importing}>
        {importing ? 'Importing...' : label}
      </Button>

      <Dialog open={!!result || !!error} onClose={close} maxWidth="sm" fullWidth>
        <DialogTitle>Import Result</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <>
              <Typography sx={{ mb: 1 }}>
                Imported {result.inserted} row(s). Skipped {result.skipped}.
                {result.mapped > 0 ? ` Mapped CO/POs/Sections on ${result.mapped} row(s).` : ''}
              </Typography>
              {result.errors.length > 0 && (
                <Box sx={{ maxHeight: 320, overflow: 'auto', bgcolor: '#fef2f2', borderRadius: 1, p: 1.5 }}>
                  {result.errors.map((e, i) => (
                    <Typography key={i} sx={{ fontSize: 13, color: '#991b1b' }}>{e}</Typography>
                  ))}
                </Box>
              )}
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={close}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default BulkImportButton;
