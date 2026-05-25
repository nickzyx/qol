package megawalls.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Transformer implements IClassTransformer, Opcodes {

    private static final String GUI_PLAYER_TAB_OVERLAY_CLASS = "net.minecraft.client.gui.GuiPlayerTabOverlay";
    private static final String TABLIST_RENDERER_CLASS = "megawalls/render/TablistRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GUI_PLAYER_TAB_OVERLAY_CLASS.equals(transformedName)) {
            return basicClass;
        }

        return transformGuiPlayerTabOverlay(name, transformedName, basicClass);
    }

    private static byte[] transformGuiPlayerTabOverlay(String name, String transformedName, byte[] basicClass) {
        try {
            String networkPlayerInfo = internalName("bdc", "net/minecraft/client/network/NetworkPlayerInfo");
            String scoreboard = internalName("auo", "net/minecraft/scoreboard/Scoreboard");
            String scoreObjective = internalName("auk", "net/minecraft/scoreboard/ScoreObjective");
            String renderPlayerlistMethodName = name("a", "renderPlayerlist");
            String renderPlayerlistMethodDesc = "(IL" + scoreboard + ";L" + scoreObjective + ";)V";
            String getPlayerNameMethodName = name("a", "getPlayerName");
            String getPlayerNameMethodDesc = "(L" + networkPlayerInfo + ";)Ljava/lang/String;";
            String getStringWidthMethodName = name("a", "getStringWidth");
            String drawStringWithShadowMethodName = name("a", "drawStringWithShadow");
            String decorateNameMethodDesc = "(Ljava/lang/String;L" + networkPlayerInfo + ";)Ljava/lang/String;";
            String renderIconsMethodDesc = "(L" + networkPlayerInfo + ";Ljava/lang/String;II)V";

            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, 0);

            boolean patchedRenderPlayerlist = false;
            for (MethodNode methodNode : classNode.methods) {
                if (renderPlayerlistMethodName.equals(methodNode.name) && renderPlayerlistMethodDesc.equals(methodNode.desc)) {
                    patchedRenderPlayerlist |= patchRenderPlayerlist(
                            classNode.name,
                            methodNode,
                            getPlayerNameMethodName,
                            getPlayerNameMethodDesc,
                            getStringWidthMethodName,
                            drawStringWithShadowMethodName,
                            decorateNameMethodDesc,
                            renderIconsMethodDesc
                    );
                }
            }

            if (!patchedRenderPlayerlist) {
                CorePlugin.LOGGER.error("Failed to patch GuiPlayerTabOverlay#renderPlayerlist for QOL tablist rendering");
                return basicClass;
            }

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            CorePlugin.LOGGER.info("Patched GuiPlayerTabOverlay for QOL tablist rendering");
            return classWriter.toByteArray();
        } catch (Throwable throwable) {
            CorePlugin.LOGGER.error("Failed to transform " + transformedName, throwable);
            return basicClass;
        }
    }

    private static boolean patchRenderPlayerlist(
            String className,
            MethodNode methodNode,
            String getPlayerNameMethodName,
            String getPlayerNameMethodDesc,
            String getStringWidthMethodName,
            String drawStringWithShadowMethodName,
            String decorateNameMethodDesc,
            String renderIconsMethodDesc
    ) {
        boolean patchedWidth = false;
        int patchedDrawCalls = 0;

        for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
            if (!(insnNode instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
            if (className.equals(methodInsnNode.owner)
                    && getPlayerNameMethodName.equals(methodInsnNode.name)
                    && getPlayerNameMethodDesc.equals(methodInsnNode.desc)) {
                if (findNextMethodCall(methodInsnNode, getStringWidthMethodName, "(Ljava/lang/String;)I", 12) != null) {
                    InsnList decorateNameHook = new InsnList();
                    decorateNameHook.add(new VarInsnNode(ALOAD, 9));
                    decorateNameHook.add(new MethodInsnNode(INVOKESTATIC, TABLIST_RENDERER_CLASS, "decorateName", decorateNameMethodDesc, false));
                    methodNode.instructions.insert(methodInsnNode, decorateNameHook);
                    patchedWidth = true;
                }
            } else if (drawStringWithShadowMethodName.equals(methodInsnNode.name)
                    && "(Ljava/lang/String;FFI)I".equals(methodInsnNode.desc)) {
                VarInsnNode nameLoad = findPreviousVarLoad(methodInsnNode, 25, 12);
                if (nameLoad != null) {
                    InsnList decorateNameHook = new InsnList();
                    decorateNameHook.add(new VarInsnNode(ALOAD, 24));
                    decorateNameHook.add(new MethodInsnNode(INVOKESTATIC, TABLIST_RENDERER_CLASS, "decorateName", decorateNameMethodDesc, false));
                    methodNode.instructions.insert(nameLoad, decorateNameHook);

                    AbstractInsnNode popNode = nextMeaningful(methodInsnNode);
                    if (popNode != null && popNode.getOpcode() == POP) {
                        InsnList renderIconsHook = new InsnList();
                        renderIconsHook.add(new VarInsnNode(ALOAD, 24));
                        renderIconsHook.add(new VarInsnNode(ALOAD, 25));
                        renderIconsHook.add(new VarInsnNode(ILOAD, 22));
                        renderIconsHook.add(new VarInsnNode(ILOAD, 23));
                        renderIconsHook.add(new MethodInsnNode(INVOKESTATIC, TABLIST_RENDERER_CLASS, "renderDiamondIcons", renderIconsMethodDesc, false));
                        methodNode.instructions.insert(popNode, renderIconsHook);
                        patchedDrawCalls++;
                    }
                }
            }
        }

        return patchedWidth && patchedDrawCalls == 2;
    }

    private static String name(String obfuscatedName, String deobfuscatedName) {
        return CorePlugin.isObfuscatedEnvironment() ? obfuscatedName : deobfuscatedName;
    }

    private static String internalName(String obfuscatedName, String deobfuscatedName) {
        return CorePlugin.isObfuscatedEnvironment() ? obfuscatedName : deobfuscatedName;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode insnNode) {
        AbstractInsnNode current = insnNode == null ? null : insnNode.getNext();
        while (current != null && isStructuralNode(current)) {
            current = current.getNext();
        }

        return current;
    }

    private static VarInsnNode findPreviousVarLoad(AbstractInsnNode insnNode, int variableIndex, int maxMeaningfulDistance) {
        int meaningfulDistance = 0;
        AbstractInsnNode current = insnNode == null ? null : insnNode.getPrevious();

        while (current != null && meaningfulDistance < maxMeaningfulDistance) {
            if (!isStructuralNode(current)) {
                meaningfulDistance++;
                if (current instanceof VarInsnNode) {
                    VarInsnNode varInsnNode = (VarInsnNode) current;
                    if (varInsnNode.getOpcode() == ALOAD && varInsnNode.var == variableIndex) {
                        return varInsnNode;
                    }
                }
            }

            current = current.getPrevious();
        }

        return null;
    }

    private static MethodInsnNode findNextMethodCall(
            AbstractInsnNode insnNode,
            String methodName,
            String methodDesc,
            int maxMeaningfulDistance
    ) {
        int meaningfulDistance = 0;
        AbstractInsnNode current = insnNode == null ? null : insnNode.getNext();

        while (current != null && meaningfulDistance < maxMeaningfulDistance) {
            if (!isStructuralNode(current)) {
                meaningfulDistance++;
                if (current instanceof MethodInsnNode) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                    if (methodName.equals(methodInsnNode.name) && methodDesc.equals(methodInsnNode.desc)) {
                        return methodInsnNode;
                    }
                }
            }

            current = current.getNext();
        }

        return null;
    }

    private static boolean isStructuralNode(AbstractInsnNode insnNode) {
        return insnNode instanceof LabelNode || insnNode instanceof LineNumberNode || insnNode instanceof FrameNode;
    }
}
