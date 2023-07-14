package optispire.util;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.StringBuilder;
import javassist.*;
import javassist.bytecode.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static javassist.bytecode.Opcode.*;

public class BytecodeHelper {
    private static final String UNDF = "UNDEFINED", NULL = "NULL";

    public static String getOpCodes(MethodInfo mi, int perLine) throws BadBytecode {
        CodeAttribute ca = mi.getCodeAttribute();
        CodeIterator ci = ca.iterator();
        StringBuilder bytecode = new StringBuilder();
        int currentLine = 0;
        while (ci.hasNext()) {
            int index = ci.next();
            int op = ci.byteAt(index);
            String hexOp = Integer.toHexString(op);
            if (hexOp.length() == 1)
                hexOp = '0' + hexOp;

            bytecode.append(hexOp).append(' ').append(String.format("%-14.14s", Mnemonic.OPCODE[op]));
            ++currentLine;
            if (currentLine >= perLine) {
                currentLine = 0;
                bytecode.append('\n');
            }
        }
        return bytecode.toString();
    }

    public static void quickTranslate(ClassPool pool, String className, String methodName) {
        try {
            CtClass clz = pool.get(className);
            CtMethod method = clz.getDeclaredMethod(methodName);
            System.out.println(new BytecodeHelper(true).set(method).translate());
        } catch (NotFoundException e) {
            System.out.println("BytecodeTranslator: Method " + className + "." + methodName + " not found.");
        } catch (BadBytecode badBytecode) {
            badBytecode.printStackTrace();
        }
    }

    //For bytecode translation
    private MethodInfo mi;
    private ConstPool cp;

    private CodeAttribute ca;
    private CodeIterator ci;

    private final TextProcessor text;

    private final Deque<VarObject> stack;
    private final Map<Integer, ProgramState> jumpMap = new HashMap<>();

    private final Map<Integer, VarObject> vars;

    private boolean isStatic;
    private CtClass[] paramTypes;

    private int arrCount;

    //extra
    private final List<String> trackParams = new ArrayList<>(4);
    private final List<String> trackResult = new ArrayList<>(4);

    public BytecodeHelper(boolean generateText) {
        text = generateText ? new OutputTextProcessor() : new NoTextProcessor();
        stack = new IntedetermineDeque();
        vars = new HashMap<>();
    }

    public BytecodeHelper set(CtMethod method) throws NotFoundException {
        return this.set(method.getMethodInfo(), Modifier.isStatic(method.getModifiers()), method.getParameterTypes());
    }
    public BytecodeHelper set(CtConstructor constructor) throws NotFoundException {
        return this.set(constructor.getMethodInfo(), Modifier.isStatic(constructor.getModifiers()), constructor.getParameterTypes());
    }
    public BytecodeHelper set(MethodInfo mi, boolean isStatic, CtClass[] paramtypes) {
        this.mi = mi;
        this.cp = mi.getConstPool();
        this.ca = mi.getCodeAttribute();
        if (ca == null) {
            return null;
        }
        this.ci = ca.iterator();

        this.isStatic = isStatic;
        this.paramTypes = paramtypes;

        return this;
    }
    public void translate(ClassPool pool, String className, String methodName) {
        try {
            CtClass clz = pool.get(className);
            CtMethod method = clz.getDeclaredMethod(methodName);
            System.out.println(set(method).translate());
        } catch (NotFoundException e) {
            System.out.println("BytecodeTranslator: Method " + className + "." + methodName + " not found.");
            e.printStackTrace();
        } catch (BadBytecode badBytecode) {
            badBytecode.printStackTrace();
        }
    }
    public void translate(CtMethod method) {
        try {
            System.out.println(set(method).translate());
        } catch (NotFoundException e) {
            System.out.println("BytecodeTranslator: Method " + method.getName() + " not found.");
        } catch (BadBytecode badBytecode) {
            badBytecode.printStackTrace();
        }
    }
    public List<String> trackParameter(CtMethod method, int paramIndex) {
        try {
            if (paramIndex >= paramTypes.length)
                throw new IndexOutOfBoundsException("index " + paramIndex + " out of bounds of method parameters " + Arrays.toString(paramTypes));

            return set(method).track("_" + paramIndex + "_");
        } catch (NotFoundException e) {
            System.out.println("BytecodeTranslator: Method " + method.getName() + " not found.");
        } catch (BadBytecode badBytecode) {
            badBytecode.printStackTrace();
        }
        return Collections.emptyList();
    }
    public String translate() throws BadBytecode {
        trackParams.clear();

        text.clear();
        stack.clear();
        vars.clear();
        jumpMap.clear();
        arrCount = 0;

        stack.push(getObj(UNDF)); //should not pop empty stack

        int off = 0;
        if (!mi.isStaticInitializer() && !isStatic) {
            vars.put(0, getObj("this"));
            off = 1;
        }
        for (int i = 0; i < paramTypes.length; ++i) {
            vars.put(off + i, getObj("_" + i + "_ (" + paramTypes[i].getSimpleName() + ")"));
        }

        ci.begin();
        while (ci.hasNext()) {
            translateOp(ci);
        }
        stack.clear();
        vars.clear();
        jumpMap.clear();
        resetObjs();

        return text.toString();
    }

    public List<String> track(String... toTrack) throws BadBytecode {
        return track(Arrays.asList(toTrack));
    }

    public List<String> track(Collection<String> toTrack) throws BadBytecode {
        trackParams.clear();
        trackResult.clear();
        trackParams.addAll(toTrack);

        text.clear();
        stack.clear();
        vars.clear();
        jumpMap.clear();
        arrCount = 0;

        stack.push(getObj(UNDF)); //should not pop empty stack

        int off = 0;
        if (!mi.isStaticInitializer() && !isStatic) {
            vars.put(0, getObj("this"));
            off = 1;
        }
        for (int i = 0; i < paramTypes.length; ++i) {
            vars.put(off + i, getObj("_" + i + "_"));
        }

        ci.begin();
        while (ci.hasNext()) {
            translateOp(ci);
        }
        stack.clear();
        vars.clear();
        jumpMap.clear();
        resetObjs();

        if (!(text instanceof NoTextProcessor))
            System.out.println(text);
        return new ArrayList<>(trackResult);
    }

