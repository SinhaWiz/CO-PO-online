import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
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
import {
  getGraduatingBatches,
  getGraduatingProgrammes,
  getGraduatingSelectedIds,
  getGraduatingStudents,
  saveGraduatingStudents,
  type GraduatingStudentRow,
} from '../../api/admin';

const ManageGraduatingStudents = () => {
  const [programmes, setProgrammes] = useState<string[]>([]);
  const [programme, setProgramme] = useState('');
  const [batches, setBatches] = useState<number[]>([]);
  const [batch, setBatch] = useState<number | ''>('');

  const [allStudents, setAllStudents] = useState<GraduatingStudentRow[]>([]);
  const [selectedGraduatingIds, setSelectedGraduatingIds] = useState<Set<string>>(new Set());
  const [commentsByStudentId, setCommentsByStudentId] = useState<Record<string, string>>({});

  const [pickedAvailableIds, setPickedAvailableIds] = useState<Set<string>>(new Set());
  const [pickedSelectedIds, setPickedSelectedIds] = useState<Set<string>>(new Set());

  const [status, setStatus] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadProgrammes = async () => {
    try {
      const response = await getGraduatingProgrammes();
      const items = response.data;
      setProgrammes(items);

      if (items.length === 0) {
        setProgramme('');
        setBatch('');
        setBatches([]);
        setAllStudents([]);
        setSelectedGraduatingIds(new Set());
        setCommentsByStudentId({});
        setStatus('No programmes found.');
        return;
      }

      if (!items.includes(programme)) {
        setProgramme(items[0]);
      }
    } catch (error) {
      console.error('Failed to load programmes', error);
      setMessage({ type: 'error', text: 'Failed to load programmes.' });
    }
  };

  const loadBatches = async (selectedProgramme: string) => {
    if (!selectedProgramme) return;

    try {
      const response = await getGraduatingBatches(selectedProgramme);
      const items = response.data;
      setBatches(items);

      if (items.length === 0) {
        setBatch('');
        setAllStudents([]);
        setSelectedGraduatingIds(new Set());
        setCommentsByStudentId({});
        setStatus(`Programme ${selectedProgramme}: 0 batch(es)`);
        return;
      }

      if (batch === '' || !items.includes(batch)) {
        setBatch(items[0]);
      }
    } catch (error) {
      console.error('Failed to load batches', error);
      setMessage({ type: 'error', text: 'Failed to load batches.' });
    }
  };

  const loadStudents = async (selectedProgramme: string, selectedBatch: number) => {
    try {
      const [studentsRes, selectedRes] = await Promise.all([
        getGraduatingStudents(selectedProgramme, selectedBatch),
        getGraduatingSelectedIds(selectedProgramme, selectedBatch),
      ]);

      const students = studentsRes.data;
      const selectedIds = new Set(selectedRes.data);
      const comments: Record<string, string> = {};

      students.forEach((student) => {
        comments[student.id] = student.comment || '';
      });

      setAllStudents(students);
      setSelectedGraduatingIds(selectedIds);
      setCommentsByStudentId(comments);
      setPickedAvailableIds(new Set());
      setPickedSelectedIds(new Set());

      const commented = students.filter((s) => (s.comment || '').trim().length > 0).length;
      setStatus(
        `Programme ${selectedProgramme}, Batch ${selectedBatch}: ${students.length} student(s), ${selectedIds.size} graduating, ${commented} with comments`,
      );
    } catch (error) {
      console.error('Failed to load students', error);
      setMessage({ type: 'error', text: 'Failed to load students.' });
    }
  };

  useEffect(() => {
    loadProgrammes();
  }, []);

  useEffect(() => {
    if (programme) {
      loadBatches(programme);
    }
  }, [programme]);

  useEffect(() => {
    if (programme && batch !== '') {
      loadStudents(programme, batch);
    }
  }, [programme, batch]);

  const selectedGraduatingList = useMemo(
    () => allStudents.filter((student) => selectedGraduatingIds.has(student.id)),
    [allStudents, selectedGraduatingIds],
  );

  const availableNonGraduating = useMemo(
    () => allStudents.filter((student) => !selectedGraduatingIds.has(student.id)),
    [allStudents, selectedGraduatingIds],
  );

  const handleAddSelected = () => {
    if (pickedAvailableIds.size === 0) return;

    const movingWithComments = availableNonGraduating.filter(
      (student) => pickedAvailableIds.has(student.id) && (commentsByStudentId[student.id] || '').trim().length > 0,
    );

    if (movingWithComments.length > 0) {
      const details = movingWithComments.map((s) => `${s.id} - ${s.name}`).join('\n');
      const ok = window.confirm(
        `The following students have comments explaining why they are not graduating:\n${details}\n\nMark them as graduating? Comments will be preserved until Save.`,
      );
      if (!ok) return;
    }

    const next = new Set(selectedGraduatingIds);
    pickedAvailableIds.forEach((id) => next.add(id));
    setSelectedGraduatingIds(next);
    setPickedAvailableIds(new Set());
    setStatus(`Moved ${pickedAvailableIds.size} student(s) to graduating list`);
  };

  const handleRemoveSelected = () => {
    if (pickedSelectedIds.size === 0) return;

    const next = new Set(selectedGraduatingIds);
    pickedSelectedIds.forEach((id) => next.delete(id));
    setSelectedGraduatingIds(next);
    setPickedSelectedIds(new Set());
  };

  const togglePicked = (bucket: 'available' | 'selected', id: string) => {
    if (bucket === 'available') {
      setPickedAvailableIds((prev) => {
        const next = new Set(prev);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
      });
      return;
    }

    setPickedSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSave = async () => {
    if (!programme) {
      setMessage({ type: 'error', text: 'Select a programme.' });
      return;
    }
    if (batch === '') {
      setMessage({ type: 'error', text: 'Select a batch.' });
      return;
    }

    const ok = window.confirm(
      `Save ${selectedGraduatingIds.size} graduating students?\nComments for graduating students will be removed from database.`,
    );
    if (!ok) return;

    try {
      const response = await saveGraduatingStudents(
        programme,
        batch,
        Array.from(selectedGraduatingIds),
        commentsByStudentId,
      );

      setMessage({
        type: 'success',
        text: `Saved. Graduating: ${response.data.graduatingCount}, commented non-graduating: ${response.data.commentedNonGraduatingCount}.`,
      });

      loadStudents(programme, batch);
    } catch (error) {
      console.error('Failed to save graduating students', error);
      setMessage({ type: 'error', text: 'Failed to save graduating students/comments.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Graduating Students</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'flex', gap: 1.25, alignItems: 'center', flexWrap: 'wrap' }}>
          <FormControl size="small" sx={{ minWidth: 280 }}>
            <InputLabel>Programme</InputLabel>
            <Select label="Programme" value={programme} onChange={(e) => setProgramme(e.target.value)}>
              {programmes.map((p) => (
                <MenuItem key={p} value={p}>{p}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel>Batch</InputLabel>
            <Select
              label="Batch"
              value={batch}
              onChange={(e) => setBatch(Number(e.target.value))}
              disabled={!programme || batches.length === 0}
            >
              {batches.map((b) => (
                <MenuItem key={b} value={b}>{b}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <Button variant="outlined" onClick={loadProgrammes}>Refresh</Button>
          <Button variant="contained" onClick={handleSave}>Save</Button>
        </Box>
      </Paper>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1.3fr auto 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 1.25 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>Available (Non-Graduating) Students</Typography>
          <TableContainer sx={{ maxHeight: 460 }}>
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Student ID</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Comment</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {availableNonGraduating.map((student) => (
                  <TableRow
                    key={student.id}
                    hover
                    selected={pickedAvailableIds.has(student.id)}
                    onClick={() => togglePicked('available', student.id)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{student.id}</TableCell>
                    <TableCell>{student.name}</TableCell>
                    <TableCell>
                      <TextField
                        value={commentsByStudentId[student.id] ?? ''}
                        onChange={(e) => setCommentsByStudentId((prev) => ({ ...prev, [student.id]: e.target.value }))}
                        size="small"
                        fullWidth
                        onClick={(e) => e.stopPropagation()}
                        placeholder="Why not graduating?"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>

        <Box sx={{ display: 'flex', flexDirection: { xs: 'row', lg: 'column' }, gap: 1, alignItems: 'center', justifyContent: 'center' }}>
          <Button variant="outlined" onClick={handleAddSelected}>Add →</Button>
          <Button variant="outlined" onClick={handleRemoveSelected}>← Remove</Button>
        </Box>

        <Paper sx={{ p: 1.25 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>Selected Graduating Students</Typography>
          <List dense sx={{ maxHeight: 460, overflowY: 'auto' }}>
            {selectedGraduatingList.map((student) => (
              <ListItem key={student.id} disablePadding>
                <ListItemButton
                  selected={pickedSelectedIds.has(student.id)}
                  onClick={() => togglePicked('selected', student.id)}
                >
                  <ListItemText primary={`${student.id} - ${student.name}`} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Paper>
      </Box>

      <Typography sx={{ mt: 2, color: '#64748b' }}>{status}</Typography>
    </Box>
  );
};

export default ManageGraduatingStudents;
