package nbradham.satisPlanner;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
import java.util.function.BiFunction;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

final class Planner {

	private static final BiFunction<Double, Double, Double> MERGER = (a, b) -> a + b;
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
			gbc.insets = new Insets(1, 5, 1, 5);
			for (String i : items) {
				gbc.gridx = 0;
				++gbc.gridy;
				recipeSelectPane.add(new JLabel(i), gbc);
				gbc.gridx = 1;
				recipeSelectPane.add(comboByItem.get(i), gbc);
			}
			JScrollPane scrollRecipes = new JScrollPane(recipeSelectPane, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
					JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scrollRecipes.getVerticalScrollBar().setUnitIncrement(20);
			scrollRecipes.setBorder(new TitledBorder("Recipe Selection"));
			JSpinner spin = new JSpinner(new SpinnerNumberModel(1, 0, Float.MAX_VALUE, .1));
			spin.setPreferredSize(new Dimension(60, spin.getPreferredSize().height));
			String[] options = recipesByOut.keySet().toArray(new String[recipesByOut.size()]);
			Arrays.sort(options);
			JComboBox<String> itmSel = new JComboBox<>(options);
			JFrame frame = new JFrame("Production Planner");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setLayout(new BorderLayout());
			JButton calculate = new JButton("Calculate");
			DefaultTableModel prodModel = new CustomTableModel(
					new String[] { "Prod %", "Machine", "Recipe", "Input", "Output" }),
					rawModel = new CustomTableModel(new String[] { "Item", "Rate" });
			JTable prodTable = new JTable(prodModel), rawTable = new JTable(rawModel);
			DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
				private static final long serialVersionUID = 1L;

				@Override
				protected final void setValue(Object value) {
					setText(String.format("%.4f", value));
				}
			};
			TableColumnModel columnModel = prodTable.getColumnModel();
			TableColumn percent = columnModel.getColumn(0);
			percent.setCellRenderer(renderer);
			percent.setPreferredWidth(50);
			short[] widths = { 50, 100, 500, 200 };
			for (byte i = 0; i < widths.length; ++i)
				columnModel.getColumn(i + 1).setPreferredWidth(widths[i]);
			rawTable.getColumnModel().getColumn(1).setCellRenderer(renderer);
			prodTable.setFillsViewportHeight(true);
			rawTable.setFillsViewportHeight(true);
			calculate.addActionListener(_ -> {
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
							if (!raws.contains(mod) && queueSet.add(mod))
								queue.offer(mod);
							needs.merge(mod, e.getValue() * adj, MERGER);
						}
						for (Entry<String, Float> e : rec.outs.entrySet()) {
							String mod = e.getKey();
							if (!mod.equals(item) && !raws.contains(mod) && queueSet.add(mod))
								queue.offer(mod);
							needs.merge(mod, -(e.getValue() * adj), MERGER);
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
				String[] needSort = needs.keySet().toArray(new String[needs.size()]);
				Arrays.sort(needSort);
				rawModel.setRowCount(0);
				for (String s : needSort)
					rawModel.addRow(new Object[] { s, needs.get(s) });
				ProductionStep[] stepSort = steps.values().toArray(new ProductionStep[steps.size()]);
				Arrays.sort(stepSort, (a, b) -> a.recipe.name.compareTo(b.recipe.name));
				prodModel.setNumRows(0);
				for (ProductionStep s : stepSort) {
					String ins = s.recipe.ins.keySet().toString(), outs = s.recipe.outs.keySet().toString();
					prodModel.addRow(new Object[] { s.count * 100, s.recipe.machine,
							s.recipe.name.isBlank() ? "Default" : s.recipe.name, ins.substring(1, ins.length() - 1),
							outs.substring(1, outs.length() - 1) });
				}
			});
			gbc.gridy = gbc.gridx = 0;
			gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
			JPanel outPane = new JPanel(new GridBagLayout()), center = new JPanel(new GridBagLayout()),
					prodTablePane = new JPanel(new GridLayout(0, 1)), rawTablePane = new JPanel(new GridLayout(0, 1));
			outPane.setBorder(new TitledBorder("Production Output"));
			outPane.add(new JLabel("Item: "), gbc);
			gbc.gridy = 1;
			outPane.add(new JLabel("Rate (items/min): "), gbc);
			gbc.gridx = 1;
			gbc.anchor = GridBagConstraints.BASELINE_LEADING;
			outPane.add(spin, gbc);
			gbc.gridy = 0;
			outPane.add(itmSel, gbc);
			gbc.gridx = 0;
			center.add(outPane, gbc);
			gbc.gridx = 1;
			gbc.anchor = GridBagConstraints.LINE_START;
			center.add(calculate, gbc);
			prodTablePane.add(prodTable.getTableHeader());
			prodTablePane.add(new JScrollPane(prodTable));
			rawTablePane.add(rawTable.getTableHeader());
			rawTablePane.add(new JScrollPane(rawTable));
			gbc.gridx = 0;
			gbc.gridy = 1;
			gbc.weightx = gbc.weighty = 1;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.BOTH;
			JTabbedPane tabPane = new JTabbedPane();
			tabPane.addTab("Production", prodTablePane);
			tabPane.addTab("Raw Inputs", rawTablePane);
			center.add(tabPane, gbc);
			frame.add(scrollRecipes, BorderLayout.LINE_START);
			frame.add(center, BorderLayout.CENTER);
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

	private static final class CustomTableModel extends DefaultTableModel {
		private static final long serialVersionUID = 1L;

		public CustomTableModel(String[] headers) {
			super(null, headers);
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	}
}