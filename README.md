<img align="right" width="128" height="128" alt="Intel Power Gadget logo" src="https://github.com/user-attachments/assets/a534a0ec-ef20-4a33-a910-368b95846b46" />

# PowerGadget

A lightweight non-overlay power gadget for Windows with a graph-based UI that doesn't suck, inspired by macOS Intel Power Gadget

### Intuition
- HWiNFO's graph UI sucks, OCCT's is better but it doesn't 'stack' the frequencies chart 
- Based on my understanding, no one has made a (Mac-like) UI for monitoring performance on Windows that is also not an overlay
- So I wanted to make my own because I believe there are no true alternatives to the Intel Power Gadget for macOS (productivity based, dense UI etc)

### Structure
- Custom made my own plotting library called `CorePlot`. Its goal is to faithfully recreate the macOS Power Gadget plots, but it's also quite suitable for other graphing purposes
- The actual monitoring part (backend) of this project is from [epinter](https://github.com/epinter)'s [LibreHardwareService](https://github.com/epinter/LibreHardwareService) (Mozilla 2.0), which uploads [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor) data to shared memory. I read it using the FFM API. Whatever CPUs/GPUs that supports, Power Gadget probably also supports. 
- Also custom made my own Windows light/dark mode detector, which is a rewrite of [Dansoftowner](https://github.com/Dansoftowner)'s [jSystemThemeDetector](https://github.com/Dansoftowner/jSystemThemeDetector) (Apache 2.0) using a newer FFM API and removing the slf4j and compiler annotations for a leaner package. Somehow it works faster than UWP apps
- Also custom made the launcher for the app in a child project

### Demo

---

<img width="1841" height="1532" alt="Power Gadget by willuhd, in both light & dark mode" src="https://github.com/user-attachments/assets/5d5eb2af-8619-4790-bb4a-c4fc932b389a" />

> works great in both light and dark mode, and automatically switches the color scheme based on Windows colors

---

### How to run
- Grab the latest release, unzip, and double-click the `.exe` file!
- The app is a portable folder. Install it wherever you want!
- Small memory footprint (~65-85MB), minimal impact on performance (consumes about 10-20MHz effective clock)
- Currently, this works on all x64 Windows 10/11 systems, but support is only tested for Intel IA/GT

### Build
- Launcher build command for `native-image`:
	```
	native-image --no-fallback -0b -jar .\launcher.jar
	```
 	> Note: Using `-0b` quick build has smaller file size compared to `-O3` build. DO NOT use `-march=native`.

 	> The [launcher's folder](https://github.com/WillUHD/PowerGadget/tree/main/launcher) is an idea project in itself, made for launching PowerGadget, intended to be compiled AOT.

- Minimal runtime made from `openjdk 25.0.2 2026-01-20 LTS` (Amazon Corretto, 47.4MB):
	```
	jlink --add-modules java.base,java.desktop --strip-debug --no-man-pages --compress=2 --output runtime
	```
 - `LibreHardwareService.exe` backend from epinter: [Release v0.3.0](https://github.com/epinter/LibreHardwareService/releases/tag/v0.3.0)
	> Note: The `.exe` is too big to fit in the [service](https://github.com/WillUHD/PowerGadget/tree/main/service) folder. It's included in releases, but you have to place it yourself when compiling.
	
	> The backend comes with the MPL 2.0 License, which is included in the source and all forms of distribution (kudos epinter!)
- The launcher reads a `powergadget.vmoptions` file located in the [`service`](https://github.com/WillUHD/PowerGadget/tree/main/service) folder.
	> That file contains info on where to locate `javaw.exe` and the target JAR, as well as all vmoptions.

	> The current build uses ZGC because it has the least footprint (~65MB) and one of the least idle CPU usage (0-0.1%), matching or sometimes exceeding HWiNFO
- Used `rcedit-x64.exe` to set the icon to [this `.ico` file](https://github.com/WillUHD/PowerGadget/blob/main/intelPG.ico), which is Intel Power Gadget's logo
- A fat build for the main `.jar` should work fine on any JDK25, I used GraalVM JDK 25.0.2. 
- Currently (since I'm the only one making this), there is no build script. If you would like to contribute, reference the existing build folders or hit me up!
	> Uses Intel's Power Gadget logo, Apple's SF Pro font for the UI, epinter's LibreHardwareService, FlatLaf, Jackson, Oracle's GraalVM JDK 25, Amazon's Corretto JRE

	> No intention of infringement. If you have any issues, hit me up!

### Future direction
- A settings page to adjust plot capacity, plot interval, exclude certain metrics, pin custom values, etc
- Auto-detect nominal TDP/frequency to pin on the graph (as opposed to hard-set 24W/2.0GHz)
- Known issue: Will crash when switching the graphics context (eg. entering remote desktop), which is an AWT issue and is unfixable
- Improve backend support for non-Intel systems with d/eGPUs (currently, an AMD CPU will only show utilization)
