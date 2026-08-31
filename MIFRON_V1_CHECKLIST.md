# Mifron v1.0 Release Checklist

このチェックリストは、Mifronを「機能追加中のサーバー」から「正式公開できるv1.0」へ移行するための基準です。

## 運用ルール

- Sランクが1つでも未完了なら正式公開しない。
- Aランクは原則v1.0前に完了する。
- Bランクはv1.0後に回してよい。
- 新機能は原則追加せず、必要ならBacklogへ記録する。
- 「コードが存在する」だけでは完了扱いにしない。実サーバー上で再現テストを通す。
- 修正後は関連項目を再テストする。
- Java版とBedrock版で挙動が分かれる機能は両方確認する。

---

# S: 正式公開ブロッカー

## S-CORE 基盤・起動

- [ ] S-CORE-001 Paperサーバーが正常起動する
- [ ] S-CORE-002 MifronPluginがエラーなくEnableされる
- [ ] S-CORE-003 最新mainのGitHub Actionsビルドが成功する
- [ ] S-CORE-004 サーバー再起動後も主要データが保持される
- [ ] S-CORE-005 plugin.ymlのコマンドが正常登録される
- [ ] S-CORE-006 権限不足の一般プレイヤーが管理コマンドを実行できない
- [ ] S-CORE-007 OP / mifron.admin が必要な管理操作を実行できる
- [ ] S-CORE-008 serverSecret等の本番用設定が初期値のままではない
- [ ] S-CORE-009 重大エラー発生時にデータファイルが破損しない
- [ ] S-CORE-010 バックアップから復元できることを確認する

## S-JOIN Java / Bedrock参加

- [ ] S-JOIN-001 Java版から正常参加できる
- [ ] S-JOIN-002 Bedrock版から正常参加できる
- [ ] S-JOIN-003 初参加時にスポーン位置が正しい
- [ ] S-JOIN-004 再参加時に異常な位置・状態にならない
- [ ] S-JOIN-005 Java版で主要UIが操作できる
- [ ] S-JOIN-006 Bedrock版で主要UIが操作できる
- [ ] S-JOIN-007 Geyser未導入時にMifronPlugin本体が異常終了しない

## S-WORLD ワールド移動・ルール

- [ ] S-WORLD-001 Hubスポーンが正常
- [ ] S-WORLD-002 Survivalスポーンが正常
- [ ] S-WORLD-003 Athleticスポーンが正常
- [ ] S-WORLD-004 Minigameスポーンが正常
- [ ] S-WORLD-005 PvP無効ワールドでPvPできない
- [ ] S-WORLD-006 PvP有効ワールドでPvPできる
- [ ] S-WORLD-007 固定昼ワールドの時刻が想定通り
- [ ] S-WORLD-008 WorldChange時に不要な状態が残留しない
- [ ] S-WORLD-009 FFA退出時に適切なワールドへ戻る
- [ ] S-WORLD-010 ログアウト中のワールド変更・再起動で位置が壊れない

## S-INVENTORY インベントリ分離

- [ ] S-INV-001 Hub → Survivalで正しいインベントリへ切り替わる
- [ ] S-INV-002 Survival → Hubで正しいインベントリへ戻る
- [ ] S-INV-003 Hub → FFAで通常アイテムを持ち込めない
- [ ] S-INV-004 FFA → HubでFFAアイテムを持ち出せない
- [ ] S-INV-005 FFA → SurvivalでFFAアイテムを持ち出せない
- [ ] S-INV-006 Armorがグループごとに正常保存される
- [ ] S-INV-007 Offhandがグループごとに正常保存される
- [ ] S-INV-008 Level / XP / 選択スロットがグループごとに保存・復元される
- [ ] S-INV-009 グループ読込時、HPが min(最大HP, 20) へ戻る
- [ ] S-INV-010 Hunger / Saturationがグループごとに保存・復元される
- [ ] S-INV-011 Potion Effectはインベントリグループ保存の対象外である
- [ ] S-INV-012 EnderChestはインベントリグループ保存の対象外である
- [ ] S-INV-013 死亡時に別グループのアイテムをドロップしない
- [ ] S-INV-014 サーバー再起動後も各グループの所持品が保持される
- [ ] S-INV-015 高速な連続ワールド移動でアイテム複製・消失が起きない

