# PlayerVsPlayer — 2D Real-Time Competitive Java Game

## Overview

PlayerVsPlayer is a real-time 2D competitive desktop game built in Java 21 using a custom game loop and object-oriented architecture. The game supports two human players competing simultaneously on the same system, with randomly spawning items that affect gameplay dynamics.

The project is built using Gradle and distributed as a runnable JAR for easy execution.

---

## Core Gameplay

- Local 2-player competitive match
- Real-time character movement
- Projectile-based combat
- Randomly spawning power-up items
- Collision detection between players, items, and projectiles
- Structured game state management

The gameplay is designed around responsiveness and fairness, requiring careful synchronization of input handling and rendering updates.

---

## Technical Architecture

The system follows a modular object-oriented structure.

### Game Loop Engine
- Custom update-render cycle
- Controlled frame updates for smooth gameplay
- Separation of update logic and drawing logic

### Entity System
- Base entity abstraction
- Player character entities
- Projectile entities
- Randomly spawning item entities

### Collision System
- Bounding-box collision detection
- Projectile impact handling
- Item pickup logic

### Input Handling
- Event-driven keyboard listeners
- Simultaneous two-player input support
- Non-blocking input processing

### Rendering
- Real-time 2D rendering using Java graphics APIs
- Frame redraw management
- Object-layered drawing pipeline

---

## Technology Stack

- Java 21
- Gradle build system
- IntelliJ IDEA (development)
- Object-Oriented Programming
- Event-Driven Architecture

---

## Build the Project (Gradle)

Using the Gradle wrapper (recommended):

```bash
./gradlew build
```

On Windows:

```bash
gradlew.bat build
```

The compiled JAR will be generated in:

```
build/libs/
```

---

## Run the Game

### Option 1 — Run the Built JAR

```bash
java -jar build/libs/PlayerVsPlayer.jar
```

### Option 2 — Run via Gradle

```bash
./gradlew run
```

### Option 3 — Run from IDE

Open the project in IntelliJ and run the main launcher class.

---

## Controls

| Action      | Player 1 | Player 2 |
|-------------|----------|----------|
| Move Up     | W        | ↑        |
| Move Down   | S        | ↓        |
| Move Left   | A        | ←        |
| Move Right  | D        | →        |
| Shoot       | Shift    | Ctrl     |

---

## Engineering Challenges

- Managing simultaneous two-player input  
- Designing deterministic collision behavior  
- Synchronizing update and render cycles  
- Implementing randomized item spawning logic  
- Structuring entity abstraction for scalability  

---

## Future Improvements

- Scoring system  
- Map variations  
- Sound engine integration  
- Additional power-up mechanics  
- Networked multiplayer  

---

## What This Project Demonstrates

- Real-time systems design  
- Custom game loop implementation  
- Strong object-oriented architecture  
- Gradle-based Java project structure  
- Multi-entity interaction handling  
