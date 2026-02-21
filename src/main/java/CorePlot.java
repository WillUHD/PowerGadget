package main.java;

import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// # CorePlot
/// > a modern, efficiency-oriented 2D plotting library written in Java
///
/// ### Key features
/// - Does not use heavy stuff like electron
/// - Very lightweight, ideal for running in the background
/// - Heavy UI/aesthetic inspiration from the macOS Intel Power Gadget v3.7
/// - Hardware acceleration and CPU efficiency for plots
/// - Automatic theme change for Windows
///
/// ### At a glance
/// - Customizable plot data complete with color and thickness, adjustable **on-the fly**, even after declaration
/// - Ability to add shading between 2 elements, and even **assign different shade colors based on positive/negative shaded area**
/// - Freely rearrange the order in which series are drawn on graph and shown in legend **(independent!)**
/// - Set default graph positions, and handle how the graph **dynamically makes room or assigns padding** to new data (or not)
/// - Axis labels use **pleasing numbers as well as unlabelled dimmed sub-axes** to make it look both pretty and precise
/// - Legend values with decimals that are squished will truncate by **reducing sig figs** before resorting to ellipses
///
/// ### Real world impact
/// - Low resource consumption: 10+ elements among 4 CorePlots each updating at 10Hz is measured in the 10s of MHz effective clock
/// - Acts as any other JPanel component, easy integration with existing Swing UIs without introducing annoying new windows
public class CorePlot extends JPanel {

	static final int    gutter      = 10,
						margin      = 10,
						header      = 55,
						radius      = 9,
						opacity     = 45; // opacity for the shading, NOT window

	// theme change
	static ColorTheme   det     = new ColorTheme();
	static Theme        c       = det.isDark() ? Theme.Dark : Theme.Light;
	static Color        cGrid   = new Color(152, 152, 152),
						cTitle  = new Color(2, 120, 255);

	public enum Theme {
		Light(
				new Color(230,230,230),
				new Color(255,255,255),
				new Color(200,200,200),
				new Color(138,139,142),
				new Color(230,230,230),
				new Color(27,27,27),
				new Color(100,100,100)
		),
		Dark(
				new Color(44,44,44),
				new Color(16,16,16),
				new Color(82,82,82),
				new Color(169,167,172),
				new Color(67,67,67),
				new Color(240,240,240),
				new Color(160,160,160)
		);

		public final Color back, graph, gridSub, pin, border, text, subtle;

		Theme(Color back, Color graph, Color gridSub, Color pin, Color border, Color text, Color subtle) {
			this.back = back;
			this.graph = graph;
			this.gridSub = gridSub;
			this.pin = pin;
			this.border = border;
			this.text = text;
			this.subtle = subtle;
		}
	}

	static final File dir;

	private static final Font fTitle, fVal, fLbl, fAxis;
	private static final float valCapH, lblCapH;

