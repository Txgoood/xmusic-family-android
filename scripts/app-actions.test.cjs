const {JSDOM}=require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const assets=process.env.XMUSIC_TEST_PAGES||path.join(root,'app/src/main/assets/stitch');
const script=fs.readFileSync(path.join(root,'app/src/main/assets/app-actions.js'),'utf8');
const player=fs.readFileSync(path.join(root,'app/src/main/assets/playback.js'),'utf8');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/example/MainActivity.kt'),'utf8').split('evaluateJavascript("""')[1].split('""".trimIndent()')[0];
let db='{}',checks=0,recentState=[],topState=[],resumeOverride={},profileMock={},approvedMock=[],reviewMock={};const report=[];
function open(folder,session={}){
 const f=path.join(assets,fs.existsSync(path.join(assets,folder+'.html'))?folder+'.html':folder+'/code.html');
 const dom=new JSDOM(fs.readFileSync(f,'utf8'),{url:'https://local.test/stitch/'+folder+'/code.html',runScripts:'outside-only'});
 const w=dom.window,calls=[];
 w.Android=new Proxy({}, {get:(_,key)=>(...args)=>{calls.push([key,...args]);return false;}});
 w.AndroidActions=new Proxy({}, {get:(_,key)=>(...args)=>{calls.push([key,...args]);if(key==='profileState')return JSON.stringify(profileMock);
 if(key==='childTracks')return JSON.stringify(approvedMock);
 if(key==='childReview')return JSON.stringify(reviewMock);
 if(['requestChildReview','setChildAge','resolveChildReview','approveChildTrack'].includes(key))return true;
 if(key==='familyMix')return JSON.stringify(approvedMock);
 if(key==='loadPlaybackResume')return JSON.stringify(resumeOverride);
 if(key==='topSearches')return JSON.stringify(topState);
 if(key==='recordSearch'){
  const query=args[0],key=query.toLocaleLowerCase('tr');let item=topState.find(x=>x.query.toLocaleLowerCase('tr')===key);
  if(!item){item={query,count:0};topState.push(item);}item.count++;topState.sort((a,b)=>b.count-a.count);return;
 }
 if(key==='searchHistory'){
 const [action,query]=args;const text=(query||'').replace(/\s+/g,' ').trim();
 const same=value=>value.toLocaleLowerCase('tr')===text.toLocaleLowerCase('tr');
 if(action==='add'&&text.length>=2&&text.length<=100)recentState=[text,...recentState.filter(v=>!same(v))].slice(0,12);
 if(action==='remove')recentState=recentState.filter(v=>!same(v));
 if(action==='clear')recentState=[];
 return JSON.stringify(recentState);
 }if(key==='loadLibrary')return db;if(key==='saveLibrary'){db=args[0];return true;}}});
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
x=open('kitapl_m');assert.ok(x.d.querySelector('main h1').textContent.includes('Kitaplığım'));
assert.ok(x.d.querySelector('#createPlaylistBtn'));checks+=2;
const favoriteCard=[...x.d.querySelectorAll('h2')].find(n=>n.textContent==='Beğenilen Şarkılar').closest('[role="button"]');
favoriteCard.click();assert.ok(x.d.querySelector('main').textContent.includes(state.title));checks++;
x.d.querySelector('#searchToggleBtn').click();
const libInput=x.d.querySelector('input[aria-label="Kitaplıkta ara"]');
libInput.value='bulunmayacak';libInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));
assert.ok(!x.d.getElementById('xm-library-tracks').textContent.includes(state.title));checks++;
libInput.value='';libInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));
let b=x.d.querySelector('[aria-label="Yeni Çalma Listesi Oluştur"]');b.click();
x.d.querySelector('#xm-dialog input').value='Aile Listem';[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Kaydet').click();
const playlist=JSON.parse(db).playlists[0];assert.equal(playlist.name,'Aile Listem');checks++;
x.dom.window.close();
x=open('alma_listesi_y_netimi',{'xm-playlist':playlist.id});x.w.__xmusicPlayback(state);
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Çalan Parçayı Ekle').click();assert.equal(JSON.parse(db).playlists[0].tracks[0].id,state.id);checks++;
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Dinle').click();assert.deepEqual(x.calls.at(-1),['resolveAudiusTrack','test123',false]);checks++;
x.dom.window.close();
x=open('aile_profili_se_imi');
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Aktif profil görselini değiştir').click();
[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Telefondan fotoğraf seç').click();
assert.deepEqual(x.calls.at(-1),['pickActiveAvatar']);checks++;x.dom.window.close();
x=open('uyku_zamanlay_c_s');
assert.equal(x.d.querySelector('header h1').textContent,'Uyku Zamanlayıcısı');
assert.ok(!x.d.querySelector('main').textContent.includes('Boran’ın Odası'));
x.w.__xmusicPlayback({...state,duration:120000,position:30000});
assert.ok(x.d.getElementById('xm-timer-track').textContent.includes('Gerçek Parça'));
assert.ok(x.d.getElementById('xm-timer-track-remaining').textContent.includes('1:30'));
x.d.getElementById('toggle-fade').checked=true;checks+=4;
x.d.querySelector('[data-minutes="15"]').click();x.d.getElementById('submit-timer-btn').click();assert.deepEqual(x.calls.at(-1),['setSleepTimer',15]);checks++;
x.d.getElementById('cancel-timer-btn').click();assert.deepEqual(x.calls.at(-1),['setSleepTimer',0]);checks++;
x.w.__xmusicPlayback({...state,sleepRemaining:60000});assert.ok(x.d.getElementById('xm-sleep-status').textContent.includes('1 dakika'));checks++;
x.dom.window.close();
x=open('oynatma_s_ras_queue');x.w.__xmusicPlayback({...state,queue:[state,{...state,id:'local:content://test',title:'İkinci'}],index:0});
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Kaldır').click();assert.deepEqual(x.calls.at(-1),['queueAction','remove',0,0]);checks++;
x.dom.window.close();
x=open('podcast_program_ve_b_l_m_detay',{'xm-feed':'https://example.org/rss'});
assert.deepEqual(x.calls.find(c=>c[0]==='loadRss'),['loadRss','https://example.org/rss',1]);checks++;
assert.ok(x.d.getElementById('followBtn'));assert.ok(x.d.getElementById('heroPlayBtn'));checks+=2;
assert.equal(x.d.querySelector('section.mt-6').style.display,'none');checks++;
x.w.__xmusicRssResult(1,{title:'Gerçek Podcast',description:'Gerçek program açıklaması',items:[{id:'podcast:one',title:'Bölüm',artist:'Program'}]});
assert.equal(x.d.querySelector('main h2').textContent.trim(),'Gerçek Podcast');
assert.equal(x.d.getElementById('showDescription').textContent,'Gerçek program açıklaması');
assert.equal(x.d.querySelector('section.mt-6 h3').textContent,'Bölüm');checks+=3;
x.d.getElementById('heroPlayBtn').click();assert.deepEqual(x.calls.at(-1),['playPodcast','podcast:one',false]);checks++;
x.d.querySelector('#filterContainer button:nth-child(2)').click();assert.ok(x.d.querySelector('main').textContent.includes('Bu filtrede bölüm yok.'));checks++;
x.d.querySelector('#filterContainer button:first-child').click();
x.w.__xmusicPlayback({ready:true,id:'podcast:one',title:'Bölüm',artist:'Program',playing:true,queue:[]});
x.d.querySelector('aside [aria-label="Oynat veya Duraklat"]').click();assert.deepEqual(x.calls.at(-1),['togglePlayPause']);checks++;
x.d.querySelector('article [aria-label="Oynat"]').click();assert.deepEqual(x.calls.at(-1),['playPodcast','podcast:one',false]);checks++;
x.d.querySelector('article [aria-label="İndir"]').click();assert.deepEqual(x.calls.at(-1),['downloadPodcast','podcast:one']);checks++;
x.w.__xmusicRssResult(999,{title:'Eski yanıt',items:[]});assert.ok(!x.d.querySelector('main').textContent.includes('Eski yanıt'));checks++;
x.dom.window.close();
x=open('kitapl_m');x.w.__xmusicLocalImported({id:'local:content://test',title:'Kendi Kaydım',artist:'Yerel'});assert.ok(JSON.parse(db).local.some(t=>t.title==='Kendi Kaydım'));assert.ok(x.d.getElementById('xm-library-tracks').textContent.includes('Kendi Kaydım'));assert.ok(x.d.querySelector('main h1').textContent.includes('Kitaplığım'));checks+=3;x.dom.window.close();
// Keşfet regresyonu: gerçek filtre yönlendirmesi, yerleşim, eski yanıt ve çalma yolu.
x=open('arama_ve_ke_fet');
let searchTimers=[];
x.w.setTimeout=(fn,ms)=>{if(ms===350)searchTimers.push(fn);return searchTimers.length;};
x.w.clearTimeout=()=>{};
function flushSearch(){const pending=searchTimers;searchTimers=[];pending.forEach(fn=>fn());}
function searchCalls(){return x.calls.filter(c=>c[0]==='searchAppleCatalog');}
const searchInput=x.d.getElementById('search-input');
const searchShell=searchInput.closest('section');
const results=x.d.getElementById('xmusic-live-search');
assert.equal(searchShell.nextElementSibling,results);checks++;
assert.ok(!results.textContent.includes('YouTube · Deneme'));
assert.equal([...results.querySelectorAll('button')].length,0);checks++;
searchInput.value='Mabel';searchInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));flushSearch();
assert.deepEqual(searchCalls().map(c=>c[3]),['artists','albums','songs']);checks++;
const initial=searchCalls()[0];
x.w.__xmusicAppleResult(initial[4],{items:[{id:'1',title:'Mabel Matiz',subtitle:'Pop'}]});
assert.ok(results.textContent.includes('Mabel Matiz'));checks++;
[...results.querySelectorAll('button')].find(b=>b.textContent.includes('Mabel Matiz')).click();flushSearch();
let call=searchCalls().at(-1);
assert.deepEqual(call.slice(0,4),['searchAppleCatalog','Mabel','1','albums']);checks++;
x.w.__xmusicAppleResult(call[4],{items:[{id:'2',title:'Gök Nerede',subtitle:'Mabel Matiz',category:'Albüm'}]});
[...results.querySelectorAll('button')].find(b=>b.textContent.includes('Gök Nerede')).click();flushSearch();
call=searchCalls().at(-1);assert.equal(call[3],'songs');assert.equal(call[2],'2');checks++;
x.w.__xmusicAppleResult(call[4],{items:[{id:'3',title:'Gel',subtitle:'Mabel Matiz'}]});
[...results.querySelectorAll('button')].find(b=>b.textContent.includes('Gel')).click();flushSearch();
let source=x.calls.filter(c=>c[0]==='searchExperimentalSource').at(-1);
assert.equal(source[1],'Mabel Matiz Gel');checks++;
x.w.__xmusicExperimentalResult(source[2],{items:[{id:'leTDrileoGI',title:'Mabel Matiz - Gel',artist:'Yükleyen'}]});
[...results.querySelectorAll('button')].find(b=>b.textContent.includes('Mabel Matiz - Gel')).click();
assert.deepEqual(x.calls.at(-1),['playExperimentalSource','leTDrileoGI','Mabel Matiz - Gel','Yükleyen']);checks++;
for(const kind of ['songs','artists','albums']){
 x.d.querySelector('[data-filter="'+kind+'"]').click();flushSearch();
 call=searchCalls().at(-1);assert.equal(call[3],kind);assert.equal(call[2],'');
 assert.equal(x.d.querySelector('[data-filter="'+kind+'"]').getAttribute('aria-pressed'),'true');checks+=3;
}
const latest=searchCalls().at(-1);
x.w.__xmusicAppleResult(initial[4],{items:[{id:'9',title:'Eski sonuç'}]});
assert.ok(!results.textContent.includes('Eski sonuç'));checks++;
x.w.__xmusicAppleResult(latest[4],{items:[{id:'10',title:"Don't <img src=x>",subtitle:'Sanatçı',category:'Albüm'}]});
assert.ok(results.textContent.includes("Don't <img src=x>"));assert.equal(results.querySelector('img[src="x"]'),null);checks+=2;
x.d.getElementById('search-clear-btn').click();
assert.equal(searchInput.value,'');assert.equal(results.style.display,'none');assert.equal(results.nextElementSibling.style.display,'');checks+=3;
assert.ok(!x.d.querySelector('main').textContent.includes('Apple Türkiye müzik kataloğu'));
assert.ok(!x.d.querySelector('main').textContent.includes('YouTube · Deneme'));checks+=2;
x.dom.window.close();

