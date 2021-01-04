import com.sun.jdi.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Util {
	public static Response printBreakpoints(List<Integer> breakpoints) {
		if (breakpoints.size() == 0) {
			System.out.println("Currently no breakpoints");
		} else {
			System.out.println("Current breakpoints (line numbers): " + breakpoints.stream()
					.map(Object::toString).collect(Collectors.joining(", ")));
		}
		return Response.OK;
	}

	static Response printLocals(ThreadReference thread) throws IncompatibleThreadStateException {
		if (thread.frames().size() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		printLocalVars(thread.frame(0));
		return Response.OK;
	}

	static void printLocalVars(StackFrame frame) {
		ThreadReference thread = frame.thread();
		try {
			for (LocalVariable v : frame.visibleVariables()) {
				System.out.print(v.name() + ": " + v.type().name() + " = ");
				printValue(thread.frame(0).getValue(v), thread);
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	static Response printGlobals(ThreadReference thread) throws IncompatibleThreadStateException {
		if (thread.frames().size() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		printGlobalsVars(thread.frame(0));
		return Response.OK;
	}

	static void printGlobalsVars(StackFrame frame) {
		try {
			ClassType classType = (ClassType) frame.location().method().declaringType();
			ObjectReference objRef = frame.thisObject();
			for (Field f : classType.allFields()) {
				System.out.print(f.name() + ": " + f.type().name() + " = ");
				Value fieldValue;
				if (f.isStatic()) {
					fieldValue = classType.getValue(f);
				} else {
					fieldValue = objRef.getValue(f);
				}
				printValue(fieldValue);
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void printValue(Value val) {
		printValue(val, null);
	}

	static void printValue(Value val, ThreadReference thread) {
		if (val instanceof IntegerValue) {
			System.out.print(((IntegerValue) val).value() + " ");
		} else if (val instanceof LongValue) {
			System.out.print(((LongValue) val).value() + "L ");
		} else if (val instanceof FloatValue) {
			System.out.print(((FloatValue) val).value() + "f ");
		} else if (val instanceof DoubleValue) {
			System.out.print(((DoubleValue) val).value() + " ");
		} else if (val instanceof StringReference) {
			System.out.print('"' + ((StringReference) val).value() + '"' + ' ');
		} else if (val instanceof ArrayReference) {
			List<Value> values = ((ArrayReference) val).getValues();
			System.out.print("[ ");
			for (Value v : values) {
				printValue(v, thread);
			}
			System.out.print("]");
		} else if (val instanceof ObjectReference) {
			ObjectReference ref = (ObjectReference) val;
			if (ref.type().signature().equals("Ljava/util/ArrayList;")) {
				Method toArray = ref.referenceType().methodsByName("toArray", "()[Ljava/lang/Object;").get(0);
				try {
					Value value = ref.invokeMethod(thread, toArray, Collections.emptyList(), 0);
					printValue(value, thread);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				Method toString = ref.referenceType().methodsByName("toString", "()Ljava/lang/String;").get(0);
				try {
					Value value = ref.invokeMethod(thread, toString, Collections.emptyList(), 0);
					printValue(value, thread);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else if (val == null) {
			System.out.print("null ");
		} else {
			System.out.print("TODO ");
		}
	}


	public static Response stackTrace(ThreadReference thread) throws IncompatibleThreadStateException {
		List<StackFrame> frames = thread.frames();
		Consumer<Integer> identFn = (Integer x) -> {
			for (int i = 0; i < x; i++) System.out.print(" ");
		};
		System.out.println("Stack trace:");
		int ident = -2;
		for (StackFrame frame : frames) {
			Method meth = frame.location().method();
			identFn.accept(ident);
			if (ident >= 0) System.out.print("L ");
			System.out.println(meth.declaringType().name() + "." + meth.name() + ":");
			// printLocalVars(frame);
			ident += 2;
		}
		return Response.OK;
	}

	public static Response printProgramState(String debugClass, Location currLoc, List<Integer> breakpoints) {
		Path path = Paths.get(debugClass + ".java");
		try {
			List<String> programLines = Files.readAllLines(path);
			int lineNr = 0;
			for (String line : programLines) {
				lineNr++;
				if (currLoc != null && lineNr == currLoc.lineNumber()) {
					System.out.print(">");
				} else {
					System.out.print(" ");
				}
				System.out.printf("%3d", lineNr);
				System.out.print(" ");
				if (breakpoints.contains(lineNr)) {
					System.out.print("o");
				} else {
					System.out.print(" ");
				}
				System.out.print(" ");
				System.out.println(line);
			}
			return Response.OK;
		} catch (IOException ex) {
			ex.printStackTrace();
			return Response.NOK;
		}
	}

	public static Response printValueByName(ThreadReference thread, String[] args) throws IncompatibleThreadStateException, AbsentInformationException, ClassNotLoadedException {
		if (args == null || args.length > 2) {
			System.out.println("Invalid number of arguments.\nUsage: print <var> [<idx>]");
			return Response.NOK;
		}
		String varName = args[0];
		if (thread.frameCount() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		Integer index = null;
		if (args.length == 2) {
			try {
				index = Integer.valueOf(args[1]);
			} catch (Exception e) {
				System.out.println("could not convert " + args[1] + " to integer index");
				return Response.NOK;
			}
		}
		StackFrame frame = thread.frame(0);
		List<LocalVariable> vars = frame.visibleVariables();
		Optional<LocalVariable> var = vars.stream().filter(lv -> lv.name().equals(varName)).findFirst();
		if (var.isPresent()) {
			printSingleVariable(var.get(), frame, index);
		} else {
			System.out.printf("No visible local variable with name '%s' found.\n", varName);
			frame.location().method().declaringType();
			ClassType classType = (ClassType) frame.location().method().declaringType();
			List<Field> flds = classType.visibleFields();
			Optional<Field> fld = flds.stream().filter(fv -> fv.name().equals(varName)).findFirst();
			if (fld.isPresent()) {
				printSingleField(fld.get(), classType, thread, index);
			} else {
				System.out.printf("No visible field with name '%s' found.\n", varName);
			}
		}
		return Response.OK;
	}

	static void printSingleVariable(LocalVariable var, StackFrame frame, Integer idx) throws ClassNotLoadedException {
		if (var.type() instanceof ArrayType && idx != null) {
			ArrayReference arr = ((ArrayReference) frame.getValue(var));
			if (idx >= arr.getValues().size()) {
				System.out.println("Index out of range.");
			} else {
				Value val = arr.getValue(idx);
				System.out.print(var.name() + "[" + idx + "]" + (val != null ? ": " + val.type().name() : "") + " = ");
				printValue(val, frame.thread());
				System.out.println();
			}
		} else {
			System.out.print(var.name() + ": " + var.type().name() + " = ");
			printValue(frame.getValue(var), frame.thread());
			System.out.println();
		}
	}

	static void printSingleField(Field fld, ClassType classType, ThreadReference thread, Integer idx) throws ClassNotLoadedException, IncompatibleThreadStateException {
		if (fld.type() instanceof ArrayType && idx != null) {
			ArrayReference arr = ((ArrayReference) classType.getValue(fld));
			if (idx >= arr.getValues().size()) {
				System.out.println("Index out of range.");
			} else {
				Value val = arr.getValue(idx);
				System.out.print(fld.name() + "[" + idx + "]" + (val != null ? ": " + val.type().name() : "") + " = ");
				printValue(val, thread);
				System.out.println();
			}
		} else {
			System.out.print(fld.name() + ": " + fld.type().name() + " = ");
			ObjectReference objRef = thread.frame(0).thisObject();
			Value fieldValue;
			if (fld.isStatic()) {
				fieldValue = classType.getValue(fld);
			} else {
				fieldValue = objRef.getValue(fld);
			}
			printValue(fieldValue, thread);
			System.out.println();
		}
	}

	public static Response printObjectFieldByName(ThreadReference thread, String[] args) throws IncompatibleThreadStateException, AbsentInformationException, ClassNotLoadedException {
		if (args == null || args.length != 2) {
			System.out.println("Invalid number of arguments.\nUsage: print <var> <fld>");
			return Response.NOK;
		}
		String varName = args[0];
		if (thread.frameCount() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		String fieldName = args[1];
		StackFrame frame = thread.frame(0);
		List<LocalVariable> vars = frame.visibleVariables();
		Optional<LocalVariable> var = vars.stream().filter(lv -> lv.name().equals(varName)).findFirst();
		if (var.isPresent()) {
			LocalVariable lv = var.get();
			Value val = frame.getValue(lv);
			return printObjectField(val, varName, fieldName, thread);
		} else {
			System.out.printf("No visible local variable with name '%s' found.\n", varName);
			frame.location().method().declaringType();
			ClassType classType = (ClassType) frame.location().method().declaringType();
			List<Field> flds = classType.visibleFields();
			Optional<Field> fld = flds.stream().filter(fv -> fv.name().equals(varName)).findFirst();
			if (fld.isPresent()) {
				Field fl = fld.get();
				Value val = classType.getValue(fl);
				return printObjectField(val, varName, fieldName, thread);
			} else {
				System.out.printf("No visible field with name '%s' found.\n", varName);
				return Response.NOK;
			}
		}
	}

	static Response printObjectField(Value val, String varName, String fieldName, ThreadReference thread) {
		if (!(val instanceof ObjectReference)) {
			System.out.println(varName + " not an object.");
			return Response.NOK;
		}
		ObjectReference objRef = (ObjectReference) val;
		ClassType classType = (ClassType) objRef.referenceType();
		Field fld = classType.fieldByName(fieldName);
		if (fld == null) {
			System.out.println(varName + " has no field called " + fieldName + ".");
			return Response.NOK;
		}
		Value fieldValue;
		if (fld.isStatic()) {
			fieldValue = classType.getValue(fld);
		} else {
			fieldValue = objRef.getValue(fld);
		}
		System.out.print(varName + "." + fieldName + (fieldValue != null ? ": " + fieldValue.type().name() : "") + " = ");
		printValue(fieldValue);
		System.out.println();
		return Response.OK;
	}
}