	static {
		try {dir = new File(CorePlot.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();}
		catch (URISyntaxException e) {
			System.err.println("\t'--> fatal error: Can't get files - " + e.getMessage());
			try {UIManager.setLookAndFeel(new WindowsLookAndFeel());}
			catch (Exception f) {makeErr("Can't get the files in the current folder, because " + e.getMessage() +
					"\nwhich is caused by " + e.getCause() + ".\n\nAdditionally, failed to get the LaF: " + f.getMessage());}
			makeErr("Can't get the files in the current folder, because " + e.getMessage() +
					"\nwhich is caused by " + e.getCause());
			throw new RuntimeException(e); // must exit cuz can't boot LHS, also can't get fonts
		}

		var     ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Font    pro = new Font("SansSerif", Font.PLAIN, 14),
				bold = new Font("SansSerif", Font.BOLD, 14);

		try {
			pro = Font.createFont(Font.TRUETYPE_FONT, new File(dir, "service/SFPro.ttf"));
			bold = Font.createFont(Font.TRUETYPE_FONT, new File(dir, "service/SFBold.ttf"));
		} catch (Exception ignored) {}

		ge.registerFont(pro);
		ge.registerFont(bold);
		fTitle = fVal = pro.deriveFont(Font.PLAIN, 14f);
		fLbl   = bold.deriveFont(Font.PLAIN, 11f);
		fAxis  = bold.deriveFont(Font.PLAIN, 9f);
		UIManager.put("TitlePane.font", bold.deriveFont(Font.PLAIN, 13f));
		var frc = new FontRenderContext(null, true, true);
		valCapH = fVal.getLineMetrics("0", frc).getAscent() - fVal.getLineMetrics("0", frc).getDescent();
		lblCapH = fLbl.getLineMetrics("A", frc).getAscent() - fLbl.getLineMetrics("A", frc).getDescent();
	}

	// minor optimizations in the painting logic
	private final Map<Float, BasicStroke> strokeCache = new HashMap<>();
	private final Path2D.Float shadePath = new Path2D.Float();
	private float[] tmpYA;
	private float[] tmpYB;
	private List<Series> legendOrder = null;
	private List<Series> zOrder = null;

	private static final DecimalFormat dfVal = new DecimalFormat("0.00");
	private final Path2D.Float pathCache = new Path2D.Float();
	private final RoundRectangle2D.Float bgCache = new RoundRectangle2D.Float();
	private final StringBuilder sbCache = new StringBuilder();

	// {3f, 4f} - short dashes with slightly longer gaps
	private static final BasicStroke gridStroke = new BasicStroke(0.5f, 0, 0, 10f, new float[]{3f, 4f}, 0f);
	private final List<String> axisLabels = new ArrayList<>();
	private float axisStep, axisStart;

	/// ### ScalingMode
	///
	/// - `dynamic` scaling mode:
	///     - The graph adheres completely to the data
	///     - If the graph looks cramped along the top/bottom, you might need some padding.
	///     - You can pad either side (or both) by a certain float number in `setDynamic()`
	/// - `hybrid` scaling mode:
	///     - The graph will show the bounds in `setHybrid()` by default. But if the data
	///       exceeds those bounds, the graph will make more room for it.
	///     - You can fix either of the bounds (or both) so the graph doesn't expand when
	///       the data exceeds them.
	///     - Fixing both bounds essentially just makes the graph static.
	public enum ScalingMode { dynamic, hybrid }
	public record Range(float min, float max) {}
	public record Shade(String idA, String idB, Color pos, Color neg, Color pan, Color pA, Color nA, Color xA) {
		public Shade(String a, String b, Color p, Color n, Color x) {
			this(a, b, p, n, x, a(p), a(n), a(x));
		}
		static Color a(Color c) {return new Color(c.getRed(), c.getGreen(), c.getBlue(), CorePlot.opacity);}
	}

	private int maxPts;
	private ScalingMode mode = ScalingMode.dynamic;
	private float hRefMin, hRefMax, dynPadTop = 0f, dynPadBot = 0f;
	private boolean hMinFixed = false, hMaxFixed = false;
	private String title = "CorePlot", unit = "willuhd";

	private final List<Series> series = new CopyOnWriteArrayList<>();
	private final List<Shade> shades = new CopyOnWriteArrayList<>();
	private final List<Float> pins = new CopyOnWriteArrayList<>();

	private Range range = new Range(0, 10);
	private boolean rangeInv = true;
	private Point pScr, pWin;

	// mark: public APIs
	public CorePlot(int maxPts) {
		this.maxPts = maxPts;
		enableDrag();
		setBackground(c.back);
	}

	public static class Series {
		final String id; final Color c;
		final float[] vBuf; // primitive arrays for cache locality
		final boolean[] pBuf; // much faster than a List (doesn't unpack each value)
		final int valW; final float strokeWidth;
		int cachedLblW = -1;
		int head = 0, n = 0; final int cap; final CorePlot p;

		Series(String id, Color c, int cap, CorePlot p, int valW, float strokeWidth) {
			this.id = id; this.c = c; this.cap = cap; this.p = p;
			this.valW = valW; this.strokeWidth = strokeWidth;
			this.vBuf = new float[cap];
			this.pBuf = new boolean[cap];
		}

		public void add(float v) {
			vBuf[head] = v;
			head = (head + 1) % cap;
			if (n < cap) n++;
			p.rangeInv = true;
		}
	}

	public void changeCapacity(int maxPts) {
		this.maxPts = maxPts;
		repaint();
	}

	public Series addSeries(String name, Color color, int barWidth, float lineWidth) {
		var s = new Series(name, color, maxPts, this, barWidth, lineWidth);
		series.add(s);
		return s;
	}

	public void addShade(Series a, Series b, Color p, Color n, Color x) {
		if (a == null || b == null) return;
		shades.add(new Shade(a.id, b.id, p, n, x));
	}

	public void pin(float v) { pins.add(v); rangeInv = true; repaint(); }
	public void setTitle(String t) { this.title = t; repaint(); }
	public void setUnit(String u) { this.unit = u.toUpperCase(); repaint(); }

	public void setDynamic(float pad) { setDynamic(pad, pad); }
	public void setDynamic(float topPad, float botPad) {
		this.dynPadTop = topPad;
		this.dynPadBot = botPad;
		this.mode = ScalingMode.dynamic;
		this.rangeInv = true;
		repaint();
	}

	public void setHybrid(float min, float max) {setHybrid(min, max, false, false);}
	public void setHybrid(float min, float max, boolean fixMin, boolean fixMax) {
		this.hRefMin = min; this.hRefMax = max;
		this.hMinFixed = fixMin; this.hMaxFixed = fixMax;
		this.mode = ScalingMode.hybrid;
		this.rangeInv = true;
	}

	public void setLegendOrder(Series... series) {
		this.legendOrder = Arrays.asList(series);
		repaint();
	}

	public void setGraphOrder(Series... series) {
		this.zOrder = Arrays.asList(series);
		repaint();
	}

	// mark: private helpers
	private Series getS(String id) {
		for (var s : series) if (s.id.equals(id)) return s; return null;
	}

	private String truncate(String txt, FontMetrics fm, int maxW) {
		if (fm.stringWidth(txt) <= maxW) return txt;
		if (txt.indexOf('.') > -1) {
			String sub = txt.substring(0, txt.length() - 1);
			if (fm.stringWidth(sub) <= maxW) return sub;
		}
		var ell = "...";
		var ew = fm.stringWidth(ell);
		if (ew > maxW) return ".";
		var chars = txt.toCharArray();
		for (var v = chars.length - 1; v > 0; v--) {
			if (fm.charsWidth(chars, 0, v) + ew <= maxW) {
				sbCache.setLength(0);
				return sbCache.append(chars, 0, v).append(ell).toString();
			}
		}
		return ell;
	}

	// directly from my Launcher source
	private static void makeErr(String msg){
		var options = new String[]{"Cancel", "Create issue"};
		var v = JOptionPane.showOptionDialog(
				null, msg, "Error",
				JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
				null, options, options[0]
		);

		if(v == 1)
			try {Desktop.getDesktop().browse(new URI("https://github.com/willuhd/PowerGadget/issues/new"));}
			catch(Exception e) {JOptionPane.showMessageDialog(null,
					"Can't get the browser to open the issue creation link" +
							"https://github.com/willuhd/PowerGadget/issues/new" + ",\ndue to " +
							e.getMessage() + "\nwhich is caused by " + e.getCause() +
							".\nIf you still want to create the issue (appreciated!), here's the message:\n\n"
							+ msg, "Error", JOptionPane.ERROR_MESSAGE);}
	}

	private void enableDrag() {
		var m = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				var w = SwingUtilities.getWindowAncestor(CorePlot.this);
				if (!(w instanceof Frame f)) {
					pScr = null;
					return;
				}

				int state = f.getExtendedState();
				if ((state & Frame.MAXIMIZED_BOTH) != 0) {
					pScr = null;
					return;
				}

				pScr = e.getLocationOnScreen();
				pWin = f.getLocation();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (pScr == null) return;

				var w = SwingUtilities.getWindowAncestor(CorePlot.this);
				var c = e.getLocationOnScreen();
				w.setLocation(pWin.x + (c.x - pScr.x), pWin.y + (c.y - pScr.y));
			}
		};

