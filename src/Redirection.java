import java.io.*;

class Redirection extends Thread {
	Reader in;
	Writer out;

	Redirection(InputStream is, OutputStream os) {
		super();
		in = new InputStreamReader(is);
		out = new OutputStreamWriter(os);
	}

	public void run() {
		char[] buf = new char[1024];
		try {
			while (true) {
				int n = in.read(buf, 0, 1024);
				if (n < 0) break;
				out.write(buf, 0, n);
			}
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
