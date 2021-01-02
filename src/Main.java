import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static java.lang.System.exit;

public class Main {

	public static void main(String[] args) throws Exception {
		if (args == null || args.length != 1) {
			System.out.println("Invalid amount of arguments.");
			printUsage();
			return;
		} else if (args[0].equals("-h") || args[0].equals("--help")) {
			printUsage();
			return;
		}
		String testProgram = args[0];
		compileProgram(testProgram);

		BlockingQueue<Response> responseQueue = new ArrayBlockingQueue<>(1);
		Debugger debugger = new Debugger(testProgram, responseQueue);

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

	private static void printUsage() {
		System.out.println("Usage:\njava Main <TestProgram>");
		System.out.println("    <TestProgram> ... the Java class file to compile and debug (without extension).");
	}

	private static void compileProgram(String toCompile) throws IOException, InterruptedException {
		int exitVal = new ProcessBuilder("javac", "-g", toCompile + ".java").start().waitFor();
		if (exitVal != 0) {
			System.out.println("Error compiling test program.");
			exit(exitVal);
		}
	}
}

