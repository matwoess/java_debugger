import com.sun.jdi.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
		try {
			for (LocalVariable v : frame.visibleVariables()) {
				System.out.print(v.name() + ": " + v.type().name() + " = ");
				printValue(frame.getValue(v));
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
			for (Field f : classType.allFields()) {
				System.out.print(f.name() + ": " + f.type().name() + " = ");
				printValue(classType.getValue(f));
				System.out.println();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void printValue(Value val) {
		if (val instanceof IntegerValue) {
			System.out.print(((IntegerValue) val).value() + " ");
		} else if (val instanceof FloatValue) {
			System.out.print(((FloatValue) val).value() + " ");
		} else if (val instanceof StringReference) {
			System.out.print('"' + ((StringReference) val).value() + '"' + ' ');
		} else if (val instanceof ArrayReference) {
			System.out.print("[ ");
			for (Value v : ((ArrayReference) val).getValues()) {
				printValue(v);
			}
			System.out.println("]");
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
}
