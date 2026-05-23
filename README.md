# MiniVenture 🕹️

MiniVenture is a premium isometric voxel engine built from scratch using **Java** and **LWJGL 3**. It features a procedurally generated world, dynamic lighting, and a smooth gameplay experience.

![MiniVenture Logo](assets/textures/MiniVentureLogo.png)

## ✨ Features

- **Infinite World**: Procedural chunk-based generation using Perlin Noise.
- **Dynamic Day/Night Cycle**: Real-time lighting transitions and atmosphere changes.
- **Voxel Interaction**: Break and place blocks with precision ray-casting.
- **Advanced Rendering**: Optimized isometric view with custom shaders and texture mapping.
- **In-game Chat & UI**: Functional chat system and a glassmorphic settings menu.
- **Performance Settings**: Adjustable render distance, shading quality, and progressive loading.

## 🎮 Controls

| Key | Action |
|-----|--------|
| **W, A, S, D** | Move Player |
| **Space** | Jump |
| **LMB** (Left Click) | Break Block |
| **RMB** (Right Click) | Place Block |
| **1 - 9** | Select Hotbar Slot |
| **T** | Open Chat |
| **ESC** | Open Settings / Close UI |
| **C + Scroll** | Zoom In/Out |

## 🛠️ Technical Stack

- **Language**: Java
- **Graphics API**: OpenGL 3.3 (Core Profile)
- **Framework**: [LWJGL 3](https://www.lwjgl.org/)
- **Math Library**: [JOML](https://github.com/JOML-CI/JOML)
- **Noise**: Custom Perlin Noise implementation

## 🚀 How to Run

### Prerequisites
- Java Development Kit (JDK) 17 or higher.

### Option A — Runnable JAR (recommended for sharing)
If you have the `lib/` folder (from a full dev copy) or after downloading deps:

```bash
chmod +x build_jar.sh download_libs.sh
./download_libs.sh   # only if lib/ is missing
./build_jar.sh
cd dist && ./run.sh          # macOS / Linux
cd dist && run.bat           # Windows (double-click or cmd)
```

Share the **`dist/`** folder (`MiniVenture.jar`, `assets/`, `run.sh`, `run.bat`). Recipients only need Java installed.

`lib/`, `build/`, and `dist/` are not in git — build locally.

### Option B — Dev run (macOS)
```bash
chmod +x run_game.sh download_libs.sh
./download_libs.sh   # if lib/ is missing
./run_game.sh
```

### Option C — Compile manually
```bash
javac -d bin -cp "lib/*" $(find src -name "*.java")
java -XstartOnFirstThread -cp "bin:lib/*" com.miniv.core.Main   # macOS
```

## 📂 Project Structure
- `src/`: Java source files.
- `assets/`: Textures and shaders.
- `lib/`: LWJGL and JOML libraries (local only; run `download_libs.sh`).
- `build_jar.sh` / `dist/`: Packaged game JAR (local only).
- `run_game.sh`: Launch script for macOS dev.

---
Developed by **alanthecoderishere**