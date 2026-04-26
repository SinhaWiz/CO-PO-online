import { useEffect, useState } from 'react';
import { Alert, Box, Button, Paper, TextField, Typography } from '@mui/material';
import { getThresholds, updateThresholds, type Thresholds } from '../../api/admin';

const defaultThresholds: Thresholds = {
  coIndividual: 60,
  poIndividual: 40,
  coCohort: 50,
  poCohort: 50,
};

const ManageThresholds = () => {
  const [form, setForm] = useState<Thresholds>(defaultThresholds);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadThresholds = async () => {
    try {
      const response = await getThresholds();
      setForm(response.data);
    } catch (error) {
      console.error('Failed to load thresholds', error);
      setMessage({ type: 'error', text: 'Failed to load thresholds. Defaults are shown.' });
      setForm(defaultThresholds);
    }
  };

  useEffect(() => {
    loadThresholds();
  }, []);

  const validate = (value: number, label: string) => {
    if (!Number.isFinite(value)) return `${label} threshold must be a number.`;
    if (value < 0 || value >= 100) return `${label} threshold must be between 0 and 99.99.`;
    return null;
  };

  const handleSave = async () => {
    const checks = [
      validate(form.coIndividual, 'CO individual'),
      validate(form.poIndividual, 'PO individual'),
      validate(form.coCohort, 'CO cohort'),
      validate(form.poCohort, 'PO cohort'),
    ].filter(Boolean) as string[];

    if (checks.length > 0) {
      setMessage({ type: 'error', text: checks[0] });
      return;
    }

    try {
      await updateThresholds(form);
      setMessage({ type: 'success', text: 'Thresholds updated successfully.' });
    } catch (error) {
      console.error('Failed to save thresholds', error);
      setMessage({ type: 'error', text: 'Failed to save thresholds.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Thresholds</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, maxWidth: 700 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="CO Individual Threshold (%)"
            type="number"
            size="small"
            value={form.coIndividual}
            onChange={(e) => setForm({ ...form, coIndividual: Number(e.target.value) })}
            slotProps={{ htmlInput: { min: 0, max: 99.99, step: 0.01 } }}
          />
          <TextField
            label="PO Individual Threshold (%)"
            type="number"
            size="small"
            value={form.poIndividual}
            onChange={(e) => setForm({ ...form, poIndividual: Number(e.target.value) })}
            slotProps={{ htmlInput: { min: 0, max: 99.99, step: 0.01 } }}
          />
          <TextField
            label="CO Cohort Threshold (%)"
            type="number"
            size="small"
            value={form.coCohort}
            onChange={(e) => setForm({ ...form, coCohort: Number(e.target.value) })}
            slotProps={{ htmlInput: { min: 0, max: 99.99, step: 0.01 } }}
          />
          <TextField
            label="PO Cohort Threshold (%)"
            type="number"
            size="small"
            value={form.poCohort}
            onChange={(e) => setForm({ ...form, poCohort: Number(e.target.value) })}
            slotProps={{ htmlInput: { min: 0, max: 99.99, step: 0.01 } }}
          />
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleSave}>Save</Button>
          <Button variant="outlined" onClick={loadThresholds}>Reload</Button>
        </Box>
      </Paper>
    </Box>
  );
};

export default ManageThresholds;
