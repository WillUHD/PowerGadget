package main.java;

import java.util.List;

record PwrMetrics(float ia, float gt, float dram, float pkg) {}
record FreqMetrics(float coreMax, float coreMin, float coreAvg, float gt) {}
record TempMetrics(float coreMax, float coreAvg, float pkg) {}
record UtilMetrics(float coreMax, float cpuTotal, float gt) {}
record PKGMetrics(PwrMetrics power,
                  FreqMetrics frequency,
                  TempMetrics temperature,
                  UtilMetrics utilization) {}

class MetricsExtractor {

	public static PKGMetrics extract(List<HWRecord> allHardware) {
		HWRecord cpu = null;
		HWRecord gpu = null;

		for (var hw : allHardware)
			if (cpu == null && "Cpu".equals(hw.hardwareType())) cpu = hw;
			else if (gpu == null && hw.hardwareType().startsWith("Gpu")) gpu = hw;

		return new PKGMetrics(
				extrPwr(cpu, gpu),
				extrFreq(cpu, gpu),
				extrTemp(cpu),
				extrUtil(cpu, gpu)
		);
	}

	private static PwrMetrics extrPwr(HWRecord cpu, HWRecord gpu) {
		return new PwrMetrics(
				findVal(cpu, "CPU Cores", "Power"),
				findVal(gpu, "GPU Power", "Power"),
				findVal(cpu, "CPU Memory", "Power"),
				findVal(cpu, "CPU Package", "Power")
		);
	}

	private static FreqMetrics extrFreq(HWRecord cpu, HWRecord gpu) {
		if (cpu == null) return new FreqMetrics(0, 0, 0, 0);

		var minClock = Float.MAX_VALUE;
		var maxClock = 0f;
		var sumClock = 0f;
		var count = 0;

		var sensors = cpu.sensors();
		for (var s : sensors)
			if ("Clock".equals(s.sensorType()) && s.name().startsWith("CPU Core #")) {
				var val = s.value();
				if (val < minClock) minClock = val;
				if (val > maxClock) maxClock = val;
				sumClock += val;
				count++;
			}

		if (count == 0) return new FreqMetrics(0, 0, 0, 0);

		return new FreqMetrics(
				maxClock / 1000f,
				minClock / 1000f,
				(sumClock / count) / 1000f,
				findVal(gpu, "GPU Clock", "Clock") / 1000f
		);
	}

	private static TempMetrics extrTemp(HWRecord cpu) {
		return new TempMetrics(
				findVal(cpu, "Core Max", "Temperature"),
				findVal(cpu, "Core Average", "Temperature"),
				findVal(cpu, "CPU Package", "Temperature")
		);
	}

	private static UtilMetrics extrUtil(HWRecord cpu, HWRecord gpu) {
		return new UtilMetrics(
				findVal(cpu, "CPU Core Max", "Load"),
				findVal(cpu, "CPU Total", "Load"),
				findVal(gpu, "D3D 3D", "Load")
		);
	}

	private static float findVal(HWRecord hw, String name, String type) {
		if (hw == null) return 0f;
		var sensors = hw.sensors();
		for (var s : sensors) if (type.equals(s.sensorType()) && name.equals(s.name())) return s.value();
		return 0f;
	}
}
