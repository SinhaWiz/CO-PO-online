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
import { getFaculties, createFaculty, deleteFaculty, importFaculties } from '../../api/admin';
import type { Faculty } from '../../api/admin';
import BulkImportButton from '../../components/BulkImportButton';

const ManageFaculties = () => {
  const [faculties, setFaculties] = useState<Faculty[]>([]);
  const [form, setForm] = useState<Faculty>({ id: '', shortname: '', fullName: '', email: '', password: '' });

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

  const handleAdd = async () => {
    try {
      await createFaculty(form);
      setForm({ id: '', shortname: '', fullName: '', email: '', password: '' });
      fetchFaculties();
    } catch (error) {
      console.error('Failed to add faculty', error);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteFaculty(id);
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
          <TextField label="ID" value={form.id} onChange={(e) => setForm({ ...form, id: e.target.value })} size="small" />
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
            label="Password"
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            size="small"
          />
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
          <Button variant="contained" onClick={handleAdd}>
            Add Faculty
          </Button>
          <Button variant="outlined" disabled>
            Edit Faculty
          </Button>
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
                  <Button color="error" variant="contained" onClick={() => handleDelete(f.id)}>
                    Remove Faculty
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

export default ManageFaculties;
