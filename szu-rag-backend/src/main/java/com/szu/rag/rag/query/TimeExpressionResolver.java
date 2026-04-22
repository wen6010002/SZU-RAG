package com.szu.rag.rag.query;

import com.szu.rag.rag.calendar.CampusCalendarService;
import com.szu.rag.rag.calendar.model.entity.CampusCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 时间表达式解析器 —— 将"这学期"/"下学期"/"开学初"等口语化时间表达转换为具体日期
 */
@Component
@RequiredArgsConstructor
public class TimeExpressionResolver {

    private final CampusCalendarService calendarService;

    public String resolve(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        CampusCalendar current = calendarService.getCurrentSemester();
        CampusCalendar next = calendarService.getNextSemester();

        String resolved = query;

        if (current != null) {
            String currentLabel = current.getAcademicYear() + "学年" + current.getSemester();
            resolved = resolved.replace("这学期", currentLabel);
            resolved = resolved.replace("本学期", currentLabel);
            resolved = resolved.replace("开学初", current.getStartDate().toString());
            resolved = resolved.replace("学期初", current.getStartDate().toString());

            // "期末" → "期末考试周"（但不要误替换"期中"）
            if (resolved.contains("期末") && !resolved.contains("期末考试")) {
                resolved = resolved.replace("期末", "期末考试周");
            }
        }

        if (next != null) {
            String nextLabel = next.getAcademicYear() + "学年" + next.getSemester();
            resolved = resolved.replace("下学期", nextLabel);
        }

        // 相对时间表达式
        LocalDate today = LocalDate.now();
        resolved = resolved.replace("今天", today.toString());
        resolved = resolved.replace("这周", "本周（" + today + "所在周）");

        return resolved;
    }
}
