public class Test {
	public static int globalVar = 10;

	public static void main(String[] args) {
		int i = 1;
		int j = i * 2;
		System.out.println(j);
		int k = add(i, j);
		System.out.println(k * globalVar);
	}

	public static int add(int x, int y) {
		return x + y;
	}
}
