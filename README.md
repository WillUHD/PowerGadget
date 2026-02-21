<img align="right" width="128" height="128" alt="Intel Power Gadget logo" src="https://github.com/user-attachments/assets/a534a0ec-ef20-4a33-a910-368b95846b46" />

# PowerGadget

Lightweight power gadget for Windows with a UI that doesn't suck, inspired by macOS Intel Power Gadget

### Intuition
- HWiNFO's graph UI sucks, OCCT's is better but it doesn't 'stack' the frequencies chart 
- Based on my understanding, no one has made a clean/minimalist (Mac-like) UI for monitoring performance on Windows that is also not an overlay
- So I wanted to make my own because I believe there are no true alternatives to the Intel Power Gadget for macOS (productivity based, dense UI etc)

### Demo

<img width="346" height="690" alt="image" src="https://github.com/user-attachments/assets/c2d4f065-a3b5-44e3-b126-a5bc45cad369" />

> when compiling the launcher using `native-image`

<img width="530" height="847" alt="image" src="https://github.com/user-attachments/assets/4af37f7f-c5bf-47a5-b8d2-64d6e9630c8b" />

> works great in both light and dark mode, and automatically switches the color scheme based on Windows colors

### Build
- Launcher build command for `native-image` (produces one `.exe` along with several `.dll`s):
	```
	native-image --no-fallback -0b -jar .\launcher.jar
	```
 	> Note: Using `-0b` quick build has smaller file size compared to `-O3` build. DO NOT use `-march=native`.

 	> The [launcher](https://github.com/WillUHD/PowerGadget/tree/main/launcher) folder is an IDEA project in itself, because it's made just for PowerGadget

 	> There's a lot of defensive programming to ensure a UI pops up on an issue. It's also the reason why there are a bunch of extra dlls in the build. This is by design, and the `plaf` flag (here and in the IDEA project compiler options) are therefore needed. 

- Minimal runtime made from `openjdk 25.0.2 2026-01-20 LTS` (Amazon Corretto, 47.4MB):
	```
	jlink --add-modules java.base,java.desktop --strip-debug --no-man-pages --compress=2 --output runtime
	```
 - `LibreHardwareService.exe` backend from [epinter](https://github.com/epinter): [Release v0.3.0](https://github.com/epinter/LibreHardwareService/releases/tag/v0.3.0)
	> Note: The `.exe` is too big to fit in the [service](https://github.com/WillUHD/PowerGadget/tree/main/service) folder. It's included in releases, but you have to place it yourself when building
	
	> The backend comes with the MPL 2.0 License, which is included in the source and all forms of distribution (kudos epinter!)
- `ColorTheme.java` is a rewrite of [Dansoftowner](https://github.com/Dansoftowner)'s [jSystemThemeDetector](https://github.com/Dansoftowner/jSystemThemeDetector) using the FFM API, also in Apache 2.0.
- A fat build for the main `.jar` should work fine, but you have to refactor it into `pg.jar` when placed in the parent folder of the build.
- Should compile on any JDK25 build, but I used Oracle GraalVM JDK 25.0.2
- Currently (since I'm the only one making this), there is no build script. If you would like to contribute, reference the existing build folders or hit me up!
