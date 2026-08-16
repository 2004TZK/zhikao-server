$ErrorActionPreference = "Continue"
$BASE = "http://localhost:8080"
$mysql = "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe"
$PASS = 0; $FAIL = 0

function Post-Json($url, $body, $token) {
  $headers = @{ "Content-Type" = "application/json" }
  if ($token) { $headers["Authorization"] = "Bearer $token" }
  Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body
}
function Get-Json($url, $token) {
  $headers = @{}
  if ($token) { $headers["Authorization"] = "Bearer $token" }
  Invoke-RestMethod -Uri $url -Method Get -Headers $headers
}
function Check($name, $cond) {
  if ($cond) { $script:PASS++; Write-Host "  OK: $name" }
  else { $script:FAIL++; Write-Host "  FAIL: $name" }
}

# ===== T10.2 登录流程 =====
Write-Host "=== T10.2 登录流程 ==="
$user = "t102_" + (Get-Random -Minimum 100000 -Maximum 999999)
$reg = Post-Json "$BASE/api/v1/auth/register" "{`"username`":`"$user`",`"password`":`"pass123456`"}"
Check "注册" ($reg.code -eq 0)
$uid = $reg.data.userId
$token = $reg.data.accessToken
# 游客
$uuid = [guid]::NewGuid().ToString()
$guest = Post-Json "$BASE/api/v1/auth/guest" "{`"guestUuid`":`"$uuid`"}"
Check "游客登录(userType=2)" ($guest.data.userType -eq 2)
$guest2 = Post-Json "$BASE/api/v1/auth/guest" "{`"guestUuid`":`"$uuid`"}"
Check "游客UUID复用同一user_id" ($guest.data.userId -eq $guest2.data.userId)
# 刷新
$rf = Post-Json "$BASE/api/v1/auth/refresh" "{`"refreshToken`":`"$($reg.data.refreshToken)`"}"
Check "refresh" ($rf.code -eq 0)
# 登出后 refresh 失效
Post-Json "$BASE/api/v1/auth/logout" "{`"refreshToken`":`"$($reg.data.refreshToken)`"}" $token | Out-Null
$exp = Post-Json "$BASE/api/v1/auth/refresh" "{`"refreshToken`":`"$($reg.data.refreshToken)`"}"
Check "登出后refresh→2002" ($exp.code -eq 2002)
# 禁用
$admin = Post-Json "$BASE/admin/login" '{"username":"admin","password":"admin123456"}' $null
$adminToken = $admin.data.token
Post-Json "$BASE/admin/user/$uid/status?status=1" "" $adminToken | Out-Null
$disabled = Post-Json "$BASE/api/v1/auth/login" "{`"username`":`"$user`",`"password`":`"pass123456`"}"
Check "禁用后登录→2003" ($disabled.code -eq 2003)
Post-Json "$BASE/admin/user/$uid/status?status=0" "" $adminToken | Out-Null

# ===== T10.3 学习流程 =====
Write-Host "=== T10.3 学习流程 ==="
$s1 = Post-Json "$BASE/api/v1/knowledge/10/study" '{"durationSeconds":600}' $token
Check "学习上报" ($s1.code -eq 0)
$fav = Post-Json "$BASE/api/v1/knowledge/10/favorite" "" $token
Check "收藏" ($fav.data -eq $true)
$mastered = Post-Json "$BASE/api/v1/knowledge/10/mastered" "" $token
Check "标记已掌握" ($mastered.code -eq 0)
$det = Get-Json "$BASE/api/v1/knowledge/10" $token
Check "详情mastery=2" ($det.data.mastery -eq 2)

# ===== T10.4 练习流程 =====
Write-Host "=== T10.4 练习流程 ==="
$sess = Post-Json "$BASE/api/v1/practice/sessions" '{"type":1,"count":3}' $token
$sid = $sess.data.sessionId
$q1 = $sess.data.questions[0].questionId
$wrongOpt = (& $mysql -uroot -p123456 -N -e "SELECT o.id FROM zhikao.question_option o WHERE o.question_id=$q1 AND o.is_correct=0 LIMIT 1;" 2>$null | Select-Object -Last 1).Trim()
$bad = Post-Json "$BASE/api/v1/practice/sessions/$sid/answers" "{`"questionId`":$q1,`"optionId`":$wrongOpt}" $token
Check "答错判定" ($bad.data.correct -eq $false)
$wrongCount = (& $mysql -uroot -p123456 -N -e "SELECT wrong_count FROM zhikao.wrong_question WHERE user_id=$uid AND question_id=$q1;" 2>$null | Select-Object -Last 1).Trim()
Check "错题入库(wrong_count=1)" ($wrongCount -eq "1")
# 幂等
$bad2 = Post-Json "$BASE/api/v1/practice/sessions/$sid/answers" "{`"questionId`":$q1,`"optionId`":$wrongOpt}" $token
$wrongCount2 = (& $mysql -uroot -p123456 -N -e "SELECT wrong_count FROM zhikao.wrong_question WHERE user_id=$uid AND question_id=$q1;" 2>$null | Select-Object -Last 1).Trim()
Check "重复提交幂等(仍=1)" ($wrongCount2 -eq "1")

