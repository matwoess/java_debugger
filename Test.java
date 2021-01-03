import java.util.Arrays;

public class Test {
	public static int globalVar = 10;
	public static double[] globalArr = new double[] {2.0, 4.5};

	public static void main(String[] args) {
		int i = 1;
		int j = i * 2;
		float f = 6.8f;
		System.out.println(j);
		int k = add(i, j);
		System.out.println(k * globalVar);
		String[] arr = getStringArray();
		System.out.println(Arrays.toString(arr));
		int sumOfFirst6 = sumOfFirstN(6);
	}

	public static int add(int x, int y) {
		return x + y;
	}

	public static String[] getStringArray() {
		String[] arr = new String[3];
		arr[0] = "first";
		arr[1] = "second";
		arr[2] = "third";
		return arr;
	}

	public static int sumOfFirstN(int x) {
		if (x == 1) {
			return x;
		}
		return x + sumOfFirstN(x - 1);
	}
}
