'use strict';

const mods = ['libWCDB.so', 'libaff_biz.so', 'libwechatnormsg.so', 'libwechatmm.so'];

mods.forEach(name => {
  try {
    const mod = Process.getModuleByName(name);
    const exps = mod.enumerateExports();
    console.log(`\n=== ${name} (${exps.length} exports) ===`);

    // Filter for interesting exports
    const interesting = exps.filter(e =>
      /(step|exec|prepare|insert|update|run|execute|msg|message|notify|send|receive|dispatch|handle|process)/i.test(e.name)
    );

    console.log(`  Interesting: ${interesting.length}`);
    interesting.slice(0, 100).forEach(e => {
      console.log(`  ${e.name} @ ${e.address}`);
    });
  } catch (e) {
    console.log(`${name}: not loaded (${e.message})`);
  }
});

console.log('\n[DONE]');