## S-DATA データ永続化

- [ ] S-DATA-001 MP残高が再起動後も保持される
- [ ] S-DATA-002 フレンド情報が再起動後も保持される
- [ ] S-DATA-003 Status / progressionが再起動後も保持される
- [ ] S-DATA-004 FFA statsが再起動後も保持される
- [ ] S-DATA-005 Proposal情報が再起動後も保持される
- [ ] S-DATA-006 Shop情報が再起動後も保持される
- [ ] S-DATA-007 Structure / TextDisplay情報が再起動後も保持される
- [ ] S-DATA-008 YAML保存中に異常終了しても復旧可能
- [ ] S-DATA-009 UUID変更・名前変更で別プレイヤー扱いにならない

## S-MP MP / 経済の致命的不具合

- [ ] S-MP-001 MP加算が正常
- [ ] S-MP-002 MP減算が正常
- [ ] S-MP-003 MPが負の不正値にならない
- [ ] S-MP-004 MP上限付近でオーバーフローしない
- [ ] S-MP-005 同一操作で報酬が二重加算されない
- [ ] S-MP-006 Mob討伐報酬が仕様通り
- [ ] S-MP-007 Advancement報酬が仕様通り
- [ ] S-MP-008 Quest報酬が仕様通り
- [ ] S-MP-009 FFA報酬が仕様通り
- [ ] S-MP-010 再ログイン・再起動で残高が巻き戻らない
- [ ] S-MP-011 MinoruとのMP同期方式を確定する
- [ ] S-MP-012 Minecraft → MinoruのMP同期が正常
- [ ] S-MP-013 Minoru → MinecraftのMP同期が必要なら正常
- [ ] S-MP-014 同時更新時に残高が消失・上書きされない
- [ ] S-MP-015 FFA外で死亡すると所持MPの50%（切り捨て）が失われる

## S-PROTECT 保護・不正持ち出し防止

- [ ] S-PROT-001 Hub保護範囲の通常Block Breakが、`ChunkProtectionFeature`の保護判定・管理者バイパスを含めて実機で仕様通りに動作する
- [ ] S-PROT-002 Hub保護範囲の通常Block Placeが、`ChunkProtectionFeature`の保護判定・管理者バイパスを含めて実機で仕様通りに動作する
- [ ] S-PROT-003 保護されたコンテナを不正に操作できない
- [ ] S-PROT-004 Door / Trapdoor等の扱いが仕様通り
- [ ] S-PROT-005 ItemFrameを不正破壊できない
- [ ] S-PROT-006 ArmorStandを不正破壊できない
- [ ] S-PROT-007 Explosionで保護対象を破壊できない
- [ ] S-PROT-008 Pistonで保護外へ移動できない
- [ ] S-PROT-009 Lava / Waterで保護を回避できない
- [ ] S-PROT-010 Fireでショップ等を破壊できない
- [ ] S-PROT-011 Shop購入は保護範囲内でも正常動作する
- [ ] S-PROT-012 Admin bypassが正常動作する

---

# S-FFA FFA正式公開チェック

## FFA共通

