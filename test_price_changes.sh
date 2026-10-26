#!/usr/bin/env bash
# Run from anywhere with the backend running on localhost:8080.
# Needs: curl, psql. Run in Git Bash on Windows:  bash test_price_changes.sh
# It asks for what it needs. You can also pre-set EMAIL, PASS, DB_USER, DB_PASS, SYNC=1 to skip the prompts.

API=${API:-http://localhost:8080}
DB_NAME=${DB_NAME:-subshift_db}

[ -z "$EMAIL" ]   && read -r -p "SubShift app email (the account you log in with): " EMAIL
[ -z "$PASS" ]    && { read -r -s -p "SubShift app password: " PASS; echo; }
[ -z "$DB_USER" ] && { read -r -p "Postgres username [postgres]: " DB_USER; DB_USER=${DB_USER:-postgres}; }
[ -z "$DB_PASS" ] && { read -r -s -p "Postgres password: " DB_PASS; echo; }
if [ -z "$SYNC" ]; then
  read -r -p "Also test the email sync path? Uses Gemini quota and edits your Netflix price (y/N): " ans
  [ "$ans" = "y" ] || [ "$ans" = "Y" ] && SYNC=1
fi
export PGPASSWORD="$DB_PASS"
q() { psql -h localhost -U "$DB_USER" -d "$DB_NAME" -tA -c "$1"; }
pass=0; fail=0
check() { # check "label" expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1 (got $3)"; pass=$((pass+1)); else echo "FAIL  $1 (expected $2, got $3)"; fail=$((fail+1)); fi
}

echo "Backend health: $(curl -s -m 5 "$API/api/health" || echo 'NOT REACHABLE from this shell')"
command -v psql >/dev/null || { echo "psql not found in this shell. Add PostgreSQL's bin folder to PATH, or use Git Bash."; exit 1; }
RESP=$(curl -s -m 15 -w '\nHTTP_STATUS:%{http_code}' -X POST "$API/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
CODE=${RESP##*HTTP_STATUS:}
TOKEN=$(echo "$RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "Login failed. HTTP status: $CODE"
  echo "Response body: ${RESP%HTTP_STATUS:*}"
  echo "(000 = could not reach $API from this shell; 401 = wrong email or password; 400 = bad request)"
  exit 1
fi
UID_=$(q "SELECT id FROM users WHERE email='$EMAIL'")
TODAY=$(date +%F)
auth=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

echo "== A. Manual entry: hike, drop, same-day tie-break =="
q "DELETE FROM services WHERE name='ZZ Price Test'" >/dev/null 2>&1
SID=$(q "INSERT INTO services(name,category_id) VALUES ('ZZ Price Test',1) RETURNING id" | head -1)
for p in 649 849 499 499; do
  curl -s -o /dev/null -X POST "$API/api/subscriptions/manual" "${auth[@]}" \
    -d "{\"serviceId\":$SID,\"price\":$p,\"dateRecorded\":\"$TODAY\"}"
done
SUB=$(q "SELECT id FROM subscriptions WHERE user_id=$UID_ AND service_id=$SID")
check "dashboard view shows latest price" "499.00" "$(q "SELECT current_price FROM active_subscriptions_overview WHERE subscription_id=$SUB")"
check "alerts created (hike + drop, none for repeat)" "2" "$(q "SELECT count(*) FROM alerts WHERE subscription_id=$SUB")"
check "last alert is the drop 849 -> 499" "849.00|499.00" "$(q "SELECT old_price||'|'||new_price FROM alerts WHERE subscription_id=$SUB ORDER BY id DESC LIMIT 1")"
check "price_log rows (no row for repeated 499)" "3" "$(q "SELECT count(*) FROM price_log WHERE subscription_id=$SUB")"
echo "API alert list contains the drop:"; curl -s "$API/api/alerts" "${auth[@]}" | grep -o '"newPrice":499[^,]*' | head -1

# cleanup of test data
q "DELETE FROM alerts WHERE subscription_id=$SUB; DELETE FROM price_log WHERE subscription_id=$SUB; DELETE FROM subscriptions WHERE id=$SUB; DELETE FROM services WHERE id=$SID;" >/dev/null

echo "== B. Notification type accepts PRICE_DROP =="
check "insert PRICE_DROP notification" "INSERT 0 1" "$(psql -h localhost -U "$DB_USER" -d "$DB_NAME" -c "INSERT INTO notifications(user_id,type,message) VALUES ($UID_,'PRICE_DROP','schema test')" | head -1)"
q "DELETE FROM notifications WHERE message='schema test'" >/dev/null

if [ "$SYNC" = "1" ]; then
  echo "== C. Email sync: forced drop, then forced hike (uses Gemini quota) =="
  NSUB=$(q "SELECT s.id FROM subscriptions s JOIN services sv ON sv.id=s.service_id WHERE s.user_id=$UID_ AND sv.name ILIKE 'netflix%' LIMIT 1")
  [ -z "$NSUB" ] && { echo "No Netflix subscription for this user: run Sync now once first"; exit 1; }
  force() { # set latest Netflix price to $1 and reset the sync watermark
    q "UPDATE price_log SET price=$1 WHERE id=(SELECT id FROM price_log WHERE subscription_id=$NSUB ORDER BY date_recorded DESC, id DESC LIMIT 1); UPDATE gmail_connections SET last_synced_at=NULL WHERE user_id=$UID_;" >/dev/null
  }
  for case in "999 dropsNotified PRICE_DROP" "100 hikesNotified PRICE_HIKE"; do
    set -- $case
    force "$1"
    BEFORE=$(q "SELECT coalesce(max(id),0) FROM notifications")
    OUT=$(curl -s -X POST "$API/api/email/sync?limit=40" "${auth[@]}")
    check "sync report: $2 >= 1" "yes" "$(echo "$OUT" | grep -o "\"$2\":[0-9]*" | cut -d: -f2 | awk '{print ($1>=1)?"yes":"no"}')"
    check "this sync created a $3 notification" "yes" "$(q "SELECT CASE WHEN count(*)>0 THEN 'yes' ELSE 'no' END FROM notifications WHERE user_id=$UID_ AND type='$3' AND id>$BEFORE")"
    echo "   notifications from this sync:"; q "SELECT '     '||type||': '||message FROM notifications WHERE user_id=$UID_ AND id>$BEFORE ORDER BY id"
    echo "   alert:   $(q "SELECT old_price||' -> '||new_price FROM alerts WHERE subscription_id=$NSUB ORDER BY id DESC LIMIT 1")"
    echo "   dashboard price: $(q "SELECT current_price FROM active_subscriptions_overview WHERE subscription_id=$NSUB")"
  done
fi

echo; echo "Result: $pass passed, $fail failed"
[ $fail -eq 0 ]