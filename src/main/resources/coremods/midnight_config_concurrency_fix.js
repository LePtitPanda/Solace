var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');

// MidnightLib stores every mod's config entries in shared, unsynchronized maps.
// Forge constructs mods in parallel, so two calls to MidnightConfig.init can
// otherwise mutate the global entries map while one of them is iterating it.
// Making the static initializer synchronized keeps each complete registration
// and JSON load atomic without changing MidnightLib's config behavior.
function initializeCoreMod() {
    return {
        'SynchronizeMidnightConfigInit': {
            'target': {
                'type': 'METHOD',
                'class': 'eu.midnightdust.lib.config.MidnightConfig',
                'methodName': 'init',
                'methodDesc': '(Ljava/lang/String;Ljava/lang/Class;)V'
            },
            'transformer': function(method) {
                ASMAPI.log('INFO', '[Solace] Serializing MidnightConfig.init to prevent parallel config corruption');
                method.access = method.access | Opcodes.ACC_SYNCHRONIZED;
                return method;
            }
        }
    };
}
