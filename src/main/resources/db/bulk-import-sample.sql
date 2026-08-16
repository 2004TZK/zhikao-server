-- =============================================================
-- 结构化批量导入脚本（T3.5 兜底通道）
-- 用途：Excel/SQL 脚本批量导入知识点、成语、题目（与 P3.5 文档导入互补）
-- 说明：本脚本只插入 seed 级内容；生产内容必须走管理后台或导入系统并填来源
-- 注意：knowledge/idiom/question 的 id 采用自增，勿手工指定；重复执行会重复插入
-- =============================================================

USE zhikao;

-- 批量插入常识知识点示例（每分类至少 2 条 seed，来源=自建 source_type=1）
INSERT INTO knowledge (title, category_id, summary, content, key_points, common_mistakes, difficulty, status, source_type, source_title) VALUES
('科举制度概述', 4, '科举制起源于隋朝，是中国古代选拔官员的制度。', '科举制度始于隋朝，唐朝完善，清末废除，历时约 1300 年。分为乡试、会试、殿试等层级。', '["隋朝创立","唐朝完善","1905 年废除"]', '误以为科举创立于唐朝；误以为分封制是科举', 2, 1, 1, '自建'),
('中国地形特征', 5, '中国地势西高东低，呈三级阶梯分布。', '中国地形复杂多样，山地、高原、丘陵约占 2/3。第一阶梯为青藏高原，第二阶梯为内蒙古高原、黄土高原等，第三阶梯为东部平原丘陵。', '["三级阶梯","西高东低"]', '误以为东部高西部低', 2, 1, 1, '自建'),
('宪法基本原则', 2, '宪法是国家的根本法，具有最高法律效力。', '我国宪法规定国家根本制度和根本任务，是治国安邦的总章程。现行宪法为 1982 年宪法，历经多次修正。', '["根本法","1982 年宪法"]', '误以为宪法与普通法律效力相同', 2, 1, 1, '自建'),
('市场经济基本规律', 3, '价值规律是市场经济的基本规律。', '价值规律：商品的价值量由生产商品的社会必要劳动时间决定，商品交换以价值量为基础实行等价交换。', '["价值规律","社会必要劳动时间"]', '误以为供求决定价格（实际是影响价格）', 3, 1, 1, '自建');

-- 批量插入成语示例（来源=自建）
INSERT INTO idiom (word, pinyin, explanation, origin, example, difficulty, status, source_type, source_title) VALUES
('画蛇添足', 'huà shé tiān zú', '比喻做了多余的事，非但无益，反而不合适。', '《战国策·齐策二》', '事情已经完成，不要再画蛇添足。', 2, 1, 1, '自建'),
('守株待兔', 'shǒu zhū dài tù', '比喻死守狭隘经验，不知变通。', '《韩非子·五蠹》', '我们不能守株待兔，要主动创新。', 2, 1, 1, '自建'),
('亡羊补牢', 'wáng yáng bǔ láo', '比喻出了问题以后想办法补救，可以防止继续受损失。', '《战国策·楚策四》', '这次失败后及时亡羊补牢，还不算晚。', 2, 1, 1, '自建'),
('刻舟求剑', 'kè zhōu qiú jiàn', '比喻死守教条，拘泥成法，固执不知变通。', '《吕氏春秋·察今》', '形势变了，方法也要变，不能刻舟求剑。', 3, 1, 1, '自建');

-- 成语-分类关联（画蛇添足→高频/近义辨析；守株待兔→高频；亡羊补牢→高频/真题语境；刻舟求剑→近义辨析/常见误用）
-- 先清理本脚本产生的关联（保证脚本可重复执行不报错），再插入
DELETE r FROM idiom_category_rel r
JOIN idiom i ON r.idiom_id = i.id
WHERE i.word IN ('画蛇添足', '守株待兔', '亡羊补牢', '刻舟求剑');

INSERT INTO idiom_category_rel (idiom_id, category_id)
SELECT i.id, c.id FROM idiom i JOIN idiom_category c ON 1=1
WHERE (i.word = '画蛇添足' AND c.name IN ('高频成语', '近义辨析'))
   OR (i.word = '守株待兔' AND c.name = '高频成语')
   OR (i.word = '亡羊补牢' AND c.name IN ('高频成语', '真题语境'))
   OR (i.word = '刻舟求剑' AND c.name IN ('近义辨析', '常见误用'));

-- 批量插入题目示例（全部关联知识点；答案唯一）
INSERT INTO question (type, content, analysis, difficulty, knowledge_id, source_type, source_name, status) VALUES
(1, '科举制度正式创立于哪个朝代？', '科举制起源于隋朝，唐朝完善。', 2, (SELECT id FROM knowledge WHERE title = '科举制度概述' LIMIT 1), 3, '自编题', 1),
(1, '我国地势的整体特征是？', '我国地势西高东低，呈三级阶梯分布。', 2, (SELECT id FROM knowledge WHERE title = '中国地形特征' LIMIT 1), 3, '自编题', 1),
(1, '宪法的地位是？', '宪法是国家的根本法，具有最高法律效力。', 2, (SELECT id FROM knowledge WHERE title = '宪法基本原则' LIMIT 1), 3, '自编题', 1);

-- 对应选项（每题 4 个选项，唯一正确答案为 B）
INSERT INTO question_option (question_id, label, content, is_correct)
SELECT q.id, t.label, t.content, t.is_correct
FROM question q
JOIN (
    SELECT 'A' AS label, '唐朝' AS content, 0 AS is_correct UNION ALL
    SELECT 'B', '隋朝', 1 UNION ALL
    SELECT 'C', '宋朝', 0 UNION ALL
    SELECT 'D', '汉朝', 0
) t ON q.content = '科举制度正式创立于哪个朝代？'
WHERE NOT EXISTS (SELECT 1 FROM question_option o WHERE o.question_id = q.id AND o.label = t.label);

INSERT INTO question_option (question_id, label, content, is_correct)
SELECT q.id, t.label, t.content, t.is_correct
FROM question q
JOIN (
    SELECT 'A' AS label, '东高西低' AS content, 0 AS is_correct UNION ALL
    SELECT 'B', '西高东低', 1 UNION ALL
    SELECT 'C', '南北高中间低', 0 UNION ALL
    SELECT 'D', '中间高四周低', 0
) t ON q.content = '我国地势的整体特征是？'
WHERE NOT EXISTS (SELECT 1 FROM question_option o WHERE o.question_id = q.id AND o.label = t.label);

INSERT INTO question_option (question_id, label, content, is_correct)
SELECT q.id, t.label, t.content, t.is_correct
FROM question q
JOIN (
    SELECT 'A' AS label, '普通法律' AS content, 0 AS is_correct UNION ALL
    SELECT 'B', '国家的根本法', 1 UNION ALL
    SELECT 'C', '行政法规', 0 UNION ALL
    SELECT 'D', '地方法规', 0
) t ON q.content = '宪法的地位是？'
WHERE NOT EXISTS (SELECT 1 FROM question_option o WHERE o.question_id = q.id AND o.label = t.label);

-- =============================================================
-- 说明：以上为结构化批量导入示例（T3.5 验收：一次导入无重复无报错）。
-- 生产环境内容量目标见任务书 9.1（常识≥200、成语≥300、题目≥500），
-- 内容来源必须遵守 9.2 版权规范；本脚本仅为能力验证 seed。
-- =============================================================
