var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Type = Java.type('org.objectweb.asm.Type');
var Handle = Java.type('org.objectweb.asm.Handle');

var RENAMES = {};

function replaceText(text) {
    if (text == null) {
        return text;
    }

    var result = String(text);
    Object.keys(RENAMES).forEach(function(oldName) {
        result = result.split(oldName).join(RENAMES[oldName]);
    });
    return result;
}

function replaceType(type) {
    var descriptor = replaceText(type.getDescriptor());
    if (descriptor.equals(type.getDescriptor())) {
        return type;
    }
    return Type.getType(descriptor);
}

function replaceHandle(handle) {
    var owner = replaceText(handle.getOwner());
    var desc = replaceText(handle.getDesc());
    if (owner.equals(handle.getOwner()) && desc.equals(handle.getDesc())) {
        return handle;
    }
    return new Handle(handle.getTag(), owner, handle.getName(), desc, handle.isInterface());
}

function patchInvokeDynamic(node) {
    node.desc = replaceText(node.desc);
    for (var i = 0; i < node.bsmArgs.length; i++) {
        var arg = node.bsmArgs[i];
        if (arg instanceof Handle) {
            node.bsmArgs[i] = replaceHandle(arg);
        } else if (arg instanceof Type) {
            node.bsmArgs[i] = replaceType(arg);
        } else if (arg instanceof java.lang.String) {
            node.bsmArgs[i] = replaceText(arg);
        }
    }
}

function patchClass(label) {
    return function(classNode) {
        ASMAPI.log('INFO', '[Solace] Patching TravelOptics Cataclysm references in ' + label);

        classNode.superName = replaceText(classNode.superName);
        classNode.signature = replaceText(classNode.signature);
        for (var interfaceIndex = 0; interfaceIndex < classNode.interfaces.size(); interfaceIndex++) {
            classNode.interfaces.set(interfaceIndex, replaceText(classNode.interfaces.get(interfaceIndex)));
        }

        classNode.fields.forEach(function(field) {
            field.desc = replaceText(field.desc);
            field.signature = replaceText(field.signature);
        });

        classNode.methods.forEach(function(method) {
            method.desc = replaceText(method.desc);
            method.signature = replaceText(method.signature);

            if (method.localVariables != null) {
                method.localVariables.forEach(function(local) {
                    local.desc = replaceText(local.desc);
                    local.signature = replaceText(local.signature);
                });
            }

            if (method.tryCatchBlocks != null) {
                method.tryCatchBlocks.forEach(function(block) {
                    block.type = replaceText(block.type);
                });
            }

            var iterator = method.instructions.iterator();
            while (iterator.hasNext()) {
                var insn = iterator.next();
                if (insn.owner !== undefined) {
                    insn.owner = replaceText(insn.owner);
                }
                if (insn.desc !== undefined) {
                    insn.desc = replaceText(insn.desc);
                }
                if (insn.cst !== undefined && insn.cst instanceof Type) {
                    insn.cst = replaceType(insn.cst);
                }
                if (insn.bsmArgs !== undefined) {
                    patchInvokeDynamic(insn);
                }
            }
        });

        ASMAPI.log('INFO', '[Solace] Patched TravelOptics Cataclysm references in ' + label);
        return classNode;
    };
}

function initializeCoreMod() {
    return {
        'PatchTravelOpticsItemsCataclysmNames': {
            'target': { 'type': 'CLASS', 'name': 'com.gametechbc.traveloptics.init.TravelopticsItems' },
            'transformer': patchClass('TravelopticsItems')
        },
        'PatchTravelOpticsEntitiesCataclysmNames': {
            'target': { 'type': 'CLASS', 'name': 'com.gametechbc.traveloptics.init.TravelopticsEntities' },
            'transformer': patchClass('TravelopticsEntities')
        },
        'PatchTravelOpticsAquaVortexCataclysmNames': {
            'target': { 'type': 'CLASS', 'name': 'com.gametechbc.traveloptics.entity.projectiles.aqua_vortex.AquaVortexEntity' },
            'transformer': patchClass('AquaVortexEntity')
        },
        'PatchTravelOpticsEternalMaelstromCataclysmNames': {
            'target': { 'type': 'CLASS', 'name': 'com.gametechbc.traveloptics.entity.projectiles.aqua_trident.EternalMaelstromTridentEntity' },
            'transformer': patchClass('EternalMaelstromTridentEntity')
        }
    };
}
