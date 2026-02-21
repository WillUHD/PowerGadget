package main.java;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.*;

import javax.swing.*;
import java.awt.*;

import static main.java.CorePlot.*;

// does not work with native-image (at leat for graalvm 25)
// it is ENTIRELY oracle's fault 😡
// DLL hell trying to use linux fontconfig.dll on windows
public class Main {

	void main() {
		SwingUtilities.invokeLater(() -> {
			var pg = new JFrame(title);
			pg.setMinimumSize(new Dimension(360, 640));
			pg.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			pg.setSize(360, 640);
			pg.setLocationRelativeTo(null);
			pg.getContentPane().setBackground(c.back);
			pg.setIconImage(Toolkit.getDefaultToolkit().getImage(img)); // set windows icon
			var panel = new SplitPanel();
			panel.setBackground(c.back);
			panel.addPane(pwr);
			panel.addPane(freq);
			panel.addPane(temp);
			panel.addPane(util);
			pg.add(panel, BorderLayout.CENTER);
			pg.setVisible(true);

			det.registerListener(isDark -> {
				c = isDark ? Theme.Dark : Theme.Light;
				panel.dividerColor = c.gridSub;
				setColors();
				if (isDark) FlatMacDarkLaf.setup();
				else FlatMacLightLaf.setup();
				FlatLaf.updateUI();
				FlatMacDarkLaf.updateUI();
				SwingUtilities.invokeLater(() -> {
					SwingUtilities.updateComponentTreeUI(pg);
					pg.getContentPane().setBackground(c.back); // NEEDED for title bar theme change
					pg.getRootPane().setBackground(c.back);
					pwr.setBackground(c.back);
					freq.setBackground(c.back);
					temp.setBackground(c.back);
					util.setBackground(c.back);
					panel.setBackground(c.back);
				});
			});
		});

		new Thread(() -> {
			ServiceGate.launch(m -> {
				pPKG.add(m.power().pkg());
				pIA.add(m.power().ia());
				pGT.add(m.power().gt());
				pDRAM.add(m.power().dram());
				fMAX.add(m.frequency().coreMax());
				fAVG.add(m.frequency().coreAvg());
				fMIN.add(m.frequency().coreMin());
				fGT.add(m.frequency().gt());
				tPKG.add(m.temperature().pkg());
				tMAX.add(m.temperature().coreMax());
				tAVG.add(m.temperature().coreAvg());
				uIA.add(m.utilization().cpuTotal());
				uMAX.add(m.utilization().coreMax());
				uGT.add(m.utilization().gt());
				SwingUtilities.invokeLater(() -> {
					pwr.repaint();
					freq.repaint();
					temp.repaint();
					util.repaint();
				});
			});
		}).start();
	}

	// power gadget
	static Color            cPKG     = new Color(2, 136, 208),
							cGT      = new Color(180, 212, 73),
							cIA      = new Color(2, 189, 242),
							cInvis   = new Color(0, 212, 255, CorePlot.opacity),
							cShade   = new Color(0, 212, 255),
							cMax     = new Color(2, 116, 203);
	static String           img      = "service/intelPG.png",
							title    = "Power Gadget",
							pkg      = "pkg",
							ia       = "core",
							gt       = "gfx",
							avg      = "avg",
							max      = "max";
	static int              capacity = 90;
	static CorePlot         pwr      = new CorePlot(capacity),
							freq     = new CorePlot(capacity),
							temp     = new CorePlot(capacity),
							util     = new CorePlot(capacity);
	static CorePlot.Series  pPKG     = pwr.addSeries(pkg, cPKG, 40, 2f),
							pIA      = pwr.addSeries(ia, cIA, 40, 2f),
							pGT      = pwr.addSeries(gt, cGT, 40, 2f),
							pDRAM    = pwr.addSeries("dram", new Color(255, 195, 22), 30, 2f),
							fMIN     = freq.addSeries("min", cInvis, 35, 0f),
							fAVG     = freq.addSeries(avg, cIA, 35, 2f),
							fMAX     = freq.addSeries(max, cMax, 35, 0.75f),
							fGT      = freq.addSeries(gt, cGT, 35, 2f),
							tPKG     = temp.addSeries(pkg, cIA, 40, 2f),
							tMAX     = temp.addSeries(max, cMax, 40, 0.75f),
							tAVG     = temp.addSeries(avg, cInvis, 40, 0f),
							uIA      = util.addSeries(ia, cIA, 40, 2f),
							uMAX     = util.addSeries(max, cPKG, 40, 2f),
							uGT      = util.addSeries(gt, cGT, 40, 2f);

	static {
		System.setProperty("swing.aatext", "true");
		System.setProperty("sun.java2d.d3d", "true");
		if (det.isDark()) FlatMacDarkLaf.setup();
		else FlatMacLightLaf.setup();
		UIManager.put("RenderingHints.KEY_TEXT_ANTIALIASING", RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		UIManager.put("RenderingHints.KEY_FRACTIONALMETRICS", RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		UIManager.put("RenderingHints.KEY_STROKE_CONTROL", RenderingHints.VALUE_STROKE_NORMALIZE);
		UIManager.put("RenderingHints.KEY_TEXT_LCD_CONTRAST", 140);
		UIManager.put("TitlePane.showIcon", false);
		UIManager.put("TitlePane.centerTitle", true);
		UIManager.put("TitlePane.buttonSize", new Dimension(30, 24));
		setColors();
		pwr.setTitle("Power");
		pwr.setUnit("w");
		pwr.setHybrid(0, 28, true, false);
		pwr.pin(35.0f);
		pwr.setGraphOrder(pDRAM, pGT,  pIA, pPKG);
		freq.setTitle("Frequency");
		freq.setUnit("ghz");
		freq.pin(2.0f);
		freq.setHybrid(0, 2, true, false);
		freq.addShade(fMAX, fMIN, cShade, cShade, Color.RED);
		freq.setGraphOrder(fGT, fMAX, fAVG, fMIN);
		temp.setTitle("Temperature");
		temp.setUnit("°c");
		temp.setHybrid(50, 100, false, true);
		temp.addShade(tAVG, tMAX, cShade, cShade, Color.RED);
		temp.setGraphOrder(tAVG, tMAX, tPKG);
		util.setTitle("Utilization");
		util.setUnit("%");
		util.setHybrid(0, 100, true, true);
		util.setGraphOrder(uGT, uMAX, uIA);
	}

	private static void setColors(){
		UIManager.put("RootPane.background", c.back);
		UIManager.put("TitlePane.background", c.back);
		UIManager.put("TitlePane.inactiveBackground", c.back);
		UIManager.put("TitlePane.foreground", c.subtle);
		UIManager.put("TitlePane.inactiveForeground", c.subtle);
		UIManager.put("TitlePane.borderColor", c.border);
		UIManager.put("Panel.background", c.back);
	}
}
