package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.jobf.IClassProcessor;
import me.superblaubeere27.jobf.JObfImpl;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.NodeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NumberObfuscationProcessor implements IClassProcessor {
    private static Random random = new Random();
    private JObfImpl inst;

    private static boolean lenghtMode = true;
    private static boolean xorMode = true;
    private static boolean simpleMathMode = true;

    public NumberObfuscationProcessor(JObfImpl inst) {
        this.inst = inst;
    }

    public static InsnList getInstructionsMultipleTimes(int value, int iterations) {
        InsnList list = new InsnList();
        list.add(NodeUtils.generateIntPush(value));

        for (int i = 0; i < iterations; i++) {
            list = obfuscateInsnList(list);
        }
        return list;
    }

    public static InsnList obfuscateInsnList(InsnList list) {
        for (AbstractInsnNode abstractInsnNode : list.toArray()) {
            if (NodeUtils.isIntegerNumber(abstractInsnNode)) {
                int number = NodeUtils.getIntValue(abstractInsnNode);

                if (number == Integer.MIN_VALUE) {
                    continue;
                }
                list.insert(abstractInsnNode, getInstructions(number));
                list.remove(abstractInsnNode);
            }
        }
        return list;
    }

    public static InsnList getInstructions(int value) {
        /*
         * Method 0: FASTEST, longer JIT (just in time) compiling, the length variable of string is predefined
         * Method 1: FAST, XOR is faster than ADD and SUB
         * Method 3: FAST
         */
        InsnList methodInstructions = new InsnList();
        int method;

        if (lenghtMode && (Math.abs(value) < 4 || (!xorMode && !simpleMathMode)))
            method = 0;
        else if (xorMode && (Math.abs(value) < Byte.MAX_VALUE || (!lenghtMode && !simpleMathMode)))
            method = 1;
        else
            method = 2;

        final boolean negative = value < 0;

        if (negative)
            value = -value;

        switch (method) {
            case 0:
                /*
                 * Generates a string.length() statement (e. 4 will be "kfjr".length())
                 */
                methodInstructions.add(new LdcInsnNode(NameUtils.generateSpaceString(value)));
                methodInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
                break;
            case 1:
                /*
                 * Generates a XOR statement 20 will be 29 ^ 9 <--- It's random that there a two 9s
                 */
                int A = value;
                int B = random.nextInt(200);
                A = A ^ B;
                methodInstructions.add(NodeUtils.generateIntPush(A));
                methodInstructions.add(NodeUtils.generateIntPush(B));
                methodInstructions.add(new InsnNode(Opcodes.IXOR));
                break;
            default:
                /*
                 * Generates a simple calculation e. 5 + 3 - 2 + 3 = 9
                 */
                final int ADD_1 = random.nextInt(value);
                final int ADD_2 = random.nextInt(value);
                final int ADD_3 = random.nextInt(value);
                final int SUB = (ADD_1 + ADD_2 + ADD_3) - value;

                methodInstructions.add(NodeUtils.generateIntPush(ADD_1));
                methodInstructions.add(NodeUtils.generateIntPush(ADD_2));
                methodInstructions.add(new InsnNode(Opcodes.IADD));
                methodInstructions.add(NodeUtils.generateIntPush(SUB));
                methodInstructions.add(new InsnNode(Opcodes.ISUB));
                methodInstructions.add(NodeUtils.generateIntPush(ADD_3));
                methodInstructions.add(new InsnNode(Opcodes.IADD));
                break;
        }
        if (negative)
            methodInstructions.add(new InsnNode(Opcodes.INEG));
        return methodInstructions;
    }

    @Override
    public void process(ClassNode node, int mode) {
        int i = 0;
        String fieldName = NameUtils.generateFieldName(node.name);
        List<Integer> integerList = new ArrayList<>();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray()) {
                if (abstractInsnNode == null) {
                    System.out.println(method.name + method.desc);
                    throw new RuntimeException();
                }
                if (NodeUtils.isIntegerNumber(abstractInsnNode)) {
                    int number = NodeUtils.getIntValue(abstractInsnNode);

                    if (number == Integer.MIN_VALUE) {
                        continue;
                    }
//                    if (abstractInsnNode instanceof LdcInsnNode && ((LdcInsnNode) abstractInsnNode).cst instanceof Number && ((int) ((LdcInsnNode) abstractInsnNode).cst) == Integer.MIN_VALUE) {
//                        System.out.println(((LdcInsnNode) abstractInsnNode).cst + "/" + number);
//                    }
                    if (!Modifier.isInterface(node.access)
                            && mode == 1
                            ) {
                        int containedSlot = -1;
                        int j = 0;
                        for (Integer integer : integerList) {
                            if (integer == number) containedSlot = j;
                            j++;
                        }
                        if (containedSlot == -1) integerList.add(number);
                        method.instructions.insertBefore(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
                        method.instructions.insertBefore(abstractInsnNode, NodeUtils.generateIntPush(containedSlot == -1 ? i : containedSlot));
                        method.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.IALOAD));
                        method.instructions.remove(abstractInsnNode);
                        if (containedSlot == -1) i++;
                        method.maxStack += 2;
                    } else {
                        method.maxStack += 4;

                        method.instructions.insertBefore(abstractInsnNode, getInstructionsMultipleTimes(number, random.nextInt(2) + 1));
                        method.instructions.remove(abstractInsnNode);
                    }
                }
            }
        }
        if (i != 0) {
            node.fields.add(new FieldNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE) | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC, fieldName, "[I", null, null));
            MethodNode clInit = NodeUtils.getMethod(node, "<clinit>");
            if (clInit == null) {
                clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
                node.methods.add(clInit);
            }
            if (clInit.instructions == null)
                clInit.instructions = new InsnList();

            InsnList toAdd = new InsnList();

//            if (clInit.instructions.getFirst() == null)
//                clInit.instructions.insert(NodeUtils.generateIntPush(i));
//            else
            toAdd.add(NodeUtils.generateIntPush(i));

            toAdd.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
//            toAdd.insert(new IntInsnNode(Opcodes.NEWARRAY, 0));
            toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, fieldName, "[I"));

            for (int j = 0; j < i; j++) {
                toAdd.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
                toAdd.add(NodeUtils.generateIntPush(j));
                toAdd.add(getInstructions(integerList.get(j)));
                toAdd.add(new InsnNode(Opcodes.IASTORE));
            }

            MethodNode generateIntegers = new MethodNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC, NameUtils.generateMethodName(node, "()V"), "()V", null, new String[0]);
            generateIntegers.instructions = toAdd;
            generateIntegers.instructions.add(new InsnNode(Opcodes.RETURN));
            generateIntegers.maxStack = 6;
            node.methods.add(generateIntegers);

            if (clInit.instructions == null || clInit.instructions.getFirst() == null) {
                clInit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateIntegers.name, generateIntegers.desc, false));
                clInit.instructions.add(new InsnNode(Opcodes.RETURN));
            } else {
                clInit.instructions.insertBefore(clInit.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateIntegers.name, generateIntegers.desc, false));
            }
//            clInit.maxStack = Math.max(clInit.maxStack, 6);
        }
        inst.setWorkDone();
    }


}