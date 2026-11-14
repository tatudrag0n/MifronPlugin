// MifronPlugin レリックテクスチャを Blockbench プロジェクト (.bbmodel) として書き出す
// 各 16x16 PNG を data URL で埋め込むため、単体で Blockbench に開ける。
const fs = require('fs');
const path = require('path');

const packDir = path.resolve(__dirname, '..', '..', 'resourcepack', 'mifron-relics');
const texDir = path.join(packDir, 'assets', 'mifron', 'textures', 'item', 'relic');
const outFile = path.join(packDir, 'Mifron-Relic-Textures.bbmodel');

const ids = [
  'hookshot', 'enchbook', 'jetpack', 'skates', 'resonance', 'fireball',
  'decayed_sword', 'enchgrind', 'chain', 'giantcore', 'seaswim', 'dashfoot',
  'reflect', 'nightclock', 'magnet', 'shrinkstar', 'lightning', 'blink',
  'totem', 'wither', 'bell', 'dragonbreath', 'vision'
];

const uuid = () => {
  const h = () => Math.floor(Math.random() * 0x10000).toString(16).padStart(4, '0');
  return `${h()}${h()}-${h()}-4${h().slice(1)}-${(8 + Math.floor(Math.random() * 4)).toString(16)}${h().slice(1)}-${h()}${h()}${h()}`;
};

const textures = [];
const elements = [];

ids.forEach((id, i) => {
  const png = path.join(texDir, `${id}.png`);
  if (!fs.existsSync(png)) throw new Error(`missing texture: ${png}`);
  const b64 = fs.readFileSync(png).toString('base64');

  const texUuid = uuid();
  textures.push({
    name: id,
    id: String(i),
    uuid: texUuid,
    folder: 'assets/mifron/textures/item/relic',
    namespace: 'mifron',
    path: `assets/mifron/textures/item/relic/${id}.png`,
    source: `data:image/png;base64,${b64}`,
    width: 16,
    height: 16
  });

  // 16x16 の板ポリゴンとして並べる（1 枚 = 1 要素、UV は全面）
  const col = i % 6;
  const row = Math.floor(i / 6);
  elements.push({
    name: id,
    uuid: uuid(),
    type: 'cube',
    from: [col * 2, -row * 2, 0],
    to: [col * 2 + 2, -row * 2 - 2, 0],
    origin: [0, 0, 0],
    faces: {
      north: { uv: [0, 0, 16, 16], texture: i },
      south: { uv: [0, 0, 16, 16], texture: i },
      east: { uv: [0, 0, 16, 16], texture: i },
      west: { uv: [0, 0, 16, 16], texture: i },
      up: { uv: [0, 0, 16, 16], texture: i },
      down: { uv: [0, 0, 16, 16], texture: i }
    }
  });
});

const project = {
  meta: {
    format_version: '4.10',
    model_format: 'java_block',
    box_uv: false
  },
  name: 'Mifron Relic Textures',
  model_identifier: '',
  visible_box: [1, 1, 0],
  variable_placeholders: '',
  variable_placeholder_buttons: [],
  timeline_setups: [],
  unhandled_root_fields: {},
  resolution: { width: 16, height: 16 },
  elements,
  outliner: elements.map(e => e.uuid),
  textures
};

fs.writeFileSync(outFile, JSON.stringify(project, null, 2), 'utf8');
const st = fs.statSync(outFile);
console.log(`wrote ${outFile} (${st.size} bytes, ${textures.length} textures)`);
