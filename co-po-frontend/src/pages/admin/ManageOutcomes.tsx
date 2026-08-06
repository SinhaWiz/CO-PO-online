import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import {
  createCO,
  createPO,
  deleteCO,
  deletePO,
  getCOs,
  getPOs,
  type CourseOutcome,
  type ProgramOutcome,
} from '../../api/admin';
import { useConfirmDialog } from '../../components/ConfirmDialog';

const ManageOutcomes = () => {
  const [cos, setCos] = useState<CourseOutcome[]>([]);
  const [pos, setPos] = useState<ProgramOutcome[]>([]);
  const [newCo, setNewCo] = useState('');
  const [newPo, setNewPo] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const { confirm, ConfirmDialog } = useConfirmDialog();

  const loadData = async () => {
    try {
      const [coRes, poRes] = await Promise.all([getCOs(), getPOs()]);
      setCos(coRes.data);
      setPos(poRes.data);
    } catch (error) {
      console.error('Failed to load CO/PO master data', error);
      setMessage({ type: 'error', text: 'Failed to load CO/PO master data.' });
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAddCo = async () => {
    const value = newCo.trim().toUpperCase();
    if (!value) return;

    try {
      await createCO(value);
      setNewCo('');
      loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to add CO.';
      setMessage({ type: 'error', text });
    }
  };

  const handleAddPo = async () => {
    const value = newPo.trim().toUpperCase();
    if (!value) return;

    try {
      await createPO(value);
      setNewPo('');
      loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to add PO. PO numbers must be unique.';
      setMessage({ type: 'error', text });
    }
  };

  const handleDeleteCo = async (co: CourseOutcome) => {
    const ok = await confirm(`Remove ${co.coNumber}? Any question already mapped to it will lose that mapping.`, {
      title: 'Remove CO',
      confirmLabel: 'Remove',
      confirmColor: 'error',
    });
    if (!ok) return;

    try {
      await deleteCO(co.id);
      loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to remove CO.';
      setMessage({ type: 'error', text });
    }
  };

  const handleDeletePo = async (po: ProgramOutcome) => {
    const ok = await confirm(`Remove ${po.poNumber}? Any question already mapped to it will lose that mapping.`, {
      title: 'Remove PO',
      confirmLabel: 'Remove',
      confirmColor: 'error',
    });
    if (!ok) return;

    try {
      await deletePO(po.id);
      loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || 'Failed to remove PO.';
      setMessage({ type: 'error', text });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>
        Manage Course &amp; Program Outcomes
      </Typography>
      <Typography sx={{ color: '#64748b', mb: 2 }}>
        This is the master list of Course Outcomes (COs) and Program Outcomes (POs) courses can be scoped to
        and assessment questions can be mapped to.
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 2 }}>
          <Typography sx={{ fontWeight: 700, mb: 1.5 }}>Course Outcomes (CO)</Typography>
          <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
            <TextField
              label="New CO"
              placeholder="CO21"
              size="small"
              value={newCo}
              onChange={(e) => setNewCo(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddCo()}
              fullWidth
            />
            <Button variant="contained" onClick={handleAddCo}>
              Add
            </Button>
          </Box>
          <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
            {cos.map((co) => (
              <Chip key={co.id} label={co.coNumber} onDelete={() => handleDeleteCo(co)} />
            ))}
            {cos.length === 0 && <Typography sx={{ color: '#94a3b8' }}>No COs defined yet.</Typography>}
          </Stack>
        </Paper>

        <Paper sx={{ p: 2 }}>
          <Typography sx={{ fontWeight: 700, mb: 1.5 }}>Program Outcomes (PO)</Typography>
          <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
            <TextField
              label="New PO"
              placeholder="PO13"
              size="small"
              value={newPo}
              onChange={(e) => setNewPo(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddPo()}
              fullWidth
            />
            <Button variant="contained" onClick={handleAddPo}>
              Add
            </Button>
          </Box>
          <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
            {pos.map((po) => (
              <Chip key={po.id} label={po.poNumber} onDelete={() => handleDeletePo(po)} />
            ))}
            {pos.length === 0 && <Typography sx={{ color: '#94a3b8' }}>No POs defined yet.</Typography>}
          </Stack>
        </Paper>
      </Box>

      {ConfirmDialog}
    </Box>
  );
};

export default ManageOutcomes;