- [ ] S-FFA-001 FFAへ正常参加できる
- [ ] S-FFA-002 Kit選択が正常
- [ ] S-FFA-003 選択Kitの装備・アイテムが正常配布される
- [ ] S-FFA-004 FFA中に通常インベントリへアクセスできない
- [ ] S-FFA-005 FFA中にアイテムDropできない
- [ ] S-FFA-006 FFA中に不要なItem Pickupができない
- [ ] S-FFA-007 FFA中にBlock Breakできない
- [ ] S-FFA-008 FFA中にBlock Placeできない
- [ ] S-FFA-009 許可外コマンドが制限される
- [ ] S-FFA-010 Friend同士でも攻撃できる
- [ ] S-FFA-011 Mob → Playerのダメージが正常
- [ ] S-FFA-012 Player → Mobのダメージが正常
- [ ] S-FFA-013 Projectileが途中で不正消滅しない
- [ ] S-FFA-014 Arrowが正常動作する
- [ ] S-FFA-015 Tridentが正常動作する
- [ ] S-FFA-016 Wind Chargeが正常動作する
- [ ] S-FFA-017 Potion Projectileが正常動作する
- [ ] S-FFA-018 食料ゲージが仕様通り減少する
- [ ] S-FFA-019 落下ダメージが仕様通り
- [ ] S-FFA-020 高さ制限が仕様通り
- [ ] S-FFA-021 天候イベントが正常開始する
- [ ] S-FFA-022 天候表示がプレイヤー側で同期する
- [ ] S-FFA-023 雨イベント効果が正常
- [ ] S-FFA-024 猛吹雪等のイベント効果が正常
- [ ] S-FFA-025 Kill報酬が正常
- [ ] S-FFA-026 同一相手連続Kill時の報酬補正が正常
- [ ] S-FFA-027 Death時に操作不能にならない
- [ ] S-FFA-028 Death時に仕様通りFFA退出する
- [ ] S-FFA-029 Death後に再参加できる
- [ ] S-FFA-030 Logout → LoginでFFA状態が壊れない
- [ ] S-FFA-031 WorldChangeでFFA状態が残留しない
- [ ] S-FFA-032 FFA退出後にKitアイテムが残らない
- [ ] S-FFA-033 FFA Statsが正常加算される
- [ ] S-FFA-034 FFA Statsが再起動後も保持される

## 18 Kits

各Kitについて最低限「配布 / 特殊能力 / PvP / Mob / Death / Exit / Rejoin」を確認する。

- [ ] FFA-KIT-001 Axe / 戦士
- [ ] FFA-KIT-002 Bow / 狩人
- [ ] FFA-KIT-003 Spear / 騎士
- [ ] FFA-KIT-004 Crossbow / リボルバー
- [ ] FFA-KIT-005 Sword / 剣士
- [ ] FFA-KIT-006 Shield / シールダー
- [ ] FFA-KIT-007 Trident / 海の戦士
- [ ] FFA-KIT-008 Mace / 重戦士
- [ ] FFA-KIT-009 Gambler / ギャンブラー
- [ ] FFA-KIT-010 Wizard / ケミスト
- [ ] FFA-KIT-011 Sniper / スナイパー
- [ ] FFA-KIT-012 Vampire / ヴァンパイア
- [ ] FFA-KIT-013 Grappler / グラップラー
- [ ] FFA-KIT-014 Assassin / アサシン
- [ ] FFA-KIT-015 Necromancer / ネクロマンサー
- [ ] FFA-KIT-016 Trapper / トラッパー
- [ ] FFA-KIT-017 Bug Mania / バグマニア
- [ ] FFA-KIT-018 Crusher / クラッシャー

## 重点Kit検証

### Gambler

- [ ] FFA-GAM-001 与ダメージ倍率の抽選範囲が仕様通り
- [ ] FFA-GAM-002 マイナスダメージ時の処理が仕様通り
- [ ] FFA-GAM-003 被ダメージ側のランダム補正が仕様通り
- [ ] FFA-GAM-004 攻撃抽選はアクションバー、防御補正はチャットへ表示される
- [ ] FFA-GAM-005 Jackpot回数上限が正常
- [ ] FFA-GAM-006 Mob相手でも想定通り動作する

### Sniper

