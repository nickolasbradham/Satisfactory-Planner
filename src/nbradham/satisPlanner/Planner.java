package nbradham.satisPlanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

final class Planner {

	private static final String DELIM = "\t|\r*\n";

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
		scan.nextLine();
		HashSet<String> raws = new HashSet<>();
		scan.forEachRemaining(s -> raws.add(s));
		scan.close();
		// TODO Decide to proceed.
	}

	private static final record Recipe(String name, HashMap<String, Float> ins, String machine,
			HashMap<String, Float> outs) {
	}
}