import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.file.*;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;

// requires the native-image flag --add-exports java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED
public class Launcher {

	static String[] options = {"Cancel", "Create issue"};
	static String   url = "https://github.com/willuhd/PowerGadget/issues/new",
					jarName = "pg.jar",
					javaw = "runtime/bin/javaw.exe",
					vmOptions = "service/vmoptions.txt";

	void main() {
		try {
			var baseDir = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
			Path optionsPath = baseDir.resolve(vmOptions);
			String[] vmArgs = null;
			if (Files.exists(optionsPath)) {
				String content = Files.readString(optionsPath).trim();
				if (!content.isEmpty()) vmArgs = content.split("\\s+");
			}
			var vmArgsLen = (vmArgs != null) ? vmArgs.length : 0;
			var cmd = new String[vmArgsLen + 3];
			cmd[0] = baseDir.resolve(javaw).toString();
			if (vmArgsLen > 0) System.arraycopy(vmArgs, 0, cmd, 1, vmArgsLen);
			cmd[cmd.length - 2] = "-jar";
			cmd[cmd.length - 1] = baseDir.resolve(jarName).toString();
			new ProcessBuilder(cmd).directory(baseDir.toFile()).start();
		} catch (Exception e) {
			try {UIManager.setLookAndFeel(new WindowsLookAndFeel());}
			catch (Exception f) {makeErr("Can't get current folder to launch PowerGadget, because " + e.getMessage() +
					"\nwhich is caused by " + e.getCause() + ".\n\nAdditionally, failed to get the LaF: " + f.getMessage());}
			makeErr("Can't get current folder to launch PowerGadget, because " + e.getMessage() +
					"\nwhich is caused by " + e.getCause());
		}
	}

	void makeErr(String msg){
		var v = JOptionPane.showOptionDialog(
				null, msg, "Error",
				JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
				null, options, options[0]
		);

		if(v == 1)
			try {Desktop.getDesktop().browse(new URI(url));}
			catch(Exception e) {JOptionPane.showMessageDialog(null,
					"Can't get the browser to open the issue creation link" + url + ",\ndue to " +
							e.getMessage() + "\nwhich is caused by " + e.getCause() +
							".\nIf you still want to create the issue (appreciated!), here's the message:\n\n"
							+ msg, "Error", JOptionPane.ERROR_MESSAGE);}
		System.exit(0); // don't need anymore
	}
}
