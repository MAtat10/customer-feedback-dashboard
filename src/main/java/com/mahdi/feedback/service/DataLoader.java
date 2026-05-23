package com.mahdi.feedback.service;

import com.mahdi.feedback.model.Feedback;
import com.mahdi.feedback.repository.FeedbackRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * On startup, parses the bundled xlsx and inserts rows into H2.
 * Idempotent: skipped if data is already present.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final FeedbackRepository repository;
    private final ThemeClassifier classifier;
    private final ResourceLoader resourceLoader;
    private final String datasetPath;

    public DataLoader(FeedbackRepository repository,
                      ThemeClassifier classifier,
                      ResourceLoader resourceLoader,
                      @Value("${feedback.dataset.path}") String datasetPath) {
        this.repository = repository;
        this.classifier = classifier;
        this.resourceLoader = resourceLoader;
        this.datasetPath = datasetPath;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            log.info("Feedback table already populated ({} rows) — skipping load.", repository.count());
            return;
        }

        Resource resource = resourceLoader.getResource(datasetPath);
        if (!resource.exists()) {
            log.warn("Dataset not found at {} — skipping load.", datasetPath);
            return;
        }

        log.info("Loading feedback dataset from {}", datasetPath);
        try (InputStream is = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Feedback> batch = new ArrayList<>();
            boolean headerSkipped = false;

            for (Row row : sheet) {
                if (!headerSkipped) { headerSkipped = true; continue; }
                if (row == null) continue;

                Cell tsCell = row.getCell(0);
                Cell svcCell = row.getCell(1);
                Cell txtCell = row.getCell(2);
                Cell sentCell = row.getCell(3);
                if (tsCell == null || svcCell == null || txtCell == null) continue;

                LocalDateTime ts = parseTimestamp(tsCell);
                String service = stringValue(svcCell);
                String text = stringValue(txtCell);
                String sentiment = sentCell == null ? "Neutral" : stringValue(sentCell);

                if (service.isBlank() || text.isBlank()) continue;

                Feedback f = new Feedback();
                f.setTimestamp(ts);
                f.setService(service.trim());
                f.setText(text.trim());
                f.setSentiment(sentiment.trim());
                f.setThemes(classifier.classifyAsCsv(text));
                batch.add(f);
            }

            repository.saveAll(batch);
            log.info("Loaded {} feedback rows.", batch.size());
        }
    }

    private static LocalDateTime parseTimestamp(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        String s = stringValue(cell);
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String stringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
