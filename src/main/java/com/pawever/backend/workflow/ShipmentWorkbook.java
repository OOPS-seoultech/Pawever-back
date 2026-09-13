package com.pawever.backend.workflow;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ShipmentWorkbook {
  public byte[] create(List<List<String>> rows) {
    try (var workbook = new XSSFWorkbook();
        var bytes = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("준등기");
      var style = workbook.createCellStyle();
      style.setDataFormat(workbook.createDataFormat().getFormat("@"));
      for (int r = 0; r < rows.size(); r++) {
        var row = sheet.createRow(r);
        for (int c = 0; c < 6; c++) {
          var cell = row.createCell(c, CellType.STRING);
          cell.setCellStyle(style);
          cell.setCellValue(rows.get(r).get(c));
        }
      }
      workbook.write(bytes);
      return bytes.toByteArray();
    } catch (java.io.IOException e) {
      throw new WorkflowException(503, "EXPORT_FAILED", "파일을 생성하지 못했습니다. 포장 상태는 바뀌지 않았습니다.");
    }
  }
}
