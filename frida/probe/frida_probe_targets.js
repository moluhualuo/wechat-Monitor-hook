/*
 * Quick probe to check if WeChat plaintext hook targets exist on this device
 */
'use strict';

function log(tag, msg) { console.log(`[${tag}] ${msg}`); }

Java.perform(function () {
  log('PROBE', 'Java VM ready');

  const targets = [
    'ok.y7',
    'com.tencent.mm.storage.f8',
    'com.tencent.mm.storage.h8',
    'com.tencent.mm.autogen.events.ChatMsgNotifyEvent',
    'com.tencent.wcdb.database.SQLiteDatabase',
    'com.tencent.wcdb.core.Database',
  ];

  targets.forEach(function (className) {
    try {
      const Cls = Java.use(className);
      const methods = Cls.class.getDeclaredMethods();
      const methodNames = methods.map(function (m) { return m.getName(); }).sort();

      // Filter for msg-related methods
      const interesting = methodNames.filter(function (n) {
        return /^(j|getContent|getTalker|P0|getMsgId|getType|getCreateTime|setText|insert|update|delete|execSQL)$/i.test(n);
      });

      log('FOUND', className + ' (' + methods.length + ' methods)');
      if (interesting.length > 0) {
        log('  TARGETS', interesting.join(', '));
      }
    } catch (e) {
      log('MISSING', className + ' — ' + e.message);
    }
  });

  log('DONE', 'Probe complete — if ok.y7 is MISSING, you need to find new hook targets for this WeChat version');
});
