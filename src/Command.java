public enum Command {
	QUIT("q"),
	RUN("run"),
	LOCALS("locals"),
	GLOBALS("globals"),
	SET_BREAKPOINT("break"),
	REMOVE_BREAKPOINT("rmbreak"),
	PRINT_BREAKPOINTS("lsbreak"),
	STEP_OVER("step"),
	STEP_INTO("into"),
	METHOD_ENTRY("entry"),
	STACK_TRACE("stack"),
	PRINT_VALUE("print"),
	PRINT_FIELD("printf"),
	STATE("state"),
	UNKNOWN("");

	private final String cmd;

	Command(String cmd) {
		this.cmd = cmd;
	}

	public String getCmd() {
		return cmd;
	}

	public static Command fromString(String command) {
		for (Command c : Command.values()) {
			if (c.getCmd().equals(command)) {
				return c;
			}
		}
		return UNKNOWN;
	}
}
