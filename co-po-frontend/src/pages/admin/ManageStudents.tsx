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
import { getStudents, createStudent, updateStudent, deleteStudent, importStudents } from '../../api/admin';
import type { Student } from '../../api/admin';
import BulkImportButton from '../../components/BulkImportButton';

const emptyForm: Student = { id: '', batch: 2026, name: '', email: '', department: 'CSE', programme: 'BSC' };

const ManageStudents = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [form, setForm] = useState<Student>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);

  const fetchStudents = async () => {
    try {
      const response = await getStudents();
      setStudents(response.data);
    } catch (error) {
      console.error('Failed to fetch students', error);
    }
  };

  useEffect(() => {
    fetchStudents();
  }, []);

  const handleSave = async () => {
    try {
      if (editingId) {
        await updateStudent(editingId, { ...form, batch: Number(form.batch) });
      } else {
        await createStudent({ ...form, batch: Number(form.batch) });
      }
      setForm(emptyForm);
      setEditingId(null);
      fetchStudents();
    } catch (error) {
      console.error('Failed to save student', error);
    }
  };

  const handleEdit = (student: Student) => {
    setForm(student);
    setEditingId(student.id);
  };

  const handleCancelEdit = () => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteStudent(id);
      if (editingId === id) handleCancelEdit();
      fetchStudents();
    } catch (error) {
      console.error('Failed to delete student', error);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Students</Typography>
      
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(6, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="ID"
            value={form.id}
            onChange={(e) => setForm({ ...form, id: e.target.value })}
            size="small"
            disabled={Boolean(editingId)}
          />
          <TextField label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} size="small" />
          <TextField
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            size="small"
          />
          <TextField
            label="Batch"
            type="number"
            value={form.batch}
            onChange={(e) => setForm({ ...form, batch: Number(e.target.value) })}
            size="small"
          />
          <TextField
            label="Department"
            value={form.department}
            onChange={(e) => setForm({ ...form, department: e.target.value })}
            size="small"
          />
          <TextField
            label="Programme"
            value={form.programme}
            onChange={(e) => setForm({ ...form, programme: e.target.value })}
            size="small"
          />
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleSave}>
            {editingId ? 'Update Student' : 'Add Student'}
          </Button>
          {editingId && (
            <Button variant="outlined" onClick={handleCancelEdit}>
              Cancel
            </Button>
          )}
          <Button variant="outlined" disabled>
            Get Excel Template
          </Button>
          <BulkImportButton label="Bulk Import (Excel)" onImport={importStudents} onComplete={fetchStudents} />
        </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>S/N</TableCell>
              <TableCell>ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Batch</TableCell>
              <TableCell>Dept/Prog</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {students.map((s, index) => (
              <TableRow key={s.id}>
                <TableCell>{index + 1}</TableCell>
                <TableCell>{s.id}</TableCell>
                <TableCell>{s.name}</TableCell>
                <TableCell>{s.email}</TableCell>
                <TableCell>{s.batch}</TableCell>
                <TableCell>{s.department} / {s.programme}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button variant="outlined" onClick={() => handleEdit(s)}>
                      Edit
                    </Button>
                    <Button color="error" variant="contained" onClick={() => handleDelete(s.id)}>
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

export default ManageStudents;
