public enum Command {
	QUIT("q"),
	RUN("run"),
	LOCALS("locals"),
	SET_BREAKPOINT("break"),
	PRINT_BREAKPOINTS("breaks"),
	STEP_OVER("step"),
	STEP_INTO("into"),
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