// Son aramalar: demo yerine gerçek veri, gecikmeli kayıt, tekrar arama ve kalıcı silme.
recentState=[];
x=open('arama_ve_ke_fet');
assert.ok(x.d.getElementById('history-items-wrapper').textContent.includes('Henüz arama yapmadın'));checks++;
let recentTimers=[];
x.w.setTimeout=(fn,ms)=>{if(ms===350||ms===1200)recentTimers.push({fn,ms});return recentTimers.length;};
x.w.clearTimeout=()=>{};
function flushRecent(ms){const ready=recentTimers.filter(t=>t.ms===ms);recentTimers=recentTimers.filter(t=>t.ms!==ms);ready.forEach(t=>t.fn());}
let recentInput=x.d.getElementById('search-input');
recentInput.value='Tarkan';recentInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));flushRecent(350);
assert.equal(recentState.length,0);checks++;
flushRecent(1200);
assert.deepEqual(recentState,['Tarkan']);checks++;
x.d.getElementById('search-clear-btn').click();
const recentArea=x.d.getElementById('history-items-wrapper');
assert.ok(recentArea.textContent.includes('Tarkan'));checks++;
recentArea.querySelector('[aria-label="Tarkan yeniden ara"]').click();flushRecent(350);
assert.equal(recentInput.value,'Tarkan');
assert.equal(x.calls.filter(c=>c[0]==='searchAppleCatalog').at(-1)[1],'Tarkan');checks+=2;
x.d.getElementById('search-clear-btn').click();
const beforeRemove=x.calls.filter(c=>c[0]==='searchAppleCatalog').length;
recentArea.querySelector('[aria-label="Kaldır Tarkan"]').click();
assert.equal(recentState.length,0);
assert.equal(x.calls.filter(c=>c[0]==='searchAppleCatalog').length,beforeRemove);checks+=2;
x.dom.window.close();
x=open('arama_ve_ke_fet');
assert.ok(x.d.getElementById('history-items-wrapper').textContent.includes('Henüz arama yapmadın'));checks++;
x.dom.window.close();
recentState=['Tarkan',"Don't <img src=x>"];
x=open('arama_ve_ke_fet');
assert.ok(x.d.getElementById('history-items-wrapper').textContent.includes("Don't <img src=x>"));
assert.equal(x.d.getElementById('history-items-wrapper').querySelector('img'),null);checks+=2;
x.d.getElementById('clear-all-history-btn').click();
assert.deepEqual(recentState,[]);checks++;
x.dom.window.close();
x=open('arama_ve_ke_fet');
assert.ok(x.d.getElementById('history-items-wrapper').textContent.includes('Henüz arama yapmadın'));
assert.equal(x.d.getElementById('clear-all-history-btn').style.display,'none');checks+=2;
x.dom.window.close();

