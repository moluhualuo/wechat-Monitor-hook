/*
 * WeChat 8.0.68 message monitor — capture message ContentValues
 * Simplified: focus on working Java-level hooks
 */
'use strict';

function now() { const d = new Date(); return d.toISOString().replace('T',' ').replace('Z',''); }
function log(tag, msg) { const line = `[${now()}] [${tag}] ${msg}`; console.log(line); }

function toStr(v) {
  if (v === null || v === undefined) return '<null>';
  try {
    if (typeof v === 'string') return v;
    if (typeof v === 'number' || typeof v === 'boolean') return String(v);
    return v.toString();
  } catch (e) { return '<err>'; }
}

function dumpCV(cv) {
  if (!cv) return '{}';
  try {
    const keys = cv.keySet();
    if (!keys) return toStr(cv);
    const iter = keys.iterator();
    const parts = [];
    while (iter.hasNext()) {
      const k = toStr(iter.next());
      const v = cv.get(k);
      const vs = (v === null) ? '<null>' :
          (v.getClass && v.getClass().getName && v.getClass().getName() === '[B') ?
          `<byte[${Java.array('byte', v).length}]>` : toStr(v);
      parts.push(`${k}=${vs}`);
    }
    return '{' + parts.join(', ') + '}';
  } catch (e) { return toStr(cv); }
}

function isMsgTable(table) {
  if (!table) return false;
  return table === 'message' || table.indexOf('msg') >= 0 || table.indexOf('chat') >= 0;
}

Java.perform(function () {
  console.log(`[${now()}] [INIT] Java VM ready`);

  // ====== SQLiteDatabase hooks ======
  const Sdb = Java.use('com.tencent.wcdb.database.SQLiteDatabase');

  // insert(String table, String nullColumnHack, ContentValues values)
  Sdb.insert.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (isMsgTable(table) || table === 'rconversation') {
        log('INSERT', `table=${table} cv=${dumpCV(arguments[2])} ret=${r}`);
      }
      return r;
    };
  });

  // insertWithOnConflict(String table, String nullColumnHack, ContentValues values, int algo)
  Sdb.insertWithOnConflict.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (isMsgTable(table) || table === 'rconversation') {
        log('INSERT-CONFLICT', `table=${table} algo=${arguments[3]} cv=${dumpCV(arguments[2])} ret=${r}`);
      }
      return r;
    };
  });

  // replace(String table, String nullColumnHack, ContentValues values)
  Sdb.replace.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (isMsgTable(table) || table === 'rconversation') {
        log('REPLACE', `table=${table} cv=${dumpCV(arguments[2])} ret=${r}`);
      }
      return r;
    };
  });

  // update(String table, ContentValues values, String whereClause, String[] whereArgs)
  Sdb.update.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (isMsgTable(table) || table === 'rconversation') {
        log('UPDATE', `table=${table} cv=${dumpCV(arguments[1])} where=${toStr(arguments[2])} ret=${r}`);
      }
      return r;
    };
  });

  // delete(String table, String whereClause, String[] whereArgs)
  Sdb.delete.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (isMsgTable(table) || table === 'rconversation') {
        log('DELETE', `table=${table} where=${toStr(arguments[1])} ret=${r}`);
      }
      return r;
    };
  });

  // execSQL(String sql)
  Sdb.execSQL.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const sql = toStr(arguments[0]);
      const s = sql.toLowerCase();
      if (s.indexOf('message') >= 0 || s.indexOf('msg') >= 0 ||
          s.indexOf('chat') >= 0 || s.indexOf('rconversation') >= 0 ||
          s.indexOf('talker') >= 0) {
        log('EXECSQL', sql);
      }
      return ov.apply(this, arguments);
    };
  });

  console.log(`[${now()}] [HOOK] SQLiteDatabase insert/insertWithOnConflict/replace/update/delete/execSQL`);

  // ====== ChatMsgNotifyEvent ======
  const Evt = Java.use('com.tencent.mm.autogen.events.ChatMsgNotifyEvent');
  Evt.$init.overloads.forEach(function (ov) {
    ov.implementation = function () {
      console.log(`[${now()}] [EVENT] ChatMsgNotifyEvent CONSTRUCTOR!`);
      try {
        const fields = this.getClass().getDeclaredFields();
        for (let i = 0; i < fields.length; i++) {
          const f = fields[i];
          f.setAccessible(true);
          try {
            const v = f.get(this);
            if (v !== null) console.log(`[${now()}] [EVENT-FIELD] ${f.getName()}=${toStr(v)}`);
          } catch (e) {}
        }
      } catch (e) {}
      return ov.apply(this, arguments);
    };
  });
  console.log(`[${now()}] [HOOK] ChatMsgNotifyEvent`);
});

console.log(`[${now()}] [READY] Send messages in WeChat NOW!`);