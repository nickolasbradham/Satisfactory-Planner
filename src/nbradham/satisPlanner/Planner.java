package nbradham.satisPlanner;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Scanner;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

final class Planner {

	private static final String DELIM = "\t|\r*\n";
	private static final int ROUND_MAG = 10000000;

	private static HashMap<String, Float> parseList(String s) {
		HashMap<String, Float> map = new HashMap<>();
		int i;
		for (String split : s.split(", "))
			if ((i = split.indexOf('x')) != -1)
				map.put(split.substring(i + 1), Float.parseFloat(split.substring(0, i)));
		return map;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			Scanner scan = new Scanner(Planner.class.getResourceAsStream("/raws.tsv")).useDelimiter("\r*\n");
			HashSet<String> raws = new HashSet<>();
			scan.forEachRemaining(s -> raws.add(s));
			scan.close();
			scan = new Scanner(Planner.class.getResourceAsStream("/recipes.tsv")).useDelimiter(DELIM);
			scan.nextLine();
			HashMap<String, ArrayList<Recipe>> recipesByOut = new HashMap<>();
			HashMap<String, Recipe> best = new HashMap<>();
			while (scan.hasNextLine()) {
				Recipe recipe = new Recipe(scan.next(), parseList(scan.next()), scan.next(), parseList(scan.next()));
				recipe.outs.keySet().forEach(o -> {
					if (!raws.contains(o)) {
						ArrayList<Recipe> recipes = recipesByOut.get(o);
						if (recipes == null)
							recipesByOut.put(o, recipes = new ArrayList<>());
						recipes.add(recipe);
					}
				});
				String s = scan.next();
				if (!s.isBlank())
					best.put(s, recipe);
			}
			scan.close();
			JPanel recipeSelectPane = new JPanel(new GridBagLayout());
			HashMap<String, JComboBox<Recipe>> comboByItem = new HashMap<>();
			recipesByOut.forEach((k, v) -> {
				Recipe[] arr = v.toArray(new Recipe[v.size()]);
				Arrays.sort(arr, (a, b) -> a.name.compareTo(b.name));
				JComboBox<Recipe> combo = new JComboBox<>(arr);
				comboByItem.put(k, combo);
				Recipe r = best.get(k);
				if (r != null)
					combo.setSelectedItem(r);
			});
			String[] items = comboByItem.keySet().toArray(new String[comboByItem.size()]);
			Arrays.sort(items);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.BASELINE_LEADING;
			gbc.insets = new Insets(1,5,1,5);
			for (String i : items) {
				gbc.gridx = 0;
				++gbc.gridy;
				recipeSelectPane.add(new JLabel(i), gbc);
				gbc.gridx = 1;
				recipeSelectPane.add(comboByItem.get(i), gbc);
			}
			JScrollPane scroll = new JScrollPane(recipeSelectPane, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
					JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.getVerticalScrollBar().setUnitIncrement(20);
			scroll.setBorder(new TitledBorder("Recipe Selection"));
			JSpinner spin = new JSpinner(new SpinnerNumberModel(1, 0, Float.MAX_VALUE, .1));
			spin.setPreferredSize(new Dimension(60, spin.getPreferredSize().height));
			String[] options = recipesByOut.keySet().toArray(new String[recipesByOut.size()]);
			Arrays.sort(options);
			JComboBox<String> itmSel = new JComboBox<>(options);
			JTextArea out = new JTextArea();
			out.setEditable(false);
			JFrame frame = new JFrame("Production Planner");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setLayout(new BorderLayout());
			JButton calculate = new JButton("Calculate");
			calculate.addActionListener(event -> {
				HashMap<String, Double> needs = new HashMap<>();
				HashMap<String, ProductionStep> steps = new HashMap<>();
				Queue<String> queue = new LinkedList<>();
				HashSet<String> queueSet = new HashSet<>();
				String item = (String) itmSel.getSelectedItem();
				needs.put(item, (double) spin.getValue());
				queue.offer(item);
				queueSet.add(item);
				byte n = Byte.MIN_VALUE;
				while ((item = queue.poll()) != null) {
					queueSet.remove(item);
					System.out.printf("Getting recipe for %s...%n", item);
					Recipe rec = (Recipe) comboByItem.get(item).getSelectedItem();
					double adj = Math.ceil(needs.remove(item) * ROUND_MAG / rec.outs.get(item)) / ROUND_MAG;
					System.out.printf("Adjustment: %f%n", adj);
					if (Math.abs(adj) > .000001) {
						ProductionStep step = steps.get(item);
						if (step == null)
							steps.put(item, new ProductionStep(rec, adj));
						else
							step.count += adj;
						for (Entry<String, Float> e : rec.ins.entrySet()) {
							String mod = e.getKey();
							if (!mod.equals(item)) {
								if (!raws.contains(mod) && queueSet.add(mod))
									queue.offer(mod);
								needs.merge(mod, (e.getValue() * adj), (a, b) -> a + b);
							}
						}
					}
					if (++n == Byte.MAX_VALUE)
						if (JOptionPane.showConfirmDialog(frame,
								"Possibly caught in an infinite loop. Check your recipe selections.\nForce stop?",
								"Warning", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
							queue.clear();
						else
							n = Byte.MIN_VALUE;
				}
				StringBuilder sb = new StringBuilder();
				sb.append("Raw Inputs:\n");
				String[] needSort = needs.keySet().toArray(new String[needs.size()]);
				Arrays.sort(needSort);
				for (String s : needSort)
					sb.append(String.format("%s: %f%n", s, needs.get(s)));
				sb.append("\nProduction Steps:\nMachine%\tMachine\tRecipe\n");
				ProductionStep[] stepSort = steps.values().toArray(new ProductionStep[steps.size()]);
				Arrays.sort(stepSort, (a, b) -> a.recipe.name.compareTo(b.recipe.name));
				for (ProductionStep s : stepSort) {
					sb.append(String.format("%.4f%%\t%s\t", s.count * 100, s.recipe.machine));
					if (!s.recipe.name.isBlank())
						sb.append(String.format("%s\t", s.recipe.name));
					sb.append(String.format("(%s > %s)%n", s.recipe.ins.keySet(), s.recipe.outs.keySet()));
				}
				out.setText(sb.toString());
			});
			gbc.gridy = gbc.gridx = 0;
			gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
			JPanel outPane = new JPanel(new GridBagLayout());
			outPane.setBorder(new TitledBorder("Production Output"));
			outPane.add(new JLabel("Item: "), gbc);
			gbc.gridy = 1;
			outPane.add(new JLabel("Rate (items/min): "), gbc);
			gbc.gridy = 2;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			outPane.add(calculate, gbc);
			gbc.gridy = 3;
			gbc.weightx = gbc.weighty = 1;
			gbc.fill = GridBagConstraints.BOTH;
			outPane.add(new JScrollPane(out), gbc);
			gbc.gridwidth = 1;
			gbc.gridx = gbc.gridy = 1;
			gbc.weightx = gbc.weighty = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbc.anchor = GridBagConstraints.BASELINE_LEADING;
			outPane.add(spin, gbc);
			gbc.gridy = 0;
			outPane.add(itmSel, gbc);
			frame.add(scroll, BorderLayout.LINE_START);
			frame.add(outPane, BorderLayout.CENTER);
			frame.pack();
			frame.setSize(frame.getWidth(), Toolkit.getDefaultToolkit().getScreenSize().height);
			frame.setVisible(true);
		});
	}

	private static final record Recipe(String name, HashMap<String, Float> ins, String machine,
			HashMap<String, Float> outs) {

		@Override
		public String toString() {
			return name.isBlank() ? "Default" : name;
		}
	}

	private static final class ProductionStep {

		private final Recipe recipe;
		private double count;

		ProductionStep(Recipe setRecipe, double setCount) {
			recipe = setRecipe;
			count = setCount;
		}
	}
}