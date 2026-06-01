package megawalls.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Transformer implements IClassTransformer, Opcodes {

    private static final String GUI_PLAYER_TAB_OVERLAY_CLASS = "net.minecraft.client.gui.GuiPlayerTabOverlay";
    private static final String TABLIST_RENDERER_CLASS = "megawalls/render/TablistRenderer";
    private static final String BLOCK_STATE_MAPPER_CLASS = "net.minecraft.client.renderer.block.statemap.BlockStateMapper";
    private static final String BLOCK_BARRIER_CLASS = "net.minecraft.block.BlockBarrier";
    private static final String BARRIER_BLOCK_REPLACEMENT_CLASS = "megawalls/render/BarrierBlockReplacement";
    private static final String RENDER_GLOBAL_CLASS = "net.minecraft.client.renderer.RenderGlobal";
    private static final String RENDERER_LIVING_ENTITY_CLASS = "net.minecraft.client.renderer.entity.RendererLivingEntity";
    private static final String STRENGTH_OUTLINE_RENDERER_CLASS = "megawalls/render/StrengthOutlineRenderer";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        if (GUI_PLAYER_TAB_OVERLAY_CLASS.equals(transformedName)) {
            return transformGuiPlayerTabOverlay(transformedName, basicClass);
        }

        if (BLOCK_STATE_MAPPER_CLASS.equals(transformedName)) {
            return transformBlockStateMapper(transformedName, basicClass);
        }

        if (BLOCK_BARRIER_CLASS.equals(transformedName)) {
            return transformBlockBarrier(transformedName, basicClass);
        }

        if (RENDER_GLOBAL_CLASS.equals(transformedName)) {
            return transformRenderGlobal(transformedName, basicClass);
        }

        if (RENDERER_LIVING_ENTITY_CLASS.equals(transformedName)) {
            return transformRendererLivingEntity(transformedName, basicClass);
        }

        return basicClass;
    }

    private static byte[] transformRenderGlobal(String transformedName, byte[] basicClass) {
        try {
            String entity = internalName("pk", "net/minecraft/entity/Entity");
            String camera = internalName("bia", "net/minecraft/client/renderer/culling/ICamera");
            String framebuffer = internalName("bfw", "net/minecraft/client/shader/Framebuffer");
            String shaderGroup = internalName("blr", "net/minecraft/client/shader/ShaderGroup");
            String renderEntitiesName = name("a", "renderEntities");
            String renderEntitiesDesc = "(L" + entity + ";L" + camera + ";F)V";
            String renderOutlineFramebufferName = name("c", "renderEntityOutlineFramebuffer");
            String isRenderEntityOutlinesName = name("d", "isRenderEntityOutlines");
            String outlineFramebufferField = name("A", "entityOutlineFramebuffer");
            String outlineShaderField = name("B", "entityOutlineShader");
            String outlineHookDesc = "(ZL" + entity + ";L" + camera + ";FL" + framebuffer + ";L" + shaderGroup + ";)Z";

            ClassNode classNode = readClass(basicClass);

            boolean patchedRenderEntities = false;
            boolean patchedFinalDraw = false;
            for (MethodNode methodNode : classNode.methods) {
                if (renderEntitiesName.equals(methodNode.name) && renderEntitiesDesc.equals(methodNode.desc)) {
                    for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                        if (isMethodCall(insnNode, classNode.name, isRenderEntityOutlinesName, "()Z")) {
                            methodNode.instructions.insert(insnNode, createStrengthOutlineHook(
                                    classNode.name,
                                    outlineFramebufferField,
                                    framebuffer,
                                    outlineShaderField,
                                    shaderGroup,
                                    outlineHookDesc
                            ));
                            patchedRenderEntities = true;
                        }
                    }
                } else if (renderOutlineFramebufferName.equals(methodNode.name) && "()V".equals(methodNode.desc)) {
                    for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                        if (isMethodCall(insnNode, classNode.name, isRenderEntityOutlinesName, "()Z")) {
                            methodNode.instructions.insert(insnNode, new MethodInsnNode(
                                    INVOKESTATIC,
                                    STRENGTH_OUTLINE_RENDERER_CLASS,
                                    "shouldDrawOutlineFramebuffer",
                                    "(Z)Z",
                                    false
                            ));
                            patchedFinalDraw = true;
                        }
                    }
                }
            }

            if (!patchedRenderEntities || !patchedFinalDraw) {
                CorePlugin.LOGGER.error("Failed to patch RenderGlobal for strength outlines");
                return basicClass;
            }

            CorePlugin.LOGGER.info("Patched RenderGlobal for strength outlines");
            return writeClass(classNode);
        } catch (Throwable throwable) {
            CorePlugin.LOGGER.error("Failed to transform " + transformedName, throwable);
            return basicClass;
        }
    }

    private static InsnList createStrengthOutlineHook(
            String renderGlobalClass,
            String outlineFramebufferField,
            String framebuffer,
            String outlineShaderField,
            String shaderGroup,
            String outlineHookDesc
    ) {
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(ALOAD, 1));
        hook.add(new VarInsnNode(ALOAD, 2));
        hook.add(new VarInsnNode(FLOAD, 3));
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new FieldInsnNode(
                GETFIELD,
                renderGlobalClass,
                outlineFramebufferField,
                "L" + framebuffer + ";"
        ));
        hook.add(new VarInsnNode(ALOAD, 0));
        hook.add(new FieldInsnNode(
                GETFIELD,
                renderGlobalClass,
                outlineShaderField,
                "L" + shaderGroup + ";"
        ));
        hook.add(new MethodInsnNode(
                INVOKESTATIC,
                STRENGTH_OUTLINE_RENDERER_CLASS,
                "renderStrengthOutlines",
                outlineHookDesc,
                false
        ));
        return hook;
    }

    private static byte[] transformRendererLivingEntity(String transformedName, byte[] basicClass) {
        try {
            String entityLivingBase = internalName("pr", "net/minecraft/entity/EntityLivingBase");
            String setScoreTeamColorName = name("c", "setScoreTeamColor");
            String setScoreTeamColorDesc = "(L" + entityLivingBase + ";)Z";

            ClassNode classNode = readClass(basicClass);

            boolean patchedColor = false;
            for (MethodNode methodNode : classNode.methods) {
                if (!setScoreTeamColorName.equals(methodNode.name) || !setScoreTeamColorDesc.equals(methodNode.desc)) {
                    continue;
                }

                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (
                            insnNode instanceof VarInsnNode &&
                            insnNode.getOpcode() == ISTORE &&
                            ((VarInsnNode) insnNode).var == 2
                    ) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(ILOAD, 2));
                        hook.add(new VarInsnNode(ALOAD, 1));
                        hook.add(new MethodInsnNode(
                                INVOKESTATIC,
                                STRENGTH_OUTLINE_RENDERER_CLASS,
                                "getOutlineColor",
                                "(IL" + entityLivingBase + ";)I",
                                false
                        ));
                        hook.add(new VarInsnNode(ISTORE, 2));
                        methodNode.instructions.insert(insnNode, hook);
                        patchedColor = true;
                    }
                }
            }

            if (!patchedColor) {
                CorePlugin.LOGGER.error("Failed to patch RendererLivingEntity#setScoreTeamColor for strength outlines");
                return basicClass;
            }

            CorePlugin.LOGGER.info("Patched RendererLivingEntity for strength outline colors");
            return writeClass(classNode);
        } catch (Throwable throwable) {
            CorePlugin.LOGGER.error("Failed to transform " + transformedName, throwable);
            return basicClass;
        }
    }

    private static byte[] transformBlockBarrier(String transformedName, byte[] basicClass) {
        try {
            String renderTypeMethodName = name("b", "getRenderType");
            String blockLayerMethodName = name("m", "getBlockLayer");
            String shouldSideBeRenderedName = name("a", "shouldSideBeRendered");
            String enumWorldBlockLayer = internalName("adf", "net/minecraft/util/EnumWorldBlockLayer");
            String block = internalName("afh", "net/minecraft/block/Block");
            String blockAccess = internalName("adq", "net/minecraft/world/IBlockAccess");
            String blockPos = internalName("cj", "net/minecraft/util/BlockPos");
            String enumFacing = internalName("cq", "net/minecraft/util/EnumFacing");
            String blockLayerMethodDesc = "()L" + enumWorldBlockLayer + ";";
            String shouldSideBeRenderedDesc =
                    "(L" + blockAccess + ";L" + blockPos + ";L" + enumFacing + ";)Z";

            ClassNode classNode = readClass(basicClass);

            boolean patchedRenderType = false;
            boolean patchedBlockLayer = false;
            boolean patchedShouldSideBeRendered = false;
            for (MethodNode methodNode : classNode.methods) {
                if (renderTypeMethodName.equals(methodNode.name) && "()I".equals(methodNode.desc)) {
                    methodNode.instructions.clear();
                    methodNode.instructions.add(new InsnNode(ICONST_M1));
                    methodNode.instructions.add(new MethodInsnNode(
                            INVOKESTATIC,
                            BARRIER_BLOCK_REPLACEMENT_CLASS,
                            "getBarrierRenderType",
                            "(I)I",
                            false
                    ));
                    methodNode.instructions.add(new InsnNode(IRETURN));
                    patchedRenderType = true;
                } else if (blockLayerMethodName.equals(methodNode.name) && blockLayerMethodDesc.equals(methodNode.desc)) {
                    methodNode.instructions.clear();
                    addBarrierBlockLayerInstructions(methodNode.instructions, enumWorldBlockLayer);
                    patchedBlockLayer = true;
                } else if (
                        shouldSideBeRenderedName.equals(methodNode.name) &&
                        shouldSideBeRenderedDesc.equals(methodNode.desc)
                ) {
                    methodNode.instructions.clear();
                    addBarrierShouldSideBeRenderedInstructions(
                            methodNode.instructions,
                            block,
                            blockAccess,
                            blockPos,
                            enumFacing
                    );
                    patchedShouldSideBeRendered = true;
                }
            }

            if (!patchedBlockLayer) {
                MethodNode blockLayerMethod = new MethodNode(
                        ACC_PUBLIC,
                        blockLayerMethodName,
                        blockLayerMethodDesc,
                        null,
                        null
                );
                addBarrierBlockLayerInstructions(blockLayerMethod.instructions, enumWorldBlockLayer);
                classNode.methods.add(blockLayerMethod);
                patchedBlockLayer = true;
            }

            if (!patchedShouldSideBeRendered) {
                MethodNode shouldSideMethod = new MethodNode(
                        ACC_PUBLIC,
                        shouldSideBeRenderedName,
                        shouldSideBeRenderedDesc,
                        null,
                        null
                );
                addBarrierShouldSideBeRenderedInstructions(
                        shouldSideMethod.instructions,
                        block,
                        blockAccess,
                        blockPos,
                        enumFacing
                );
                classNode.methods.add(shouldSideMethod);
                patchedShouldSideBeRendered = true;
            }

            if (!patchedRenderType || !patchedBlockLayer || !patchedShouldSideBeRendered) {
                CorePlugin.LOGGER.error("Failed to patch BlockBarrier for visible barriers");
                return basicClass;
            }

            CorePlugin.LOGGER.info("Patched BlockBarrier for visible barriers");
            return writeClass(classNode);
        } catch (Throwable throwable) {
            CorePlugin.LOGGER.error("Failed to transform " + transformedName, throwable);
            return basicClass;
        }
    }

    private static void addBarrierBlockLayerInstructions(InsnList instructions, String enumWorldBlockLayer) {
        instructions.add(new MethodInsnNode(
                INVOKESTATIC,
                BARRIER_BLOCK_REPLACEMENT_CLASS,
                "getBarrierBlockLayer",
                "()L" + enumWorldBlockLayer + ";",
                false
        ));
        instructions.add(new InsnNode(ARETURN));
    }

    private static void addBarrierShouldSideBeRenderedInstructions(
            InsnList instructions,
            String block,
            String blockAccess,
            String blockPos,
            String enumFacing
    ) {
        String shouldSideBeRenderedDesc =
                "(L" + blockAccess + ";L" + blockPos + ";L" + enumFacing + ";)Z";
        instructions.add(new VarInsnNode(ALOAD, 0));
        instructions.add(new VarInsnNode(ALOAD, 1));
        instructions.add(new VarInsnNode(ALOAD, 2));
        instructions.add(new VarInsnNode(ALOAD, 3));
        instructions.add(new MethodInsnNode(
                INVOKESPECIAL,
                block,
                name("a", "shouldSideBeRendered"),
                shouldSideBeRenderedDesc,
                false
        ));
        instructions.add(new VarInsnNode(ALOAD, 1));
        instructions.add(new VarInsnNode(ALOAD, 2));
        instructions.add(new VarInsnNode(ALOAD, 3));
        instructions.add(new VarInsnNode(ALOAD, 0));
        instructions.add(new MethodInsnNode(
                INVOKESTATIC,
                BARRIER_BLOCK_REPLACEMENT_CLASS,
                "shouldRenderBarrierSide",
                "(ZL" + blockAccess + ";L" + blockPos + ";L" + enumFacing + ";L" + block + ";)Z",
                false
        ));
        instructions.add(new InsnNode(IRETURN));
    }

    private static byte[] transformBlockStateMapper(String transformedName, byte[] basicClass) {
        try {
            String putAllStateModelLocationsName = name("a", "putAllStateModelLocations");

            ClassNode classNode = readClass(basicClass);

            boolean patchedContains = false;
            boolean patchedReturn = false;
            for (MethodNode methodNode : classNode.methods) {
                if (!putAllStateModelLocationsName.equals(methodNode.name) || !"()Ljava/util/Map;".equals(methodNode.desc)) {
                    continue;
                }

                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (isMethodCall(insnNode, "java/util/Set", "contains", "(Ljava/lang/Object;)Z")) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                        methodInsnNode.setOpcode(INVOKESTATIC);
                        methodInsnNode.owner = BARRIER_BLOCK_REPLACEMENT_CLASS;
                        methodInsnNode.name = "shouldTreatAsBuiltInBlock";
                        methodInsnNode.desc = "(Ljava/util/Set;Ljava/lang/Object;)Z";
                        methodInsnNode.itf = false;
                        patchedContains = true;
                    }
                }

                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (insnNode.getOpcode() == ARETURN) {
                        InsnList hook = new InsnList();
                        hook.add(new InsnNode(DUP));
                        hook.add(new MethodInsnNode(
                                INVOKESTATIC,
                                BARRIER_BLOCK_REPLACEMENT_CLASS,
                                "applyBarrierModelLocations",
                                "(Ljava/util/Map;)V",
                                false
                        ));
                        methodNode.instructions.insertBefore(insnNode, hook);
                        patchedReturn = true;
                    }
                }
            }

            if (!patchedContains || !patchedReturn) {
                CorePlugin.LOGGER.error("Failed to patch BlockStateMapper for visible barriers");
                return basicClass;
            }

            CorePlugin.LOGGER.info("Patched BlockStateMapper for visible barriers");
            return writeClass(classNode);
        } catch (Throwable throwable) {
            CorePlugin.LOGGER.error("Failed to transform " + transformedName, throwable);
            return basicClass;
        }
    }

    private static byte[] transformGuiPlayerTabOverlay(String transformedName, byte[] basicClass) {
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

            ClassNode classNode = readClass(basicClass);

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

            CorePlugin.LOGGER.info("Patched GuiPlayerTabOverlay for QOL tablist rendering");
            return writeClass(classNode);
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

    private static ClassNode readClass(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        return classNode;
    }

    private static byte[] writeClass(ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
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

    private static boolean isMethodCall(
            AbstractInsnNode insnNode,
            String owner,
            String methodName,
            String methodDesc
    ) {
        if (!(insnNode instanceof MethodInsnNode)) {
            return false;
        }

        MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
        return owner.equals(methodInsnNode.owner) &&
                methodName.equals(methodInsnNode.name) &&
                methodDesc.equals(methodInsnNode.desc);
    }

    private static boolean isStructuralNode(AbstractInsnNode insnNode) {
        return insnNode instanceof LabelNode || insnNode instanceof LineNumberNode || insnNode instanceof FrameNode;
    }
}