- [ ] FFA-SNP-001 1クリックで1発のみ発射される
- [ ] FFA-SNP-002 矢が正常に生成される
- [ ] FFA-SNP-003 装弾数1が正常
- [ ] FFA-SNP-004 自動リロードされない
- [ ] FFA-SNP-005 手動リロードが正常
- [ ] FFA-SNP-006 リロードSE / delayが正常
- [ ] FFA-SNP-007 Death / Exit後にリロード状態が残留しない

### Revolver

- [ ] FFA-REV-001 装弾数6が正常
- [ ] FFA-REV-002 発射数カウントが正常
- [ ] FFA-REV-003 リロードが正常
- [ ] FFA-REV-004 リロードSE / delayが正常
- [ ] FFA-REV-005 Exit後に残弾状態が残留しない

### Vampire

- [ ] FFA-VAM-001 Lifestealが正常
- [ ] FFA-VAM-002 攻撃時の満腹度回復が正常
- [ ] FFA-VAM-003 隠し満腹度を含む処理が仕様通り
- [ ] FFA-VAM-004 攻撃力蓄積が実際にダメージへ反映される
- [ ] FFA-VAM-005 蓄積表示がレベルバー上のみ
- [ ] FFA-VAM-006 移動速度上昇が過剰でない
- [ ] FFA-VAM-007 太陽ダメージが正常

### Assassin

- [ ] FFA-ASN-001 致命の短剣が初期配布される
- [ ] FFA-ASN-002 毒の短剣が初期配布される
- [ ] FFA-ASN-003 致命の短剣が使用後に仕様通り消費される
- [ ] FFA-ASN-004 毒効果が正常

### Necromancer

- [ ] FFA-NEC-001 召喚Mobが本人を攻撃しない
- [ ] FFA-NEC-002 召喚Mobが敵プレイヤーを攻撃する
- [ ] FFA-NEC-003 召喚Mobが仕様対象の非Playerも攻撃する
- [ ] FFA-NEC-004 Mob数制限が現行仕様通り
- [ ] FFA-NEC-005 腐肉が正常配布され食べられる
- [ ] FFA-NEC-006 Exit時に召喚Mobが残留しない

### Trapper

- [ ] FFA-TRP-001 Trap設置が正常
- [ ] FFA-TRP-002 敵が踏んだ瞬間に反応する
- [ ] FFA-TRP-003 本人が踏んでもTrapが壊れた状態にならない
- [ ] FFA-TRP-004 Fire Trapが正常
- [ ] FFA-TRP-005 Exit時にTrapが残留しない

### Bug Mania

- [ ] FFA-BUG-001 Silverfishが本人を攻撃しない
- [ ] FFA-BUG-002 Silverfishが敵Playerを攻撃する
- [ ] FFA-BUG-003 Silverfishが対象Mobを攻撃する
- [ ] FFA-BUG-004 Silverfishがブロックへ潜らない
- [ ] FFA-BUG-005 Silverfish討伐で不正報酬が発生しない
- [ ] FFA-BUG-006 Exit時にSilverfishが残留しない

### Crusher

- [ ] FFA-CRS-001 攻撃時の爆発抽選が仕様通り
- [ ] FFA-CRS-002 被弾時の爆発抽選が仕様通り
- [ ] FFA-CRS-003 爆発確率分布が仕様通り
- [ ] FFA-CRS-004 Husk相手でも正常発動する
- [ ] FFA-CRS-005 爆発AoEが意図しない対象へ異常ダメージを出さない

### Mace

- [ ] FFA-MACE-001 Maceが正常配布される
- [ ] FFA-MACE-002 Wind Chargeが正常使用できる
- [ ] FFA-MACE-003 Wind Charge補充が正常
- [ ] FFA-MACE-004 最大ダメージ制限が正常
- [ ] FFA-MACE-005 高さ制限との連携が正常

---

# A: v1.0前に完了推奨

## A-TELEPORTER

