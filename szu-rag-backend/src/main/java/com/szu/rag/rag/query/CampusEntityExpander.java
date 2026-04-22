package com.szu.rag.rag.query;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 校园实体词典 —— 将口语化表达替换为标准术语，提升 embedding 召回率
 * 使用 LinkedHashMap 保证替换顺序（长词优先匹配）
 */
@Component
public class CampusEntityExpander {

    private static final LinkedHashMap<String, String> ENTITY_MAP = new LinkedHashMap<>();

    static {
        // 校区别名（长词优先）
        ENTITY_MAP.put("深圳大学丽湖校区", "深圳大学丽湖校区");
        ENTITY_MAP.put("深圳大学粤海校区", "深圳大学粤海校区");
        ENTITY_MAP.put("深圳大学沧海校区", "深圳大学沧海校区");
        ENTITY_MAP.put("深圳大学罗湖校区", "深圳大学罗湖校区");
        ENTITY_MAP.put("丽湖校区", "深圳大学丽湖校区");
        ENTITY_MAP.put("粤海校区", "深圳大学粤海校区");
        ENTITY_MAP.put("沧海校区", "深圳大学沧海校区");
        ENTITY_MAP.put("罗湖校区", "深圳大学罗湖校区");
        ENTITY_MAP.put("荔园", "深圳大学");
        ENTITY_MAP.put("荔天", "深圳大学丽湖校区");
        ENTITY_MAP.put("粤海", "深圳大学粤海校区");
        ENTITY_MAP.put("沧海", "深圳大学沧海校区");
        ENTITY_MAP.put("后海", "深圳大学粤海校区");
        ENTITY_MAP.put("罗湖", "深圳大学罗湖校区");

        // 考试别名
        ENTITY_MAP.put("四六级", "全国大学英语四六级考试 CET");
        ENTITY_MAP.put("英语六级", "大学英语六级考试 CET-6");
        ENTITY_MAP.put("英语四级", "大学英语四级考试 CET-4");
        ENTITY_MAP.put("六级", "大学英语六级考试 CET-6");
        ENTITY_MAP.put("四级", "大学英语四级考试 CET-4");
        ENTITY_MAP.put("雅思", "IELTS 雅思考试");
        ENTITY_MAP.put("托福", "TOEFL 托福考试");
        ENTITY_MAP.put("考研", "全国硕士研究生招生考试");
        ENTITY_MAP.put("国考", "国家公务员考试");
        ENTITY_MAP.put("教资", "中小学教师资格考试");

        // 教务术语
        ENTITY_MAP.put("选课", "本科生选课");
        ENTITY_MAP.put("退课", "本科生退课 补退选");
        ENTITY_MAP.put("教务", "教务部");
        ENTITY_MAP.put("挂科", "课程不及格 补考 重修");
        ENTITY_MAP.put("综测", "综合测评");
        ENTITY_MAP.put("学分", "学分认定 学分转换");
        ENTITY_MAP.put("绩点", "GPA 绩点计算");
        ENTITY_MAP.put("保研", "推荐免试攻读硕士研究生");
        ENTITY_MAP.put("毕业论文", "本科毕业论文 学位论文");
        ENTITY_MAP.put("答辩", "毕业论文答辩");
        ENTITY_MAP.put("转专业", "本科生转专业 申请条件 考核方式");
        ENTITY_MAP.put("辅修", "辅修学位 双学位");
        ENTITY_MAP.put("休学", "学生休学 复学");
        ENTITY_MAP.put("退学", "学生退学");

        // 校园生活
        ENTITY_MAP.put("宿舍报修", "学生宿舍维修报修");
        ENTITY_MAP.put("校园卡", "校园卡 读者证 一卡通");
        ENTITY_MAP.put("食堂", "餐厅 食堂 荔山餐厅 听荔餐厅");
        ENTITY_MAP.put("校巴", "校园巴士 校内接驳车");
        ENTITY_MAP.put("图书馆", "图书馆 馆藏 借阅");
        ENTITY_MAP.put("体育馆", "体育场馆 运动场");
        ENTITY_MAP.put("宿舍", "学生宿舍 住宿");
        ENTITY_MAP.put("校医院", "校医院 医疗保健中心");
        ENTITY_MAP.put("快递", "快递驿站 菜鸟驿站");

        // 办事流程
        ENTITY_MAP.put("请假", "学生请假 销假");
        ENTITY_MAP.put("奖学金", "奖学金评定 国家奖学金 校级奖学金");
        ENTITY_MAP.put("助学金", "助学金 国家助学金");
        ENTITY_MAP.put("贷款", "助学贷款 生源地信用助学贷款");
        ENTITY_MAP.put("医保", "学生医疗保险 大学生医保");
        ENTITY_MAP.put("户口", "集体户口 户口迁移");
        ENTITY_MAP.put("兵役", "大学生征兵 入伍 学籍保留");

        // 部门别名
        ENTITY_MAP.put("学工", "学生部");
        ENTITY_MAP.put("后勤", "后勤保障部");
        ENTITY_MAP.put("信息中心", "信息中心 信息化建设处");
        ENTITY_MAP.put("财务", "财务部");
        ENTITY_MAP.put("招生办", "招生办公室");
        ENTITY_MAP.put("就业", "就业指导中心");
        ENTITY_MAP.put("团委", "共青团深圳大学委员会");
        ENTITY_MAP.put("教务处", "教务部");
    }

    /**
     * 将口语化表达替换为标准术语
     */
    public String expand(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        String expanded = query;
        for (Map.Entry<String, String> entry : ENTITY_MAP.entrySet()) {
            if (expanded.contains(entry.getKey())) {
                expanded = expanded.replace(entry.getKey(), entry.getValue());
            }
        }
        return expanded;
    }
}
