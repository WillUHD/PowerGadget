package main.java;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// A rewrite of `Dansoftowner/jSystemThemeDetector` using the FFM API.
///
/// Does not require JNA or other libraries. Only works on Windows 10 or above.
///
/// Somehow this works faster than native Windows apps 💀
///
/// @author willuhd
/// @Original: Dansoftowner
class ColorTheme {

    private static final String REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String REGISTRY_VALUE = "AppsUseLightTheme";

    private static final int HKEY_CURRENT_USER = 0x80000001;
    private static final int KEY_READ = 0x20019;
    private static final int REG_NOTIFY_CHANGE_LAST_SET = 0x00000004;
    private static final int ERROR_SUCCESS = 0;

    private final Set<Consumer<Boolean>> listeners = new ConcurrentHashSet<>();
    private volatile DetectorThread detectorThread;

    private static final Linker linker = Linker.nativeLinker();
    private static final SymbolLookup advapi = SymbolLookup.libraryLookup("Advapi32", Arena.global());

    private static final MethodHandle RegOpenKeyExW;
    private static final MethodHandle RegNotifyChangeKeyValue;
    private static final MethodHandle RegQueryValueExW;
    private static final MethodHandle RegCloseKey;

    static {
        RegOpenKeyExW = linker.downcallHandle(
                advapi.find("RegOpenKeyExW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );

        RegNotifyChangeKeyValue = linker.downcallHandle(
                advapi.find("RegNotifyChangeKeyValue").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                )
        );

        RegQueryValueExW = linker.downcallHandle(
                advapi.find("RegQueryValueExW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );

        RegCloseKey = linker.downcallHandle(
                advapi.find("RegCloseKey").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                )
        );
    }

    ColorTheme() {}

    public boolean isDark() {
        try (Arena arena = Arena.ofConfined()) {
            var hKeyOut = arena.allocate(ValueLayout.ADDRESS);

            var subKey = arena.allocateFrom(ValueLayout.JAVA_CHAR, (REGISTRY_PATH + "\0").toCharArray());

            var err = (int) RegOpenKeyExW.invoke(
                    HKEY_CURRENT_USER,
                    subKey,
                    0,
                    KEY_READ,
                    hKeyOut
            );

            if (err != ERROR_SUCCESS) return false;

            var hKey = hKeyOut.get(ValueLayout.ADDRESS, 0);

            var valueName = arena.allocateFrom(ValueLayout.JAVA_CHAR, (REGISTRY_VALUE + "\0").toCharArray());

            var typeOut = arena.allocate(ValueLayout.JAVA_INT);
            var dataOut = arena.allocate(ValueLayout.JAVA_INT);
            var dataSize = arena.allocate(ValueLayout.JAVA_INT);
            dataSize.set(ValueLayout.JAVA_INT, 0, 4);

            err = (int) RegQueryValueExW.invoke(
                    hKey,
                    valueName,
                    MemorySegment.NULL,
                    typeOut,
                    dataOut,
                    dataSize
            );

            RegCloseKey.invoke(hKey);

            if (err != ERROR_SUCCESS) return false;

            var value = dataOut.get(ValueLayout.JAVA_INT, 0);
            return value == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public synchronized void registerListener(Consumer<Boolean> darkThemeListener) {
        Objects.requireNonNull(darkThemeListener);
        var added = listeners.add(darkThemeListener);
        var first = added && listeners.size() == 1;

        if (first || (detectorThread != null && detectorThread.isInterrupted())) {
            var t = new DetectorThread(this);
            detectorThread = t;
            t.start();
        }
    }

    public synchronized void removeListener(Consumer<Boolean> darkThemeListener) {
        listeners.remove(darkThemeListener);
        if (listeners.isEmpty() && detectorThread != null) {
            detectorThread.interrupt();
            detectorThread = null;
        }
    }

    private static final class DetectorThread extends Thread {

        private final ColorTheme detector;
        private boolean lastValue;

        DetectorThread(ColorTheme detector) {
            this.detector = detector;
            this.lastValue = detector.isDark();
            setName("Windows Theme Detector Thread (FFM)");
            setDaemon(true);
            setPriority(Thread.NORM_PRIORITY - 1);
        }

        @Override
        public void run() {
            try (Arena arena = Arena.ofConfined()) {
                var hKeyOut = arena.allocate(ValueLayout.ADDRESS);
                var subKey = arena.allocateFrom(ValueLayout.JAVA_CHAR, (REGISTRY_PATH + "\0").toCharArray());

                int err = (int) RegOpenKeyExW.invoke(
                        HKEY_CURRENT_USER,
                        subKey,
                        0,
                        KEY_READ,
                        hKeyOut
                );

                if (err != ERROR_SUCCESS) throw new RuntimeException("RegOpenKeyExW failed: " + err);
                var hKey = hKeyOut.get(ValueLayout.ADDRESS, 0);
                while (!isInterrupted()) {
                    err = (int) RegNotifyChangeKeyValue.invoke(
                            hKey,
                            0,
                            REG_NOTIFY_CHANGE_LAST_SET,
                            MemorySegment.NULL,
                            0
                    );

                    if (err != ERROR_SUCCESS) {
                        throw new RuntimeException("RegNotifyChangeKeyValue failed: " + err);
                    }

                    boolean now = detector.isDark();
                    if (now != lastValue) {
                        lastValue = now;
                        for (var listener : detector.listeners)
                            try {listener.accept(now);}
                            catch (RuntimeException ignored) {}
                    }
                }

                RegCloseKey.invoke(hKey);
            } catch (Throwable ignored) {}
        }
    }
}
