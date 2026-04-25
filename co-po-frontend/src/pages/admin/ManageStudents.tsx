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
import { getStudents, createStudent, deleteStudent } from '../../api/admin';
import type { Student } from '../../api/admin';

const ManageStudents = () => {
  const [students, setStudents] = useState<Student[]>([]);
  const [form, setForm] = useState<Student>({ 
    id: '', batch: 2026, name: '', email: '', department: 'CSE', programme: 'BSC' 
  });

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

  const handleAdd = async () => {
    try {
      await createStudent({ ...form, batch: Number(form.batch) });
      setForm({ ...form, id: '', name: '', email: '' }); // reset some fields
      fetchStudents();
    } catch (error) {
      console.error('Failed to add student', error);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteStudent(id);
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
          <TextField label="ID" value={form.id} onChange={(e) => setForm({ ...form, id: e.target.value })} size="small" />
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
          <Button variant="contained" onClick={handleAdd}>
            Add Student
          </Button>
          <Button variant="outlined" disabled>
            Edit Student
          </Button>
          <Button variant="outlined" disabled>
            Get Excel Template
          </Button>
          <Button variant="outlined" disabled>
            Bulk Import (Excel)
          </Button>
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
                  <Button color="error" variant="contained" onClick={() => handleDelete(s.id)}>
                    Remove Student
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

export default ManageStudents;
