$ErrorActionPreference = "Continue"
$BASE = "http://localhost:8080"
$PASS = 0
$FAIL = 0
$mysql = "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe"

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

Write-Host "=== Auth ==="
$user = "t10_" + (Get-Random -Minimum 100000 -Maximum 999999)
$reg = Post-Json "$BASE/api/v1/auth/register" "{`"username`":`"$user`",`"password`":`"pass123456`"}"
Check "register" ($reg.code -eq 0)
$login = Post-Json "$BASE/api/v1/auth/login" "{`"username`":`"$user`",`"password`":`"pass123456`"}"
Check "login" ($login.code -eq 0)
$token = $login.data.accessToken

Write-Host "=== Home ==="
$ov = Get-Json "$BASE/api/v1/home/overview" $token
Check "overview" ($null -ne $ov.data.reviewPendingTotal)
$rec = Get-Json "$BASE/api/v1/home/recommend" $token
Check "recommend" ($null -ne $rec.data.knowledgeList)

Write-Host "=== Knowledge ==="
$cats = Get-Json "$BASE/api/v1/knowledge/categories" $token
Check "knowledge categories" ($cats.code -eq 0)
$list = Get-Json "$BASE/api/v1/knowledge/list?size=51" $token
Check "knowledge list size=51" ($list.code -eq 0)

Write-Host "=== Idiom ==="
$icat = Get-Json "$BASE/api/v1/idiom/categories" $token
Check "idiom categories" ($icat.code -eq 0)
$ilist = Get-Json "$BASE/api/v1/idiom/list" $token
Check "idiom list" ($ilist.code -eq 0)

Write-Host "=== Practice ==="
$sess = Post-Json "$BASE/api/v1/practice/sessions" '{"type":1,"count":3}' $token
Check "create session" ($null -ne $sess.data.sessionId)
$sid = $sess.data.sessionId
$qid = $sess.data.questions[0].questionId
$correctOpt = (& $mysql -uroot -p123456 -N -e "SELECT o.id FROM zhikao.question_option o WHERE o.question_id=$qid AND o.is_correct=1 LIMIT 1;" 2>$null | Select-Object -Last 1).Trim()
$ans = Post-Json "$BASE/api/v1/practice/sessions/$sid/answers" "{`"questionId`":$qid,`"optionId`":$correctOpt}" $token
Check "judge correct" ($ans.data.correct -eq $true)
$res = Get-Json "$BASE/api/v1/practice/sessions/$sid/result" $token
Check "session result" ($null -ne $res.data.accuracy)

Write-Host "=== Review ==="
$review = Get-Json "$BASE/api/v1/review/today" $token
Check "review today" ($null -ne $review.data.pendingTotal)

Write-Host "=== Stats ==="
$stats = Get-Json "$BASE/api/v1/stats/overview" $token
Check "stats overview" ($null -ne $stats.data.studyDays)

Write-Host "=== Admin ==="
$admin = Post-Json "$BASE/admin/login" '{"username":"admin","password":"admin123456"}' $null
Check "admin login" ($admin.code -eq 0)
$adminToken = $admin.data.token
$forbidden = Get-Json "$BASE/admin/knowledge" $token
Check "user forbidden 3001" ($forbidden.code -eq 3001)
$docs = Get-Json "$BASE/admin/import/documents" $adminToken
Check "import documents" ($docs.code -eq 0)

Write-Host ""
Write-Host "RESULT: PASS=$PASS FAIL=$FAIL"
if ($FAIL -gt 0) { exit 1 } else { exit 0 }
