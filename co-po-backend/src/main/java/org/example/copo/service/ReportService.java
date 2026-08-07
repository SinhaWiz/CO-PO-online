package org.example.copo.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.copo.entity.Course;
import org.example.copo.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final CourseRepository courseRepository;

    public byte[] generateCourseExcelReport() throws IOException {
        List<Course> courses = courseRepository.findAll();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Courses Report");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Course Code");
            headerRow.createCell(1).setCellValue("Programme");
            headerRow.createCell(2).setCellValue("Name");
            headerRow.createCell(3).setCellValue("Credits");
            headerRow.createCell(4).setCellValue("Department");

            int rowNum = 1;
            for (Course course : courses) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(course.getCourseCode());
                row.createCell(1).setCellValue(course.getProgramme());
                row.createCell(2).setCellValue(course.getCourseName());
                row.createCell(3).setCellValue(course.getCredits());
                row.createCell(4).setCellValue(course.getDepartment());
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateCoursePdfReport() throws IOException {
        List<Course> courses = courseRepository.findAll();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            
            document.add(new Paragraph("CO-PO Assessment Course Report").setFontSize(18f));
            for (Course course : courses) {
                document.add(new Paragraph(String.format("%s (%s): %s | %s | %.1f Credits", 
                    course.getCourseCode(), course.getProgramme(), course.getCourseName(), 
                    course.getDepartment(), course.getCredits())));
            }
            document.close();
            return out.toByteArray();
        }
    }

    // Ports the desktop app's "Summarize Feedbacks" button from the Summary Report
    // screen. Desktop called an external BYOK service (ApiFree.ai) with a faculty-
    // supplied API key pasted into the UI - reoriented here to reuse the Vertex AI
    // pathway generateCourseSummaryWithAI already established instead of introducing a
    // second, unrelated third-party AI integration and a new credential the app would
    // have to handle. Same graceful fallback if GCP isn't configured, same
    // project/location params - proper secrets/config management for this is phase
    // 10.2's job, not this one's.
    public String summarizeFeedback(String rawFeedback, String projectId, String location) throws Exception {
        String prompt = "Summarize the following student feedback for a university course in one concise "
            + "paragraph, highlighting the main themes, what students appreciated, and areas for improvement:\n\n"
            + rawFeedback;

        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerativeModel model = new GenerativeModel("gemini-1.5-pro", vertexAI);
            GenerateContentResponse response = model.generateContent(
                ContentMaker.fromMultiModalData(
                    PartMaker.fromMimeTypeAndData("text/plain", prompt.getBytes())
                )
            );
            return response.getCandidates(0).getContent().getParts(0).getText();
        } catch (Exception e) {
            return "AI summarization failed (likely GCP unauthenticated or missing endpoint). Raw feedback was: \n\n" + rawFeedback;
        }
    }

    public String generateCourseSummaryWithAI(String projectId, String location) throws Exception {
        List<Course> courses = courseRepository.findAll();
        StringBuilder payload = new StringBuilder("Summarize the following academic courses:\n");
        for (Course c : courses) {
            payload.append("- ").append(c.getCourseName()).append(" (").append(c.getCourseCode()).append(")\n");
        }

        // Placeholder for Gemini usage
        // Make sure you have proper GCP credentials when deploying this in real environments.
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            GenerativeModel model = new GenerativeModel("gemini-1.5-pro", vertexAI);
            GenerateContentResponse response = model.generateContent(
                ContentMaker.fromMultiModalData(
                    PartMaker.fromMimeTypeAndData("text/plain", payload.toString().getBytes())
                )
            );
            return response.getCandidates(0).getContent().getParts(0).getText();
        } catch (Exception e) {
            // Fallback for demonstration since we might not have GCP configured right now
            return "AI Generation failed (likely GCP unauthenticated or missing endpoint). Payload would have been: \n\n" + payload.toString();
        }
    }
}
