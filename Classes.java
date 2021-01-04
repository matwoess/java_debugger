import java.util.ArrayList;
import java.util.List;

public class Classes {
	public static void main(String[] args) {
		Dog dog = new Dog();
		Cat cat = new Cat();
		List<Animal> animals = new ArrayList<>();
		animals.add(dog);
		animals.add(cat);
		for (Animal animal : animals) {
			System.out.println(animal.toString());
			animal.communicate();
		}
	}

	abstract static class Animal {
		protected static String sound = null;

		public void communicate() {
			System.out.println("The animal makes no sound");
		}

		@Override
		public String toString() {
			return "Animal";
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

		@Override
		public String toString() {
			return super.toString() + "::Dog";
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

		@Override
		public String toString() {
			return super.toString() + "::Cat";
		}
	}
}
