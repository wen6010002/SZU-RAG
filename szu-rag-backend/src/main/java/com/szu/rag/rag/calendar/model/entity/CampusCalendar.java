package com.szu.rag.rag.calendar.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_campus_calendar")
public class CampusCalendar {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String academicYear;
    private String semester;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer weekCount;
    private String eventName;
    private String eventType;
    private LocalDate eventStart;
    private LocalDate eventEnd;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
