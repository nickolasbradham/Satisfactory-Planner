package nbradham.satisPlanner;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Scanner;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

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
		Scanner scan = new Scanner(Planner.class.getResourceAsStream("/best.tsv")).useDelimiter(DELIM);
		scan.nextLine();
		HashMap<String, Recipe> recipes = new HashMap<>();
		while (scan.hasNextLine())
			recipes.put(scan.next(),
					new Recipe(scan.next(), parseList(scan.next()), scan.next(), parseList(scan.next())));
		scan.close();
		JPanel pane = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = gbc.gridx = 0;
		gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
		pane.add(new JLabel("Item: "), gbc);
		gbc.gridy = 1;
		pane.add(new JLabel("Rate (items/min): "), gbc);
		gbc.gridx = 1;
		gbc.anchor = GridBagConstraints.BASELINE_LEADING;
		JSpinner spin = new JSpinner(new SpinnerNumberModel(1, 0, Float.MAX_VALUE, .1));
		spin.setPreferredSize(new Dimension(60, spin.getPreferredSize().height));
		pane.add(spin, gbc);
		gbc.gridy = 0;
		String[] options = recipes.keySet().toArray(new String[recipes.size()]);
		Arrays.sort(options);
		JComboBox<String> itmSel = new JComboBox<>(options);
		pane.add(itmSel, gbc);
		JOptionPane.showMessageDialog(null, pane, "What is the desired item and rate?", JOptionPane.QUESTION_MESSAGE);
		scan = new Scanner(Planner.class.getResourceAsStream("/raws.tsv")).useDelimiter("\r*\n");
		HashSet<String> raws = new HashSet<>();
		scan.forEachRemaining(s -> raws.add(s));
		System.out.printf("Raws: %s%n", raws);
		scan.close();
		HashMap<String, Double> needs = new HashMap<>();
		HashMap<String, ProductionStep> steps = new HashMap<>();
		Queue<String> queue = new LinkedList<>();
		HashSet<String> queueSet = new HashSet<>();
		String item = (String) itmSel.getSelectedItem();
		needs.put(item, (double) spin.getValue());
		queue.offer(item);
		queueSet.add(item);
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
		StringBuilder sb = new StringBuilder();
		sb.append("Raw Inputs:\n");
		needs.forEach((k, v) -> sb.append(String.format("%s: %f%n", k, v)));
		sb.append("\nProduction Steps:\nMachine Count x Machine\tRecipe\n");
		steps.values().forEach(s -> {
			sb.append(String.format("%f x %s\t", s.count, s.recipe.machine));
			if (!s.recipe.name.isBlank())
				sb.append(String.format("%s\t", s.recipe.name));
			sb.append(String.format("(%s > %s)%n", s.recipe.ins.keySet(), s.recipe.outs.keySet()));
		});
		JTextArea out = new JTextArea(sb.toString());
		out.setEditable(false);
		JOptionPane.showMessageDialog(null, out);
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