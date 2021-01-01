import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

public class Debugger {

	private final EventRequestManager reqManager;
	private final EventQueue eventQueue;
	private final String debugClass;
	private final VirtualMachine vm;
	private Location currLocation;
	private ThreadReference thread;
	private final List<Integer> breakpoints = new ArrayList<>();
	private final BlockingQueue<Response> responseQueue;

	public Debugger(String debugClass, BlockingQueue<Response> responseQueue) throws Exception {
		this.debugClass = debugClass;
		this.responseQueue = responseQueue;
		vm = initVM();
		reqManager = vm.eventRequestManager();
		eventQueue = vm.eventQueue();
		new Listener().start();
		enableClassPrepareRequest();
	}

	private VirtualMachine initVM() throws Exception {
		LaunchingConnector con = Bootstrap.virtualMachineManager().defaultConnector();
		Map<String, Connector.Argument> vmArgs = con.defaultArguments();
		vmArgs.get("main").setValue(debugClass); // set the main class
		try {
			VirtualMachine vm = con.launch(vmArgs);
			Process proc = vm.process();
			new Redirection(proc.getErrorStream(), System.err).start();
			new Redirection(proc.getInputStream(), System.out).start();
			return vm;
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new Exception("Error initializing VM");
	}

	public void enableClassPrepareRequest() {
		ClassPrepareRequest cpReq = reqManager.createClassPrepareRequest();
		cpReq.addClassFilter(debugClass);
		cpReq.enable();
	}

	public ThreadReference getThread() {
		return thread;
	}

	public void sendCommand(String commandString) throws IncompatibleThreadStateException {
		String command = commandString.split(" ")[0];
		Command cmd = Command.fromString(command);
		String[] args = null;
		if (commandString.length() > command.length()) {
			args = commandString.substring(command.length() + 1).split(" ");
		}
		switch (cmd) {
			case QUIT -> vm.exit(0);
			case RUN -> vm.resume();
			case LOCALS -> printLocals();
			case STEP_OVER -> stepOver(getThread());
			case STEP_INTO -> stepInto(getThread());
			case SET_BREAKPOINT -> installBreakpoint(args);
			case PRINT_BREAKPOINTS -> printBreakpoints();
			default -> {
				System.out.println("Invalid command");
				respond(Response.NOK);
			}
		}
	}

	private void printBreakpoints() {
		if (breakpoints.size() == 0) {
			System.out.println("No breakpoints yet");
		} else {
			System.out.println("Breakpoints at line numbers: " + breakpoints.stream()
					.map(Object::toString).collect(Collectors.joining(", ")));
		}
		respond(Response.OK);
	}

	private void respond(Response response) {
		responseQueue.add(response);
	}

	private void stepInto(ThreadReference thread) {
		System.out.println("NOT IMPLEMENTED");
	}

	private void installBreakpoint(String[] args) {
		if (args == null || args.length != 1) {
			System.out.println("Invalid number of arguments. Line number must be specified.");
			respond(Response.NOK);
			return;
		}
		int lineNr = Integer.parseInt(args[0]);
		breakpoints.add(lineNr);
		respond(Response.OK);
	}

	void stepOver(ThreadReference thread) {
		try {
			StepRequest req = reqManager.createStepRequest(thread,
					StepRequest.STEP_LINE, StepRequest.STEP_OVER);
			req.addClassFilter("*Test"); // create step requests only in class Test
			req.addCountFilter(1); // create step event after 1 step
			req.enable();
			vm.resume();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void printLocals() throws IncompatibleThreadStateException {
		if (getThread().frames().size() == 0) {
			System.out.println("No frames initialized yet");
			respond(Response.NOK);
			return;
		}
		printVars(getThread().frame(0));
		respond(Response.OK);
	}

	void printVars(StackFrame frame) {
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

	static void printValue(Value val) {
		if (val instanceof IntegerValue) {
			System.out.print(((IntegerValue) val).value() + " ");
		} else if (val instanceof StringReference) {
			System.out.print(((StringReference) val).value() + " ");
		} else if (val instanceof ArrayReference) {
			for (Value v : ((ArrayReference) val).getValues()) {
				printValue(v);
				System.out.println();
			}
		} else {
		}
	}

	public class Listener extends Thread {
		@Override
		public void run() {
			for (; ; ) {
				try {
					EventSet events = eventQueue.remove();
					for (Event e : events) {
						Response resp = processEvent(e);
						if (resp != null) {
							respond(resp);
							if (resp == Response.QUIT) return;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					break;
				}
			}
		}

		private Response processEvent(Event e) throws IncompatibleThreadStateException, AbsentInformationException {
			System.out.println("Event: " + e);

			if (e instanceof VMStartEvent) {
				thread = ((VMStartEvent) e).thread();
				return null;
			} else if (e instanceof MethodEntryEvent) {
				currLocation = ((MethodEntryEvent) e).location();
				printLocation(currLocation);
			} else if (e instanceof BreakpointEvent) {
				currLocation = ((BreakpointEvent) e).location();
				printLocation(currLocation);
			} else if (e instanceof StepEvent) {
				step((StepEvent) e);
			} else if (e instanceof ClassPrepareEvent) {
				setBreakPoints((ClassPrepareEvent) e);
				return null;
			} else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) {
				return Response.QUIT;
			}

			return Response.OK;
		}

		private void step(StepEvent se) throws IncompatibleThreadStateException {
			System.out.print("step halted in " + se.location().method().name() + " at ");
			currLocation = se.location();
			printLocation(se.location());
			//printLocals();
			reqManager.deleteEventRequest(se.request());
		}


		private void setBreakPoints(ClassPrepareEvent e) throws AbsentInformationException {
			ClassType classType = (ClassType) e.referenceType();
			for (Integer lineNumber : breakpoints) {
				Location location = classType.locationsOfLine(lineNumber).get(0);
				BreakpointRequest bpReq = reqManager.createBreakpointRequest(location);
				bpReq.enable();
			}
			vm.resume();
		}

		private void printLocation(Location location) {
			System.out.printf("Line: %d, bci: %d\n", location.lineNumber(), location.codeIndex());
		}
	}
}

