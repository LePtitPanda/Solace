var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');

// Guard that returns early if ModList is not ready yet.
// Use this for <clinit> and init() - methods that SET UP the attributes.
function buildModListGuard() {
    var skipLabel = new LabelNode();
    var patch = new InsnList();
    patch.add(ASMAPI.buildMethodCall(
        'net/minecraftforge/fml/ModList',
        'get',
        '()Lnet/minecraftforge/fml/ModList;',
        ASMAPI.MethodType.STATIC
    ));
    patch.add(new JumpInsnNode(Opcodes.IFNONNULL, skipLabel));
    patch.add(new InsnNode(Opcodes.RETURN));
    patch.add(skipLabel);
    return patch;
}

// Guard that returns early if the first static attribute field is null.
// Use this for injectLivingAttributes - a method that USES the attributes.
// We check the static field ElixirumAttributes.AGILITY (first field in the class,
// which will be null if <clinit> was skipped early).
function buildAttributeNullGuard() {
    var skipLabel = new LabelNode();
    var patch = new InsnList();
    // GETSTATIC ElixirumAttributes.AGILITY (first attribute field)
    patch.add(new FieldInsnNode(
        Opcodes.GETSTATIC,
        'dev/obscuria/elixirum/common/registry/ElixirumAttributes',
        'POTION_MASTERY',
        'Lnet/minecraft/world/entity/ai/attributes/Attribute;'
    ));
    patch.add(new JumpInsnNode(Opcodes.IFNONNULL, skipLabel));
    patch.add(new InsnNode(Opcodes.RETURN));
    patch.add(skipLabel);
    return patch;
}

function makeModListTransformer(label) {
    return function(method) {
        ASMAPI.log('INFO', '[Solace] Patching ' + label);
        method.instructions.insertBefore(method.instructions.getFirst(), buildModListGuard());
        ASMAPI.log('INFO', '[Solace] Patched ' + label);
        return method;
    };
}

function makeAttributeNullTransformer(label) {
    return function(method) {
        ASMAPI.log('INFO', '[Solace] Patching ' + label);
        method.instructions.insertBefore(method.instructions.getFirst(), buildAttributeNullGuard());
        ASMAPI.log('INFO', '[Solace] Patched ' + label);
        return method;
    };
}

function initializeCoreMod() {
    return {
        'PatchElixirumRegistriesClinit': {
            'target': { 'type': 'METHOD', 'class': 'dev.obscuria.elixirum.common.registry.ElixirumRegistries', 'methodName': '<clinit>', 'methodDesc': '()V' },
            'transformer': makeModListTransformer('ElixirumRegistries.<clinit>')
        },
        'PatchElixirumRegistriesInit': {
            'target': { 'type': 'METHOD', 'class': 'dev.obscuria.elixirum.common.registry.ElixirumRegistries', 'methodName': 'init', 'methodDesc': '()V' },
            'transformer': makeModListTransformer('ElixirumRegistries.init')
        },
        'PatchElixirumAttributesClinit': {
            'target': { 'type': 'METHOD', 'class': 'dev.obscuria.elixirum.common.registry.ElixirumAttributes', 'methodName': '<clinit>', 'methodDesc': '()V' },
            'transformer': makeModListTransformer('ElixirumAttributes.<clinit>')
        },
        'PatchElixirumAttributesInit': {
            'target': { 'type': 'METHOD', 'class': 'dev.obscuria.elixirum.common.registry.ElixirumAttributes', 'methodName': 'init', 'methodDesc': '()V' },
            'transformer': makeModListTransformer('ElixirumAttributes.init')
        },
        'PatchElixirumInjectLiving': {
            'target': { 'type': 'METHOD', 'class': 'dev.obscuria.elixirum.common.registry.ElixirumAttributes', 'methodName': 'injectLivingAttributes', 'methodDesc': '(Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;)V' },
            'transformer': makeAttributeNullTransformer('ElixirumAttributes.injectLivingAttributes')
        }
    };
}
