package nbradham.satisPlanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Scanner;

final class Planner {

	private static final String DELIM = "\t|\r*\n";
	private static final int ROUND_MAG = 1000000;

	private static HashMap<String, Float> parseList(String s) {
		HashMap<String, Float> map = new HashMap<>();
		int i;
		for (String split : s.split(", "))
			if ((i = split.indexOf('x')) != -1)
				map.put(split.substring(i + 1), Float.parseFloat(split.substring(0, i)));
		return map;
	}

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Arguments: <Item Name> <Desired Rate>\n\tExample: \"Adaptive Control Unit\" 2.5");
			return;
		}
		Scanner scan = new Scanner(Planner.class.getResourceAsStream("/best.tsv")).useDelimiter(DELIM);
		scan.nextLine();
		HashMap<String, Recipe> recipes = new HashMap<>();
		while (scan.hasNextLine())
			recipes.put(scan.next(),
					new Recipe(scan.next(), parseList(scan.next()), scan.next(), parseList(scan.next())));
		scan.close();
		scan = new Scanner(Planner.class.getResourceAsStream("/raws.tsv")).useDelimiter("\r*\n");
		HashSet<String> raws = new HashSet<>();
		scan.forEachRemaining(s -> raws.add(s));
		System.out.printf("Raws: %s%n", raws);
		scan.close();
		HashMap<String, Double> needs = new HashMap<>();
		HashMap<String, ProductionStep> steps = new HashMap<>();
		Queue<String> queue = new LinkedList<>();
		HashSet<String> queueSet = new HashSet<>();
		needs.put(args[0], Double.parseDouble(args[1]));
		queue.offer(args[0]);
		queueSet.add(args[0]);
		String item;
		while ((item = queue.poll()) != null) {
			queueSet.remove(item);
			System.out.printf("Getting recipe for %s...%n", item);
			Recipe rec = recipes.get(item);
			double adj = Math.ceil(needs.remove(item) * ROUND_MAG / rec.outs.get(item)) / ROUND_MAG;
			System.out.printf("Adjustment: %f%n", adj);
			if (Math.abs(adj) > .000001) {
				ProductionStep step = steps.get(item);
				if (step == null)
					steps.put(item, new ProductionStep(rec, adj));
				else
					step.count += adj;
				for (Entry<String, Float> e : rec.ins.entrySet()) {
					if (!raws.contains(item = e.getKey()) && queueSet.add(item))
						queue.offer(item);
					needs.merge(item, (e.getValue() * adj), (a, b) -> a + b);
				}
			}
		}
		System.out.printf("Raw Inputs: %s%nSteps:%n", needs);
		steps.values().forEach(s -> {
			System.out.printf("\t%10f x %20s: ", s.count, s.recipe.machine);
			if (!s.recipe.name.isBlank())
				System.out.printf("%s ", s.recipe.name);
			System.out.printf("(%s > %s)%n", s.recipe.ins.keySet(), s.recipe.outs.keySet());
		});
	}

	private static final record Recipe(String name, HashMap<String, Float> ins, String machine,
			HashMap<String, Float> outs) {
	}

	private static final class ProductionStep {

		private final Recipe recipe;
		private double count;

		ProductionStep(Recipe setRecipe, double setCount) {
			recipe = setRecipe;
			count = setCount;
		}

		@Override
		public String toString() {
			return String.format("ProductionStep[recipe=%s, count=%f]", recipe, count);
		}
	}
}