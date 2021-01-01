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

public class Debugger {

	private final EventRequestManager reqManager;
	private final EventQueue eventQueue;
	private final String debugClass;
	private final VirtualMachine vm;
	private Location currLocation;
	private ThreadReference thread;
	private List<Integer> breakpoints = new ArrayList<>();
	private BlockingQueue<Response> responseQueue;

	public Debugger(String debugClass, BlockingQueue<Response> responseQueue) throws Exception {
		this.debugClass = debugClass;
		this.responseQueue = responseQueue;
		vm = initVM();
		reqManager = vm.eventRequestManager();
		eventQueue = vm.eventQueue();
	}

	private VirtualMachine initVM() throws Exception {
		LaunchingConnector con = Bootstrap.virtualMachineManager().defaultConnector();
		Map<String, Connector.Argument> vmargs = con.defaultArguments();
		vmargs.get("main").setValue(debugClass); // set the main class
		try {
			VirtualMachine vm = con.launch(vmargs);
			Process proc = vm.process();
			new Redirection(proc.getErrorStream(), System.err).start();
			new Redirection(proc.getInputStream(), System.out).start();
			return vm;
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new Exception("Error initializing VM");
	}

	public void startListening() {
		new Listener().start();
		enableClassPrepareRequest();
	}

	public void enableClassPrepareRequest() {
		ClassPrepareRequest cpReq = reqManager.createClassPrepareRequest();
		cpReq.addClassFilter(debugClass);
		cpReq.enable();
	}

	public ThreadReference getThread() {
		return thread;
	}

	public void sendCommand(String commandString) throws IncompatibleThreadStateException, AbsentInformationException {
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
			case BREAKPOINT -> installBreakpoint(args);
			default -> {
				System.out.println("Invalid command");
				respond(Response.NOK);
			}
		}
	}

	private void respond(Response response) {
		responseQueue.add(response);
	}

	private void stepInto(ThreadReference thread) {
		System.out.println("NOT IMPLEMENTED");
	}

	private void installBreakpoint(String[] args) {
		int lineNr = Integer.parseInt(args[0]);
		breakpoints.add(lineNr);
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
		printVars(getThread().frame(0));
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
						respond(resp);
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
			} else if (e instanceof MethodEntryEvent) {
				currLocation = ((MethodEntryEvent) e).location();
			} else if (e instanceof BreakpointEvent) {
				currLocation = ((BreakpointEvent) e).location();
				printLocation(currLocation);
			} else if (e instanceof StepEvent) {
				StepEvent se = (StepEvent) e;
				System.out.print("step halted in " + se.location().method().name() + " at ");
				currLocation = se.location();
				printLocation(se.location());
				printVars(se.thread().frame(0));
				reqManager.deleteEventRequest(se.request());
			} else if (e instanceof ClassPrepareEvent) {
				setBreakPoints((ClassPrepareEvent) e);
				vm.resume();
			} else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) {
				return Response.QUIT;
			}

			return Response.OK;
		}


		private void setBreakPoints(ClassPrepareEvent e) throws AbsentInformationException {
			ClassType classType = (ClassType) e.referenceType();
			for (Integer lineNumber : breakpoints) {
				Location location = classType.locationsOfLine(lineNumber).get(0);
				BreakpointRequest bpReq = reqManager.createBreakpointRequest(location);
				bpReq.enable();
			}
		}

		private void printLocation(Location location) {
			System.out.println(String.format("Stopped at line: %d, bci: %d", location.lineNumber(), location.codeIndex()));
		}
	}
}

