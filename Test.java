import java.util.Arrays;

public class Test {
	public static int globalVar = 10;

	public static void main(String[] args) {
		int i = 1;
		int j = i * 2;
		System.out.println(j);
		int k = add(i, j);
		System.out.println(k * globalVar);
		String[] arr = getStringArray();
		System.out.println(Arrays.toString(arr));
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
}