# ===== T10.5 复习算法 =====
Write-Host "=== T10.5 复习算法 ==="
# stage=5 答对置熟练（构造 stage=5 记录）
& $mysql -uroot -p123456 -e "INSERT INTO zhikao.user_knowledge (user_id, knowledge_id, mastery, review_stage, correct_count, wrong_count, next_review_time) VALUES ($uid, 11, 3, 4, 1, 0, NOW() - INTERVAL 1 DAY);" 2>$null | Out-Null
$submit = Post-Json "$BASE/api/v1/review/sessions/1/items" "{`"targetType`":1,`"targetId`":11,`"isCorrect`":true}" $token
Check "复习答对提交" ($submit.code -eq 0)
$uk = (& $mysql -uroot -p123456 -N -e "SELECT CONCAT(review_stage,'|',mastery) FROM zhikao.user_knowledge WHERE user_id=$uid AND knowledge_id=11;" 2>$null | Select-Object -Last 1).Trim()
Check "stage4→5且mastery=4(熟练)" ($uk -eq "5|4")
# stage=0 答错边界
& $mysql -uroot -p123456 -e "INSERT INTO zhikao.user_idiom (user_id, idiom_id, mastery, review_stage, correct_count, wrong_count, next_review_time) VALUES ($uid, 10, 2, 0, 1, 0, NOW() - INTERVAL 10 MINUTE);" 2>$null | Out-Null
$sub2 = Post-Json "$BASE/api/v1/review/sessions/1/items" "{`"targetType`":2,`"targetId`":10,`"isCorrect`":false}" $token
$ui = (& $mysql -uroot -p123456 -N -e "SELECT review_stage FROM zhikao.user_idiom WHERE user_id=$uid AND idiom_id=10;" 2>$null | Select-Object -Last 1).Trim()
Check "stage=0答错保持0" ($ui -eq "0")

# ===== T10.6 数据一致性 =====
Write-Host "=== T10.6 数据一致性 ==="
# 学习→掌握→复习联动：user_knowledge 存在且 next_review_time 已更新
$consistent = (& $mysql -uroot -p123456 -N -e "SELECT COUNT(*) FROM zhikao.user_knowledge WHERE user_id=$uid AND next_review_time IS NOT NULL;" 2>$null | Select-Object -Last 1).Trim()
Check "掌握度与复习时间联动(有next_review_time)" ([int]$consistent -ge 1)
# 答题→错题→复习记录
$rrCount = (& $mysql -uroot -p123456 -N -e "SELECT COUNT(*) FROM zhikao.review_record WHERE user_id=$uid;" 2>$null | Select-Object -Last 1).Trim()
Check "review_record落库" ([int]$rrCount -ge 2)
# 统计由明细推导
$stats = Get-Json "$BASE/api/v1/stats/overview" $token
$dbAnswers = (& $mysql -uroot -p123456 -N -e "SELECT COUNT(*) FROM zhikao.user_answer WHERE user_id=$uid;" 2>$null | Select-Object -Last 1).Trim()
Check "统计与明细一致(答题量)" ([string]$stats.data.totalAnswers -eq [string]$dbAnswers)

Write-Host ""
Write-Host "RESULT: PASS=$PASS FAIL=$FAIL"
if ($FAIL -gt 0) { exit 1 } else { exit 0 }
