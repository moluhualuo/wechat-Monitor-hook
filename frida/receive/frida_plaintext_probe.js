/*
 * Capture WeChat plaintext from WCDB ContentValues.
 * Output: send_time | talker | contentUnicode | contentB64
 */
'use strict';

function now() {
  const d = new Date();
  return d.toISOString().replace('T', ' ').replace('Z', '');
}

function printLine(line) {
  try {
    send({ type: 'msg', line: `[${now()}] [MSG] ${line}` });
  } catch (e) {}
}

function toStr(v) {
  if (v === null || v === undefined) return '';
  try { return (typeof v === 'string') ? v : v.toString(); } catch (e) { return ''; }
}

function trimText(v) {
  return toStr(v).replace(/\r/g, ' ').replace(/\n/g, ' ').trim();
}

function escapeUnicode(s) {
  try {
    let out = '';
    for (let i = 0; i < s.length; i++) {
      const c = s.charCodeAt(i);
      if (c >= 0x20 && c <= 0x7e) out += s[i];
      else if (c <= 0xff) out += '\\x' + ('0' + c.toString(16)).slice(-2);
      else out += '\\u' + ('0000' + c.toString(16)).slice(-4);
    }
    return out;
  } catch (e) {
    return '';
  }
}

function base64Utf8(s) {
  try {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    const bytes = [];
    for (let i = 0; i < s.length; i++) {
      let c = s.charCodeAt(i);
      if (c < 0x80) bytes.push(c);
      else if (c < 0x800) bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
      else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
        const n = s.charCodeAt(++i);
        c = 0x10000 + ((c & 0x3ff) << 10) + (n & 0x3ff);
        bytes.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 0x3f), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
      } else bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
    }

    let out = '';
    for (let i = 0; i < bytes.length; i += 3) {
      const b1 = bytes[i];
      const b2 = i + 1 < bytes.length ? bytes[i + 1] : NaN;
      const b3 = i + 2 < bytes.length ? bytes[i + 2] : NaN;
      out += chars.charAt(b1 >> 2);
      out += chars.charAt(((b1 & 3) << 4) | ((b2 || 0) >> 4));
      out += isNaN(b2) ? '=' : chars.charAt(((b2 & 15) << 2) | ((b3 || 0) >> 6));
      out += isNaN(b3) ? '=' : chars.charAt(b3 & 63);
    }
    return out;
  } catch (e) {
    return '';
  }
}

function base64DecodeUtf8(b64) {
  try {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    const bytes = [];
    let i = 0;
    while (i < b64.length) {
      const e1 = chars.indexOf(b64.charAt(i++));
      const e2 = chars.indexOf(b64.charAt(i++));
      const e3 = chars.indexOf(b64.charAt(i++));
      const e4 = chars.indexOf(b64.charAt(i++));
      const c1 = (e1 << 2) | (e2 >> 4);
      const c2 = ((e2 & 15) << 4) | (e3 >> 2);
      const c3 = ((e3 & 3) << 6) | e4;
      bytes.push(c1);
      if (e3 !== 64) bytes.push(c2);
      if (e4 !== 64) bytes.push(c3);
    }

    let out = '';
    for (let j = 0; j < bytes.length;) {
      const b1 = bytes[j++];
      if (b1 < 0x80) {
        out += String.fromCharCode(b1);
      } else if (b1 < 0xe0) {
        const b2 = bytes[j++];
        out += String.fromCharCode(((b1 & 0x1f) << 6) | (b2 & 0x3f));
      } else if (b1 < 0xf0) {
        const b2 = bytes[j++];
        const b3 = bytes[j++];
        out += String.fromCharCode(((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f));
      } else {
        const b2 = bytes[j++];
        const b3 = bytes[j++];
        const b4 = bytes[j++];
        let cp = ((b1 & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3f) << 6) | (b4 & 0x3f);
        cp -= 0x10000;
        out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      }
    }
    return out;
  } catch (e) {
    return '';
  }
}

function cvGet(cv, key) {
  try { return trimText(cv.get(key)); } catch (e) { return ''; }
}

function formatTime(timestamp) {
  try {
    let ts = parseInt(timestamp, 10);
    if (isNaN(ts) || ts <= 0) return '';
    if (ts < 10000000000) ts *= 1000;
    return String(ts);
  } catch (e) {
    return '';
  }
}

function isUsefulContent(content) {
  if (!content) return false;
  if (content.length === 0 || content.length > 2000) return false;
  if (/^[0-9:：\-\s/年月日.]+$/.test(content)) return false;
  if (/^(发送|复制|删除|转发|收藏|引用|翻译|多选|提醒)$/.test(content)) return false;
  return true;
}

let lastFingerprint = '';
let lastFingerprintAt = 0;

function shouldEmit(talker, createTime, content) {
  const fingerprint = talker + '|' + createTime + '|' + content;
  const t = Date.now();
  if (fingerprint === lastFingerprint && t - lastFingerprintAt < 3000) return false;
  lastFingerprint = fingerprint;
  lastFingerprintAt = t;
  return true;
}

function emitContentValues(table, cv) {
  if (table !== 'message') return;
  const talker = cvGet(cv, 'talker');
  const content = cvGet(cv, 'content');
  const createTime = cvGet(cv, 'createTime');
  if (!talker || !isUsefulContent(content)) return;
  if (!shouldEmit(talker, createTime, content)) return;

  const contentB64 = base64Utf8(content);
  const decoded = base64DecodeUtf8(contentB64);
  const line = formatTime(createTime) + ' | ' + talker + ' | decodedUnicode=' + escapeUnicode(decoded) + ' | contentB64=' + contentB64;
  setTimeout(function () { printLine(line); }, 0);
}

function hookCvMethod(Sdb, methodName, tableIndex, cvIndex) {
  try {
    Sdb[methodName].overloads.forEach(function (ov) {
      ov.implementation = function () {
        const table = toStr(arguments[tableIndex]);
        const cv = arguments[cvIndex];
        const ret = ov.apply(this, arguments);
        try { emitContentValues(table, cv); } catch (e) {}
        return ret;
      };
    });
    printLine('HOOK OK: SQLiteDatabase.' + methodName);
  } catch (e) {
    printLine('HOOK FAIL SQLiteDatabase.' + methodName + ': ' + e.message);
  }
}

Java.perform(function () {
  printLine('Java VM ready');
  try {
    const Sdb = Java.use('com.tencent.wcdb.database.SQLiteDatabase');
    hookCvMethod(Sdb, 'insert', 0, 2);
    hookCvMethod(Sdb, 'insertWithOnConflict', 0, 2);
    printLine('OUTPUT: send_time_ms | talker | decodedUnicode | contentB64');
  } catch (e) {
    printLine('HOOK FAIL SQLiteDatabase: ' + e.message);
  }

  setTimeout(function () {
    console.log(`[${now()}] [READY] Send or receive a message now`);
  }, 500);
});
