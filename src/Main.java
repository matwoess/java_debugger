import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static java.lang.System.exit;

public class Main {

	public static void main(String[] args) throws Exception {
		String testProgram = args[0];
		compileProgram(testProgram);

		BlockingQueue<Response> responseQueue = new ArrayBlockingQueue<>(1);
		Debugger debugger = new Debugger(testProgram, responseQueue);
		debugger.startListening();

		Scanner scanner = new Scanner(System.in);
		for (; ; ) {
			System.out.print("$ ");
			String cmd = scanner.nextLine();
			debugger.sendCommand(cmd);
			Response response = responseQueue.take();
			if (response == Response.QUIT) {
				break;
			}
		}
	}

	private static void compileProgram(String toCompile) throws IOException, InterruptedException {
		int exitVal = new ProcessBuilder("javac", "-g", toCompile + ".java").start().waitFor();
		if (exitVal != 0) {
			System.out.println("Error compiling test program.");
			exit(exitVal);
		}
	}
}

