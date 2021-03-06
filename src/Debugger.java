import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class Debugger {

	private final VirtualMachine vm;
	private final String debugClass;
	private final EventRequestManager reqManager;
	private final EventQueue eventQueue;
	private final BlockingQueue<Response> responseQueue;
	private ThreadReference thread;
	private Location currLocation;
	private final List<Integer> breakpoints = new ArrayList<>();
	private final MethodEntryRequest breakOnEnterReq;

	public Debugger(String debugClass, BlockingQueue<Response> responseQueue) throws Exception {
		this.debugClass = debugClass;
		this.responseQueue = responseQueue;
		vm = initVM();
		reqManager = vm.eventRequestManager();
		eventQueue = vm.eventQueue();
		new Listener().start();
		ClassPrepareRequest cpReq = reqManager.createClassPrepareRequest();
		cpReq.addClassFilter(debugClass + "*");
		cpReq.enable();
		breakOnEnterReq = reqManager.createMethodEntryRequest();
		breakOnEnterReq.addClassFilter(debugClass + "*");
		breakOnEnterReq.disable();
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

	public void sendCommand(String commandString) throws IncompatibleThreadStateException, AbsentInformationException, ClassNotLoadedException {
		String command = commandString.split(" ")[0];
		Command cmd = Command.fromString(command);
		String[] args = null;
		if (commandString.length() > command.length()) {
			args = commandString.substring(command.length() + 1).split(" ");
		}
		switch (cmd) {
			case QUIT -> vm.exit(0);
			case RUN -> vm.resume();
			case STEP_OVER -> step(getThread(), StepRequest.STEP_OVER);
			case STEP_INTO -> step(getThread(), StepRequest.STEP_INTO);
			case LOCALS -> respond(Variables.printLocals(getThread()));
			case GLOBALS -> respond(Variables.printGlobals(getThread()));
			case SET_BREAKPOINT -> respond(installBreakpoint(args));
			case REMOVE_BREAKPOINT -> respond(removeBreakpoint(args));
			case PRINT_BREAKPOINTS -> respond(Util.printBreakpoints(breakpoints));
			case METHOD_ENTRY -> respond(methodEntry());
			case STACK_TRACE -> respond(Util.stackTrace(getThread()));
			case PRINT_VALUE -> respond(Variables.printValueByName(getThread(), args));
			case PRINT_FIELD -> respond(Variables.printObjectFieldByName(getThread(), args));
			case STATE -> respond(Util.printProgramState(debugClass, currLocation, breakpoints));
			case HELP -> respond(Util.printHelp());
			default -> {
				System.out.println("Invalid command");
				respond(Response.NOK);
			}
		}
	}

	public ThreadReference getThread() {
		return thread;
	}

	private void respond(Response response) {
		responseQueue.add(response);
	}

	private Response methodEntry() {
		if (breakOnEnterReq.isEnabled()) {
			breakOnEnterReq.disable();
		} else {
			breakOnEnterReq.enable();
		}
		System.out.printf("Break on method entry: %s.\n", breakOnEnterReq.isEnabled() ? "on" : "off");
		return Response.OK;
	}

	private Response installBreakpoint(String[] args) {
		if (args == null || args.length != 1) {
			System.out.println("Invalid number of arguments. Line number must be specified.");
			return Response.NOK;
		}
		Integer lineNr;
		try {
			lineNr = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Could not convert '" + args[0] + "' to integer (line number).");
			return Response.NOK;
		}
		breakpoints.add(lineNr);
		System.out.printf("Breakpoint in line %s added.\n", lineNr);
		return Response.OK;
	}

	private Response removeBreakpoint(String[] args) {
		if (args == null || args.length != 1) {
			System.out.println("Invalid number of arguments. Line number must be specified.");
			return Response.NOK;
		}
		Integer lineNr;
		try {
			lineNr = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Could not convert '" + args[0] + "' to integer (line number).");
			return Response.NOK;
		}
		if (!breakpoints.contains(lineNr)) {
			System.out.println("No breakpoint yet in line number " + lineNr);
			return Response.NOK;
		}
		breakpoints.remove(lineNr);
		System.out.printf("Breakpoint in line %s removed.\n", lineNr);
		return Response.OK;
	}

	void step(ThreadReference thread, int stepType) {
		if (currLocation == null) {
			System.out.println("Not at any breakpoint. Use 'run' first.");
			respond(Response.NOK);
			return;
		}
		try {
			StepRequest req = reqManager.createStepRequest(thread, StepRequest.STEP_LINE, stepType);
			ObjectReference thisRef = null;
			if (thread.frameCount() > 0) {
				thisRef = thread.frame(0).thisObject();
			}
			if (thisRef != null) {
				req.addInstanceFilter(thisRef);
			} else {
				req.addClassFilter(debugClass + "*");
			}
			req.addCountFilter(1);
			req.enable();
			vm.resume();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public class Listener extends Thread {
		@Override
		public void run() {
			for (; ; ) {
				try {
					EventSet events = eventQueue.remove();
					Response resp = null;
					for (Event e : events) {
						resp = processEvent(e);
					}
					if (resp != null) {
						respond(resp);
						if (resp == Response.QUIT) return;
					}
				} catch (Exception e) {
					e.printStackTrace();
					break;
				}
			}
		}

		private Response processEvent(Event e) throws AbsentInformationException {
			System.out.println("Event: " + e);

			if (e instanceof VMStartEvent) {
				thread = ((VMStartEvent) e).thread();
				return null;
			} else if (e instanceof MethodEntryEvent) {
				if (!breakOnEnterReq.isEnabled()) {
					vm.resume();
					return null;
				}
				MethodEntryEvent me = (MethodEntryEvent) e;
				currLocation = me.location();
				System.out.printf("Halted while entering method '%s' at ", me.method().name());
				Util.printLocation(currLocation);
			} else if (e instanceof BreakpointEvent) {
				currLocation = ((BreakpointEvent) e).location();
				System.out.print("Breakpoint halted in " + currLocation.method().name() + " at ");
				Util.printLocation(currLocation);
				reqManager.deleteEventRequest(e.request());
			} else if (e instanceof StepEvent) {
				StepEvent se = (StepEvent) e;
				System.out.print("Step halted in " + se.location().method().name() + " at ");
				Util.printLocation(se.location());
				currLocation = se.location();
				reqManager.deleteEventRequest(se.request());
			} else if (e instanceof ClassPrepareEvent) {
				setClassBreakPoints((ClassPrepareEvent) e);
				return null;
			} else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) {
				System.out.println("Program terminated.");
				return Response.QUIT;
			}

			return Response.OK;
		}


		private void setClassBreakPoints(ClassPrepareEvent e) throws AbsentInformationException {
			ClassType classType = (ClassType) e.referenceType();
			for (Integer lineNumber : breakpoints) {
				List<Location> locations = classType.locationsOfLine(lineNumber);
				if (locations.size() < 1) {
					System.out.printf("Warning: Could not set breakpoint in line %d" +
							", no such code location found in class %s.\n", lineNumber, classType.name());
					continue;
				}
				Location location = locations.get(0);
				BreakpointRequest bpReq = reqManager.createBreakpointRequest(location);
				bpReq.enable();
			}
			vm.resume();
		}
	}
}

