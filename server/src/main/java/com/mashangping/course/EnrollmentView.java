package com.mashangping.course;

/** 成员出参：displayName 已激活取账号实名，待激活取导入快照 */
public record EnrollmentView(long id, String studentNo, String displayName, String status) {}