// Podcast keşfi: arama, eski yanıtı reddetme, takip, bölüm ve indirme.
x=open('podcast_merkezi');
assert.ok(x.d.querySelector('main h1').textContent.includes('Podcast'));checks++;
const podcastInput=x.d.getElementById('xm-podcast-search');
assert.ok(podcastInput);checks++;
podcastInput.value='bilim';podcastInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));
podcastInput.closest('form').dispatchEvent(new x.w.Event('submit',{bubbles:true,cancelable:true}));
let podcastCall=x.calls.filter(t=>t[0]==='searchPodcasts').at(-1);
assert.equal(podcastCall[1],'bilim');checks++;
const oldPodcastId=podcastCall[2];
podcastInput.value='tarih';podcastInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));
x.w.__xmusicPodcastSearchResult(oldPodcastId,{items:[{title:'Eski sonuç',feed:'https://example.org/old'}]});
assert.ok(!x.d.getElementById('xm-podcast-results').textContent.includes('Eski sonuç'));checks++;
podcastInput.closest('form').dispatchEvent(new x.w.Event('submit',{bubbles:true,cancelable:true}));
podcastCall=x.calls.filter(t=>t[0]==='searchPodcasts').at(-1);
const pod={title:'Tarih Sohbetleri',artist:'Yayıncı',feed:'https://example.org/rss',artwork:'https://example.org/img.jpg',genre:'Tarih',episodeCount:5,url:'https://podcasts.apple.com/tr/podcast/id1'};
x.w.__xmusicPodcastSearchResult(podcastCall[2],{items:[pod]});
let podcastResults=x.d.getElementById('xm-podcast-results');
assert.ok(podcastResults.textContent.includes('Tarih Sohbetleri'));checks++;
[...podcastResults.querySelectorAll('button')].find(b=>b.textContent==='Takip Et').click();
assert.ok(JSON.parse(db).feeds.some(f=>f.url===pod.feed));checks++;
podcastResults.querySelector('[aria-label="Bölümleri Aç"]').click();
assert.deepEqual(x.calls.at(-1),['navigateTo','podcast_program_ve_b_l_m_detay']);checks++;
const podInfo=x.w.sessionStorage.getItem('xm-feed-info');x.dom.window.close();
x=open('podcast_program_ve_b_l_m_detay',{'xm-feed':pod.feed,'xm-feed-info':podInfo});
const rssCall=x.calls.find(t=>t[0]==='loadRss');
assert.equal(rssCall[1],pod.feed);checks++;
x.w.__xmusicRssResult(rssCall[2],{feed:pod.feed,title:'Tarih Sohbetleri',items:[{id:'podcast:test',title:'İlk bölüm',artist:'Tarih Sohbetleri',published:'2026',description:'Yeni bölüm'}]});
assert.ok(x.d.querySelector('main').textContent.includes('Yeni bölüm'));checks++;
x.d.querySelector('article [aria-label="Oynat"]').click();
assert.deepEqual(x.calls.at(-1),['playPodcast','podcast:test',false]);checks++;
x.d.querySelector('article [aria-label="İndir"]').click();
assert.deepEqual(x.calls.at(-1),['downloadPodcast','podcast:test']);checks++;
x.dom.window.close();
x=open('podcast_merkezi');
assert.ok(x.d.getElementById('xm-podcast-following').textContent.includes('Tarih Sohbetleri'));
assert.ok(x.d.querySelector('#podcast-chips'));checks+=2;
assert.equal([...x.d.querySelectorAll('main h2')].find(h=>h.textContent.includes('Aile & Çocuk Podcastleri')).closest('section').style.display,'none');checks++;
x.dom.window.close();
x=open('kitapl_m');
const podcastsChip=[...x.d.querySelectorAll('.filter-chip')].find(b=>b.textContent.trim()==="Podcast'ler");
podcastsChip.click();
assert.ok(x.d.getElementById('xm-library-tracks').textContent.includes('Tarih Sohbetleri'));checks++;
x.d.getElementById('xm-library-tracks').querySelector('button').click();
assert.deepEqual(x.calls.at(-1),['navigateTo','podcast_program_ve_b_l_m_detay']);checks++;
x.dom.window.close();

