// =============================================================
// 内容批量生成器（C.1~C.3 生产内容通道）
// 读取 scripts/content-data/*.js 数据 → 生成结构化 SQL（幂等）
// 用法：node scripts/generate-content.js
// 输出：scripts/content-data/content-seed.sql
// 说明：题目为自编题（source_type=3），全部关联知识点或成语；
//       正确选项位置随机；重复执行会先清理再插入（幂等）。
// =============================================================
const fs = require('fs');
const path = require('path');

const DATA_DIR = path.join(__dirname, 'content-data');

// ---- 读取数据 ----
const idioms = [];
for (const f of ['idioms-part1.js', 'idioms-part2.js']) {
  const arr = require(path.join(DATA_DIR, f));
  for (const it of arr) idioms.push(it);
}
const knowledge = [];
for (const f of ['knowledge-part1.js', 'knowledge-part2.js', 'knowledge-part3.js']) {
  const arr = require(path.join(DATA_DIR, f));
  for (const it of arr) knowledge.push(it);
}

// 去重（成语按 word，知识点按 title）
const seenI = new Set();
const uniqIdioms = idioms.filter(i => !seenI.has(i.word) && seenI.add(i.word));
const seenK = new Set();
const uniqKnowledge = knowledge.filter(k => !seenK.has(k.title) && seenK.add(k.title));

