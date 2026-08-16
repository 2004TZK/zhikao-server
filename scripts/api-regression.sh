#!/usr/bin/env bash
# =============================================================
# 知考 API 自动化回归测试（T10.1）
# 覆盖：认证/首页/常识/成语/练习/错题/复习/统计/导入 核心链路
# 用法：启动后端后执行 bash scripts/api-regression.sh http://localhost:8080
# 通过标准：全部 PASS；失败项返回非零退出码
# =============================================================

BASE="${1:-http://localhost:8080}"
PASS=0
FAIL=0
REGISTERED_USER=""
REGISTERED_PASS="pass123456"

log() { echo "[TEST] $1"; }
ok() { PASS=$((PASS+1)); echo "  ✓ PASS: $1"; }
bad() { FAIL=$((FAIL+1)); echo "  ✗ FAIL: $1"; }

# ---------- 认证 ----------
log "认证模块"
RESP=$(curl -sS -X POST "$BASE/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"username\":\"t10_$(date +%s)\",\"password\":\"$REGISTERED_PASS\"}")
REGISTERED_USER=$(echo "$RESP" | sed 's/.*"username":"\([^"]*\)".*/\1/')
if echo "$RESP" | grep -q '"code":0'; then ok "注册成功"; else bad "注册失败: $RESP"; fi

LOGIN=$(curl -sS -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$REGISTERED_USER\",\"password\":\"$REGISTERED_PASS\"}")
TOKEN=$(echo "$LOGIN" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
AUTH="Authorization: Bearer $TOKEN"
if echo "$LOGIN" | grep -q '"code":0'; then ok "登录成功"; else bad "登录失败"; fi

# ---------- 首页 ----------
log "首页模块"
HOME_RESP=$(curl -sS "$BASE/api/v1/home/overview" -H "$AUTH")
if echo "$HOME_RESP" | grep -q '"reviewPendingTotal"'; then ok "overview 返回任务与积压"; else bad "overview 异常"; fi
REC_RESP=$(curl -sS "$BASE/api/v1/home/recommend" -H "$AUTH")
if echo "$REC_RESP" | grep -q '"knowledgeList"'; then ok "recommend 返回推荐"; else bad "recommend 异常"; fi

# ---------- 常识 ----------
log "常识模块"
CAT_RESP=$(curl -sS "$BASE/api/v1/knowledge/categories" -H "$AUTH")
if echo "$CAT_RESP" | grep -q '"code":0'; then ok "常识分类"; else bad "常识分类异常"; fi
LIST_RESP=$(curl -sS "$BASE/api/v1/knowledge/list?size=51" -H "$AUTH")
if echo "$LIST_RESP" | grep -q '"code":0'; then ok "常识列表(size=51 边界)"; else bad "常识列表异常"; fi

# ---------- 成语 ----------
log "成语模块"
I_CAT=$(curl -sS "$BASE/api/v1/idiom/categories" -H "$AUTH")
if echo "$I_CAT" | grep -q '"code":0'; then ok "成语分类"; else bad "成语分类异常"; fi
I_LIST=$(curl -sS "$BASE/api/v1/idiom/list" -H "$AUTH")
if echo "$I_LIST" | grep -q '"code":0'; then ok "成语列表"; else bad "成语列表异常"; fi

# ---------- 练习与错题 ----------
log "练习模块"
SESSION=$(curl -sS -X POST "$BASE/api/v1/practice/sessions" -H "$AUTH" -H "Content-Type: application/json" -d '{"type":1,"count":3}')
if echo "$SESSION" | grep -q '"sessionId"'; then ok "组卷成功"; else bad "组卷失败: $SESSION"; fi
SESSION_ID=$(echo "$SESSION" | sed 's/.*"sessionId":\([0-9]*\).*/\1/')
QID=$(echo "$SESSION" | sed 's/.*"questionId":\([0-9]*\).*/\1/')
# 查正确答案选项 id
MYSQL_RESULT=$(mysql -uroot -p123456 -N -e "SELECT o.id FROM zhikao.question_option o JOIN zhikao.question q ON o.question_id=q.id WHERE q.id=$QID AND o.is_correct=1 LIMIT 1" 2>/dev/null)
ANSWER=$(curl -sS -X POST "$BASE/api/v1/practice/sessions/$SESSION_ID/answers" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"questionId\":$QID,\"optionId\":$MYSQL_RESULT}")
if echo "$ANSWER" | grep -q '"correct":true'; then ok "答对判定"; else bad "判分异常: $ANSWER"; fi

# ---------- 复习 ----------
log "复习模块"
REVIEW=$(curl -sS "$BASE/api/v1/review/today" -H "$AUTH")
if echo "$REVIEW" | grep -q '"pendingTotal"'; then ok "复习概览"; else bad "复习概览异常"; fi

# ---------- 统计 ----------
log "统计模块"
STATS=$(curl -sS "$BASE/api/v1/stats/overview" -H "$AUTH")
if echo "$STATS" | grep -q '"studyDays"'; then ok "统计概览"; else bad "统计异常"; fi

# ---------- 管理员与导入 ----------
log "管理后台"
ADMIN=$(curl -sS -X POST "$BASE/admin/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123456"}')
ADMIN_TOKEN=$(echo "$ADMIN" | sed 's/.*"token":"\([^"]*\)".*/\1/')
ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"
if echo "$ADMIN" | grep -q '"code":0'; then ok "管理员登录"; else bad "管理员登录失败"; fi

# 普通用户访问 /admin 应 3001
FORBIDDEN=$(curl -sS "$BASE/admin/knowledge" -H "$AUTH")
if echo "$FORBIDDEN" | grep -q '"code":3001'; then ok "普通用户访问/admin被拒(3001)"; else bad "3001 校验失败: $FORBIDDEN"; fi

# 导入任务列表
IMPORT=$(curl -sS "$BASE/admin/import/documents" -H "$ADMIN_AUTH")
if echo "$IMPORT" | grep -q '"code":0'; then ok "导入任务列表"; else bad "导入列表异常"; fi

echo ""
echo "======================================"
echo "结果: PASS=$PASS FAIL=$FAIL"
echo "======================================"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
