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
  Typography,
} from '@mui/material';
import {
  getCulminationCoursesForProgramme,
  getCulminationProgrammes,
  getSelectedCulminationCourseCodes,
  saveCulminationCourses,
  type CulminationCourseItem,
} from '../../api/admin';

const ManageCulminationCourses = () => {
  const [programmes, setProgrammes] = useState<string[]>([]);
  const [programme, setProgramme] = useState('');

  const [allCourses, setAllCourses] = useState<CulminationCourseItem[]>([]);
  const [selectedCodes, setSelectedCodes] = useState<Set<string>>(new Set());

  const [pickedAvailable, setPickedAvailable] = useState<Set<string>>(new Set());
  const [pickedSelected, setPickedSelected] = useState<Set<string>>(new Set());

  const [status, setStatus] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const loadProgrammes = async () => {
    try {
      const response = await getCulminationProgrammes();
      const list = response.data;
      setProgrammes(list);

      if (list.length === 0) {
        setProgramme('');
        setAllCourses([]);
        setSelectedCodes(new Set());
        setStatus('No programmes found.');
        return;
      }

      if (!list.includes(programme)) {
        setProgramme(list[0]);
      }
    } catch (error) {
      console.error('Failed to load programmes', error);
      setMessage({ type: 'error', text: 'Failed to load programmes.' });
    }
  };

  const loadProgrammeCourses = async (selectedProgramme: string) => {
    if (!selectedProgramme) return;

    try {
      const [coursesRes, selectedRes] = await Promise.all([
        getCulminationCoursesForProgramme(selectedProgramme),
        getSelectedCulminationCourseCodes(selectedProgramme),
      ]);

      setAllCourses(coursesRes.data);
      setSelectedCodes(new Set(selectedRes.data));
      setPickedAvailable(new Set());
      setPickedSelected(new Set());

      setStatus(
        `Programme ${selectedProgramme}: ${coursesRes.data.length} course(s), ${selectedRes.data.length} selected`,
      );
    } catch (error) {
      console.error('Failed to load courses for programme', error);
      setMessage({ type: 'error', text: 'Failed to load courses for selected programme.' });
    }
  };

  useEffect(() => {
    loadProgrammes();
  }, []);

  useEffect(() => {
    if (programme) {
      loadProgrammeCourses(programme);
    }
  }, [programme]);

  const selectedList = useMemo(
    () => allCourses.filter((course) => selectedCodes.has(course.courseCode)),
    [allCourses, selectedCodes],
  );

  const availableList = useMemo(
    () => allCourses.filter((course) => !selectedCodes.has(course.courseCode)),
    [allCourses, selectedCodes],
  );

  const togglePicked = (bucket: 'available' | 'selected', code: string) => {
    if (bucket === 'available') {
      setPickedAvailable((prev) => {
        const next = new Set(prev);
        if (next.has(code)) next.delete(code);
        else next.add(code);
        return next;
      });
      return;
    }

    setPickedSelected((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const addSelected = () => {
    if (pickedAvailable.size === 0) return;
    const next = new Set(selectedCodes);
    pickedAvailable.forEach((code) => next.add(code));
    setSelectedCodes(next);
    setPickedAvailable(new Set());
  };

  const removeSelected = () => {
    if (pickedSelected.size === 0) return;
    const next = new Set(selectedCodes);
    pickedSelected.forEach((code) => next.delete(code));
    setSelectedCodes(next);
    setPickedSelected(new Set());
  };

  const handleSave = async () => {
    if (!programme) {
      setMessage({ type: 'error', text: 'Select a programme first.' });
      return;
    }

    const codes = selectedList.map((course) => course.courseCode);
    if (codes.length === 0) {
      setMessage({ type: 'error', text: 'Add at least one course to Culmination Courses.' });
      return;
    }

    try {
      const response = await saveCulminationCourses(programme, codes);
      const result = response.data;

      if (!result.saved) {
        setMessage({
          type: 'error',
          text: `PO Coverage Incomplete. Missing: ${result.missingPOs.join(', ')}`,
        });
        return;
      }

      setMessage({ type: 'success', text: `Culmination courses updated for ${programme}.` });
      setStatus(`Saved ${codes.length} course(s) for ${programme}`);
      loadProgrammeCourses(programme);
    } catch (error) {
      console.error('Failed to save culmination courses', error);
      setMessage({ type: 'error', text: 'Failed to save culmination courses.' });
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Culmination Courses</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'flex', gap: 1.25, alignItems: 'center', flexWrap: 'wrap' }}>
          <FormControl size="small" sx={{ minWidth: 300 }}>
            <InputLabel>Programme</InputLabel>
            <Select label="Programme" value={programme} onChange={(e) => setProgramme(e.target.value)}>
              {programmes.map((p) => (
                <MenuItem key={p} value={p}>
                  {p}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <Button variant="outlined" onClick={loadProgrammes}>Refresh Programmes</Button>
          <Button variant="contained" onClick={handleSave}>Save</Button>
        </Box>
      </Paper>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr auto 1fr' }, gap: 2 }}>
        <Paper sx={{ p: 1.25 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>Available Courses</Typography>
          <List dense sx={{ maxHeight: 420, overflowY: 'auto' }}>
            {availableList.map((course) => (
              <ListItem key={course.courseCode} disablePadding>
                <ListItemButton
                  selected={pickedAvailable.has(course.courseCode)}
                  onClick={() => togglePicked('available', course.courseCode)}
                >
                  <ListItemText primary={course.display} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Paper>

        <Box sx={{ display: 'flex', flexDirection: { xs: 'row', lg: 'column' }, gap: 1, alignItems: 'center', justifyContent: 'center' }}>
          <Button variant="outlined" onClick={addSelected}>
            Add →
          </Button>
          <Button variant="outlined" onClick={removeSelected}>
            ← Remove
          </Button>
        </Box>

        <Paper sx={{ p: 1.25 }}>
          <Typography sx={{ fontWeight: 700, mb: 1 }}>Selected Culmination Courses</Typography>
          <List dense sx={{ maxHeight: 420, overflowY: 'auto' }}>
            {selectedList.map((course) => (
              <ListItem key={course.courseCode} disablePadding>
                <ListItemButton
                  selected={pickedSelected.has(course.courseCode)}
                  onClick={() => togglePicked('selected', course.courseCode)}
                >
                  <ListItemText primary={course.display} />
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

export default ManageCulminationCourses;