// ---- SQL 转义 ----
const esc = s => String(s == null ? '' : s).replace(/'/g, "''");

// ---- 随机工具 ----
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
function pick(arr, n, exclude) {
  const pool = arr.filter(x => x !== exclude);
  const sh = shuffle(pool);
  return sh.slice(0, n);
}

// ---- 分类 id 映射（政治1 法律2 经济3 历史4 地理5 科技6 文化7 生态8 生活9）----
const CAT = { '政治': 1, '法律': 2, '经济': 3, '历史': 4, '地理': 5, '科技': 6, '文化': 7, '生态': 8, '生活': 9 };

// ---- 生成 ----
const out = [];
out.push('-- =============================================================');
out.push('-- 内容生产种子数据（由 scripts/generate-content.js 自动生成）');
out.push('-- 常识 ' + uniqKnowledge.length + ' 条 / 成语 ' + uniqIdioms.length + ' 条 / 题目（自编）');
out.push('-- 生成时间：' + new Date().toISOString());
out.push('-- 幂等：重复执行先清理 source_name=\'内容自编题\' 再插入');
out.push('-- =============================================================');
out.push('USE zhikao;');
out.push('SET NAMES utf8mb4;');
out.push('');

// 清理旧数据（幂等）
out.push('-- 清理本脚本产生的旧数据（保持幂等）');
out.push("DELETE FROM question_option WHERE question_id IN (SELECT id FROM question WHERE source_name = '内容自编题');");
out.push("DELETE FROM question WHERE source_name = '内容自编题';");
out.push('DELETE r FROM idiom_category_rel r JOIN idiom i ON r.idiom_id = i.id WHERE i.word IN (' +
  uniqIdioms.map(i => "'" + esc(i.word) + "'").join(',') + ');');
out.push('DELETE FROM idiom WHERE word IN (' + uniqIdioms.map(i => "'" + esc(i.word) + "'").join(',') + ');');
out.push('DELETE FROM knowledge WHERE title IN (' + uniqKnowledge.map(k => "'" + esc(k.title) + "'").join(',') + ');');
out.push('');

// ---- 常识知识点 ----
out.push('-- ================= 常识知识点（' + uniqKnowledge.length + ' 条） =================');
const kValues = uniqKnowledge.map(k => {
  const catId = CAT[k.category] || 1;
  return "('" + esc(k.title) + "', " + catId + ", '" + esc(k.summary) + "', '" + esc(k.content) + "', '" +
    esc(k.keyPoints || '[]') + "', '" + esc(k.commonMistakes || '') + "', " + (k.difficulty || 3) + ", 1, 1, '内容自编-公考常识整理')";
});
out.push('INSERT INTO knowledge (title, category_id, summary, content, key_points, common_mistakes, difficulty, status, source_type, source_title) VALUES');
out.push(kValues.join(',\n') + ';');
out.push('');

// ---- 成语 ----
out.push('-- ================= 成语（' + uniqIdioms.length + ' 条） =================');
const iValues = uniqIdioms.map(i => {
  return "('" + esc(i.word) + "', '" + esc(i.pinyin || '') + "', '" + esc(i.explanation || '') + "', '" +
    esc(i.origin || '') + "', '" + esc(i.example || '') + "', '" + esc(i.synonyms || '[]') + "', '" +
    esc(i.antonyms || '[]') + "', '" + esc(i.confusing || '') + "', '" + esc(i.common_error || '') + "', " +
    (i.difficulty || 3) + ", 1, 1, '内容自编-公考成语整理')";
});
out.push('INSERT INTO idiom (word, pinyin, explanation, origin, example, synonyms, antonyms, confusing, common_error, difficulty, status, source_type, source_title) VALUES');
out.push(iValues.join(',\n') + ';');
out.push('');

// 成语分类关联：高频成语=1、易错成语=2、近义辨析=3、常见误用=4、真题语境=5
out.push('-- 成语-分类关联');
out.push('INSERT INTO idiom_category_rel (idiom_id, category_id)');
out.push("SELECT i.id, 1 FROM idiom i WHERE i.word IN (" + uniqIdioms.map(x => "'" + esc(x.word) + "'").join(',') + ');');
const withAntonyms = uniqIdioms.filter(i => i.antonyms && i.antonyms !== '[]' && i.antonyms !== '');
const withConfusing = uniqIdioms.filter(i => i.confusing && i.confusing.trim() !== '');
if (withAntonyms.length) {
  out.push('INSERT INTO idiom_category_rel (idiom_id, category_id)');
  out.push("SELECT i.id, 3 FROM idiom i WHERE i.word IN (" + withAntonyms.map(x => "'" + esc(x.word) + "'").join(',') + ')');
  out.push('  AND NOT EXISTS (SELECT 1 FROM idiom_category_rel r WHERE r.idiom_id = i.id AND r.category_id = 3);');
}
if (withConfusing.length) {
  out.push('INSERT INTO idiom_category_rel (idiom_id, category_id)');
  out.push("SELECT i.id, 4 FROM idiom i WHERE i.word IN (" + withConfusing.map(x => "'" + esc(x.word) + "'").join(',') + ')');
  out.push('  AND NOT EXISTS (SELECT 1 FROM idiom_category_rel r WHERE r.idiom_id = i.id AND r.category_id = 4);');
}
out.push('');

// ---- 题目 ----
out.push('-- ================= 题目（自编题，关联知识点/成语） =================');
const qInsert = [];
const optInsert = [];
let qNo = 0;

// 成语题：每题 1 道（题干引用释义，选成语）
for (const id of uniqIdioms) {
  qNo++;
  const correct = id.word;
  const distractorWords = pick(uniqIdioms.map(x => x.word), 3, correct);
  const opts = shuffle([{ c: correct, ok: 1 }, ...distractorWords.map(w => ({ c: w, ok: 0 }))]);
  const labels = ['A', 'B', 'C', 'D'];
  qInsert.push("INSERT INTO question (type, content, analysis, difficulty, idiom_id, source_type, source_name, status) VALUES (1, '" +
    esc('下列成语中，与"' + id.explanation + '"意思相符的一项是？') + "', '" +
    esc('正确答案是"' + correct + '"。' + (id.origin ? '出处：' + id.origin + '。' : '') + (id.confusing ? id.confusing : '')) +
    "', " + (id.difficulty || 3) + ", (SELECT id FROM idiom WHERE word = '" + esc(correct) + "'), 3, '内容自编题', 1);");
  // 关联选项：使用 id 号锚定
  opts.forEach((o, i) => {
    optInsert.push("INSERT INTO question_option (question_id, label, content, is_correct) VALUES ((SELECT id FROM question WHERE content LIKE '" +
      esc('下列成语中，与"' + id.explanation + '"') + "%' ORDER BY id DESC LIMIT 1), '" + labels[i] + "', '" + esc(o.c) + "', " + o.ok + ');');
  });
}

// 常识题：每题 1 道（知识点 summary 为正确项）
for (const k of uniqKnowledge) {
  qNo++;
  const correct = k.summary;
  const distractors = pick(uniqKnowledge.map(x => x.summary), 3, correct);
  const opts = shuffle([{ c: correct, ok: 1 }, ...distractors.map(s => ({ c: s, ok: 0 }))]);
  const labels = ['A', 'B', 'C', 'D'];
  qInsert.push("INSERT INTO question (type, content, analysis, difficulty, knowledge_id, source_type, source_name, status) VALUES (1, '" +
    esc('关于"' + k.title + '"，下列说法正确的是？') + "', '" +
    esc(k.content + (k.commonMistakes ? '易错点：' + k.commonMistakes : '')) +
    "', " + (k.difficulty || 3) + ", (SELECT id FROM knowledge WHERE title = '" + esc(k.title) + "' LIMIT 1), 3, '内容自编题', 1);");
  opts.forEach((o, i) => {
    optInsert.push("INSERT INTO question_option (question_id, label, content, is_correct) VALUES ((SELECT id FROM question WHERE content LIKE '" +
      esc('关于"' + k.title + '"') + "%' ORDER BY id DESC LIMIT 1), '" + labels[i] + "', '" + esc(o.c) + "', " + o.ok + ');');
  });
}

out.push('-- 题目共 ' + qNo + ' 道');
out.push(qInsert.join('\n'));
out.push('');
out.push('-- 选项');
out.push(optInsert.join('\n'));
out.push('');
out.push('-- 数据校验：各表总数');
out.push("SELECT 'knowledge' AS t, COUNT(*) AS n FROM knowledge WHERE status=1 UNION ALL SELECT 'idiom', COUNT(*) FROM idiom WHERE status=1 UNION ALL SELECT 'question', COUNT(*) FROM question WHERE status=1 UNION ALL SELECT 'question_option', COUNT(*) FROM question_option;");

const sql = out.join('\n');
const target = path.join(DATA_DIR, 'content-seed.sql');
fs.writeFileSync(target, sql, 'utf8');
console.log('OK 生成 ' + target);
console.log('  常识 ' + uniqKnowledge.length + ' / 成语 ' + uniqIdioms.length + ' / 题目 ' + qNo);
