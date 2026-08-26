package com.mashangping.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.common.LikeUtils;
import com.mashangping.course.dto.CourseUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;

    public Course create(long teacherUid, CourseUpsertRequest request) {
        Course c = new Course();
        c.setName(request.name());
        c.setTerm(request.term());
        c.setDescription(request.description());
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        return c;
    }

    public Page<CourseView> listMine(long teacherUid, int page, int size, String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherUid)
                .like(keyword != null && !keyword.isBlank(), Course::getName,
                        LikeUtils.escapeForLike(keyword.trim()))
                .orderByDesc(Course::getId);
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return (Page<CourseView>) result.convert(CourseView::from);
    }

    /** 归属链校验的唯一入口：不存在或非属主一律 40400「课程不存在」 */
    public Course getOwned(long teacherUid, long courseId) {
        Course c = courseMapper.selectById(courseId);
        if (c == null || c.getTeacherId() == null || c.getTeacherId() != teacherUid) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return c;
    }

    public void update(long teacherUid, long courseId, CourseUpsertRequest request) {
        Course c = getOwned(teacherUid, courseId);
        c.setName(request.name());
        c.setTerm(request.term());
        c.setDescription(request.description());
        courseMapper.updateById(c);
    }
}
