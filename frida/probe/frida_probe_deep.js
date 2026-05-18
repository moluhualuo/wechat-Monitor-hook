'use strict';

function log(tag, msg) { console.log('[' + tag + '] ' + msg); }

Java.perform(function () {
  log('PROBE', 'Deep probe started');

  // 1. Check class hierarchy: is ok.y7 a superclass of f8?
  try {
    var f8 = Java.use('com.tencent.mm.storage.f8');
    var superClass = f8.class.getSuperclass();
    log('f8.super', superClass ? superClass.getName() : 'null');
    if (superClass) {
      var superSuper = superClass.getSuperclass();
      log('f8.super.super', superSuper ? superSuper.getName() : 'null');
    }
  } catch (e) {
    log('ERR', 'f8 hierarchy: ' + e.message);
  }

  try {
    var y7 = Java.use('ok.y7');
    var superClass = y7.class.getSuperclass();
    log('ok.y7.super', superClass ? superClass.getName() : 'null');
    var interfaces = y7.class.getInterfaces();
    var ifNames = [];
    for (var i = 0; i < interfaces.length; i++) ifNames.push(interfaces[i].getName());
    log('ok.y7.interfaces', ifNames.join(', '));
  } catch (e) {
    log('ERR', 'ok.y7 hierarchy: ' + e.message);
  }

  // 2. Check h8 methods - find U8, W8
  try {
    var h8 = Java.use('com.tencent.mm.storage.h8');
    var methods = h8.class.getDeclaredMethods();
    var interesting = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var name = m.getName();
      if (/^[A-Z]\d/.test(name) || /insert|update|save|add/i.test(name)) {
        var params = m.getParameterTypes();
        var paramStr = [];
        for (var j = 0; j < params.length; j++) paramStr.push(params[j].getSimpleName());
        interesting.push(name + '(' + paramStr.join(', ') + ') -> ' + m.getReturnType().getSimpleName());
      }
    }
    interesting.sort();
    log('h8 storage methods', interesting.length + ' found:');
    interesting.forEach(function (s) { log('  h8', s); });
  } catch (e) {
    log('ERR', 'h8 methods: ' + e.message);
  }

  // 3. Check xv0.t9 methods
  try {
    var t9 = Java.use('xv0.t9');
    var methods = t9.class.getDeclaredMethods();
    var all = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var params = m.getParameterTypes();
      var paramStr = [];
      for (var j = 0; j < params.length; j++) paramStr.push(params[j].getSimpleName());
      all.push(m.getName() + '(' + paramStr.join(', ') + ') -> ' + m.getReturnType().getSimpleName());
    }
    all.sort();
    log('xv0.t9', all.length + ' methods:');
    all.forEach(function (s) { log('  t9', s); });
  } catch (e) {
    log('ERR', 'xv0.t9: ' + e.message);
  }

  // 4. Check xv0.wc methods
  try {
    var wc = Java.use('xv0.wc');
    var methods = wc.class.getDeclaredMethods();
    var all = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var params = m.getParameterTypes();
      var paramStr = [];
      for (var j = 0; j < params.length; j++) paramStr.push(params[j].getSimpleName());
      all.push(m.getName() + '(' + paramStr.join(', ') + ') -> ' + m.getReturnType().getSimpleName());
    }
    all.sort();
    log('xv0.wc', all.length + ' methods:');
    all.forEach(function (s) { log('  wc', s); });
  } catch (e) {
    log('ERR', 'xv0.wc: ' + e.message);
  }

  // 5. Check px0.q1 and px0.r1 (send message classes)
  try {
    var q1 = Java.use('px0.q1');
    var methods = q1.class.getDeclaredMethods();
    var all = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var params = m.getParameterTypes();
      var paramStr = [];
      for (var j = 0; j < params.length; j++) paramStr.push(params[j].getSimpleName());
      all.push(m.getName() + '(' + paramStr.join(', ') + ') -> ' + m.getReturnType().getSimpleName());
    }
    all.sort();
    log('px0.q1', all.length + ' methods:');
    all.forEach(function (s) { log('  q1', s); });
  } catch (e) {
    log('ERR', 'px0.q1: ' + e.message);
  }

  try {
    var r1 = Java.use('px0.r1');
    var methods = r1.class.getDeclaredMethods();
    var all = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var params = m.getParameterTypes();
      var paramStr = [];
      for (var j = 0; j < params.length; j++) paramStr.push(params[j].getSimpleName());
      all.push(m.getName() + '(' + paramStr.join(', ') + ') -> ' + m.getReturnType().getSimpleName());
    }
    all.sort();
    log('px0.r1', all.length + ' methods:');
    all.forEach(function (s) { log('  r1', s); });
  } catch (e) {
    log('ERR', 'px0.r1: ' + e.message);
  }

  // 6. f8 all no-arg methods (full list)
  try {
    var f8 = Java.use('com.tencent.mm.storage.f8');
    var methods = f8.class.getDeclaredMethods();
    var noArg = [];
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      if (m.getParameterTypes().length === 0) {
        noArg.push(m.getName() + ' -> ' + m.getReturnType().getSimpleName());
      }
    }
    noArg.sort();
    log('f8 no-arg', noArg.length + ' methods:');
    noArg.forEach(function (s) { log('  f8', s); });
  } catch (e) {
    log('ERR', 'f8 no-arg: ' + e.message);
  }

  log('DONE', 'Deep probe complete');
});
