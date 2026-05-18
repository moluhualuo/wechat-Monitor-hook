'use strict';

function log(tag, msg) {
  const line = `[${new Date().toISOString().replace('T',' ').replace('Z','')}] [${tag}] ${msg}`;
  console.log(line);
  send(line);
}

Java.perform(function () {
  // Enumerate all native methods in SQLiteDatabase and hook them
  const targets = [
    'com.tencent.wcdb.database.SQLiteDatabase',
    'com.tencent.wcdb.database.SQLiteConnection',
    'com.tencent.wcdb.core.Database',
  ];

  targets.forEach(function (className) {
    try {
      const C = Java.use(className);
      const methods = C.class.getDeclaredMethods();
      log('JNI-SCAN', `=== ${className} methods ===`);
      methods.forEach(function (m) {
        const name = m.getName();
        const isNative = (m.getModifiers() & 0x100) !== 0; // Modifier.NATIVE
        if (isNative) {
          log('JNI-NATIVE', `${className}.${name}`);
        } else if (/native|sql|exec|insert|update|delete|query|raw/i.test(name)) {
          log('JNI-METHOD', `${className}.${name}`);
        }
      });
    } catch (e) {
      log('JNI-ERR', `${className}: ${e}`);
    }
  });

  // Hook the actual native methods in SQLiteConnection
  try {
    const Conn = Java.use('com.tencent.wcdb.database.SQLiteConnection');
    // Try to hook common native methods
    ['nativeExecSQL', 'nativeExecute', 'execute', 'executeForChangedRowCount',
     'executeForLastInsertedRowId', 'executeForLong', 'executeForString',
     'nativePrepare', 'prepare', 'nativeBind']
    .forEach(function (m) {
      try {
        Conn[m].overloads.forEach(function (ov) {
          ov.implementation = function () {
            log('JNI-HIT', `SQLiteConnection.${m} args=${Array.from(arguments)}`);
            return ov.apply(this, arguments);
          };
        });
        log('JNI-HOOKED', `SQLiteConnection.${m}`);
      } catch (e) {}
    });
  } catch (e) {
    log('JNI-ERR', 'SQLiteConnection hook: ' + e);
  }

  // Also hook Database class
  try {
    const Db = Java.use('com.tencent.wcdb.core.Database');
    ['executeSQL', 'execSQL', 'rawQuery', 'insert', 'update', 'delete']
    .forEach(function (m) {
      try {
        Db[m].overloads.forEach(function (ov) {
          ov.implementation = function () {
            log('DB-HIT', `Database.${m} args=${Array.from(arguments)}`);
            return ov.apply(this, arguments);
          };
        });
        log('DB-HOOKED', `Database.${m}`);
      } catch (e) {}
    });
  } catch (e) {
    log('JNI-ERR', 'Database hook: ' + e);
  }

  log('JNI-DONE', 'All JNI scan complete');
});