- [ ] A-TP-001 Teleporter起動操作が正常
- [ ] A-TP-002 登録先がすべて表示される
- [ ] A-TP-003 サーバー順序が設定通り
- [ ] A-TP-004 サーバーアイコンが設定通り
- [ ] A-TP-005 横方向アーク配置が崩れない
- [ ] A-TP-006 展開アニメーションが最後まで正常
- [ ] A-TP-007 選択操作で正しいワールドへ移動する
- [ ] A-TP-008 複数人同時使用で表示が干渉しない
- [ ] A-TP-009 展開途中Logoutで表示Entityが残らない
- [ ] A-TP-010 Bedrockから操作できる

## A-SHOP

- [ ] A-SHOP-001 Shop Wandで作成できる
- [ ] A-SHOP-002 Shop Wandで削除できる
- [ ] A-SHOP-003 一般プレイヤーがShop Blockを破壊できない
- [ ] A-SHOP-004 Explosion / Piston / Liquid / Fireで壊れない
- [ ] A-SHOP-005 商品購入が正常
- [ ] A-SHOP-006 残高不足時に購入できない
- [ ] A-SHOP-007 商品カタログが1から順番に並ぶ
- [ ] A-SHOP-008 商品がカテゴリごとに整理される
- [ ] A-SHOP-009 Slot MachineがShelf採番対象にならない
- [ ] A-SHOP-010 再採番コマンドが正常
- [ ] A-SHOP-011 ClearAll系管理操作が正常
- [ ] A-SHOP-012 再起動後もShopが保持される
- [ ] A-SHOP-013 連打で二重購入・MP複製が起きない

## A-AUCTION

- [ ] A-AUC-001 旧Shop Wandで価格表上許可された額縁だけAuction化できる
- [ ] A-AUC-002 Wallet右クリックで最高額+bid-stepの入札になり、必要MPが預けられる
- [ ] A-AUC-003 Sneak+Wallet右クリックで最高額+sneak-bid-stepの入札になる
- [ ] A-AUC-004 必要MP不足と出品者本人の入札が拒否される
- [ ] A-AUC-005 最高額更新時に旧最高入札者へ預かりMPが全額返金される
- [ ] A-AUC-006 最高入札者・最高額が更新され再起動後も保持される
- [ ] A-AUC-007 ItemFrameが通常操作・破壊から保護される
- [ ] A-AUC-008 出品者または管理者だけが解除でき、預かりMPが返金される
- [ ] A-AUC-009 期限後に落札品が落札者へ、落札MPが出品者へ配送される
- [ ] A-AUC-010 オフライン落札者が次回ログイン時に商品を受け取れる
- [ ] A-AUC-011 入札なし終了では商品が額縁に残る
- [ ] A-AUC-012 旧版の未徴収入札が精算・返金対象にならず無効化される

## A-QUEST

- [ ] A-QUEST-001 Quest開始が正常
- [ ] A-QUEST-002 Quest進捗が正常加算される
- [ ] A-QUEST-003 Quest完了判定が正常
- [ ] A-QUEST-004 Quest報酬が正常
- [ ] A-QUEST-005 Reincarnation bonusが正常
- [ ] A-QUEST-006 Anti-abuseが正常
- [ ] A-QUEST-007 再起動後に進捗が保持される
- [ ] A-QUEST-008 quests.ymlのconditionが実際のprogress-key加算条件と一致する
- [ ] A-QUEST-009 旧運営付与条件16件が対応イベントから自動加算される
- [ ] A-QUEST-010 専用ワールド到達条件がquests.automatic.world-progressに従う

## A-FRIEND / STATUS

