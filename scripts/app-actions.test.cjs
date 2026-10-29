const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const assets=process.env.XMUSIC_TEST_PAGES||path.join(root,'app/src/main/assets/stitch');
const script=fs.readFileSync(path.join(root,'app/src/main/assets/app-actions.js'),'utf8');
const player=fs.readFileSync(path.join(root,'app/src/main/assets/playback.js'),'utf8');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/example/MainActivity.kt'),'utf8').split('evaluateJavascript("""')[1].split('""".trimIndent()')[0];
let db='{}',checks=0;const report=[];
function open(folder,session={}){
 const f=path.join(assets,fs.existsSync(path.join(assets,folder+'.html'))?folder+'.html':folder+'/code.html');
 const dom=new JSDOM(fs.readFileSync(f,'utf8'),{url:'https://local.test/stitch/'+folder+'/code.html',runScripts:'outside-only'});
 const w=dom.window,calls=[];
 w.Android=new Proxy({}, {get:(_,key)=>(...args)=>{calls.push([key,...args]);return false;}});
 w.AndroidActions=new Proxy({}, {get:(_,key)=>(...args)=>{calls.push([key,...args]);if(key==='loadLibrary')return db;if(key==='saveLibrary'){db=args[0];return true;}}});
 Object.entries(session).forEach(([k,v])=>w.sessionStorage.setItem(k,v));
 // Ekranın mevcut demo olayları gerçek uygulamadaki gibi önce yüklenir.
 for(const tag of w.document.querySelectorAll('script:not([src])'))if(!tag.id){try{w.eval(tag.textContent);}catch(e){throw new Error(folder+': '+e.message);}}
 w.eval(script);w.eval(player);w.eval(main);
 return {dom,w,d:w.document,calls};
}
for(const entry of fs.readdirSync(assets)){
 const folder=entry.replace(/\.html$/,'');const f=path.join(assets,entry.endsWith('.html')?entry:entry+'/code.html');if(!fs.existsSync(f))continue;
 const x=open(folder);
 const audit=x.w.__xmusicActionsAudit();
 const missing=audit.filter(b=>!b.action&&!b.blocked&&b.label!=='Ayarlar');assert.equal(missing.length,0,folder+JSON.stringify(missing));checks++;
 report.push({page:folder,active:audit.filter(x=>x.action).length,blocked:audit.filter(x=>x.blocked).map(x=>({label:x.label,reason:x.blocked}))});
 x.dom.window.close();
}
let x=open('imdi_al_yor');
const state={ready:true,id:'audius:test123',title:'Gerçek Parça',artist:'Sanatçı',playing:true,queue:[],duration:120000,position:1000};
x.w.__xmusicPlayback(state);
x.d.querySelector('[aria-label="Favorilere Ekle"]').click();
assert.equal(JSON.parse(db).favorites[0].id,state.id);checks++;
x.d.querySelector('[aria-label="Oynat veya Duraklat"]').click();
assert.equal(x.calls.filter(c=>c[0]==='togglePlayPause').length,1);checks++;
x.d.querySelector('[aria-label="Karışık Çal"]').click();assert.deepEqual(x.calls.at(-1),['queueAction','shuffle',0,0]);checks++;
x.dom.window.close();
x=open('kitapl_m');assert.ok(x.d.querySelector('main').textContent.includes(state.title));checks++;
let b=[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Yeni Çalma Listesi Oluştur');b.click();
x.d.querySelector('#xm-dialog input').value='Aile Listem';[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Kaydet').click();
const playlist=JSON.parse(db).playlists[0];assert.equal(playlist.name,'Aile Listem');checks++;
x.dom.window.close();
x=open('alma_listesi_y_netimi',{'xm-playlist':playlist.id});x.w.__xmusicPlayback(state);
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Çalan Parçayı Ekle').click();assert.equal(JSON.parse(db).playlists[0].tracks[0].id,state.id);checks++;
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Dinle').click();assert.deepEqual(x.calls.at(-1),['resolveAudiusTrack','test123',false]);checks++;
x.dom.window.close();
x=open('uyku_zamanlay_c_s');x.d.querySelector('[data-minutes="15"]').click();x.d.getElementById('submit-timer-btn').click();assert.deepEqual(x.calls.at(-1),['setSleepTimer',15]);checks++;
x.d.getElementById('cancel-timer-btn').click();assert.deepEqual(x.calls.at(-1),['setSleepTimer',0]);checks++;
x.w.__xmusicPlayback({...state,sleepRemaining:60000});assert.ok(x.d.getElementById('xm-sleep-status').textContent.includes('1 dakika'));checks++;
x.dom.window.close();
x=open('oynatma_s_ras_queue');x.w.__xmusicPlayback({...state,queue:[state,{...state,id:'local:content://test',title:'İkinci'}],index:0});
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Kaldır').click();assert.deepEqual(x.calls.at(-1),['queueAction','remove',0,0]);checks++;
x.dom.window.close();
x=open('podcast_program_ve_b_l_m_detay',{'xm-feed':'https://example.org/rss'});
assert.deepEqual(x.calls.find(c=>c[0]==='loadRss'),['loadRss','https://example.org/rss',1]);checks++;
x.w.__xmusicRssResult(1,{title:'Gerçek Podcast',items:[{id:'podcast:one',title:'Bölüm',artist:'Program'}]});
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Oynat').click();assert.deepEqual(x.calls.at(-1),['playPodcast','podcast:one',false]);checks++;
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='İndir').click();assert.deepEqual(x.calls.at(-1),['downloadPodcast','podcast:one']);checks++;
x.w.__xmusicRssResult(999,{title:'Eski yanıt',items:[]});assert.ok(!x.d.querySelector('main').textContent.includes('Eski yanıt'));checks++;
x.dom.window.close();
x=open('kitapl_m');x.w.__xmusicLocalImported({id:'local:content://test',title:'Kendi Kaydım',artist:'Yerel'});assert.ok(JSON.parse(db).local.some(t=>t.title==='Kendi Kaydım'));checks++;x.dom.window.close();
console.log('Application actions: '+checks+' assertions passed.');
console.log('Audited pages: '+report.length+'; active controls: '+report.reduce((n,p)=>n+p.active,0)+'; explicitly blocked controls: '+report.reduce((n,p)=>n+p.blocked.length,0));
if(process.env.XMUSIC_AUDIT_OUTPUT)fs.writeFileSync(process.env.XMUSIC_AUDIT_OUTPUT,JSON.stringify(report,null,2));
