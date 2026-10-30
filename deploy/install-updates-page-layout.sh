#!/usr/bin/env bash
# Installs (idempotent) the static part of the official site updates feed:
#   mifron/updates/index.html,.css,.js  — updates.json renderer w/ branding
#   mifron/updates/updates.json         — seeded with the pipeline intro
#
# Usage: install-updates-page-layout.sh <site-repo-dir>
set -euo pipefail

TARGET_REPO="${1:?site repo dir required}"
UPDATES_DIR="$TARGET_REPO/mifron/updates"
mkdir -p "$UPDATES_DIR"

if [ -s "$UPDATES_DIR/index.html" ]; then
  echo "updates page already present: $UPDATES_DIR"
  exit 0
fi

cat > "$UPDATES_DIR/updates.json" <<'JSON'
[
  {
    "id": "pipeline-20261030T160000Z-pipe",
    "date": "2026-10-30T16:00:00Z",
    "type": "pipeline",
    "title": "更新配信パイプラインを導入",
    "body": [
      "ローカルバックアップを導入しました (毎日20:00 / 14世代 / SHA-256検証)",
      "GitHub→ビルド→staging の自動検証を開始しました (本番反映は手動承認制)",
      "Geyser-Spigot を自動更新するようにしました (毎日19:30 / 異常時ロールバック)",
      "このフィードに今後の自動更新イベントが配信されます (Discordにもbot経由で配信)"
    ],
    "commit": "57a0d55",
    "version": "26.1.2",
    "source": "MifronPlugin"
  }
]
JSON

cat > "$UPDATES_DIR/index.html" <<'HTML'
<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <title>Mifron | 更新情報</title>
  <meta name="description" content="Mifronのサーバーアップデート・Geyser/サイト更新の自動通知フィード。">
  <meta name="theme-color" content="#0b0c10">
  <link rel="icon" href="../assets/mifron-favicon.png">
  <link rel="stylesheet" href="updates.css">
</head>
<body>
  <header>
    <div class="shell nav">
      <a class="brand" href="../">← Mifron<small>Games &amp; Digital Experiences</small></a>
    </div>
  </header>
  <main class="shell">
    <h1>更新情報</h1>
    <p class="lead">サーバー/プラグイン/Geyser/配信インフラの自動更新フィードです。</p>
    <noscript><p class="empty">JavaScriptが無効のため履歴の表示には <a href="updates.json">updates.json</a> をご覧ください。</p></noscript>
    <div id="events" role="feed"></div>
    <footer>Mifron ops feed · <a href="updates.json">updates.json</a></footer>
  </main>
  <script src="updates.js" defer></script>
</body>
</html>
HTML

cat > "$UPDATES_DIR/updates.css" <<'CSS'
:root { color-scheme: dark; }
* { box-sizing: border-box; }
body { margin: 0; background: #0b0c10; color: #e8ecf1;
       font-family: "Noto Sans JP", system-ui, sans-serif; }
a { color: #7dd3fc; }
.shell { max-width: 880px; margin: 0 auto; padding: 0 20px; }
header { border-bottom: 1px solid #23252d; }
.nav { display: flex; align-items: center; gap: 16px; padding: 16px 0; }
.brand { display: flex; align-items: center; gap: 10px; color: #fff; text-decoration: none;
         font-weight: 700; letter-spacing: .02em; }
.brand small { display: block; font-weight: 400; color: #9aa3af; font-size: .68rem; }
main.shell { padding: 28px 20px 60px; }
h1 { font-size: 1.5rem; margin: 0 0 4px; }
.lead { color: #9aa3af; font-size: .9rem; margin: 0 0 26px; }
.event { border: 1px solid #23252d; border-radius: 14px; padding: 16px 18px; margin-bottom: 14px;
         background: #111319; }
.event h2 { font-size: 1.02rem; margin: 0 0 6px; }
.meta { color: #8b93a1; font-size: .78rem; display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 8px; }
.tag { border-radius: 999px; padding: 2px 10px; font-size: .72rem; border: 1px solid #2c2f3a; color: #c9d1dc; }
ul { margin: 6px 0 0 18px; padding: 0; color: #c9d1dc; font-size: .88rem; line-height: 1.55; }
footer { color: #6b7280; font-size: .75rem; padding: 24px 0 40px; text-align: center; }
.empty { color: #9aa3af; font-size: .9rem; }
CSS

cat > "$UPDATES_DIR/updates.js" <<'JS'
(function () {
  var TYPE_TAGS = {
    deploy: 'アップデート反映', build: 'ビルド', backup: 'バックアップ',
    geyser: 'Geyser', pipeline: 'インフラ', other: 'その他'
  };
  function esc(value) {
    return String(value || '').replace(/[&<>]/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[ch];
    });
  }
  fetch('updates.json', { cache: 'no-store' })
    .then(function (response) { return response.json(); })
    .then(function (events) {
      var target = document.getElementById('events');
      if (!Array.isArray(events) || events.length === 0) {
        target.innerHTML = '<p class="empty">更新はまだありません。</p>';
        return;
      }
      events.forEach(function (event) {
        if (!event || !event.title) return;
        var box = document.createElement('article');
        box.className = 'event';
        var rawDate = (event.date || '').slice(0, 10);
        var tag = esc(TYPE_TAGS[event.type] || 'その他');
        var lines = '';
        (event.body || []).forEach(function (line) {
          if (!line) return;
          lines += '<li>' + esc(line) + '</li>';
        });
        box.innerHTML =
          '<div class="meta"><span class="tag">' + tag + '</span><span>' + esc(rawDate) + '</span>' +
          (event.version ? '<span>version ' + esc(event.version) + '</span>' : '') +
          (event.commit ? '<span>commit ' + esc(event.commit) + '</span>' : '') + '</div>' +
          '<h2>' + esc(event.title) + '</h2>' +
          (lines ? '<ul>' + lines + '</ul>' : '');
        target.appendChild(box);
      });
    })
    .catch(function () {
      var target = document.getElementById('events');
      if (target) target.innerHTML = '<p class="empty">フィードの取得に失敗しました。</p>';
    });
})();
JS

echo "installed updates page at $UPDATES_DIR"