- [ ] A-FRIEND-001 Friend request送信が正常
- [ ] A-FRIEND-002 Friend request承認が正常
- [ ] A-FRIEND-003 Friend削除が正常
- [ ] A-FRIEND-004 Offline messageが正常
- [ ] A-FRIEND-005 Friend情報が再起動後も保持される
- [ ] A-FRIEND-006 FFAではFriend関係がPvPを妨げない
- [ ] A-STATUS-001 Status UIが正常表示される
- [ ] A-STATUS-002 Status data reset権限が正常
- [ ] A-STATUS-003 称号解除条件が正常
- [ ] A-STATUS-004 Advancement連動称号が正常

## A-ATHLETIC

- [ ] A-ATH-001 Athletic開始が正常
- [ ] A-ATH-002 Checkpointが正常
- [ ] A-ATH-003 Death / Fall時の復帰が正常
- [ ] A-ATH-004 Retryが正常
- [ ] A-ATH-005 Goal判定が正常
- [ ] A-ATH-006 Rewardが正常
- [ ] A-ATH-007 Logout / Rejoinで状態が壊れない

## A-PROPOSAL

- [ ] A-PROP-001 外部ツールがproposals.ymlのpendingへ投入したProposalをlist / reviewできる
- [ ] A-PROP-002 title / custom_itemだけをapprove / forceapproveでconfigへ反映できる
- [ ] A-PROP-003 廃止済みshop_itemと未対応typeを承認しない
- [ ] A-PROP-004 rejectでpendingからrejectedへ移動し、再起動後も保持される
- [ ] A-PROP-005 ゲーム内Proposal作成・投票が現行コードに存在しないことを運用資料と一致させる

## A-MINORU

- [ ] A-BOT-001 Discord認証後に/profileが認証済み表示になる
- [ ] A-BOT-002 Minecraft PlayerとDiscord Userの紐付け方式を固定する
- [ ] A-BOT-003 MP同期が正常
- [ ] A-BOT-004 Proposal連携が正常
- [ ] A-BOT-005 Vote連携が必要なら正常
- [ ] A-BOT-006 Bot再起動後も認証状態が保持される
- [ ] A-BOT-007 権限不足Userが管理操作できない

---

# A-SECURITY 公開前不正対策

- [ ] A-SEC-001 Inventory切替を利用したDuplicationができない
- [ ] A-SEC-002 Deathを利用したDuplicationができない
- [ ] A-SEC-003 WorldChangeを利用したDuplicationができない
- [ ] A-SEC-004 Shop連打でMP / Item Duplicationができない
- [ ] A-SEC-005 Auction同時操作でMP / Item Duplicationができない
- [ ] A-SEC-006 Questを単純反復して不正稼ぎできない
- [ ] A-SEC-007 Mob farm報酬制限が正常
- [ ] A-SEC-008 FFA itemを通常ワールドへ持ち出せない
- [ ] A-SEC-009 通常itemをFFAへ持ち込めない
- [ ] A-SEC-010 管理用PersistentDataを一般操作で偽装できない
- [ ] A-SEC-011 管理コマンド権限を迂回できない
- [ ] A-SEC-012 保護チャンクをPiston / Explosion等で迂回できない
- [ ] A-SEC-013 異常な文字列・長文でFriend / Proposal等を壊せない
- [ ] A-SEC-014 YAMLへ危険なキーを注入できない
- [ ] A-SEC-015 連打・Spamでサーバー負荷が異常上昇しない

---

# A-PERFORMANCE / RECOVERY

- [ ] A-PERF-001 5人同時接続でTPSが安定する
- [ ] A-PERF-002 10人同時接続でTPSが安定する
- [ ] A-PERF-003 FFA複数人戦闘でTPSが安定する
- [ ] A-PERF-004 Mob大量召喚時に異常負荷が発生しない
- [ ] A-PERF-005 Structure生成時にサーバー停止級の負荷が出ない
- [ ] A-PERF-006 TextDisplay / Teleporter Entityが大量残留しない
- [ ] A-REC-001 定期バックアップが取得できる
- [ ] A-REC-002 data.ymlをバックアップから復元できる
- [ ] A-REC-003 ffa-stats.ymlをバックアップから復元できる
- [ ] A-REC-004 proposals.ymlをバックアップから復元できる
- [ ] A-REC-005 Shop / Structure関連データを復元できる

