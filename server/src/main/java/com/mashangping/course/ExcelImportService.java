package com.mashangping.course;

import com.alibaba.excel.EasyExcel;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 学号-姓名两列名单导入：部分成功模式，坏行明细随响应返回。 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final int MAX_ROWS = 500;

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public record RowFailure(int row, String reason) {}

    public record ImportResult(int totalRows, int activated, int pending, int skipped,
                               List<RowFailure> failures) {}

    public ImportResult importStudents(long teacherUid, long courseId, MultipartFile file) {
        courseService.getOwned(teacherUid, courseId);
        List<List<String>> grid = parseGrid(file);

        int activated = 0;
        int pending = 0;
        int skipped = 0;
        List<RowFailure> failures = new ArrayList<>();
        Set<String> seenNos = new HashSet<>();

        for (int i = 0; i < grid.size(); i++) {
            List<String> row = grid.get(i);
            int excelRow = i + 2; // 第1行是表头，Excel 展示行号从2开始计数据行
            String no = cell(row, 0);
            String name = cell(row, 1);
            if (no.isEmpty() && name.isEmpty()) {
                continue; // 整行为空静默忽略（常见于尾部空行）
            }
            EnrollmentService.ProcessOutcome outcome =
                    enrollmentService.processSingle(courseId, no, name);
            switch (outcome.kind()) {
                case ACTIVATED -> activated++;
                case PENDING -> pending++;
                case SKIPPED_DUPLICATE -> skipped++;
                case FAILED -> failures.add(new RowFailure(excelRow, outcome.failReason()));
            }
            if (outcome.kind() == EnrollmentService.ProcessOutcome.Kind.ACTIVATED
                    || outcome.kind() == EnrollmentService.ProcessOutcome.Kind.PENDING) {
                seenNos.add(no.trim()); // 记录成功行，供文件内去重提示
            } else if (!no.isBlank() && !seenNos.add(no.trim())
                    && outcome.kind() == EnrollmentService.ProcessOutcome.Kind.SKIPPED_DUPLICATE) {
                // 已是本班成员或文件内重复——SKIPPED 语义已覆盖，无需额外动作
            }
        }
        return new ImportResult(grid.size(), activated, pending, skipped,
                List.copyOf(failures));
    }

    private String cell(List<String> row, int idx) {
        return row != null && row.size() > idx && row.get(idx) != null
                ? row.get(idx).trim() : "";
    }

    /** 读为纯二维字符串网格：第0行须为 [学号, 姓名]，否则 40014 */
    private List<List<String>> parseGrid(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (file.isEmpty() || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR);
        }
        List<List<String>> grid = new ArrayList<>();
        try (var in = file.getInputStream()) {
            // headRowNumber(0)：不把任何物理行当表头吞掉，第0行交由上方网格校验
            EasyExcel.read(in, new AnalysisEventListenerAdapter(grid))
                    .sheet().headRowNumber(0).doRead();
        } catch (IOException | RuntimeException e) {
            // EasyExcel 对损坏/伪造文件抛运行时异常，统一按格式错误表达
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR);
        }
        if (grid.isEmpty() || grid.size() > MAX_ROWS + 1) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR);
        }
        List<String> header = grid.get(0);
        if (!"学号".equals(cell(header, 0)) || !"姓名".equals(cell(header, 1))) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR);
        }
        return grid.subList(1, grid.size());
    }

    /** 逐行收集为字符串网格的小适配器（读完为止，不建模） */
    @RequiredArgsConstructor
    static class AnalysisEventListenerAdapter
            extends com.alibaba.excel.event.AnalysisEventListener<Map<Integer, String>> {

        private final List<List<String>> grid;

        /** EasyExcel 无模型读取按 Map<列号,单元格文本> 交付行数据，这里转为定长字符串网格 */
        @Override
        public void invoke(Map<Integer, String> row, com.alibaba.excel.context.AnalysisContext ctx) {
            int lastCol = row.keySet().stream().max(Integer::compareTo).orElse(-1);
            List<String> cells = new ArrayList<>(lastCol + 1);
            for (int c = 0; c <= lastCol; c++) {
                cells.add(row.get(c));
            }
            grid.add(cells);
        }

        @Override
        public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext ctx) {
            // no-op
        }
    }
}
