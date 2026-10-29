const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const assets = process.env.XMUSIC_TEST_PAGES || path.join(root, 'app/src/main/assets/stitch');
const script = fs.readFileSync(path.join(root, 'app/src/main/assets/playback.js'), 'utf8');
let checks = 0;
const folders = fs.readdirSync(assets);
for (const entry of folders) {
  const folder = entry.replace(/\.html$/, '');
  const file = entry.endsWith('.html') ? path.join(assets, entry) : path.join(assets, entry, 'code.html');
  if (!fs.existsSync(file)) continue;
  const dom = new JSDOM(fs.readFileSync(file, 'utf8'), { url: 'https://local.test/stitch/' + folder + '/code.html', runScripts: 'outside-only' });
  const w = dom.window, d = w.document;
  const calls = [];
  w.Android = new Proxy({}, { get: (_, name) => (...args) => calls.push([name, ...args]) });
  // Şablonun demo oynatıcı olayları da etkin; native köprü bunları bastırmalı.
  if (folder === 'imdi_al_yor') for (const tag of d.querySelectorAll('script:not([src])')) if (!tag.id) w.eval(tag.textContent);
  w.eval(script);
  const full = folder === 'imdi_al_yor';
  const mini = d.querySelector('aside');
  const title = full ? d.querySelector('main h3') : mini?.querySelector('p');
  if (title) {
    assert.equal(title.textContent, 'Henüz parça seçilmedi'); checks++;
    const state = { ready: true, id: 'audius:test', title: '<img onerror=bad()>', artist: 'Test artist', artwork: '', playing: true, position: 30000, duration: 120000, seekable: true, hasNext: false };
    w.__xmusicPlayback(state);
    assert.equal(title.textContent, state.title); assert.equal(title.children.length, 0); checks += 2;
    const button = (full ? d : mini).querySelector('[aria-label="Oynat veya Duraklat"]');
    if (button) { button.click(); assert.deepEqual(calls.pop(), ['togglePlayPause']); assert.equal(button.querySelector('span').textContent, 'pause'); checks += 2; }
    w.__xmusicPlayback({...state, playing:false, error:'Kaynak hatası'});
    assert.ok(d.querySelector('[role="status"]').textContent.includes('Kaynak hatası')); checks++;
    if (full) {
      assert.equal(d.getElementById('currentTime').textContent, '0:30');
      assert.equal(d.getElementById('totalTime').textContent, '2:00');
      assert.equal(d.getElementById('progressBar').style.width, '25%');
      const scrubber = d.getElementById('scrubberContainer');
      scrubber.getBoundingClientRect = () => ({left:0,width:200});
      scrubber.dispatchEvent(new w.MouseEvent('click',{bubbles:true,clientX:100}));
      assert.deepEqual(calls.pop(), ['seekTo',60000]);
      assert.equal(d.getElementById('currentTime').textContent, '0:30');
      assert.equal(d.querySelector('[aria-label="Sonraki Parça"]').disabled,true);
      checks += 6;
    } else {
      title.click(); assert.deepEqual(calls.pop(), ['navigateTo','imdi_al_yor']); checks++;
    }
  }
  dom.window.close();
}
console.log('Playback UI: ' + checks + ' assertions passed across Stitch pages.');
assert.ok(checks >= 40);
