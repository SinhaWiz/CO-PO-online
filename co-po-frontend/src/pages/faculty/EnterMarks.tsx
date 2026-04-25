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
import { getMarks, saveMarks } from '../../api/faculty';
import type { StudentAssessmentMarks } from '../../api/faculty';

const EnterMarks = () => {
  const [questionId, setQuestionId] = useState<number>(1);
  const [marks, setMarks] = useState<StudentAssessmentMarks[]>([]);
  const [form, setForm] = useState<StudentAssessmentMarks>({ studentId: '', questionId: 1, marksObtained: 0 });

  const fetchMarks = async () => {
    try {
      const response = await getMarks(questionId);
      setMarks(response.data);
    } catch (error) {
      console.error('Failed to fetch marks', error);
    }
  };

  const handleSave = async () => {
    try {
      await saveMarks({ ...form, questionId });
      setForm({ ...form, studentId: '', marksObtained: 0 });
      fetchMarks();
    } catch (error) {
      console.error('Failed to save mark', error);
    }
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Student Marks</Typography>
      
      <Paper sx={{ p: 2, mb: 2 }}>
      <Box sx={{ mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField 
          label="Question ID" 
          type="number" 
          value={questionId} 
          onChange={(e) => setQuestionId(Number(e.target.value))} 
          size="small" 
        />
        <Button variant="outlined" onClick={fetchMarks}>Load Marks</Button>
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, gap: 1.5 }}>
        <TextField label="Student ID (Roll)" value={form.studentId} onChange={(e) => setForm({...form, studentId: e.target.value})} size="small" />
        <TextField label="Marks Obtained" type="number" value={form.marksObtained} onChange={(e) => setForm({...form, marksObtained: Number(e.target.value)})} size="small" />
      </Box>
      <Box sx={{ mt: 2 }}>
        <Button variant="contained" onClick={handleSave}>Save/Update Mark</Button>
      </Box>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Database ID</TableCell>
              <TableCell>Student ID</TableCell>
              <TableCell>Question ID</TableCell>
              <TableCell>Marks Obtained</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {marks.map((m) => (
              <TableRow key={m.id}>
                <TableCell>{m.id}</TableCell>
                <TableCell>{m.studentId}</TableCell>
                <TableCell>{m.questionId}</TableCell>
                <TableCell>{m.marksObtained}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default EnterMarks;