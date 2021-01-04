import java.util.ArrayList;
import java.util.List;

public class Classes {
	public static void main(String[] args) {
		List<Animal> animals = new ArrayList<>();
		animals.add(new Dog());
		animals.add(new Cat());
		for (Animal animal : animals) {
			animal.communicate();
		}
	}

	abstract static class Animal {
		protected String sound = null;
		public void communicate() {
			System.out.println("The animal makes no sound");
		}
	}

	static class Dog extends Animal {
		public Dog() {
			sound = "bark";
		}

		@Override
		public void communicate() {
			System.out.println("The dog " + sound + "s.");
		}
	}

	static class Cat extends Animal {
		public Cat() {
			sound = "meow";
		}

		@Override
		public void communicate() {
			System.out.println("The cat " + sound + "s.");
		}
	}
}
