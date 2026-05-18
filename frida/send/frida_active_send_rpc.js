/*
 * WeChat 8.0.68 active friend text sender.
 * It does not send on load. Call rpc.exports.sendtext(talker, content, type) manually.
 */
'use strict';

function now() {
  const d = new Date();
  return d.toISOString().replace('T', ' ').replace('Z', '');
}

function log(tag, msg) {
  const line = `[${now()}] [${tag}] ${msg}`;
  console.log(line);
  try { send({ type: tag, line: line }); } catch (e) {}
}

function toStr(v) {
  if (v === null || v === undefined) return '';
  try {
    if (typeof v === 'string') return v;
    return v.toString();
  } catch (e) {
    return '<toString-error>';
  }
}

function getField(obj, name) {
  try {
    const f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(obj);
  } catch (e) {
    return null;
  }
}

function dumpQ1(q1) {
  if (!q1) return '<null q1>';
  return 'talker=' + toStr(getField(q1, 'b')) +
    ' type=' + toStr(getField(q1, 'e')) +
    ' flag=' + toStr(getField(q1, 'f')) +
    ' mode=' + toStr(getField(q1, 'i')) +
    ' content=' + toStr(getField(q1, 'd'));
}

let ready = false;
let R1 = null;

Java.perform(function () {
  try {
    R1 = Java.use('px0.r1');
    ready = true;
    log('READY', 'RPC ready. Call sendtext(talker, content, type). Default type is 1.');
  } catch (e) {
    log('INIT-FAIL', e.message);
  }
});

rpc.exports = {
  sendtext: function (talker, content, type) {
    if (!talker || !content) {
      throw new Error('Usage: sendtext(talker, content, type). type defaults to 1.');
    }

    const msgType = type === undefined || type === null ? 1 : parseInt(type, 10);
    if (msgType !== 1) {
      throw new Error('This RPC sender is limited to text type=1 for safety.');
    }

    return Java.perform(function () {
      if (!ready || R1 === null) throw new Error('px0.r1 is not ready');

      const q1 = R1.a(talker);
      log('SEND-BUILD', dumpQ1(q1));

      q1.e(content);
      q1.h(msgType);
      log('SEND-READY', dumpQ1(q1));

      q1.b();
      log('SEND-CALLED', dumpQ1(q1));

      return 'send invoked: talker=' + talker + ' type=' + msgType + ' content=' + content;
    });
  },

  ping: function () {
    return ready ? 'ready' : 'not-ready';
  }
};
