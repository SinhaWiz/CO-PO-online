import { useState, useEffect } from 'react';
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { getFaculties, createFaculty, updateFaculty, deleteFaculty, importFaculties } from '../../api/admin';
import type { Faculty } from '../../api/admin';
import BulkImportButton from '../../components/BulkImportButton';

const emptyForm: Faculty = { id: '', shortname: '', fullName: '', email: '', password: '' };

const ManageFaculties = () => {
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [form, setForm] = useState<Faculty>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);

  const fetchFaculties = async () => {
    try {
      const response = await getFaculties();
      setFaculties(response.data);
    } catch (error) {
      console.error('Failed to fetch faculties', error);
    }
  };

  useEffect(() => {
    fetchFaculties();
  }, []);

  const handleSave = async () => {
    try {
      if (editingId) {
        await updateFaculty(editingId, form);
      } else {
        await createFaculty(form);
      }
      setForm(emptyForm);
      setEditingId(null);
      fetchFaculties();
    } catch (error) {
      console.error('Failed to save faculty', error);
    }
  };

  const handleEdit = (faculty: Faculty) => {
    // password comes back empty from the API (write-only field) - leaving it blank
    // here means "keep the current password", matching updateFaculty's own behavior.
    setForm({ ...faculty, password: '' });
    setEditingId(faculty.id);
  };

  const handleCancelEdit = () => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteFaculty(id);
      if (editingId === id) handleCancelEdit();
      fetchFaculties();
    } catch (error) {
      console.error('Failed to delete faculty', error);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Faculties</Typography>
      
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(5, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="ID"
            value={form.id}
            onChange={(e) => setForm({ ...form, id: e.target.value })}
            size="small"
            disabled={Boolean(editingId)}
          />
          <TextField
            label="Short Name"
            value={form.shortname}
            onChange={(e) => setForm({ ...form, shortname: e.target.value })}
            size="small"
          />
          <TextField
            label="Full Name"
            value={form.fullName}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            size="small"
          />
          <TextField
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            size="small"
          />
          <TextField
            label={editingId ? 'New Password (leave blank to keep current)' : 'Password'}
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            size="small"
          />
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleSave}>
            {editingId ? 'Update Faculty' : 'Add Faculty'}
          </Button>
          {editingId && (
            <Button variant="outlined" onClick={handleCancelEdit}>
              Cancel
            </Button>
          )}
          <Button variant="outlined" disabled>
            Get Excel Template
          </Button>
          <BulkImportButton label="Bulk Import (Excel)" onImport={importFaculties} onComplete={fetchFaculties} />
        </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>S/N</TableCell>
              <TableCell>ID</TableCell>
              <TableCell>Short Name</TableCell>
              <TableCell>Full Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {faculties.map((f, index) => (
              <TableRow key={f.id}>
                <TableCell>{index + 1}</TableCell>
                <TableCell>{f.id}</TableCell>
                <TableCell>{f.shortname}</TableCell>
                <TableCell>{f.fullName}</TableCell>
                <TableCell>{f.email}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button variant="outlined" onClick={() => handleEdit(f)}>
                      Edit
                    </Button>
                    <Button color="error" variant="contained" onClick={() => handleDelete(f.id)}>
                      Remove
                    </Button>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ManageFaculties;
