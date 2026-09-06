-- 计划10 判题明细回显：逐点「实际输出」（WA 样例点诊断「你的输出」；教师属主隐藏点完整视图）。
-- 仅新增一列可空，不动历史数据；判题未启用时该列为 NULL，读侧照常工作。
ALTER TABLE `judge_detail`
    ADD COLUMN `actual_output` MEDIUMTEXT NULL
        COMMENT '该测试点程序实际输出尾段(≤2000)；WA 时写回，样例点学生可读，隐藏点仅教师属主可读' AFTER `message`;
