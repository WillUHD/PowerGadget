package main.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static main.java.CorePlot.*;

public class SplitPanel extends JPanel {
	private final java.util.List<Component> panes = new ArrayList<>();
	private final List<Divider> dividers = new ArrayList<>();
	private double[] proportions = new double[0];

	private final int dividerSize = 1;
	private final int minPaneHeight = 100;
	public Color dividerColor = c.gridSub;

	public SplitPanel() {
		setLayout(null);
		setOpaque(true);
		setBackground(Color.WHITE);
	}

	public void addPane(Component c) {
		if (!panes.isEmpty()) {
			Divider d = new Divider(dividers.size());
			dividers.add(d);
			add(d);
		}
		panes.add(c);
		add(c);

		proportions = new double[panes.size()];
		Arrays.fill(proportions, 1.0 / proportions.length);
		doLayout();
	}

	@Override
	public void doLayout() {
		if (panes.isEmpty()) return;

		var totalHeight = getHeight() - (dividers.size() * dividerSize);
		if (totalHeight <= 0) return;

		var y = 0;
		for (var v = 0; v < panes.size(); v++) {
			int h = (int) (proportions[v] * totalHeight);
			panes.get(v).setBounds(0, y, getWidth(), h);
			y += h;

			if (v < dividers.size()) {
				dividers.get(v).setBounds(0, y, getWidth(), dividerSize);
				y += dividerSize;
			}
		}
	}

	class Divider extends JComponent {

		public Divider(int index) {
			setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
			setOpaque(true);
			setBackground(dividerColor);

			MouseAdapter ma = new MouseAdapter() {
				private int startY;
				private double startTopProp;
				private double startBottomProp;

				@Override
				public void mousePressed(MouseEvent e) {
					startY = e.getYOnScreen();
					startTopProp = proportions[index];
					startBottomProp = proportions[index + 1];
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					int deltaY = e.getYOnScreen() - startY;
					int totalContentHeight = SplitPanel.this.getHeight() - (dividers.size() * dividerSize);

					if (totalContentHeight <= 0) return;

					double deltaProp = (double) deltaY / totalContentHeight;

					double newTopProp = startTopProp + deltaProp;
					double newBottomProp = startBottomProp - deltaProp;

					// minimum height constraint check
					double minProp = (double) minPaneHeight / totalContentHeight;
					if (newTopProp < minProp || newBottomProp < minProp) return;

					proportions[index] = newTopProp;
					proportions[index + 1] = newBottomProp;

					SplitPanel.this.doLayout();
					SplitPanel.this.repaint();
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(dividerColor);
			g.fillRect(0, 0, getWidth(), getHeight());
		}
	}
}