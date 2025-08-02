package tankgame.game;

import tankgame.GameConstants;
import tankgame.Launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal embedded enum for power-up types; could be moved to its own file later */
enum PowerUpType {
    RICK_OWENS,
    VAMP_HEAL,
    SLATT_SURGE; // added

    public void applyEffect(Tank t) {
        if (this == RICK_OWENS) {
            t.setSpeedMultiplier(1.5f);
        }
        // VAMP_HEAL instant heal handled elsewhere
        // SLATT_SURGE effect interpreted in GameWorld (rapid fire) so no state here
    }

    public void removeEffect(Tank t) {
        if (this == RICK_OWENS) {
            t.resetSpeedMultiplier();
        }
        // others no-op
    }
}

/**
 * MapType encapsulates the level file and its associated background music.
 * Add new entries here for future maps.
 */
enum MapType {
    WLR("wlrlevel1.txt", "wlrmusic.wav"),
    MAGNOLIA("magnolialevel.txt", "magnoliamusic.wav");

    public final String levelFile;
    public final String music;

    MapType(String levelFile, String music) {
        this.levelFile = levelFile;
        this.music = music;
    }
}

public class GameWorld extends JPanel implements Runnable {
    private final Launcher lf;
    private BufferedImage offscreen;
    private BufferedImage map;
    private Tank t1, t2;
    private final CopyOnWriteArrayList<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final List<Wall> walls = new ArrayList<>();

    private boolean cartiShotFired  = false;
    private boolean swampShotFired = false;

    // new: low-HP voice line guards
    private boolean cartiLowPlayed = false;
    private boolean swampLowPlayed = false;

    // current map / level state
    private MapType currentMap = null;
    private String currentLevel = null; // kept in sync with currentMap for backwards compatibility

    // ** added for level music **
    private final AudioManager audioManager = new AudioManager();
    private String currentBackgroundMusic = null; // to avoid restarting same track

    // Health and lives
    private int health1 = 100;
    private int health2 = 100;
    private int lives1 = 3;
    private int lives2 = 3;

    // stored spawn positions (from level)
    private float spawnCartiX = 100, spawnCartiY = 100;
    private float spawnSwampX = 400, spawnSwampY = 100;

    // world size based on original map
    public static final int WORLD_WIDTH = 5888;
    public static final int WORLD_HEIGHT = 3312;

    // power-up logic
    private static final long RICK_DURATION_MS = 10_000;
    private static final long POWERUP_RESPAWN_INTERVAL_MS = 20_000;
    private static final long VAMP_HEAL_RESPAWN_INTERVAL_MS = 20_000; // less frequent
    private static final long SLATT_SURGE_RESPAWN_INTERVAL_MS = 50_000; // same as vamp heal
    private static final int MAX_POWERUPS = 3;
    private final List<PowerUp> powerUps = new CopyOnWriteArrayList<>();
    private final List<ActiveEffect> activeEffects = new CopyOnWriteArrayList<>();
    private long lastSpawnTime = 0;
    private long lastVampSpawnTime = 0; // for vamp heal
    private long lastSlattSpawnTime = 0; // for slatt surge

    // rapid fire tracking
    private static final long RAPID_FIRE_INTERVAL_MS = 150; // adjust rate as desired
    private long lastRapidFireT1 = 0;
    private long lastRapidFireT2 = 0;

    // split screen / mini-map toggles
    private boolean splitScreenEnabled = false; // off by default, flip when ready
    private boolean miniMapEnabled = true;

    private final Camera cam1 = new Camera(WORLD_WIDTH / 2f, WORLD_HEIGHT);
    private final Camera cam2 = new Camera(WORLD_WIDTH / 2f, WORLD_HEIGHT);

    // zoom for split style (zoomed-in per player)
    private static final float SPLIT_ZOOM = 1.5f;

    // exit button
    private BufferedImage exitImg;
    private Rectangle exitRect;
    private static final int EXIT_PADDING = 10;
    private static final int EXIT_SIZE = 50;

    // control for the game loop (to prevent stacking multiple loops/speedups)
    private volatile boolean shouldStop = false;

    public void setSplitScreenEnabled(boolean enabled) {
        this.splitScreenEnabled = enabled;
    }

    public boolean isSplitScreenEnabled() {
        return splitScreenEnabled;
    }

    public void setMiniMapEnabled(boolean enabled) {
        this.miniMapEnabled = enabled;
    }

    public boolean isMiniMapEnabled() {
        return miniMapEnabled;
    }