---

# A-PUBLIC 正式公開前

- [ ] A-PUB-001 mct-official.comにMifron紹介ページがある
- [ ] A-PUB-002 Java版参加方法が掲載されている
- [ ] A-PUB-003 Bedrock版参加方法が掲載されている
- [ ] A-PUB-004 Discord参加導線がある
- [ ] A-PUB-005 サーバールールが掲載されている
- [ ] A-PUB-006 FAQがある
- [ ] A-PUB-007 問い合わせ手段がある
- [ ] A-PUB-008 プライバシー上のデータ保存内容を整理する
- [ ] A-PUB-009 利用規約 / 運営ルールを整理する
- [ ] A-PUB-010 障害時の告知手段を決める
- [ ] A-PUB-011 支援 / 寄付を導入する場合の方針を確定する
- [ ] A-PUB-012 Minecraft内報酬と現実通貨の扱いを最終確認する

---

# B: v1.0後に回してよい

- [ ] B-001 Status UI全面改善
- [ ] B-002 Teleporter演出の追加改善
- [ ] B-003 新FFA Kit
- [ ] B-004 FFA大規模バランス変更
- [ ] B-005 新天候イベント
- [ ] B-006 新World
- [ ] B-007 新Minigame
- [ ] B-008 Slot Machine追加演出
- [ ] B-009 Proposal UI高度化
- [ ] B-010 Minecraft ↔ Discord投票の高度化
- [ ] B-011 Web上のPlayer Profile
- [ ] B-012 ランキング機能高度化
- [ ] B-013 AI自動実装 / 自動デバッグ構想

---

# リリース判定

## Alpha完了条件

- [ ] S-CORE完了
- [ ] S-JOIN完了
- [ ] S-WORLD完了
- [ ] S-INVENTORY完了
- [ ] S-DATA完了

## Closed Beta開始条件

- [ ] Alpha完了
- [ ] S-MP完了
- [ ] S-PROTECT完了
- [ ] S-FFA完了
- [ ] 重大Duplicationが存在しない

## Public Beta開始条件

- [ ] Closed Betaで致命的障害がない
- [ ] A-TELEPORTER完了
- [ ] A-SHOP完了
- [ ] A-FRIEND / STATUS完了
- [ ] Java / Bedrock双方で基本動作確認済み
- [ ] バックアップ / 復元確認済み

## v1.0正式公開条件

- [ ] Sランク全項目完了
- [ ] Aランクの公開必須項目完了
- [ ] 既知のCritical / Highバグが0件
- [ ] 既知のDuplication Exploitが0件
- [ ] 既知のデータ消失バグが0件
- [ ] Java / Bedrockの参加導線が公開済み
- [ ] 運営ルール・問い合わせ導線が公開済み
- [ ] Closed / Public Betaのフィードバックを反映済み

---

# テスト記録テンプレート

完了チェックを入れる際は、必要に応じてIssueやCommitへ証跡を残す。

```text
Test ID: S-INV-004
Date: YYYY-MM-DD
Build/Commit: <SHA or version>
Edition: Java / Bedrock / Both
Tester: <name>
Result: PASS / FAIL
Notes:
- 実施内容
- 発生した問題
- 関連Issue / Commit
```

---

# Backlogルール

v1.0に不要なアイデアを思いついた場合、このチェックリストへ新しいS/A項目として直接追加しない。

1. まずBacklogへ記録する。
2. v1.0の公開可否に直接影響するか判断する。
3. CriticalでなければBランクまたはv1.1以降へ回す。

Mifron v1.0の最優先目標は、機能数ではなく **安定性・データ保全・不正耐性・Java/Bedrock双方での遊びやすさ** とする。
