import { useState } from 'react';
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
import { getQuestions, createQuestion, deleteQuestion } from '../../api/faculty';
import type { AssessmentQuestion } from '../../api/faculty';

const ManageAssessments = () => {
  const [assessmentId, setAssessmentId] = useState<number>(1); // Dummy default assessment ID
  const [questions, setQuestions] = useState<AssessmentQuestion[]>([]);
  const [form, setForm] = useState<AssessmentQuestion>({ assessmentId: 1, title: '', marks: 0, coId: undefined, poId: undefined });

  const fetchQuestions = async () => {
    try {
      const response = await getQuestions(assessmentId);
      setQuestions(response.data);
    } catch (error) {
      console.error('Failed to fetch questions', error);
    }
  };

  const handleAdd = async () => {
    try {
      await createQuestion({ ...form, assessmentId });
      setForm({ ...form, title: '', marks: 0 }); // reset form
      fetchQuestions();
    } catch (error) {
      console.error('Failed to add question', error);
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) return;
    try {
      await deleteQuestion(id);
      fetchQuestions();
    } catch (error) {
      console.error('Failed to delete question', error);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Course Questions</Typography>
      
      <Paper sx={{ p: 2, mb: 2 }}>
      <Box sx={{ mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField 
          label="Assessment ID" 
          type="number" 
          value={assessmentId} 
          onChange={(e) => setAssessmentId(Number(e.target.value))} 
          size="small" 
        />
        <Button variant="outlined" onClick={fetchQuestions}>Load Questions</Button>
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
        <TextField label="Question Title (e.g. 1a)" value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} size="small" />
        <TextField label="Marks" type="number" value={form.marks} onChange={(e) => setForm({...form, marks: Number(e.target.value)})} size="small" />
        <TextField label="CO ID (opt)" type="number" value={form.coId || ''} onChange={(e) => setForm({...form, coId: Number(e.target.value)})} size="small" />
        <TextField label="PO ID (opt)" type="number" value={form.poId || ''} onChange={(e) => setForm({...form, poId: Number(e.target.value)})} size="small" />
      </Box>
      <Box sx={{ mt: 2 }}>
        <Button variant="contained" onClick={handleAdd}>Add Question</Button>
      </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell>
              <TableCell>Marks</TableCell>
              <TableCell>CO MAP</TableCell>
              <TableCell>PO MAP</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {questions.map((q) => (
              <TableRow key={q.id}>
                <TableCell>{q.title}</TableCell>
                <TableCell>{q.marks}</TableCell>
                <TableCell>{q.coId || '-'}</TableCell>
                <TableCell>{q.poId || '-'}</TableCell>
                <TableCell>
                  <Button color="error" onClick={() => handleDelete(q.id)}>Delete</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default ManageAssessments;