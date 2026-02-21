import java.io.*;
import java.nio.file.*;

// UIs just won't work on native-image unless I do PGO
// which results in a huge binary with a ton of DLL's
// so unfortunately if the launch fails the user won't
// have any idea it just fails :(
public class Launcher {

	static String defaultJar = "pg.jar",
			defaultJavaw = "runtime/bin/javaw.exe",
			vmOptions = "service/powergadget.vmoptions";

	void main() {
		try {
			var baseDir = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
			var optionsPath = baseDir.resolve(vmOptions);
			String readJar = null, readJavaw = null;
			var lines = new String[0];
			var vmArgsCount = 0;
			if (Files.exists(optionsPath)) {
				lines = Files.readString(optionsPath).lines()
						.map(String::trim)
						.filter(line -> !line.isEmpty() && !line.startsWith("#"))
						.toArray(String[]::new);

				for (var line : lines) {
					var lower = line.toLowerCase();
					var isJar = lower.endsWith(".jar");
					var isJava = lower.endsWith(".exe") || lower.contains("javaw");
					if (readJar == null && isJar && Files.isRegularFile(baseDir.resolve(line))) readJar = line;
					else if (readJavaw == null && isJava && Files.isRegularFile(baseDir.resolve(line))) readJavaw = line;
					else vmArgsCount++;
				}
			}

			var jarPath = baseDir.resolve(readJar != null ? readJar : defaultJar);
			var javawPath = baseDir.resolve(readJavaw != null ? readJavaw : defaultJavaw);
			if (!Files.isRegularFile(jarPath)) throw new FileNotFoundException("Missing JAR: " + jarPath);
			if (!Files.isRegularFile(javawPath)) throw new FileNotFoundException("Missing Runtime: " + javawPath);
			var cmd = new String[vmArgsCount + 3];
			cmd[0] = javawPath.toString();
			var currentIdx = 1;
			for (var line : lines) if (!line.equals(readJar) && !line.equals(readJavaw)) cmd[currentIdx++] = line;
			cmd[cmd.length - 2] = "-jar";
			cmd[cmd.length - 1] = jarPath.toString();
			new ProcessBuilder(cmd).directory(baseDir.toFile()).start();
		} catch (Exception e) {
			System.err.println("Can't launch PowerGadget, because " + e.getMessage() + "\nwhich is caused by " + e.getCause());
		}
	}
}
