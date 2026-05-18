console.log('[*] Enumerating all loaded modules...');
const modules = Process.enumerateModules();
console.log('[*] Total modules: ' + modules.length);
console.log('[*] Searching for WCDB, sqlite, aff_biz, wechat related modules...');

modules.forEach(function(m) {
  const name = m.name.toLowerCase();
  if (name.indexOf('wcdb') >= 0 ||
      name.indexOf('sqlite') >= 0 ||
      name.indexOf('aff') >= 0 ||
      name.indexOf('biz') >= 0 ||
      name.indexOf('wechat') >= 0 ||
      name.indexOf('tencent') >= 0) {
    console.log('[+] ' + m.name + ' @ ' + m.base + ' size=' + m.size);
  }
});

console.log('[*] Done');
