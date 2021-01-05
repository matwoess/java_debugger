import com.sun.jdi.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Util {

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

	public static void printLocation(Location location) {
		System.out.printf("Line: %d, bci: %d\n", location.lineNumber(), location.codeIndex());
	}

	public static Response printBreakpoints(List<Integer> breakpoints) {
		if (breakpoints.size() == 0) {
			System.out.println("Currently no breakpoints");
		} else {
			System.out.println("Current breakpoints (line numbers): " + breakpoints.stream()
					.map(Object::toString).collect(Collectors.joining(", ")));
		}
		return Response.OK;
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

	public static Response printHelp() {
		String helpFileName = "Commands.txt";
		Path helpFile = Paths.get(helpFileName);
		try {
			Files.readAllLines(helpFile).forEach(System.out::println);
		} catch (IOException e) {
			System.out.println("Help file '" + helpFileName + "' in root directory not found.");
		}
		return Response.OK;
	}
}