    private void translateOp(CodeIterator ci) throws BadBytecode {
        VarObject a, b, c, d;
        boolean staticMethod = false;
        int index = ci.next();
        int op = ci.byteAt(index);
        String hexOp = Integer.toHexString(op);
        if (hexOp.length() == 1)
            hexOp = '0' + hexOp;

        if (jumpMap.containsKey(index)) {
            ProgramState state = jumpMap.remove(index);
            state.restore();
        }
        text.add(String.format("%-4.4s", index)).add(hexOp).add(' ').add(String.format("%-15.15s", Mnemonic.OPCODE[op])).add(" | ");

        outer:
        switch (op) {
            case ACONST_NULL:   push(NULL); break;
            case ICONST_M1:     push(-1); break;
            case ICONST_0:      push(0); break;
            case ICONST_1:      push(1); break;
            case ICONST_2:      push(2); break;
            case ICONST_3:      push(3); break;
            case ICONST_4:      push(4); break;
            case ICONST_5:      push(5); break;
            case LCONST_0:      pushSuf(0L, 'L'); break;
            case LCONST_1:      pushSuf(1L, 'L'); break;
            case FCONST_0:      pushSuf(0F, "F"); break;
            case FCONST_1:      pushSuf(1F, "F"); break;
            case FCONST_2:      pushSuf(2F, "F"); break;
            case DCONST_0:      pushSuf(0D, "D"); break;
            case DCONST_1:      pushSuf(1D, "D"); break;
            case BIPUSH:        push(bVal(index + 1)); break;
            case SIPUSH:        push(uVal(index + 1)); break;
            case LDC:           push(cp.getLdcValue(bVal(index + 1))); break;
            case LDC_W:
            case LDC2_W:        push(cp.getLdcValue(uVal(index + 1))); break;
            case ILOAD:
            case ALOAD:         pushSuf(var(bVal(index + 1)), " [var", bVal(index + 1), ']'); break;
            case LLOAD:         pushSuf(var(bVal(index + 1)), "_L [var", bVal(index + 1), ']'); break;
            case FLOAD:         pushSuf(var(bVal(index + 1)), "_F [var", bVal(index + 1), ']'); break;
            case DLOAD:         pushSuf(var(bVal(index + 1)), "_D [var", bVal(index + 1), ']'); break;
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:       pushSuf(var(op - ILOAD_0), " [var", op - ILOAD_0, ']'); break;
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:       pushSuf(var(op - LLOAD_0), "_L [var", op - LLOAD_0, ']'); break;
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:       pushSuf(var(op - FLOAD_0), "_F [var", op - FLOAD_0, ']'); break;
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:       pushSuf(var(op - DLOAD_0), "_D [var", op - DLOAD_0, ']'); break;
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:       pushSuf(var(op - ALOAD_0), " [var", op - ALOAD_0, ']'); break;
            case IALOAD:        push(readArr(int.class)); break;
            case LALOAD:        push(readArr(long.class)); break;
            case FALOAD:        push(readArr(float.class)); break;
            case DALOAD:        push(readArr(double.class)); break;
            case AALOAD:        push(readArr(Object.class)); break;
            case BALOAD:        push(readArr(byte.class)); break;
            case CALOAD:        push(readArr(char.class)); break;
            case SALOAD:        push(readArr(short.class)); break;
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:        store(bVal(index + 1)); break;
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:      store(op - ISTORE_0); break;
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:      store(op - LSTORE_0); break;
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:      store(op - FSTORE_0); break;
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:      store(op - DSTORE_0); break;
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:      store(op - ASTORE_0); break;
            case IASTORE:       storeArr(int.class); break;
            case LASTORE:       storeArr(long.class); break;
            case FASTORE:       storeArr(float.class); break;
            case DASTORE:       storeArr(double.class); break;
            case AASTORE:       storeArr(Object.class); break;
            case BASTORE:       storeArr(byte.class); break;
            case CASTORE:       storeArr(char.class); break;
            case SASTORE:       storeArr(short.class); break;
            case POP:           endPopStr(stack.pop()); break;
            case POP2:
                endPopStr(stack.pop(), stack.pop()); //SHOULD BE: Only one pop if double/long?
                break;
            case DUP:
                popStr(a = stack.pop());
                multiPush(a, a);
                break;
            case DUP_X1:
                popStr(a = stack.pop(), b = stack.pop());
                multiPush(a, b, a);
                break;
            case DUP_X2:
                //SHOULD BE: Only two values if value2 is double/long?
                popStr(a = stack.pop(), b = stack.pop(), c = stack.pop());
                multiPush(a, c, b, a);
                break;
            case DUP2:
                //SHOULD BE: Only one value if it is a double/long?
                popStr(a = stack.pop(), b = stack.pop());
                multiPush(b, a, b, a);
                break;
            case DUP2_X1:
                popStr(a = stack.pop(), b = stack.pop(), c = stack.pop());
                multiPush(b, a, c, b, a);
                break;
            case DUP2_X2:
                popStr(a = stack.pop(), b = stack.pop(), c = stack.pop(), d = stack.pop());
                multiPush(b, a, d, c, b, a);
                break;
            case SWAP:
                popStr(a = stack.pop(), b = stack.pop());
                multiPush(b, a);
                break;
            case IADD: //-----------------Addition-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, Integer::sum, (x,y)->x+ " + " + y);
                break;
            case LADD:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, Long::sum, (x,y)->x + " + " + y, 'L');
                break;
            case FADD:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue, Float::sum, (x,y)->x + " + " + y, 'F');
                break;
            case DADD:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue, Double::sum, (x,y)->x + " + " + y, 'D');
                break;
            case ISUB: //-----------------Subtraction-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x-y, (x,y)->x + " - " + y);
                break;
            case LSUB:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x-y, (x,y)->x + " - " + y, 'L');
                break;
            case FSUB:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue, (x, y)->x-y, (x,y)->x + " - " + y, 'F');
                break;
            case DSUB:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue, (x, y)->x-y, (x,y)->x + " - " + y, 'D');
                break;
            case IMUL: //-----------------Multiplication-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x*y, (x,y)->x + " * " + y);
                break;
            case LMUL:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x*y, (x,y)->x + " * " + y, 'L');
                break;
            case FMUL:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue, (x, y)->x*y, (x,y)->x + " * " + y, 'F');
                break;
            case DMUL:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue, (x, y)->x*y, (x,y)->x + " * " + y, 'D');
                break;
            case IDIV: //-----------------Division-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x/y, (x,y)->x + " / " + y);
                break;
            case LDIV:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x/y, (x,y)->x + " / " + y, 'L');
                break;
            case FDIV:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue, (x, y)->x/y, (x,y)->x + " / " + y, 'F');
                break;
            case DDIV:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue, (x, y)->x/y, (x,y)->x + " / " + y, 'D');
                break;
            case IREM: //-----------------Remainder-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x%y, (x,y)->x + " % " + y);
                break;
            case LREM:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x%y, (x,y)->x + " % " + y, 'L');
                break;
            case FREM:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue, (x, y)->x%y, (x,y)->x + " % " + y, 'F');
                break;
            case DREM:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue, (x, y)->x%y, (x,y)->x + " % " + y, 'D');
                break;
            case INEG: //-----------------Negation-----------------
                popStr(a = stack.pop());
                arith(a, combine(Number::intValue, x->-x), (x)->"-" + x);
                break;
            case LNEG:
                popStr(a = stack.pop());
                arith(a, combine(Number::longValue, x->-x), (x)->"-" + x, 'L');
                break;
            case FNEG:
                popStr(a = stack.pop());
                arith(a, combine(Number::floatValue, x->-x), (x)->"-" + x, 'F');
                break;
            case DNEG:
                popStr(a = stack.pop());
                arith(a, combine(Number::doubleValue, x->-x), (x)->"-" + x, 'D');
                break;
            case ISHL: //-----------------Arithmetic LShift-----------------
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::intValue, (x, y)->x<<y, (x,y)->x + " << " + y);
                break;
            case LSHL:
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::longValue, (x, y)->x<<y, (x,y)->x + " << " + y, 'L');
                break;
            case ISHR: //-----------------Arithmetic (Signed) RShift-----------------
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::intValue, (x, y)->x>>y, (x,y)->x + " >> " + y);
                break;
            case LSHR:
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::longValue, (x, y)->x>>y, (x,y)->x + " >> " + y, 'L');
                break;
            case IUSHR: //-----------------Logical (Unsigned) RShift-----------------
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::intValue, (x, y)->x>>>y, (x,y)->x + " >>> " + y);
                break;
            case LUSHR:
                popStr(a = stack.pop(), b = stack.pop());
                shift(a, b, Number::longValue, (x, y)->x>>>y, (x,y)->x + " >>> " + y, 'L');
                break;
            case IAND: //-----------------Bitwise &-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x&y, (x,y)->x + " & " + y);
                break;
            case LAND:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x&y, (x,y)->x + " & " + y, 'L');
                break;
            case IOR: //-----------------Bitwise |-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x|y, (x,y)->x + " | " + y);
                break;
            case LOR:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x|y, (x,y)->x + " | " + y, 'L');
                break;
            case IXOR: //-----------------Bitwise ^-----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::intValue, (x, y)->x^y, (x,y)->x + " ^ " + y);
                break;
            case LXOR:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, (x, y)->x^y, (x,y)->x + " ^ " + y, 'L');
                break;
            case IINC:          iinc(bVal(index + 1), bVal(index + 2));
            case L2I: //-----------------Type Conversion-----------------
            case F2I:
            case D2I:
                popStr(a = stack.pop());
                arith(a, Number::intValue, (x) -> "(int) " + x);
                break;
            case I2L:
            case F2L:
            case D2L:
                popStr(a = stack.pop());
                arith(a, Number::longValue, (x) -> "(long) " + x);
                break;
            case I2F:
            case L2F:
            case D2F:
                popStr(a = stack.pop());
                arith(a, Number::floatValue, (x) -> "(float) " + x);
                break;
            case I2D:
            case L2D:
            case F2D:
                popStr(a = stack.pop());
                arith(a, Number::doubleValue, (x) -> "(double) " + x);
                break;
            case I2B:
                popStr(a = stack.pop());
                arith(a, Number::byteValue, (x) -> "(byte) " + x);
                break;
            case I2C:
                popStr(a = stack.pop());
                arith(a, (x)->(char)x.intValue(), (x) -> "(char) " + x);
                break;
            case I2S:
                popStr(a = stack.pop());
                arith(a, Number::shortValue, (x) -> "(short) " + x);
                break;
            case LCMP: //----------------Comparison----------------
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::longValue, Long::compare, (x,y)->"cmp(" + x + ", " + y + ")", 'L');
                break;
            case FCMPL:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue,
                        (x, y)->{
                            if (x.isNaN() || y.isNaN())
                                return -1;
                            return Float.compare(x, y);
                        }, (x,y)->"cmp(" + x + ", " + y + ")", 'F');
                break;
            case FCMPG:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::floatValue,
                        (x, y)->{
                            if (x.isNaN() || y.isNaN())
                                return 1;
                            return Float.compare(x, y);
                        }, (x,y)->"cmp(" + x + ", " + y + ")", 'F');
                break;
            case DCMPL:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue,
                        (x, y)->{
                            if (x.isNaN() || y.isNaN())
                                return -1;
                            return Double.compare(x, y);
                        }, (x,y)->"cmp(" + x + ", " + y + ")", 'D');
                break;
            case DCMPG:
                popStr(a = stack.pop(), b = stack.pop());
                arith(a, b, Number::doubleValue,
                        (x, y)->{
                            if (x.isNaN() || y.isNaN())
                                return 1;
                            return Double.compare(x, y);
                        }, (x,y)->"cmp(" + x + ", " + y + ")", 'D');
                break;
            case IFEQ:
                popFormatted("if (%s == 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFNE:
                popFormatted("if (%s != 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFLT:
                popFormatted("if (%s < 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFGE:
                popFormatted("if (%s >= 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFGT:
                popFormatted("if (%s > 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFLE:
                popFormatted("if (%s <= 0) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPEQ:
            case IF_ACMPEQ:
                popFormatted("if (%s == %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPNE:
            case IF_ACMPNE:
                popFormatted("if (%s != %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPLT:
                popFormatted("if (%s < %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPGE:
                popFormatted("if (%s >= %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPGT:
                popFormatted("if (%s > %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IF_ICMPLE:
                popFormatted("if (%s <= %s) go to offset ", 2);
                text.add(jump(index, uVal(index + 1)));
                break;
            case GOTO:
                text.add("go to offset ").add(jump(index, uVal(index + 1)));
                break;
            case JSR: //Might want to implement this at some point?
            case RET:
                text.add("UNSUPPORTED");
                break;
            case TABLESWITCH: //Could use more work.
            case LOOKUPSWITCH:
                endPopStr(stack.pop());
                break;
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
                endPopStr(stack.pop());
                text.add(' ');
            case RETURN:
                text.add("[Empty Stack]");
                stack.clear();
                break;
            case GETSTATIC:
                int getStaticIndex = uVal(index + 1);
                String getStaticField = cp.getFieldrefName(getStaticIndex);
                if (getStaticField == null) {
                    text.add("{FIELD NOT FOUND} ");
                    stack.push(getObj(UNDF));
                }
                else {
                    text.add(cp.getFieldrefClassName(getStaticIndex)).add('.').add(getStaticField).add(' ');
                    push(getStaticField);
                }
                break;
            case PUTSTATIC:
                popStr(stack.pop());
                text.add("=> ");
                int putStaticIndex = uVal(index + 1);
                String putStaticField = cp.getFieldrefName(putStaticIndex);
                if (putStaticField == null)
                    text.add("{FIELD NOT FOUND}");
                else
                    text.add("Field ").add(putStaticField)
                            .add(" [").add(cp.getFieldrefClassName(putStaticIndex)).add(']');
                break;
            case GETFIELD:
                popFormatted("%1$s -> %1$s.", 1);
                int getFieldIndex = uVal(index + 1);
                String getField = cp.getFieldrefName(getFieldIndex);
                if (getField == null) {
                    text.add("{FIELD NOT FOUND}");
                    stack.push(getObj(UNDF));
                }
                else {
                    text.add(getField).add(" [").add(cp.getFieldrefClassName(getFieldIndex)).add(']');
                    stack.push(getObj(getField));
                }
                break;
            case PUTFIELD:
                popFormatted("%1$s, %2$s => %1$s.", 2);
                int putFieldIndex = uVal(index + 1);
                String putField = cp.getFieldrefName(putFieldIndex);
                if (putField == null)
                    text.add("{FIELD NOT FOUND}");
                else
                    text.add(putField).add(" [").add(cp.getFieldrefClassName(putFieldIndex)).add(']');
                break;
            case INVOKESTATIC: //A static method.
                staticMethod = true;
            case INVOKEVIRTUAL: //Normally calling a method
            case INVOKESPECIAL: //Usually a super call
            case INVOKEINTERFACE: //An interface method. Has two additional bytes of info, but they're not important for this.
                int virtualMethIndex = uVal(index + 1);
                String virtualMethName = cp.getMethodrefName(virtualMethIndex);
                if (virtualMethName == null) {
                    text.add("{METHOD NOT FOUND}");
                }
                else {
                    processDescriptor(virtualMethName, cp.getMethodrefType(virtualMethIndex), staticMethod, cp.getMethodrefClassName(virtualMethIndex));
                }
                break;
            case INVOKEDYNAMIC:
                //Gets a symbolic reference to a CallSite resolved to a reference to a bound instance of CallSite for this specific instruction.
                //Then method indicated by CallSite is invoked.
                break;
            case NEW:
                int type = uVal(index + 1);
                String defClass = cp.getClassInfo(type);
                if (defClass == null) {
                    text.add("{CLASS NOT FOUND}");
                    push(UNDF);
                }
                else {
                    pushSuf(defClass, " new");
                }
                break;
            case NEWARRAY:
                a = stack.pop();
                if (a.val instanceof Integer) {
                    popStr(a);
                    push(newArray(bVal(index + 1), (Integer) a.val));
                }
                else {
                    text.add("{NO ARRAY SIZE} ").add(a.val);
                    push(newArray(bVal(index + 1), 1));
                }
                break;
            case ANEWARRAY:
                a = stack.pop();
                String arrClassInfo = cp.getClassInfo(uVal(index + 1));
                if (arrClassInfo != null) {
                    if (a.val instanceof Integer) {
                        popStr(a);
                        push(new ArrayRepresentation("arr" + arrCount++, arrClassInfo, ()->null, (Integer) a.val));
                    }
                    else {
                        text.add("{NO ARRAY SIZE} ").add(a.val);
                        push(new ArrayRepresentation("arr" + arrCount++, arrClassInfo, ()->null, 1));
                    }
                }
                else {
                    text.add("{NO CLASS FOUND AT INDEX ").add(uVal(index + 1)).add('}');
                }
                break;
            case ARRAYLENGTH:
                popStr(a = stack.pop());
                push(a + ".length");
                break;
            case ATHROW:
                endPopStr(a = stack.pop());
                stack.clear();
                stack.push(a);
                text.add(" [Empty Stack], ");
                text.add(a);
                break;
            case CHECKCAST:
                String checkCast = cp.getClassInfo(uVal(index + 1));
                if (checkCast == null) checkCast = UNDF;
                a = stack.pop();
                popFormatted("%s instanceof " + checkCast + " ", a);
                if (!trackParams.isEmpty()) {
                    String result = "((" + checkCast + ')' + a + ')';
                    push(result);

                    for (String s : trackParams) {
                        if (result.contains(s)) {
                            trackResult.add(result);
                            break;
                        }
                    }
                }
                else {
                    push(checkCast);
                }
                break;
            case INSTANCEOF:
                String instanceOf = cp.getClassInfo(uVal(index + 1));
                if (instanceOf == null) instanceOf = UNDF;
                popStr(a = stack.pop());
                push(a.toString() + " instanceof " + instanceOf);
                break;
            case MONITORENTER:
                popFormatted("synchronized on %s ->", 1);
                break;
            case MONITOREXIT:
                popFormatted("end synchronized on %s ->", 1);
                break;
            case WIDE:
                break;
            case MULTIANEWARRAY:
                String multiArrClassInfo = cp.getClassInfo(uVal(index + 1));
                if (multiArrClassInfo != null) {
                    int numDimensions = bVal(index + 3);
                    Object[] dimensions = new Object[numDimensions];
                    for (int i = 0; i < numDimensions; ++i)
                        dimensions[i] = stack.pop();
                    popStr(dimensions);
                    push(new ArrayRepresentation("arr" + arrCount++, multiArrClassInfo, ()->null, dimensions));
                }
                else {
                    text.add("{NO CLASS FOUND AT INDEX ").add(uVal(index + 1)).add('}');
                }
                break;
            case IFNULL:
                popFormatted("if (%s == null) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case IFNONNULL:
                popFormatted("if (%s != null) go to offset ", 1);
                text.add(jump(index, uVal(index + 1)));
                break;
            case GOTO_W:
                break;
            case JSR_W:
                break;
            case NOP:
            default: //breakpoint, reserved, impdep1, impdep2
                break;
        }
        text.add('\n');
    }

    private int bVal(int index) {
        return ci.byteAt(index);
    }
    private int uVal(int index) {
        return ci.u16bitAt(index);
    }
    private int wVal(int index) {
        return ci.s32bitAt(index);
    }
    private VarObject var(int index) throws BadBytecode {
        if (index < 0)
            throw new BadBytecode("Attempted to access variable at index " + index);
        VarObject o = vars.get(index);
        return o == null ? getObj(UNDF) : o;
    }

    private int jump(int index, int offset) {
        ProgramState overlap = jumpMap.remove(index + offset);
        jumpMap.put(index + offset, new ProgramState(index, overlap));
        return offset;
    }

    private void push(Object val) {
        if (val == null)
            val = NULL;
        stack.push(val instanceof VarObject ? (VarObject) val : getObj(val));
        text.add("-> ").add(val);
    }
    private void pushSuf(Object val, Object... suffix) {
        if (val == null)
            val = NULL;
        StringBuilder str = new StringBuilder();
        str.append(val);
        for (Object o : suffix) str.append(o == null ? NULL : o);
        stack.push(val instanceof VarObject ? (VarObject) val : getObj(val));
        text.add("-> ").add(str);
    }
    private void multiPush(Object... vals) {
        boolean first = true;
        text.add("-> ");
        for (Object o : vals) {
            stack.push(o instanceof VarObject ? (VarObject) o : getObj(o));
            if (first) {
                text.add(o);
                first = false;
            }
            else {
                text.add(", ").add(o);
            }
        }
    }

    private void popStr(Object... popped) {
        boolean first = true;
        for (int i = popped.length - 1; i >= 0; --i) {
            Object o = popped[i];
            if (first) {
                first = false;
            }
            else {
                text.add(", ");
            }
            text.add(o == null ? NULL : o);
        }
        text.add(' ');
    }
    private void popAmt(int amt) {
        if (amt <= 0)
            return;
        VarObject[] popped = new VarObject[amt];
        for (int i = 0; i < amt; ++i) {
            popped[i] = stack.pop();
        }
        popStr((Object[]) popped);
    }
    private String popFormatted(String format, int amt) {
        if (amt < 0)
            return "";
        VarObject[] popped = new VarObject[amt];
        for (int i = amt - 1; i >= 0; --i) {
            popped[i] = stack.pop();
        }
        String result = String.format(format, (Object[]) popped);
        text.add(result);
        return result;
    }
    private void popFormatted(String format, VarObject... popped) {
        Object[] reverse = new Object[popped.length];
        for (int i = 0; i < popped.length; ++i) {
            reverse[i] = popped[popped.length - (i + 1)].val;
            /*if (reverse[i] != null)
                reverse[i] = reverse[i].toString();*/
        }
        text.add(String.format(format, reverse));
    }
    private void endPopStr(VarObject... popped) {
        boolean first = true;
        for (int i = popped.length - 1; i >= 0; --i) {
            VarObject o = popped[i];
            if (first) {
                first = false;
            }
            else {
                text.add(", ");
            }
            text.add(o);
        }
        text.add(" ->");
    }

    private void store(int index) {
        VarObject o = stack.pop();
        text.add(o).add(" => ");
        text.add("var").add(index);
        vars.compute(index, (k, v) -> v == null ? o : v.set(o.val));
    }
    private void iinc(int index, int inc) throws BadBytecode {
        VarObject obj = var(index);
        if (obj.val instanceof Integer) {
            text.add("var").add(index).add(" = ").add(obj.val).add(" + ").add(inc);
            vars.put(index, getObj((Integer) obj.val + inc));
        }
        else {
            obj.val = obj.val.toString() + " + " + inc;
            text.add("var").add(index).add(" = ").add(obj);
            vars.put(index, obj);
        }
    }

    private Object readArr(Class<?> arrType) {
        VarObject index = stack.pop();
        if (index.val instanceof Integer) {
            VarObject arr = stack.pop();
            if (arr.val instanceof ArrayRepresentation) {
                if (arrType.equals(Object.class) || ((ArrayRepresentation) arr.val).dataType.equals(arrType.getName())) {
                    Object val = ((ArrayRepresentation) arr.val).get((Integer) index.val);
                    text.add(arr).add('[').add(index).add(']');
                    return val;
                }
                else {
                    text.add("WRONG ARRAY TYPE ").add(arr).add(' ');
                }
            }
            else {
                text.add(arr).add(' ');
            }
        }
        else {
            stack.pop();
            text.add(UNDF);
        }
        return UNDF;
    }
    private void storeArr(Class<?> arrType) {
        VarObject val = stack.pop();
        VarObject index = stack.pop();
        if (index.val instanceof Integer) {
            VarObject arr = stack.pop();
            if (arr.val instanceof ArrayRepresentation) {
                if (!arrType.equals(Object.class) && !((ArrayRepresentation) arr.val).dataType.equals(arrType.getName())) {
                    text.add("INVALID ARRAY TYPE ").add(((ArrayRepresentation) arr.val).dataType);
                    return;
                }

                text.add(arr).add('[').add(index).add("] = ").add(val).add(" -> ");
                if (!((ArrayRepresentation) arr.val).set(val, (Integer) index.val)) {
                    text.add(" INDEX OUT OF BOUNDS");
                }
            }
            else {
                text.add(arr).add('[').add(index).add("] = ").add(val).add(" -> ");
            }
        }
        else {
            text.add(stack.pop()).add('[').add(index).add("] = ").add(val).add(" -> ");
        }
    }

    private ArrayRepresentation newArray(int aType, int... size) {
        switch (aType) {
            case T_BOOLEAN:
                return new ArrayRepresentation("arr" + arrCount++, boolean.class, ()->false, size);
            case T_CHAR:
                return new ArrayRepresentation("arr" + arrCount++, char.class, ()->(char)0, size);
            case T_FLOAT:
                return new ArrayRepresentation("arr" + arrCount++, float.class, ()->0.0f, size);
            case T_DOUBLE:
                return new ArrayRepresentation("arr" + arrCount++, double.class, ()->0.0, size);
            case T_BYTE:
                return new ArrayRepresentation("arr" + arrCount++, byte.class, ()->(byte)0, size);
            case T_SHORT:
                return new ArrayRepresentation("arr" + arrCount++, short.class, ()->(short)0, size);
            case T_INT:
                return new ArrayRepresentation("arr" + arrCount++, int.class, ()->0, size);
            case T_LONG:
                return new ArrayRepresentation("arr" + arrCount++, long.class, ()->0L, size);
            default:
                text.add("INVALID ARRAY TYPE ").add(aType);
                return new ArrayRepresentation("arr" + arrCount++, Object.class, ()->null, size);
        }
    }

    private <T> Function<Number, T> combine(Function<Number, T> conv, Function<T, T> math) {
        return (x) -> {
                T result = conv.apply(x);
                result = math.apply(result);
                return result;
        };
    }

    private <T> void arith(Object a, Function<Number, T> math) {
        arith(a, math, x->x, (Object[]) null);
    }
    private <T> void arith(Object a, Function<Number, T> math, Function<String, String> strConv) {
        arith(a, math, strConv, (Object[]) null);
    }
    private <T> void arith(Object a, Function<Number, T> math, Function<String, String> strConv, Object... suffix) {
        if (a instanceof Number) {
            if (suffix != null && suffix.length > 0)
                pushSuf(math.apply((Number) a), suffix);
            else
                push(math.apply((Number) a));
        }
        else {
            if (a == null)
                push(NULL);
            else
                push(strConv.apply(a.toString()));
        }
    }

    private <T> void arith(Object a, Object b, Function<Number, T> conv, BiFunction<T, T, ?> math) {
        arith(a, b, conv, math, (x,y)->(x + ", " + y), (Object[]) null);
    }
    private <T> void arith(Object a, Object b, Function<Number, T> conv, BiFunction<T, T, ?> math, BiFunction<String, String, String> strConv) {
        arith(a, b, conv, math, strConv, (Object[]) null);
    }
    private <T> void arith(Object a, Object b, Function<Number, T> conv, BiFunction<T, T, ?> math, BiFunction<String, String, String> strConv, Object... suffix) {
        if (a == null)
            a = UNDF;
        if (b == null)
            b = UNDF;

        if (!a.getClass().equals(b.getClass())) {
            push(strConv.apply(a.toString(), b.toString()));
            return;
        }

        if (a instanceof Number && b instanceof Number) {
            if (suffix != null && suffix.length > 0)
                pushSuf(math.apply(conv.apply((Number) a), conv.apply((Number) b)), suffix);
            else
                push(math.apply(conv.apply((Number) a), conv.apply((Number) b)));
        }
        else {
            push(strConv.apply(a.toString(), b.toString()));
        }
    }

    private <T> void shift(VarObject a, VarObject b, Function<Number, T> aConv, BiFunction<T, Integer, ?> shift, BiFunction<String, String, String> strConv) {
        shift(a, b, aConv, shift, strConv, (Object[]) null);
    }
    private <T> void shift(VarObject a, VarObject b, Function<Number, T> aConv, BiFunction<T, Integer, ?> shift, BiFunction<String, String, String> strConv, Object... suffix) {
        if (!(a.val instanceof Number) || !(b.val instanceof Integer)) {
            push(strConv.apply(a.val == NULL ? UNDF : a.toString(), b.val == null ? UNDF : b.toString()));
            return;
        }

        T aVal = aConv.apply((Number) a.val);
        int dist = (Integer) b.val & 0x3f;
        Object result = shift.apply(aVal, dist);
        if (suffix != null && suffix.length > 0)
            pushSuf(result, suffix);
        else
            push(result);
    }

    private void processDescriptor(String name, String descriptor, boolean isStatic, String className) {
        if (descriptor == null || descriptor.length() < 3) {
            text.add(name).add(" [").add(className).add("]");
            return;
        }

        char c;
        int i = 1;
        int params = isStatic ? 0 : 1;
        outer:
        for (; i < descriptor.length(); ++i) { //First character is just opening parentheses
            c = descriptor.charAt(i);
            switch (c) {
                case ')':
                    ++i;
                    break outer;
                case 'L':
                    ++i;
                    while (i < descriptor.length()) {
                        if (descriptor.charAt(i) == ';')
                            break;
                        ++i;
                    }
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'Z':
                    ++params;
                    break;
                case '[':
                    break;
            }
        }

        StringBuilder format = new StringBuilder(isStatic ? className : "%s");
        format.append('.').append(name);
        if (!isStatic) {
            text.add('[').add(className).add('.').add(name).add("] ");
        }
        int n = isStatic ? 0 : 1;
        if (n < params) {
            format.append('(');
            for (; n < params - 1; ++n)
                format.append("%s, ");
            if (n < params)
                format.append("%s)");
        }
        else
            format.append("()");

        String finalFormat = format.toString();

        String popResult = "";
        if (params == 0) {
            text.add(format);
        }
        else {
            popResult = popFormatted(finalFormat, params);
        }
        text.add(' ');

        if (!popResult.isEmpty() && !trackParams.isEmpty()) {
            for (String s : trackParams) {
                if (popResult.contains(s)) {
                    trackResult.add(popResult);
                    break;
                }
            }
            push(popResult);
            return;
        }

        if (i < descriptor.length() && descriptor.charAt(i) != 'V') {
            format.setLength(0);
            for (; i < descriptor.length(); ++i) { //First character is just opening parentheses
                c = descriptor.charAt(i);
                switch (c) {
                    case 'L':
                        int start = Math.max(i, descriptor.lastIndexOf('/')) + 1;
                        if (start >= descriptor.length() - 1) {
                            push(UNDF);
                            return;
                        }
                        push(descriptor.substring(start, descriptor.length() - 1) + format);
                        return;
                    case 'B':
                        push("byte" + format); return;
                    case 'C':
                        push("char" + format); return;
                    case 'D':
                        push("double" + format); return;
                    case 'F':
                        push("float" + format); return;
                    case 'I':
                        push("int" + format); return;
                    case 'J':
                        push("long" + format); return;
                    case 'S':
                        push("short" + format); return;
                    case 'Z':
                        push("boolean" + format); return;
                    case '[':
                        format.append("[]");
                        break;
                }
            }
        }
        else if (params == 0) {
            text.add("void"); //no params, no return (no change to stack)
        }
        else {
            text.add("-> void"); //Removed params from stack
        }
    }

    private static class ArrayRepresentation {
        final String identity;
        final String dataType;
        final int[] dimensions;
        final int[] dimensionMultipliers;
        final Object[] arr;
        final boolean determinate;

        public <T> ArrayRepresentation(String identity, Class<T> clazz, Supplier<T> defaultVal, int[] dimensions) {
            this.identity = identity;
            this.dataType = clazz.getName();
            this.dimensions = dimensions;
            this.dimensionMultipliers = new int[dimensions.length + 1]; //The last value will never be used.
            dimensionMultipliers[0] = 1;
            determinate = true;

            int totalSize = 1;
            for (int i = 0; i < dimensions.length; ++i) {
                totalSize *= dimensions[i];
                dimensionMultipliers[i + 1] = dimensions[i];
            }
            arr = new Object[totalSize];
            for (int i = 0; i < totalSize; ++i)
                arr[i] = defaultVal.get();
        }
        public <T> ArrayRepresentation(String identity, String typeName, Supplier<T> defaultVal, Object... dimensions) {
            this.identity = identity;
            this.dataType = typeName;
            this.dimensions = new int[dimensions.length];
            this.dimensionMultipliers = new int[dimensions.length + 1]; //The last value will never be used.
            dimensionMultipliers[0] = 1;

            boolean determinate = true;

            int totalSize = 1;
            for (int i = 0; i < dimensions.length; ++i) {
                Object o = dimensions[i];
                if (o instanceof Integer) {
                    totalSize *= (int) o;
                    this.dimensions[i] = (int) o;
                    dimensionMultipliers[i + 1] = (int) o;
                }
                else {
                    determinate = false;
                    totalSize = 0;
                    for (; i < dimensions.length; ++i)
                        dimensionMultipliers[i + 1] = 1;
                }
            }
            this.determinate = determinate;
            if (determinate) {
                arr = new Object[totalSize];
                for (int i = 0; i < totalSize; ++i)
                    arr[i] = defaultVal.get();
            }
            else {
                arr = null;
            }
        }

        public Object get(int... indices) {
            if (indices.length != dimensions.length)
                return "ARRAY INDICES MISMATCH";

            if (!determinate) {
                StringBuilder sb = new StringBuilder(identity);
                for (int i = 0; i < indices.length; ++i)
                    sb.append('[').append(indices[i]).append(']');
                return sb.toString();
            }

            Integer index = translateIndices(indices);
            if (index == null)
                return "ARRAY INDEX OUT OF BOUNDS";

            return arr[index];
        }

        public boolean set(Object val, int... indices) {
            if (!determinate) {
                return true;
            }

            Integer index = translateIndices(indices);
            if (index == null)
                return false;

            arr[index] = val;
            return true;
        }

        private Integer translateIndices(int... indices) {
            int index = 0;
            for (int i = 0; i < indices.length; ++i) {
                if (indices[i] < 0 || indices[i] > dimensions[i])
                    return null;
                index += indices[i] * dimensionMultipliers[i];
            }
            return index;
        }

        @Override
        public String toString() {
            return identity + "_" + dataType;
        }
    }

    //The only flexibility is between primitives and their wrappers.
    public static boolean strictTestAssignable(Class<?> target, Class<?> src) {
        if (target == null)
            return false;
        if (src == null) {
            return !target.isPrimitive();
        }
        if (target.isAssignableFrom(src))
            return true;

        switch (target.getName())
        {
            case "java.lang.Byte":
                target = byte.class;
                break;
            case "java.lang.Character":
                target = char.class;
                break;
            case "java.lang.Short":
                target = short.class;
                break;
            case "java.lang.Integer":
                target = int.class;
                break;
            case "java.lang.Long":
                target = long.class;
                break;
            case "java.lang.Float":
                target = float.class;
                break;
            case "java.lang.Double":
                target = double.class;
                break;
            case "java.lang.Boolean":
                target = boolean.class;
                break;
        }

        //The exceptional cases are primitives, which require equality for isAssignableFrom to return true.
        if (target.isPrimitive()) {
            //convert src from primitive wrapper to primitive
            switch (src.getName())
            {
                case "java.lang.Byte":
                    src = byte.class;
                    break;
                case "java.lang.Character":
                    src = char.class;
                    break;
                case "java.lang.Short":
                    src = short.class;
                    break;
                case "java.lang.Integer":
                    src = int.class;
                    break;
                case "java.lang.Long":
                    src = long.class;
                    break;
                case "java.lang.Float":
                    src = float.class;
                    break;
                case "java.lang.Double":
                    src = double.class;
                    break;
                case "java.lang.Boolean":
                    src = boolean.class;
                    break;
            }
            if (!src.isPrimitive()) //can't assign non-primitives to primitives.
                return false;

            return target == src;
        }

        return false;
    }


    private static class IntedetermineDeque implements Deque<VarObject> {
        private Deque<VarObject> subQueue = new ArrayDeque<>();

        public void setSub(Deque<VarObject> stack) {
            subQueue = stack;
        }

        @Override
        public void addFirst(VarObject o) {
            subQueue.addFirst(o);
        }
        @Override
        public void addLast(VarObject o) {
            subQueue.addLast(o);
        }
        @Override
        public boolean offerFirst(VarObject o) {
            return subQueue.offerFirst(o);
        }

        @Override
        public boolean offerLast(VarObject o) {
            return subQueue.offerLast(o);
        }

        @Override
        public VarObject removeFirst() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.removeFirst();
        }

        @Override
        public VarObject removeLast() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.removeLast();
        }

        @Override
        public VarObject pollFirst() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.pollFirst();
        }

        @Override
        public VarObject pollLast() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.pollLast();
        }

        @Override
        public VarObject getFirst() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.getFirst();
        }

        @Override
        public VarObject getLast() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.getLast();
        }

        @Override
        public VarObject peekFirst() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.peekFirst();
        }

        @Override
        public VarObject peekLast() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.peekLast();
        }

        @Override
        public boolean removeFirstOccurrence(Object o) {
            return subQueue.removeFirstOccurrence(o);
        }

        @Override
        public boolean removeLastOccurrence(Object o) {
            return subQueue.removeLastOccurrence(o);
        }

        @Override
        public boolean add(VarObject o) {
            return subQueue.add(o);
        }

        @Override
        public boolean offer(VarObject o) {
            return subQueue.offer(o);
        }

        @Override
        public VarObject remove() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.remove();
        }

        @Override
        public VarObject poll() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.poll();
        }

        @Override
        public VarObject element() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.element();
        }

        @Override
        public VarObject peek() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.peek();
        }

        @Override
        public void push(VarObject o) {
            subQueue.push(o);
        }

        @Override
        public VarObject pop() {
            if (subQueue.isEmpty())
                return getObj(UNDF);
            return subQueue.pop();
        }

        @Override
        public boolean remove(Object o) {
            return subQueue.remove(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return subQueue.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends VarObject> c) {
            return subQueue.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return subQueue.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return subQueue.retainAll(c);
        }

        @Override
        public void clear() {
            subQueue.clear();
        }

        @Override
        public boolean contains(Object o) {
            return subQueue.contains(o);
        }

        @Override
        public int size() {
            return subQueue.size();
        }

        @Override
        public boolean isEmpty() {
            return subQueue.isEmpty();
        }

        @Override
        public Iterator<VarObject> iterator() {
            return subQueue.iterator();
        }

        @Override
        public Object[] toArray() {
            return subQueue.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return subQueue.toArray(a);
        }

        @Override
        public Iterator<VarObject> descendingIterator() {
            return subQueue.descendingIterator();
        }
    }


    private class ProgramState {
        private final int index;
        private final Object[] stackState;
        private final HashMap<Integer, Object> varState;
        private final List<Integer> overlapIndices;

        public ProgramState(int index) {
            this(index, null);
        }

        public ProgramState(int index, ProgramState overlap) {
            this.index = index;

            this.stackState = new Object[stack.size()];
            VarObject[] stackArray = stack.toArray(new VarObject[0]);
            for (int i = 0; i < stack.size(); ++i) {
                this.stackState[i] = stackArray[i].val;
            }

            this.varState = new HashMap<>();
            for (Map.Entry<Integer, VarObject> variable : vars.entrySet()) {
                varState.put(variable.getKey(), variable.getValue().val);
            }

            if (overlap == null) {
                overlapIndices = Collections.emptyList();
            }
            else {
                overlapIndices = new ArrayList<>();
                overlapIndices.add(overlap.index);
                overlapIndices.addAll(overlap.overlapIndices);
            }
        }

        public void restore() {
            text.add("Jumped from ").add(index);
            for (int i : overlapIndices) {
                text.add(" or ").add(i);
            }
            text.add('\n');

            stack.clear();
            for (Object o : stackState)
                stack.add(getObj(o));

            vars.clear();
            for (Map.Entry<Integer, Object> variable : varState.entrySet()) {
                vars.put(variable.getKey(), getObj(variable.getValue()));
            }
        }
    }

    private static class VarObject implements Pool.Poolable {
        Object val;

        public VarObject() {
        }

        @Override
        public void reset() {
            val = UNDF;
        }

        public VarObject set(Object val) {
            this.val = val;
            return this;
        }

        @Override
        public String toString() {
            return val.toString();
        }
    }

    private static VarObject getObj(Object val) {
        return varObjectPool.obtain().set(val);
    }

    private static final Array<VarObject> allObjs = new Array<>(256);
    private static void resetObjs() {
        varObjectPool.freeAll(allObjs);
    }
    private static final Pool<VarObject> varObjectPool = new Pool<VarObject>() {
        @Override
        protected VarObject newObject() {
            VarObject obj = new VarObject();
            allObjs.add(obj);
            return obj;
        }
    };


    private interface TextProcessor {
        TextProcessor add(String s);
        TextProcessor add(Object o);

        void clear();
    }

    private static class OutputTextProcessor extends StringBuilder implements TextProcessor {
        @Override
        public TextProcessor add(String s) {
            return (TextProcessor) super.append(s);
        }
        @Override
        public TextProcessor add(Object o) {
            return (TextProcessor) super.append(o.toString());
        }

        @Override
        public void clear() {
            super.setLength(0);
        }
    }

    private static class NoTextProcessor implements TextProcessor {
        @Override
        public TextProcessor add(String s) {
            return this;
        }

        @Override
        public TextProcessor add(Object o) {
            return this;
        }

        @Override
        public void clear() {

        }
    }
}
