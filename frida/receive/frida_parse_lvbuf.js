/*
 * Parse WeChat message lvbuffer — e2 LVBuffer fields + msgsource XML
 */
'use strict';

function now() { const d = new Date(); return d.toISOString().replace('T',' ').replace('Z',''); }
function log(tag, msg) { console.log(`[${now()}] [${tag}] ${msg}`); }

function toStr(v) {
  if (v === null || v === undefined) return '<null>';
  try { return (typeof v === 'string') ? v : v.toString(); } catch (e) { return '<err>'; }
}

function hexDump(byteArr, maxLen) {
  if (!byteArr) return '<null>';
  try {
    const arr = Java.array('byte', byteArr);
    const len = Math.min(arr.length, maxLen || 1024);
    let hex = '';
    let ascii = '';
    let result = `[${arr.length} bytes]\n`;
    for (let i = 0; i < len; i++) {
      const b = arr[i] & 0xff;
      hex += ('0' + b.toString(16)).slice(-2) + ' ';
      ascii += (b >= 32 && b < 127) ? String.fromCharCode(b) : '.';
      if ((i + 1) % 16 === 0 || i === len - 1) {
        result += '  ' + hex + ' |' + ascii + '|\n';
        hex = '';
        ascii = '';
      }
    }
    if (arr.length > len) result += `  ... truncated, total ${arr.length} bytes\n`;
    return result;
  } catch (e) { return `<dump err: ${e}>`; }
}

function readU16(bytes, state) {
  if (state.off + 2 > bytes.length) throw new Error(`u16 overflow at ${state.off}`);
  const v = ((bytes[state.off] & 0xff) << 8) | (bytes[state.off + 1] & 0xff);
  state.off += 2;
  return v;
}

function readI32(bytes, state) {
  if (state.off + 4 > bytes.length) throw new Error(`i32 overflow at ${state.off}`);
  const v = ((bytes[state.off] & 0xff) << 24) | ((bytes[state.off + 1] & 0xff) << 16) |
      ((bytes[state.off + 2] & 0xff) << 8) | (bytes[state.off + 3] & 0xff);
  state.off += 4;
  return v;
}

function readString(bytes, state) {
  const len = readU16(bytes, state);
  if (state.off + len > bytes.length) throw new Error(`string overflow len=${len} at ${state.off}`);
  let s = '';
  for (let i = 0; i < len; i++) s += String.fromCharCode(bytes[state.off + i] & 0xff);
  state.off += len;
  try { return decodeURIComponent(escape(s)); } catch (e) { return s; }
}

function readBytes(bytes, state) {
  const len = readU16(bytes, state);
  if (state.off + len > bytes.length) throw new Error(`bytes overflow len=${len} at ${state.off}`);
  const out = bytes.slice(state.off, state.off + len);
  state.off += len;
  return out;
}

function parseLvBuffer(byteArr) {
  const bytes = Array.prototype.slice.call(Java.array('byte', byteArr)).map(b => b & 0xff);
  const result = { ok: false, fields: {}, endOffset: 0 };
  if (bytes.length === 0) return { ok: false, error: 'empty' };
  if (bytes[0] !== 0x7b) return { ok: false, error: `bad-start ${bytes[0]}` };
  if (bytes[bytes.length - 1] !== 0x7d) return { ok: false, error: `bad-end ${bytes[bytes.length - 1]}` };

  const st = { off: 1 };
  const fields = result.fields;
  fields.source = readString(bytes, st);
  fields.sourceFlag = readI32(bytes, st);
  fields.bizClientMsgId = readString(bytes, st);
  fields.bizChatId = readI32(bytes, st);
  fields.bizChatUserId = readI32(bytes, st);
  fields.msgSeq = readI32(bytes, st);
  fields.flag = readI32(bytes, st);
  fields.historyId = readI32(bytes, st);
  fields.reserved = readI32(bytes, st);
  fields.transContent = readString(bytes, st);
  fields.transBrandWording = readString(bytes, st);
  fields.msgSource = readString(bytes, st);
  fields.msgSourceType = readI32(bytes, st);
  fields.pushContent = readString(bytes, st);
  fields.extraBytes = readBytes(bytes, st);
  fields.fromUsername = readString(bytes, st);
  fields.toUsername = readString(bytes, st);
  fields.bizChatUserVersion = readI32(bytes, st);
  fields.bizChatVersion = readI32(bytes, st);
  fields.isShowTimer = readI32(bytes, st);
  fields.createTimeHigh = readI32(bytes, st);
  fields.createTimeLow = readI32(bytes, st);
  fields.historySource = readString(bytes, st);
  result.ok = true;
  result.endOffset = st.off;
  return result;
}

function formatLvBuffer(parsed) {
  if (!parsed.ok) return `error=${parsed.error}`;
  const f = parsed.fields;
  return Object.keys(f).filter(k => {
    const v = f[k];
    return Array.isArray(v) ? v.length > 0 : v !== '' && v !== 0;
  }).map(k => `${k}=${Array.isArray(f[k]) ? '<byte[' + f[k].length + ']>' : f[k]}`).join(' | ');
}

Java.perform(function () {
  log('INIT', 'Java VM ready');

  const Sdb = Java.use('com.tencent.wcdb.database.SQLiteDatabase');

  Sdb.insertWithOnConflict.overloads.forEach(function (ov) {
    ov.implementation = function () {
      const table = toStr(arguments[0]);
      const r = ov.apply(this, arguments);
      if (table === 'message') {
        const cv = arguments[2];
        const lvbuf = cv.get('lvbuffer');
        const content = toStr(cv.get('content'));
        const talker = toStr(cv.get('talker'));
        const type = toStr(cv.get('type'));
        const isSend = toStr(cv.get('isSend'));
        const msgId = toStr(cv.get('msgId'));

        log('MSG', `msgId=${msgId} talker=${talker} type=${type} isSend=${isSend} content=${content}`);

        if (lvbuf) {
          log('LVBUF-HEX', hexDump(lvbuf, 256));
          try {
            log('LVBUF-PARSE', formatLvBuffer(parseLvBuffer(lvbuf)));
          } catch (e) {
            log('LVBUF-PARSE', `exception=${e}`);
          }
        }
      }
      return r;
    };
  });
  log('HOOK', 'SQLiteDatabase.insertWithOnConflict — lvbuffer parser ready');
});

log('READY', 'Send messages now!');