// Gerçek tür rafı ve kişisel arama sıralaması.
topState=[];x=open('arama_ve_ke_fet');
const genreSection=[...x.d.querySelectorAll('main section')].find(s=>s.querySelector('h2')?.textContent.includes('Hızlı Göz At'));
const popCard=genreSection.querySelector('.grid').firstElementChild;popCard.click();
let shelfCall=x.calls.filter(k=>k[0]==='searchMusicShelf').at(-1);
assert.ok(shelfCall[1].includes('pop'));checks++;
x.w.__xmusicMusicShelfResult(shelfCall[2],{items:[{id:'leTDrileoGI',title:'Örnek Şarkı',artist:'Sanatçı'}]});
x.d.querySelector('#xm-dialog [aria-label="Örnek Şarkı oynat"]').click();
assert.deepEqual(x.calls.filter(k=>k[0]==='assignMusicGenre').at(-1),['assignMusicGenre','leTDrileoGI','Pop']);checks++;
assert.deepEqual(x.calls.at(-1),['playExperimentalSource','leTDrileoGI','Örnek Şarkı','Sanatçı']);checks++;
[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Kapat').click();
const topInput=x.d.getElementById('search-input');
topInput.value='Tarkan';topInput.dispatchEvent(new x.w.Event('input',{bubbles:true}));
topInput.dispatchEvent(new x.w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
topInput.dispatchEvent(new x.w.KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
assert.equal(topState[0].count,1);checks++;
x.d.getElementById('search-clear-btn').click();
const topSection=[...x.d.querySelectorAll('main section')].find(s=>s.querySelector('h2')?.textContent.includes('En Çok Arananlar'));
assert.ok(topSection.textContent.includes('Tarkan'));checks++;
topSection.querySelector('button').click();assert.equal(topInput.value,'Tarkan');checks++;
x.dom.window.close();
x=open('m_zik_merkezi');
assert.equal(x.d.querySelector('nav[data-active-classes] a[data-path="muzik"]').getAttribute('aria-current'),'page');checks++;
const shelfRequests=x.calls.filter(k=>k[0]==='searchMusicShelf');assert.equal(shelfRequests.length,2);checks++;
x.w.__xmusicMusicShelfResult(shelfRequests[0][2],{items:[{id:'leTDrileoGI',title:'Stüdyo kayıt',artist:'Sanatçı'}]});
x.d.querySelector('[aria-label="Stüdyo kayıt oynat"]').click();assert.deepEqual(x.calls.at(-1),['playExperimentalSource','leTDrileoGI','Stüdyo kayıt','Sanatçı']);checks++;
x.dom.window.close();

// Ana sayfa: gerçek geçmiş, 6 kayıt, devam etme, iki kaynak seçkisi.
x=open('ana_sayfa');
assert.ok(!x.d.querySelector('main h1').textContent.includes('Burak'));
assert.ok(!x.d.querySelector('main').textContent.includes('4/6'));
assert.ok(x.calls.some(c=>c[0]==='fetchWeather'));checks+=3;
x.w.__xmusicWeatherResult(1,{city:'İstanbul',temperature:20,code:61});
assert.ok(x.d.querySelector('main').textContent.includes('İstanbul · 20°'));checks++;
const playingHome={ready:true,id:'experimental:leTDrileoGI',title:'Son Parça',artist:'Sanatçı',artwork:'https://i.ytimg.com/vi/leTDrileoGI/mqdefault.jpg',playing:true,position:42000,duration:180000,queue:[]};
x.w.__xmusicPlayback(playingHome);
assert.equal(JSON.parse(db).resume.position,42000);checks++;
assert.equal(x.d.getElementById('xm-resume-percentage').textContent,'%23');
x.w.__xmusicPlayback({...playingHome,position:90000});
assert.equal(x.d.getElementById('xm-resume-percentage').textContent,'%50');
x.w.__xmusicPlayback({...playingHome,position:180000});
assert.equal(x.d.getElementById('xm-resume-percentage').textContent,'%100');
x.w.__xmusicPlayback({...playingHome,duration:0});
assert.equal(x.d.getElementById('xm-resume-percentage').textContent,'');
x.w.__xmusicPlayback(playingHome);checks+=4;

let homeRecent=[...x.d.querySelectorAll('main section')].find(s=>s.querySelector('h2')?.textContent.trim()==='Son Dinlenenler');
assert.ok(homeRecent.textContent.includes('Son Parça'));
assert.ok(homeRecent.querySelector('.grid').children.length<=6);checks+=2;
let homeMix=[...x.d.querySelectorAll('main section')].find(s=>s.querySelector('h2')?.textContent.includes('Günlük Aile Miksi'));
assert.equal(homeMix.children[1].children.length,2);checks++;
homeMix.children[1].children[1].click();
assert.ok(x.calls.some(c=>c[0]==='familyMix'&&c[1]==='history'));checks++;
x.dom.window.close();
x=open('ana_sayfa');
let resumeCard=[...x.d.querySelectorAll('main section')].find(s=>s.textContent.includes('Kaldığın Yerden Devam Et')).children[1];
resumeCard.click();
assert.deepEqual(x.calls.at(-1),['resumeExperimentalSource','leTDrileoGI','Son Parça','Sanatçı',42000]);checks++;
x.dom.window.close();
resumeOverride={id:'experimental:leTDrileoGI',title:'Son Parça',artist:'Sanatçı',position:99000,duration:180000,updatedAt:Date.now()+1000};
x=open('ana_sayfa');
resumeCard=[...x.d.querySelectorAll('main section')].find(s=>s.textContent.includes('Kaldığın Yerden Devam Et')).children[1];
resumeCard.click();assert.deepEqual(x.calls.at(-1),['resumeExperimentalSource','leTDrileoGI','Son Parça','Sanatçı',99000]);checks++;
x.dom.window.close();resumeOverride={};
x=open('ayarlar_ve_profil_y_netimi');
const eqTitle=[...x.d.querySelectorAll('main h4')].find(h=>h.textContent.trim()==='Ekolayzır');
eqTitle.closest('.p-4').click();assert.deepEqual(x.calls.at(-1),['openEqualizer']);checks++;
const switches=[...x.d.querySelectorAll('main input[type="checkbox"]')];
assert.equal(switches.length,3);
assert.equal(switches[0].disabled,true);assert.equal(switches[1].disabled,false);assert.equal(switches[2].disabled,true);checks+=4;
const automaticSync=[...x.d.querySelectorAll('details button')].find(b=>b.textContent==='Otomatik ortak arşiv bağlantısını aç');
automaticSync.click();assert.deepEqual(x.calls.at(-1),['enableContinuousDrive']);checks++;
const advancedDrive=[...x.d.querySelectorAll('summary')].find(n=>n.textContent==='Gelişmiş işlemler');
assert.ok(advancedDrive);assert.equal(advancedDrive.parentElement.open,false);
advancedDrive.click();assert.equal(advancedDrive.parentElement.open,true);
advancedDrive.click();assert.equal(advancedDrive.parentElement.open,false);checks+=4;
const musicAccess=[...x.d.querySelectorAll('details button')].find(b=>b.textContent==='Mevcut müziklere erişim ver');
musicAccess.click();assert.deepEqual(x.calls.at(-1),['authorizeExistingDriveAudio']);checks++;
const sharedFolder=[...x.d.querySelectorAll('details button')].find(b=>b.textContent==='Ortak müzik klasörünü seç / değiştir');
sharedFolder.click();assert.deepEqual(x.calls.at(-1),['chooseSharedDriveFolder']);checks++;
const driveSummary=x.d.querySelector('details summary');assert.ok(driveSummary.textContent.includes('Google hesap ve Drive'));
driveSummary.click();assert.equal(driveSummary.parentElement.open,true);driveSummary.click();assert.equal(driveSummary.parentElement.open,false);checks+=3;
const settingsMini=x.d.querySelector('main .fixed.bottom-2.inset-x-2');
assert.equal(settingsMini.style.display,'none');
x.w.__xmusicPlayback({ready:true,id:'experimental:leTDrileoGI',title:'Gerçek Şarkı',artist:'Sanatçı',playing:true,queue:[]});
assert.notEqual(settingsMini.style.display,'none');assert.ok(settingsMini.textContent.includes('Gerçek Şarkı'));assert.ok(!settingsMini.textContent.includes('Antidepresan'));checks+=4;
switches[1].checked=true;switches[1].dispatchEvent(new x.w.Event('change',{bubbles:true}));
assert.deepEqual(x.calls.at(-1),['setDownloadWifiOnly',true]);checks++;
const profileSection=[...x.d.querySelectorAll('main section')].find(s=>s.querySelector('h2')?.textContent.includes('Aile Profilleri'));
assert.equal(profileSection.querySelector('.grid').children.length,1);assert.ok(profileSection.querySelector('.grid').textContent.includes('Ben'));checks+=2;
x.dom.window.close();

// Yerel roller, PIN çıkışı, onaylı çocuk kitaplığı ve aile miksleri.
profileMock={active:'kid',role:'child',parentConfigured:true,profiles:[{id:'default',name:'Tolga',role:'admin',hasPin:false,needsParentPin:true},{id:'kid',name:'Can',role:'child',dailyMinutes:30,usedMinutes:2,approvedCount:1}]};
approvedMock=[{id:'experimental:abcdefghijk',title:'Onaylı Şarkı',artist:'Sanatçı'}];
x=open('ana_sayfa');
assert.ok(x.d.querySelector('main').textContent.includes('Onaylı Şarkı'));
assert.ok(!x.d.querySelector('main').textContent.includes('Stüdyo & Türkçe Pop'));checks+=2;x.dom.window.close();
x=open('aile_profili_se_imi');
assert.ok(![...x.d.querySelectorAll('button')].some(b=>b.textContent==='Yeni Profil Ekle'));
assert.ok(![...x.d.querySelectorAll('button')].some(b=>b.textContent==='Düzenle'));
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Seç').click();
assert.ok(x.d.querySelector('[role="dialog"]').getAttribute('aria-label').includes('Ebeveyn'));
checks+=3;x.dom.window.close();
profileMock={active:'default',role:'admin',parentConfigured:true,profiles:[{id:'default',name:'Tolga',role:'admin',mixEnabled:true}]};
x=open('aile_profili_se_imi');
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Yeni Profil Ekle').click();
x.d.querySelector('input[aria-label="Profil adı"]').value='Can';
x.d.querySelector('select').value='child';x.d.querySelector('select').dispatchEvent(new x.w.Event('change'));
x.d.querySelector('input[aria-label="Ebeveyn PIN’i"]').value='1357';
[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Kaydet').click();
assert.deepEqual(x.calls.at(-2)?.[0]==='saveFamilyProfile'?x.calls.at(-2):x.calls.at(-1),['saveFamilyProfile','','Can','child','','','1357',60,false]);checks++;
x.dom.window.close();profileMock={};approvedMock=[];

// Çocuk istek oluşturabilir; yönetici gerekçeli kuyruğu inceler ve reddeder.
profileMock={active:'kid',role:'child',parentConfigured:true,profiles:[{id:'kid',name:'Can',role:'child',dailyMinutes:30}]};
x=open('ana_sayfa');
x.d.querySelector('input[aria-label="Ebeveynden şarkı iste"]').value='İstediğim şarkı';
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Ebeveynden iste').click();
assert.equal(x.calls.find(c=>c[0]==='requestChildReview')[1],'kid');
assert.equal(JSON.parse(x.calls.find(c=>c[0]==='requestChildReview')[2]).title,'İstediğim şarkı');checks+=2;x.dom.window.close();
profileMock={active:'default',role:'admin',parentConfigured:true,profiles:[{id:'default',name:'Tolga',role:'admin'},{id:'kid',name:'Can',role:'child',approvedCount:0,pendingCount:1}]};
reviewMock={ageGroup:'under7',pending:[{title:'Şüpheli kayıt',requestKey:'query:test',reason:'Bilgi eksik; onay gerekli'}],denied:[]};
x=open('aile_profili_se_imi');
[...x.d.querySelectorAll('button')].find(b=>b.textContent==='Onaylı içerikleri yönet').click();
assert.equal(x.d.querySelector('select[aria-label="Çocuğun yaş grubu"]').value,'under7');
assert.ok(x.d.querySelector('#xm-dialog').textContent.includes('Bilgi eksik; onay gerekli'));
x.d.querySelector('input[aria-label="Ebeveyn PIN’i"]').value='1357';
[...x.d.querySelectorAll('#xm-dialog button')].find(b=>b.textContent==='Reddet').click();
assert.deepEqual(x.calls.find(c=>c[0]==='resolveChildReview'),['resolveChildReview','kid','query:test',true,'1357']);checks+=3;
x.dom.window.close();profileMock={};reviewMock={};

console.log('Application actions: '+checks+' assertions passed.');
console.log('Audited pages: '+report.length+'; active controls: '+report.reduce((n,p)=>n+p.active,0)+'; explicitly blocked controls: '+report.reduce((n,p)=>n+p.blocked.length,0));
if(process.env.XMUSIC_AUDIT_OUTPUT)fs.writeFileSync(process.env.XMUSIC_AUDIT_OUTPUT,JSON.stringify(report,null,2));










