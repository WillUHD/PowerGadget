package main.java;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;
import static main.java.CorePlot.*;

record Sensor(String identifier,
              String name,
              String sensorType,
              float value) {}

record HWRecord(String hardwareType,
                String identifier,
                String name,
                List<Sensor> sensors) {}

class ServiceGate {

	private static final int    updInterval = 1000, // minimum LHS (from json config) is willing to do
								retryTime = 500; // less latency when waiting

	private static final String mapName = "Global\\LibreHardwareService/json/all/data";
	private static final int mapRead = 0x0004;
	private static final MethodHandle fileMapping, mapView;

	private static final ObjectMapper jsonMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.enable(SerializationFeature.INDENT_OUTPUT);

	private static final TypeReference<List<HWRecord>> TYPE_REF = new TypeReference<>() {};

	static {
		var linker = Linker.nativeLinker();
		var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

		fileMapping = linker.downcallHandle(
				kernel32.find("OpenFileMappingW").orElseThrow(),
				FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS)
		);

		mapView = linker.downcallHandle(
				kernel32.find("MapViewOfFile").orElseThrow(),
				FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)
		);
	}

	// this program DOES NOT stop LHS once exited:
	//      it only takes 12.3mb of memory and 0 cpu
	//      makes it substantially faster for next launch
	//      will get closed if restart
	//      you need to close it manually in task manager
	//      if there are too many issues on this, i will add a shutdown hook
	static void launch(Consumer<PKGMetrics> dataListener) {
		IO.println("PowerGadget (by willuhd)");
		try {
			IO.println("---> Starting monitoring service");
			if (grabLHSJsonBytes() != null) IO.println("\t'--> LibreHardwareService is already running.\n\t\t If the data doesn't update, kill it in Task Manager and restart the app.");
			else {
				IO.println("\t'--> Launching LibreHardwareService - waiting UAC prompt confirmation");
				launchAdmin();
				IO.println("\t'--> Admin granted - waiting for the service to initialize");
			}
		} catch (AccessDeniedException e) {
			System.err.println("\t\t'--> error: Launch is aborted - " + e.getMessage());
			return;
		} catch (Exception e) {
			System.err.println("\t\t'--> error: " + e.getMessage());
			return;
		}

		IO.println("---> Connecting to shared memory");

		for (;;) {
			byte[] initialData = grabLHSJsonBytes();
			if (initialData != null) {
				IO.println("\t'--> Connected to shared memory");
				break;
			}

			IO.println("\t'--> Waiting for data to arrive");
			try { Thread.sleep(retryTime); }
			catch (InterruptedException e) {Thread.currentThread().interrupt(); return;}
		}

		IO.println("---> Monitoring live data");
		while (!Thread.currentThread().isInterrupted()) {
			try {
				byte[] rawData = grabLHSJsonBytes();
				if (rawData != null) {
					List<HWRecord> allHardware = jsonMapper.readValue(rawData, TYPE_REF);
					dataListener.accept(MetricsExtractor.extract(allHardware));
				} else {
					System.err.println("\t'--> fatal error: Memory data went kaboom");
					break;
				}
				Thread.sleep(updInterval);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				IO.println("\t'--> fatal error: Monitoring interrupted");
				break;
			} catch (Exception e) {
				System.err.println("\t'--> error: Data mismatch - " + e.getMessage());
				try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
			}
		}
	}

	// revised: added support for Windows 11
	// on Windows 11, UAC won't display even for standard user accounts, but it still works (for some reason)
	private static void launchAdmin() throws IOException, InterruptedException {
		File lhsExec = new File(dir, "service/LibreHardwareService.exe"); // ok to call, already checked in CorePlot
		if (!lhsExec.exists()) throw new IOException("Can't find executable: " + lhsExec.getAbsolutePath());

		String absolutePath = lhsExec.getAbsolutePath();
		String workingDir = lhsExec.getParentFile().getAbsolutePath();

		String psInner = String.format(
				"$exe = '%s'; " +
						"$wd  = '%s'; " +
						"$source = 'LibreHardwareService'; " +
						"try { Unblock-File -Path $exe -ErrorAction SilentlyContinue } catch {} " +
						"try { " +
						"   if (-not [System.Diagnostics.EventLog]::SourceExists($source)) { " +
						"       New-EventLog -LogName Application -Source $source " +
						"   } " +
						"} catch {} " +
						"Start-Process -FilePath $exe -WorkingDirectory $wd -WindowStyle Hidden;",
				absolutePath.replace("'", "''"),
				workingDir.replace("'", "''")
		);

		String psCommand = String.format(
				"Start-Process -FilePath powershell.exe " +
						" -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-WindowStyle','Hidden','-Command','%s' " +
						" -Verb RunAs -WindowStyle Hidden",
				psInner.replace("'", "''")
		);

		ProcessBuilder pb = new ProcessBuilder(
				"powershell",
				"-NoProfile",
				"-ExecutionPolicy", "Bypass",
				"-WindowStyle", "Hidden",
				"-Command", psCommand
		);

		Process process = pb.start();
		int exitCode = process.waitFor();

		if (exitCode != 0) throw new AccessDeniedException("\t'--> fatal error: UAC is not confirmed");
	}

	private static byte[] grabLHSJsonBytes() {
		try (var arena = Arena.ofConfined()) {
			var nameStr = arena.allocateFrom(mapName, StandardCharsets.UTF_16LE);
			var hMap = (MemorySegment) fileMapping.invokeExact(mapRead, 0, nameStr);
			if (hMap.equals(MemorySegment.NULL)) return null;
			var pBuffer = (MemorySegment) mapView.invokeExact(hMap, mapRead, 0, 0, 0L);
			if (pBuffer.equals(MemorySegment.NULL)) return null;

			pBuffer = pBuffer.reinterpret(1024 * 1024);
			var metaSize = pBuffer.get(JAVA_INT, 0);
			var dataLenOffset = 4L + metaSize;
			var dataLen = pBuffer.get(JAVA_INT, dataLenOffset);
			if (dataLen > 0 && dataLen < (1024 * 1024 - dataLenOffset - 4)) {
				var jsonOffset = dataLenOffset + 4;
				return pBuffer.asSlice(jsonOffset, dataLen).toArray(JAVA_BYTE);
			}

			return null;
		} catch (Throwable t) {return null;}
	}
}