    private static class ActiveEffect {
        final Tank tank;
        final PowerUpType type;
        final long expireAt;

        ActiveEffect(Tank tank, PowerUpType type, long expireAt) {
            this.tank = tank;
            this.type = type;
            this.expireAt = expireAt;
        }
    }

    private static MapType mapTypeForLevel(String levelFile) {
        if (levelFile == null) return null;
        for (MapType m : MapType.values()) {
            if (m.levelFile.equalsIgnoreCase(levelFile)) return m;
        }
        return null;
    }

    /** allow legacy code to change level directly; does NOT adjust music automatically unless it's a known map. */
    public void setLevel(String levelFile) {
        if (levelFile == null || levelFile.isBlank()) return;
        this.currentLevel = levelFile;
        // infer typed map if possible to drive music
        MapType inferred = mapTypeForLevel(levelFile);
        if (inferred != null) {
            this.currentMap = inferred;
            if (!inferred.music.equals(currentBackgroundMusic)) {
                audioManager.stopBackground();
                audioManager.playBackground(inferred.music);
                currentBackgroundMusic = inferred.music;
            }
        } else {
            this.currentMap = null; // drop typed map
        }
    }

    /** switch to a typed map (handles music only when changed and loads its level). */
    public void setMap(MapType map) {
        if (map == null) return;
        boolean levelChanged = this.currentMap != map;
        this.currentMap = map;
        this.currentLevel = map.levelFile;
        if (levelChanged) {
            loadLevel(map.levelFile);
        }
        if (!map.music.equals(currentBackgroundMusic)) {
            audioManager.stopBackground();
            audioManager.playBackground(map.music);
            currentBackgroundMusic = map.music;
        }
    }

    /** request the running loop to stop */
    public void stopGameLoop() {
        shouldStop = true;
    }

    /** ensure loop can run again if reused */
    private void resetLoopFlag() {
        shouldStop = false;
    }

    private void cleanUpBeforeExit() {
        // stop music
        audioManager.stopBackground();
        currentBackgroundMusic = null;
        // remove active effects (including speed multipliers)
        for (ActiveEffect ae : new ArrayList<>(activeEffects)) {
            ae.type.removeEffect(ae.tank);
        }
        activeEffects.clear();
        powerUps.clear();
        cartiShotFired = false;
        swampShotFired = false;
        cartiLowPlayed = false;
        swampLowPlayed = false;
        if (t1 != null) {
            t1.resetSpeedMultiplier();
            t1.reset(spawnCartiX, spawnCartiY);
        }
        if (t2 != null) {
            t2.resetSpeedMultiplier();
            t2.reset(spawnSwampX, spawnSwampY);
        }
        // stop background loop to avoid duplicate updates after return
        stopGameLoop();
    }

