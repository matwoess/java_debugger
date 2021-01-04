import com.sun.jdi.*;

import java.util.List;
import java.util.Optional;

public class Variables {

	static Response printLocals(ThreadReference thread) throws IncompatibleThreadStateException {
		if (thread.frames().size() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		StackFrame frame = thread.frame(0);
		try {
			for (LocalVariable v : frame.visibleVariables()) {
				System.out.print(v.name() + ": " + v.type().name() + " = ");
				Misc.printValue(thread.frame(0).getValue(v), thread);
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.OK;
	}

	static Response printGlobals(ThreadReference thread) throws IncompatibleThreadStateException {
		if (thread.frames().size() == 0) {
			System.out.println("No frames initialized yet");
			return Response.NOK;
		}
		StackFrame frame = thread.frame(0);
		ObjectReference objRef = frame.thisObject();
		ClassType classType = (ClassType) frame.location().method().declaringType();
		try {
			for (Field f : classType.allFields()) {
				System.out.print(f.name() + ": " + f.type().name() + " = ");
				Value fieldValue;
				if (f.isStatic()) {
					fieldValue = classType.getValue(f);
				} else {
					fieldValue = objRef.getValue(f);
				}
				Misc.printValue(fieldValue);
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.OK;
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
				Misc.printValue(val, frame.thread());
				System.out.println();
			}
		} else {
			System.out.print(var.name() + ": " + var.type().name() + " = ");
			Misc.printValue(frame.getValue(var), frame.thread());
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
				Misc.printValue(val, thread);
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
			Misc.printValue(fieldValue, thread);
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
			return printObjectField(val, varName, fieldName);
		} else {
			System.out.printf("No visible local variable with name '%s' found.\n", varName);
			frame.location().method().declaringType();
			ClassType classType = (ClassType) frame.location().method().declaringType();
			List<Field> flds = classType.visibleFields();
			Optional<Field> fld = flds.stream().filter(fv -> fv.name().equals(varName)).findFirst();
			if (fld.isPresent()) {
				Field fl = fld.get();
				Value val = classType.getValue(fl);
				return printObjectField(val, varName, fieldName);
			} else {
				System.out.printf("No visible field with name '%s' found.\n", varName);
				return Response.NOK;
			}
		}
	}

	static Response printObjectField(Value val, String varName, String fieldName) {
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
		Misc.printValue(fieldValue);
		System.out.println();
		return Response.OK;
	}
}
