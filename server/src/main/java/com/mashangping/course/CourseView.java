package com.mashangping.course;

/** 课程出参视图（不含审计字段以外内容，后续按需扩展成员数等统计） */
public record CourseView(long id, String name, String term, String description) {

    public static CourseView from(Course c) {
        return new CourseView(c.getId(), c.getName(), c.getTerm(), c.getDescription());
    }
}