    public GameWorld(Launcher lf) {
        this.lf = lf;
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        InitializeGame();
        // movement listeners (remain bound to same Tank instances)
        lf.getJf().addKeyListener(new TankControl(
                t1, KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_A, KeyEvent.VK_D
        ));
        lf.getJf().addKeyListener(new TankControl(
                t2, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT
        ));
        // shooting listeners
        lf.getJf().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Carti (SHIFT)
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    if (t1 != null) t1.pressed(Tank.Direction.SHOOT);
                    if (!cartiShotFired && t1 != null) {
                        cartiShotFired = true;
                        float startX = t1.getX() + (t1.isFacingRight() ? t1.getBounds().width : 0);
                        float startY = t1.getY() + t1.getBounds().height / 3.1f;
                        bullets.add(new Bullet(startX, startY, t1.isFacingRight()));
                        AudioManager.playEffect("gunshot.wav");
                    }
                }
                // Swampizzo (CTRL)
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    if (t2 != null) t2.pressed(Tank.Direction.SHOOT);
                    if (!swampShotFired && t2 != null) {
                        swampShotFired = true;
                        float startX = t2.getX() + (t2.isFacingRight() ? t2.getBounds().width : 0);
                        float startY = t2.getY() + t2.getBounds().height / 3.15f;
                        bullets.add(new Bullet(startX, startY, t2.isFacingRight()));
                        AudioManager.playEffect("gunshot.wav");
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    if (t1 != null) t1.released(Tank.Direction.SHOOT);
                    cartiShotFired = false;
                }
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    if (t2 != null) t2.released(Tank.Direction.SHOOT);
                    swampShotFired = false;
                }
            }
        });

        // load exit button image
        try {
            URL exitUrl = getClass().getClassLoader().getResource("exit.png");
            if (exitUrl != null) exitImg = ImageIO.read(exitUrl);
        } catch (Exception e) {
            System.err.println("failed to load exit.png");
        }

        // mouse handling for exit button (bottom-left)
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (exitRect != null && exitRect.contains(e.getPoint())) {
                    AudioManager.playEffect("gunshot.wav");
                    cleanUpBeforeExit();
                    lf.showMainMenu();
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (exitRect != null && exitRect.contains(e.getPoint())) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });
        // thread started externally by Launcher.setFrame("game")
    }

    public void InitializeGame() {
        offscreen = new BufferedImage(
                getWidth() > 0 ? getWidth() : GameConstants.GAME_SCREEN_WIDTH,
                getHeight() > 0 ? getHeight() : GameConstants.GAME_SCREEN_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );

        // default to WLR map
        currentMap = MapType.WLR;
        currentLevel = currentMap.levelFile;

        // load initial map (walls/spawns/etc)
        System.out.println("InitializeGame loading level: " + currentLevel);
        boolean loaded = loadLevel(currentLevel);
        if (!loaded) {
            // fallback walls & positions if level fails
            walls.clear();
            walls.add(new Wall("unbreakable.png", 500, 500, false));
            walls.add(new Wall("breakable.png", 1000, 800, true));
            if (t1 != null) t1.reset(spawnCartiX, spawnCartiY);
            if (t2 != null) t2.reset(spawnSwampX, spawnSwampY);
        }

        // instantiate tanks (spawn positions may be overridden by level)
        t1 = new Tank(100, 100, 0, 0, (short)0, "carti",     4, 0.30f);
        t2 = new Tank(400, 100, 0, 0, (short)0, "swampizzo", 3, 0.35f);

        // initial match state
        lives1 = lives2 = 3;
        health1 = health2 = 100;
        bullets.clear();
        cartiShotFired = swampShotFired = false;
        cartiLowPlayed = false;
        swampLowPlayed = false;

        // clear power-ups / effects
        powerUps.clear();
        activeEffects.clear();
        lastSpawnTime = 0;
        lastVampSpawnTime = System.currentTimeMillis(); // prevent immediate vamp heal spawn
        lastSlattSpawnTime = System.currentTimeMillis(); // prevent immediate slatt surge spawn
        lastRapidFireT1 = lastRapidFireT2 = 0;

        // start the appropriate music (only once)
        if (currentMap != null && !currentMap.music.equals(currentBackgroundMusic)) {
            audioManager.playBackground(currentMap.music);
            currentBackgroundMusic = currentMap.music;
        }

        // ensure loop flag is ready (in case reused)
        resetLoopFlag();
    }

    /** Full-match reset: lives + health + positions + level reload */
    public void startNewMatch() {
        // ensure loop can run if it was previously stopped
        resetLoopFlag();
        System.out.println("startNewMatch loading level: " + currentLevel);
        boolean loaded;
        if (currentMap != null) {
            loaded = loadLevel(currentMap.levelFile);
        } else {
            loaded = loadLevel(currentLevel);
        }
        if (!loaded) {
            walls.clear();
            walls.add(new Wall("unbreakable.png", 500, 500, false));
            walls.add(new Wall("breakable.png", 1000, 800, true));
            if (t1 != null) t1.reset(spawnCartiX, spawnCartiY);
            if (t2 != null) t2.reset(spawnSwampX, spawnSwampY);
        }

        lives1 = lives2 = 3;
        health1 = health2 = 100;
        bullets.clear();
        cartiShotFired = swampShotFired = false;
        cartiLowPlayed = false;
        swampLowPlayed = false;

        // reset powerups/effects
        powerUps.clear();
        for (ActiveEffect ae : new ArrayList<>(activeEffects)) {
            ae.type.removeEffect(ae.tank);
        }
        activeEffects.clear();
        lastSpawnTime = 0;
        lastVampSpawnTime = System.currentTimeMillis();
        lastSlattSpawnTime = System.currentTimeMillis();
        lastRapidFireT1 = lastRapidFireT2 = 0;

        // ensure correct music is playing after match reset
        if (currentMap != null) {
            if (!currentMap.music.equals(currentBackgroundMusic)) {
                audioManager.stopBackground();
                audioManager.playBackground(currentMap.music);
                currentBackgroundMusic = currentMap.music;
            }
        } else {
            // try to infer map from legacy level and play its music if known
            MapType inferred = mapTypeForLevel(currentLevel);
            if (inferred != null) {
                currentMap = inferred;
                if (!inferred.music.equals(currentBackgroundMusic)) {
                    audioManager.stopBackground();
                    audioManager.playBackground(inferred.music);
                    currentBackgroundMusic = inferred.music;
                }
            }
        }
    }

    /** Round reset (not touching lives) */
    private void resetRound() {
        boolean loaded = (currentMap != null) ? loadLevel(currentMap.levelFile) : loadLevel(currentLevel);
        if (!loaded) {
            walls.clear();
            walls.add(new Wall("unbreakable.png", 500, 500, false));
            walls.add(new Wall("breakable.png", 1000, 800, true));
            if (t1 != null) t1.reset(spawnCartiX, spawnCartiY);
            if (t2 != null) t2.reset(spawnSwampX, spawnSwampY);
        }

        health1 = health2 = 100;
        bullets.clear();
        cartiShotFired = swampShotFired = false;
        cartiLowPlayed = false;
        swampLowPlayed = false;

        for (ActiveEffect ae : new ArrayList<>(activeEffects)) {
            ae.type.removeEffect(ae.tank);
        }
        activeEffects.clear();
        powerUps.clear();
        lastSpawnTime = 0;
        lastVampSpawnTime = System.currentTimeMillis();
        lastSlattSpawnTime = System.currentTimeMillis();
        lastRapidFireT1 = lastRapidFireT2 = 0;
    }

    private void loadMap(String mapResource) {
        map = null;
        try {
            URL mapUrl = getClass().getClassLoader().getResource(mapResource);
            if (mapUrl != null) {
                map = ImageIO.read(mapUrl);
            } else {
                System.err.println("Map resource not found: " + mapResource);
            }
        } catch (Exception e) {
            e.printStackTrace();
            map = null;
        }
        if (map != null) {
            System.out.println("Loaded map image: " + mapResource);
        } else {
            System.err.println("Map is null after loadMap for: " + mapResource);
        }
    }

    /**
     * Loads a level definition from a text file. Format supports:
     * MAP <mapImage>
     * WALL <type> <x> <y> <image> <breakable:true|false>
     * SPAWN <carti|swampizzo> <x> <y>
     */
    private boolean loadLevel(String filename) {
        System.out.println("Loading level file: " + filename);
        walls.clear();
        float cartiX = 100, cartiY = 100;
        float swampX = 400, swampY = 100;
        String mapFromLevel = null;

        InputStream in = getClass().getClassLoader().getResourceAsStream(filename);
        if (in == null) {
            System.err.println("Level file not found: " + filename);
            return false;
        }

        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts[0].equalsIgnoreCase("WALL")) {
                    if (parts.length < 6) continue;
                    String image = parts[4];
                    boolean breakable = Boolean.parseBoolean(parts[5]);
                    float x = Float.parseFloat(parts[2]);
                    float y = Float.parseFloat(parts[3]);
                    walls.add(new Wall(image, x, y, breakable));
                } else if (parts[0].equalsIgnoreCase("SPAWN")) {
                    if (parts.length < 4) continue;
                    String who = parts[1];
                    float x = Float.parseFloat(parts[2]);
                    float y = Float.parseFloat(parts[3]);
                    if (who.equalsIgnoreCase("carti")) {
                        cartiX = x;
                        cartiY = y;
                    } else if (who.equalsIgnoreCase("swampizzo")) {
                        swampX = x;
                        swampY = y;
                    }
                } else if (parts[0].equalsIgnoreCase("MAP") && parts.length >= 2) {
                    mapFromLevel = parts[1];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load level " + filename);
            return false;
        }

        if (mapFromLevel != null) {
            loadMap(mapFromLevel);
        }

        spawnCartiX = cartiX;
        spawnCartiY = cartiY;
        spawnSwampX = swampX;
        spawnSwampY = swampY;

        if (t1 != null) t1.reset(spawnCartiX, spawnCartiY);
        if (t2 != null) t2.reset(spawnSwampX, spawnSwampY);

        return true;
    }

    /**
     * Attempts to spawn a power-up of the given type in a non-colliding spot.
     */
    private void trySpawnPowerUp(PowerUpType type) {
        // guard against uninitialized tanks
        if (t1 == null || t2 == null) return;
        if (powerUps.size() >= MAX_POWERUPS) return;
        int size = 128; // adjust to match the sprite if needed
        int attempts = 0;
        while (attempts++ < 30) {
            float x = (float) (Math.random() * (WORLD_WIDTH - size));
            float y = (float) (Math.random() * (WORLD_HEIGHT - size));
            Rectangle candidate = new Rectangle((int) x, (int) y, size, size);

            boolean collide = false;
            for (Wall w : walls) {
                if (candidate.intersects(w.getBounds())) {
                    collide = true;
                    break;
                }
            }
            if (collide) continue;
            if (candidate.intersects(t1.getBounds()) || candidate.intersects(t2.getBounds())) continue;
            boolean overlapsExisting = powerUps.stream().anyMatch(p -> p.getBounds().intersects(candidate));
            if (overlapsExisting) continue;

            powerUps.add(new PowerUp(type, x, y, size));
            return;
        }
    }

    private boolean hasActiveEffect(Tank tank, PowerUpType type) {
        for (ActiveEffect ae : activeEffects) {
            if (ae.tank == tank && ae.type == type) return true;
        }
        return false;
    }

    @Override
    public void run() {
        final int fps = 60;
        final long period = 1000L / fps;
        while (!shouldStop) {
            long now = System.currentTimeMillis();

            try {
                // spawn Rick Owens periodically
                if (powerUps.size() < MAX_POWERUPS && now - lastSpawnTime >= POWERUP_RESPAWN_INTERVAL_MS) {
                    trySpawnPowerUp(PowerUpType.RICK_OWENS);
                    lastSpawnTime = now;
                }

                // spawn Vamp Heal less frequently, skip immediate on start because lastVampSpawnTime initialized to now
                if (powerUps.size() < MAX_POWERUPS && now - lastVampSpawnTime >= VAMP_HEAL_RESPAWN_INTERVAL_MS) {
                    trySpawnPowerUp(PowerUpType.VAMP_HEAL);
                    lastVampSpawnTime = now;
                }

                // spawn Slatt Surge same rules as Vamp Heal
                if (powerUps.size() < MAX_POWERUPS && now - lastSlattSpawnTime >= SLATT_SURGE_RESPAWN_INTERVAL_MS) {
                    trySpawnPowerUp(PowerUpType.SLATT_SURGE);
                    lastSlattSpawnTime = now;
                }

                // handle pickups (iterate copy to avoid concurrent modification oddities)
                for (PowerUp pu : new ArrayList<>(powerUps)) {
                    if (t1 != null && pu.getBounds().intersects(t1.getBounds())) {
                        if (pu.getType() == PowerUpType.VAMP_HEAL) {
                            health1 = 100;
                        } else {
                            activeEffects.add(new ActiveEffect(t1, pu.getType(), now + RICK_DURATION_MS));
                            pu.getType().applyEffect(t1);
                        }
                        AudioManager.playEffect("cartiwhat.wav");
                        powerUps.remove(pu);
                        continue;
                    }
                    if (t2 != null && pu.getBounds().intersects(t2.getBounds())) {
                        if (pu.getType() == PowerUpType.VAMP_HEAL) {
                            health2 = 100;
                        } else {
                            activeEffects.add(new ActiveEffect(t2, pu.getType(), now + RICK_DURATION_MS));
                            pu.getType().applyEffect(t2);
                        }
                        AudioManager.playEffect("swamplaugh.wav");
                        powerUps.remove(pu);
                    }
                }

                // expire effects (iterate copy)
                for (ActiveEffect ae : new ArrayList<>(activeEffects)) {
                    if (now >= ae.expireAt) {
                        ae.type.removeEffect(ae.tank);
                        activeEffects.remove(ae);
                    }
                }

                // update tanks with wall blocking
                if (t1 != null) t1.updateWithWalls(WORLD_WIDTH, WORLD_HEIGHT, walls);
                if (t2 != null) t2.updateWithWalls(WORLD_WIDTH, WORLD_HEIGHT, walls);

                // rapid fire for Slatt Surge
                if (t1 != null && hasActiveEffect(t1, PowerUpType.SLATT_SURGE) && now - lastRapidFireT1 >= RAPID_FIRE_INTERVAL_MS) {
                    float startX = t1.getX() + (t1.isFacingRight() ? t1.getBounds().width : 0);
                    float startY = t1.getY() + t1.getBounds().height / 3.1f;
                    bullets.add(new Bullet(startX, startY, t1.isFacingRight()));
                    lastRapidFireT1 = now;
                    AudioManager.playEffect("gunshot.wav");
                }
                if (t2 != null && hasActiveEffect(t2, PowerUpType.SLATT_SURGE) && now - lastRapidFireT2 >= RAPID_FIRE_INTERVAL_MS) {
                    float startX = t2.getX() + (t2.isFacingRight() ? t2.getBounds().width : 0);
                    float startY = t2.getY() + t2.getBounds().height / 3.15f;
                    bullets.add(new Bullet(startX, startY, t2.isFacingRight()));
                    lastRapidFireT2 = now;
                    AudioManager.playEffect("gunshot.wav");
                }

                // update bullets and handle collisions
                bullets.removeIf(b -> {
                    b.update();

                    if (t1 != null && b.getBounds().intersects(t1.getBounds())) {
                        health1 -= 20;
                        return true;
                    }
                    if (t2 != null && b.getBounds().intersects(t2.getBounds())) {
                        health2 -= 20;
                        return true;
                    }

                    for (Wall w : walls) {
                        if (w.isDestroyed()) continue;
                        if (b.getBounds().intersects(w.getBounds())) {
                            if (w.breakable) {
                                w.applyDamage(20);
                            }
                            return true;
                        }
                    }

                    return b.getX() < 0 || b.getX() > WORLD_WIDTH;
                });

                // new: low HP voice lines
                if (health1 < 50) {
                    if (!cartiLowPlayed) {
                        AudioManager.playEffect("cartiseeyuh.wav");
                        cartiLowPlayed = true;
                    }
                } else {
                    cartiLowPlayed = false;
                }
                if (health2 < 50) {
                    if (!swampLowPlayed) {
                        AudioManager.playEffect("swamptag.wav");
                        swampLowPlayed = true;
                    }
                } else {
                    swampLowPlayed = false;
                }

                // life / match logic ...
                if (health1 <= 0) {
                    lives1--;
                    if (lives1 > 0) {
                        Object[] options = {"Continue", "Exit to Menu"};
                        int result = JOptionPane.showOptionDialog(
                                this,
                                "Carti lost a life! Lives remaining: " + lives1,
                                "Life Lost",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null,
                                options,
                                options[0]
                        );
                        if (result == 1) {
                            cleanUpBeforeExit();
                            lf.showMainMenu();
                            return;
                        } else {
                            resetRound();
                        }
                    } else {
                        String winner = "Swampizzo";
                        int result = JOptionPane.showOptionDialog(
                                this,
                                winner + " wins the match! Play again?",
                                "Game Over",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null, null, null
                        );
                        if (result == JOptionPane.YES_OPTION) {
                            startNewMatch();
                        } else {
                            cleanUpBeforeExit();
                            lf.showMainMenu();
                            return;
                        }
                    }
                } else if (health2 <= 0) {
                    lives2--;
                    if (lives2 > 0) {
                        Object[] options = {"Continue", "Exit to Menu"};
                        int result = JOptionPane.showOptionDialog(
                                this,
                                "Swampizzo lost a life! Lives remaining: " + lives2,
                                "Life Lost",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null,
                                options,
                                options[0]
                        );
                        if (result == 1) {
                            cleanUpBeforeExit();
                            lf.showMainMenu();
                            return;
                        } else {
                            resetRound();
                        }
                    } else {
                        String winner = "Carti";
                        int result = JOptionPane.showOptionDialog(
                                this,
                                winner + " wins the match! Play next round?",
                                "Game Over",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                null, null, null
                        );
                        if (result == JOptionPane.YES_OPTION) {
                            startNewMatch();
                        } else {
                            cleanUpBeforeExit();
                            lf.showMainMenu();
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            repaint();
            try {
                Thread.sleep(period);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Stop whatever level-specific background is playing (e.g., WLR music) */
    public void stopLevelMusic() {
        audioManager.stopBackground();
        currentBackgroundMusic = null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int screenW = getWidth();
        int screenH = getHeight();

        if (offscreen == null || offscreen.getWidth() != screenW || offscreen.getHeight() != screenH) {
            offscreen = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_RGB);
        }

        Graphics2D g2 = offscreen.createGraphics();

        // diagnostics (only draw in world-space when not split or for info)
        if (!splitScreenEnabled) {
            float scaleX = screenW / (float) WORLD_WIDTH;
            float scaleY = screenH / (float) WORLD_HEIGHT;

            if (t1 == null || t2 == null) {
                g2.setTransform(new AffineTransform()); // reset so text is readable
                g2.setColor(Color.RED);
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2.drawString("ERROR: Tank(s) null", 50, 50);
            }
            if (map == null) {
                g2.setTransform(new AffineTransform());
                g2.setColor(Color.YELLOW);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.drawString("Note: map is null, fallback background shown", 50, 80);
            }

            // single screen: scale entire world to fit
            g2.setTransform(AffineTransform.getScaleInstance(scaleX, scaleY));
            renderWorld(leftOrFull(g2));
        } else {
            // split-screen logic
            // update cameras to follow tanks
            if (t1 != null) cam1.update(t1);
            if (t2 != null) cam2.update(t2);

            // left viewport (player1)
            int halfW = screenW / 2;
            Graphics2D left = (Graphics2D) g2.create(0, 0, halfW, screenH);
            applyCameraTransformWithZoom(left, cam1, halfW, screenH, SPLIT_ZOOM);
            renderWorld(left);
            left.dispose();

            // right viewport (player2)
            Graphics2D right = (Graphics2D) g2.create(halfW, 0, halfW, screenH);
            applyCameraTransformWithZoom(right, cam2, halfW, screenH, SPLIT_ZOOM);
            renderWorld(right);
            right.dispose();

            // divider line
            g2.setTransform(new AffineTransform()); // back to screen coords
            g2.setColor(Color.WHITE);
            g2.fillRect(screenW / 2 - 2, 0, 4, screenH);
        }

        // blit offscreen
        g.drawImage(offscreen, 0, 0, null);

        // overlay UI (lives)
        drawOverlayUI(g);

        // mini-map overlay
        if (miniMapEnabled) {
            int miniW = 200;
            int miniH = (int) (miniW * (WORLD_HEIGHT / (float) WORLD_WIDTH));
            int padding = 10;
            int xOff = screenW - miniW - padding;
            int yOff = padding;

            Graphics2D mm = (Graphics2D) g.create();
            // background behind mini-map for readability
            mm.setColor(new Color(0, 0, 0, 180));
            mm.fillRoundRect(xOff - 4, yOff - 4, miniW + 8, miniH + 8, 8, 8);
            mm.translate(xOff, yOff);
            mm.setClip(0, 0, miniW, miniH);

            // draw mini map background (scaled)
            if (map != null) {
                mm.drawImage(map, 0, 0, miniW, miniH, null);
            } else {
                mm.setColor(Color.DARK_GRAY);
                mm.fillRect(0, 0, miniW, miniH);
            }

            // shared scale for mini-map elements
            float scaleX = miniW / (float) WORLD_WIDTH;
            float scaleY = miniH / (float) WORLD_HEIGHT;

            // draw walls on mini-map, skip destroyed ones so broken walls disappear
            mm.setColor(new Color(180, 180, 180, 200));
            for (Wall w : walls) {
                if (w.isDestroyed()) continue;
                Rectangle wb = w.getBounds();
                int wx = (int) (wb.x * scaleX);
                int wy = (int) (wb.y * scaleY);
                int ww = Math.max(1, (int) (wb.width * scaleX));
                int wh = Math.max(1, (int) (wb.height * scaleY));
                mm.fillRect(wx, wy, ww, wh);
            }

            // draw power-ups as small dots
            for (PowerUp pu : powerUps) {
                Rectangle b = pu.getBounds();
                int px = (int) (b.x * scaleX);
                int py = (int) (b.y * scaleY);
                mm.setColor(Color.MAGENTA);
                mm.fillOval(px, py, 6, 6);
            }

            // draw tanks
            if (t1 != null) {
                int t1x = (int) (t1.getX() * scaleX);
                int t1y = (int) (t1.getY() * scaleY);
                mm.setColor(Color.CYAN);
                mm.fillOval(t1x, t1y, 8, 8);
            }
            if (t2 != null) {
                int t2x = (int) (t2.getX() * scaleX);
                int t2y = (int) (t2.getY() * scaleY);
                mm.setColor(Color.ORANGE);
                mm.fillOval(t2x, t2y, 8, 8);
            }

            // draw camera rectangles if split-screen
            if (splitScreenEnabled) {
                mm.setColor(new Color(255, 255, 255, 150));
                Stroke old = mm.getStroke();
                mm.setStroke(new BasicStroke(2));
                mm.drawRect((int) (cam1.x * scaleX), (int) (cam1.y * scaleX),
                        (int) (cam1.viewW * scaleX), (int) (cam1.viewH * scaleY));
                mm.drawRect((int) (cam2.x * scaleX), (int) (cam2.y * scaleX),
                        (int) (cam2.viewW * scaleX), (int) (cam2.viewH * scaleY));
                mm.setStroke(old);
            }

            mm.dispose();
        }

        // draw exit button bottom-left
        int exitX = EXIT_PADDING;
        int exitY = screenH - EXIT_SIZE - EXIT_PADDING;
        exitRect = new Rectangle(exitX, exitY, EXIT_SIZE, EXIT_SIZE);
        if (exitImg != null) {
            g.drawImage(exitImg, exitX, exitY, EXIT_SIZE, EXIT_SIZE, null);
        } else {
            g.setColor(Color.RED);
            g.fillRect(exitX, exitY, EXIT_SIZE, EXIT_SIZE);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("X", exitX + EXIT_SIZE / 3, exitY + (EXIT_SIZE * 2 / 3));
        }
    }

    /** Shared world rendering; assumes g2 is already in world-space (scale/translate done if needed) */
    private void renderWorld(Graphics2D g2) {
        if (map != null) {
            g2.drawImage(map, 0, 0, WORLD_WIDTH, WORLD_HEIGHT, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        }

        for (Wall w : walls) {
            w.draw(g2);
        }

        for (PowerUp pu : powerUps) {
            pu.draw(g2);
        }

        if (t1 != null) {
            t1.drawImage(g2);
            drawHealthBar(g2, t1, health1, lives1);
        }
        if (t2 != null) {
            t2.drawImage(g2);
            drawHealthBar(g2, t2, health2, lives2);
        }

        for (Bullet b : bullets) {
            b.drawImage(g2);
        }
    }

    /** helper to unify call for full screen (no split) */
    private Graphics2D leftOrFull(Graphics2D g2) {
        return g2;
    }

    /** Applies camera transform so that camera.viewW / viewH maps to the given pixel viewport. */
    private void applyCameraTransform(Graphics2D g2, Camera cam, int viewPixelWidth, int viewPixelHeight) {
        float scaleX = viewPixelWidth / cam.viewW;
        float scaleY = viewPixelHeight / cam.viewH;
        g2.scale(scaleX, scaleY);
        g2.translate(-cam.x, -cam.y);
    }

    /** Like applyCameraTransform but zooms in around the camera's center to simulate split-screen detail. */
    private void applyCameraTransformWithZoom(Graphics2D g2, Camera cam, int viewPixelWidth, int viewPixelHeight, float zoom) {
        float effW = cam.viewW / zoom;
        float effH = cam.viewH / zoom;

        float centerX = cam.x + cam.viewW / 2f;
        float centerY = cam.y + cam.viewH / 2f;

        float newX = centerX - effW / 2f;
        float newY = centerY - effH / 2f;
        newX = Math.max(0, Math.min(newX, WORLD_WIDTH - effW));
        newY = Math.max(0, Math.min(newY, WORLD_HEIGHT - effH));

        float scaleX = viewPixelWidth / effW;
        float scaleY = viewPixelHeight / effH;
        g2.scale(scaleX, scaleY);
        g2.translate(-newX, -newY);
    }

    private void drawHealthBar(Graphics2D g2, Tank t, int health, int lives) {
        int barWidth = (int) t.getBounds().getWidth();
        int barHeight = 5;
        int x = (int) t.getX();
        int y = (int) t.getY() - barHeight - 6; // padding for lives
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(x, y, barWidth, barHeight);
        g2.setColor(Color.GREEN);
        int w = (int) (barWidth * (health / 100f));
        g2.fillRect(x, y, w, barHeight);
        int tickW = barWidth / 6;
        int spacing = 2;
        int livesY = y - barHeight - 2;
        for (int i = 0; i < 3; i++) {
            g2.setColor(i < lives ? Color.RED : Color.GRAY);
            int px = x + i * (tickW + spacing);
            g2.fillRect(px, livesY, tickW, 4);
        }
    }

    private void drawOverlayUI(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();
        String left = "C: " + lives1;
        String right = "S: " + lives2;

        int padding = 6;
        int lh = fm.getHeight();
        int lw = fm.stringWidth(left);
        int rw = fm.stringWidth(right);
        int boxH = lh + padding;
        int boxLW = lw + padding * 2;
        int boxRW = rw + padding * 2;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(10, 10, boxLW, boxH, 8, 8);
        g2.fillRoundRect(getWidth() - boxRW - 10, 10, boxRW, boxH, 8, 8);

        g2.setColor(Color.WHITE);
        int textY = 10 + fm.getAscent() + (padding / 2);
        g2.drawString(left, 10 + padding, textY);
        g2.drawString(right, getWidth() - boxRW - 10 + padding, textY);

        g2.dispose();
    }
}

// Example usage to switch to Magnolia with its own map + music:
// gameWorld.setMap(MapType.MAGNOLIA);
// gameWorld.startNewMatch();