		addMouseListener(m);
		addMouseMotionListener(m);
	}

	private BasicStroke strokeFor(float w) {
		BasicStroke s = strokeCache.get(w);
		if (s == null) {
			s = new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			strokeCache.put(w, s);
		}
		return s;
	}

	// mark: core painting logic (half of this file💀)
	@Override // 0% NEW's ZONE
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		var g2d = (Graphics2D) g;
		g2d.setRenderingHints(Map.of(
				RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
				RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
				RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE,
				RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON,
				RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY
		));

		int w = getWidth(), h = getHeight(), gw = w - gutter - margin, gh = h - header - margin;
		if (gw <= 0 || gh <= 0) return;

		if (rangeInv) {
			range = calcRange();
			calcAxis(gh);
			rangeInv = false;
		}

		g2d.setFont(fTitle); g2d.setColor(cTitle);
		g2d.drawString(title, gutter, header - 32);

		g2d.setFont(fLbl);
		g2d.drawString(unit, gutter + gw - g2d.getFontMetrics().stringWidth(unit), header - 32);

		int lx = gutter + 35, ly = header + 12, lw = gw - 45, lh = gh - 24;
		var span = Math.max(1e-9f, range.max - range.min);

		bgCache.setRoundRect(gutter, header, gw, gh, radius, radius);
		g2d.setColor(c.graph); g2d.fill(bgCache);

		FontMetrics fmAxis = g2d.getFontMetrics(fAxis);
		g2d.setFont(fAxis);
		g2d.setStroke(gridStroke);

		g2d.setColor(cGrid);
		g2d.drawLine(lx, ly, lx, ly + lh);

		var steps = axisLabels.size();
		for (var v = 0; v < steps; v++) {
			var f = axisStart + (v * axisStep);
			var y = (int) (ly + lh - ((f - range.min) / span) * lh);

			var subV = f - (axisStep / 2.0f);
			if (subV >= range.min) {
				var subY = (int) (ly + lh - ((subV - range.min) / span) * lh);
				if (subY >= ly && subY <= ly + lh) {
					g2d.setColor(c.gridSub);
					g2d.drawLine(lx, subY, lx + lw, subY);
				}
			}

			if (y >= ly - 10 && y <= ly + lh + 10) {
				if (y >= ly && y <= ly + lh) {
					var pinned = false;
					for (var p : pins) if (Math.abs(p - f) < 0.0001f) { pinned = true; break; }
					if (!pinned) {
						g2d.setColor(cGrid);
						g2d.drawLine(lx, y, lx + lw, y);
					}
				}

				var ty = y + 4f;
				if (y < ly + 8) ty = ly + 10;
				else if (y > ly + lh - 8) ty = ly + lh - 2;

				var lbl = axisLabels.get(v);
				g2d.setColor(c.subtle);
				g2d.drawString(lbl, lx - fmAxis.stringWidth(lbl) - 8, ty);
			}
		}

		g2d.setColor(c.pin);
		g2d.setStroke(new BasicStroke(1.2f));
		for (var pv : pins) {
			var y = (int) (ly + lh - ((pv - range.min) / span) * lh);
			if (y >= ly && y <= ly + lh) g2d.drawLine(lx, y, lx + lw, y);
		}

		var clip = g2d.getClip();
		g2d.clipRect(lx, ly, lw, lh);
		var lines = (zOrder != null) ? zOrder : series;
		for (var s : shades) drawShade(g2d, s, range.min, span, lx, ly, lw, lh);
		for (var l : lines) drawLine(g2d, l, range.min, span, lx, ly, lw, lh);
		g2d.setClip(clip);
		g2d.setColor(c.border);

		var bx = gutter;
		FontMetrics fmLbl = g2d.getFontMetrics(fLbl);
		FontMetrics fmVal = g2d.getFontMetrics(fVal);
		var bars = (legendOrder != null) ? legendOrder : series;

		for (var b : bars) {
			if (b.n == 0) continue;
			var lastV = b.vBuf[(b.head - 1 + b.cap) % b.cap];

			if (b.cachedLblW < 0) b.cachedLblW = fmLbl.stringWidth(b.id.toUpperCase());
			var barW = b.cachedLblW + b.valW + 5;

			g2d.setFont(fVal); g2d.setColor(c.text);
			var vTx = dfVal.format(lastV);
			if (fmVal.stringWidth(vTx) > b.valW) vTx = truncate(vTx, fmVal, b.valW);

			g2d.drawString(vTx, bx + barW - fmVal.stringWidth(vTx), header - 8);
			g2d.setFont(fLbl); g2d.setColor(c.subtle);
			g2d.drawString(b.id.toUpperCase(), bx, header - 8 - (valCapH / 2f) + (lblCapH / 2f));

			g2d.setColor(b.c); g2d.fillRect(bx, header - 3, barW, 3); // bar height
			bx += (barW + 10); // spacing between bars
		}
	}

	private void drawLine(Graphics2D g, Series s, float min, float span, int x, int y, int w, int h) {
		if (s.n < 2) return;
		pathCache.reset();

		var xs = (float) w / (maxPts - 1);
		var idx = (s.head - s.n + s.cap) % s.cap;

		float x2, y2;

		var pV = s.vBuf[idx];
		idx++;
		if (idx == s.cap) idx = 0;

		pathCache.moveTo(x, y + h - ((pV - min) / span) * h);

		for (var v = 1; v < s.n; v++) {
			var cV = s.vBuf[idx];
			x2 = (x + v * xs);
			y2 = (y + h - ((cV - min) / span) * h);
			pathCache.lineTo(x2, y2);
			idx++;
			if (idx == s.cap) idx = 0;
		}

		g.setStroke(strokeFor(s.strokeWidth));
		g.setColor(s.c);
		g.draw(pathCache);
	}

	// very disgusting logic but is cpu efficient
	private void drawShade(Graphics2D g, Shade shade, float min, float span, int x, int y, int w, int h) {
		Series s1 = getS(shade.idA), s2 = getS(shade.idB);
		if (s1 == null || s2 == null || s1.n < 2) return;

		var n = Math.min(s1.n, s2.n);
		var xs = (float) w / (maxPts - 1);

		var i1 = (s1.head - n + s1.cap) % s1.cap;
		var i2 = (s2.head - n + s2.cap) % s2.cap;

		if (tmpYA == null || tmpYA.length < n) tmpYA = new float[n];
		if (tmpYB == null || tmpYB.length < n) tmpYB = new float[n];

		float p1 = s1.vBuf[i1], p2 = s2.vBuf[i2];
		tmpYA[0] = (y + h - ((p1 - min) / span) * h);
		tmpYB[0] = (y + h - ((p2 - min) / span) * h);
		i1++; if (i1 == s1.cap) i1 = 0;
		i2++; if (i2 == s2.cap) i2 = 0;

		for (var v = 1; v < n; v++) {
			float c1 = s1.vBuf[i1], c2 = s2.vBuf[i2];
			tmpYA[v] = (y + h - ((c1 - min) / span) * h);
			tmpYB[v] = (y + h - ((c2 - min) / span) * h);
			i1++; if (i1 == s1.cap) i1 = 0;
			i2++; if (i2 == s2.cap) i2 = 0;
		}

		int runStart = 0;
		Color runColor;
		var pan0 = s1.pBuf[(s1.head - n + s1.cap) % s1.cap];
		var c10 = s1.vBuf[(s1.head - n + s1.cap) % s1.cap];
		var c20 = s2.vBuf[(s2.head - n + s2.cap) % s2.cap];
		runColor = pan0 ? shade.xA : (c10 >= c20 ? shade.pA : shade.nA);

		for (var v = 1; v < n; v++) {
			var idx1 = (s1.head - n + v + s1.cap) % s1.cap;
			var pan = s1.pBuf[idx1];
			var c1 = s1.vBuf[idx1];
			var c2 = s2.vBuf[(s2.head - n + v + s2.cap) % s2.cap];
			var segColor = pan ? shade.xA : (c1 >= c2 ? shade.pA : shade.nA);

			if (!segColor.equals(runColor)) {
				shadePath.reset();
				var xA = (x + runStart * xs);
				shadePath.moveTo(xA, tmpYA[runStart]);
				for (var k = runStart + 1; k <= v; k++) {
					float xK = (x + k * xs);
					shadePath.lineTo(xK, tmpYA[k]);
				}
				shadePath.lineTo(x + v * xs, tmpYB[v]);
				for (var k = v - 1; k >= runStart; k--) {
					float xK = (x + k * xs);
					shadePath.lineTo(xK, tmpYB[k]);
				}
				shadePath.closePath();
				g.setColor(runColor);
				g.fill(shadePath);

				runStart = v;
				runColor = segColor;
			}
		}

		if (runStart < n) {
			shadePath.reset();
			float xA = (x + runStart * xs);
			shadePath.moveTo(xA, tmpYA[runStart]);
			for (int k = runStart + 1; k < n; k++) {
				float xK = (x + k * xs);
				shadePath.lineTo(xK, tmpYA[k]);
			}
			shadePath.lineTo(x + (n - 1) * xs, tmpYB[n - 1]);
			for (int k = n - 2; k >= runStart; k--) {
				float xK = (x + k * xs);
				shadePath.lineTo(xK, tmpYB[k]);
			}
			shadePath.closePath();
			g.setColor(runColor);
			g.fill(shadePath);
		}
	}


	private Range calcRange() {
		float dMin = Float.MAX_VALUE, dMax = -Float.MAX_VALUE;
		var has = false;

		for (var s : this.series) {
			if (s.n == 0) continue;
			has = true;
			var idx = (s.head - s.n + s.cap) % s.cap;
			for (var v = 0; v < s.n; v++) {
				float f = s.vBuf[idx];
				if (f < dMin) dMin = f;
				if (f > dMax) dMax = f;
				idx++;
				if (idx == s.cap) idx = 0;
			}
		}

		for (var p : this.pins) {
			if (p < dMin) dMin = p;
			if (p > dMax) dMax = p;
			has = true;
		}

		if (!has) return new Range(0, 10);

		if (mode == ScalingMode.hybrid) {
			var rMin = hMinFixed ? hRefMin : Math.min(dMin, hRefMin);
			if (!hMinFixed && rMin < hRefMin) rMin -= Math.abs(rMin * 0.15f);

			var rMax = hMaxFixed ? hRefMax : Math.max(dMax, hRefMax);
			if (!hMaxFixed && rMax > hRefMax) rMax += Math.abs(rMax * 0.15f);
			return new Range(rMin, rMax);
		} else return new Range(dMin - dynPadBot, dMax + dynPadTop);
	}

	// a ton of random optimizations
	// please do not touch random things in here
	private void calcAxis(int h) {
		axisLabels.clear();
		var span = range.max - range.min;
		if (span <= 0) span = 1;

		// 25.0 is the axis's preferred spacing
		var targetStep = (span / (h / 25.0f));

		// small algorithm: make the numbers look like graph numbers
		// instead of random stuff like 0.37, 11.1, etc
		var mag = (float) Math.pow(10, Math.floor(Math.log10(targetStep)));
		var base = targetStep / mag;

		if (base <= 1) axisStep = 1 * mag;
		else if (base <= 2) axisStep = 2 * mag;
		else if (base <= 5) axisStep = 5 * mag;
		else axisStep = 10 * mag;

		axisStart = (float) (Math.ceil(range.min / axisStep) * axisStep);

		var axisPrec = 0;
		if (axisStep < 1.0f) axisPrec = (int) Math.ceil(-Math.log10(axisStep));

		var pattern = axisPrec > 0 ? "0." + "0".repeat(axisPrec) : "0";
		var df = new DecimalFormat(pattern);

		for (var v = axisStart; v <= range.max + (axisStep / 10.0f); v += axisStep)
			axisLabels.add(df.format(v));
	}
}
