package tankgame.game;

import tankgame.GameConstants;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.List;

public class Tank {
    public enum Direction { UP, DOWN, LEFT, RIGHT, SHOOT }

    private final float scale;            // instance‐level scale
    private static final float SPEED = 25f; // base speed
    private float speedMultiplier = 1f; // for power-ups
    private static final int DELAY = 8;

    private float x, y;
    private final BufferedImage[] frames;
    private int currentFrame = 0, timer = 0;
    private boolean facingRight = true;
    private final EnumSet<Direction> keysPressed = EnumSet.noneOf(Direction.class);

    /**
     * @param scale how much to shrink the PNGs (e.g. 0.15f for Carti, 0.10f for Swampizzo)
     */
    public Tank(float x, float y, float vx, float vy, short angle,
                String resourceBase, int jogFrames, float scale) {
        this.x = x;
        this.y = y;
        this.scale = scale;
        // slots: [0]=stand, [1..jogFrames]=jog, [jogFrames+1]=shoot
        frames = new BufferedImage[jogFrames + 2];
        try {
            // standing
            BufferedImage stand = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(resourceBase + "_standing.png")
            ));
            frames[0] = scaleImage(stand);

            // jog
            for (int i = 1; i <= jogFrames; i++) {
                BufferedImage jog = ImageIO.read(Objects.requireNonNull(
                        getClass().getClassLoader().getResource(resourceBase + "Jogf" + i + ".png")
                ));
                frames[i] = scaleImage(jog);
            }

            // shoot
            BufferedImage fire = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(resourceBase + "fire.png")
            ));
            frames[jogFrames + 1] = scaleImage(fire);

        } catch (IOException e) {
            e.printStackTrace();
            // fallback everyone to standing
            for (int i = 1; i < frames.length; i++) frames[i] = frames[0];
        }
    }

    private BufferedImage scaleImage(BufferedImage img) {
        int w = (int)(img.getWidth() * scale);
        int h = (int)(img.getHeight() * scale);
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return buf;
    }

    public void pressed(Direction d)  { keysPressed.add(d); }
    public void released(Direction d) { keysPressed.remove(d); }

    /** speed multiplier modifiers for power-ups */
    public void setSpeedMultiplier(float m) {
        this.speedMultiplier = m;
    }

    public void resetSpeedMultiplier() {
        this.speedMultiplier = 1f;
    }

    /** original/backward-compatible update (no wall blocking) */
    public void update(int maxWidth, int maxHeight) {
        // shoot frame has priority
        if (keysPressed.contains(Direction.SHOOT)) {
            currentFrame = frames.length - 1;
            return;
        }

        boolean moving = false;
        float effectiveSpeed = SPEED * speedMultiplier;
        if (keysPressed.contains(Direction.UP))    { y -= effectiveSpeed; moving = true; }
        if (keysPressed.contains(Direction.DOWN))  { y += effectiveSpeed; moving = true; }
        if (keysPressed.contains(Direction.LEFT))  { x -= effectiveSpeed; facingRight = false; moving = true; }
        if (keysPressed.contains(Direction.RIGHT)) { x += effectiveSpeed; facingRight = true;  moving = true; }

        // dynamic bounds
        x = Math.max(30, Math.min(x, maxWidth  - frames[0].getWidth()));
        y = Math.max(30, Math.min(y, maxHeight - frames[0].getHeight()));

        if (moving) {
            timer++;
            if (timer >= DELAY) {
                timer = 0;
                currentFrame = (currentFrame < frames.length - 2)
                        ? currentFrame + 1
                        : 1;
            }
        } else {
            currentFrame = 0;
            timer = 0;
        }
    }

    /**
     * Update with wall blocking. Axis-separated movement: tries horizontal then vertical and
     * reverts any axis that causes intersection with a non-destroyed wall.
     */
    public void updateWithWalls(int maxWidth, int maxHeight, List<Wall> walls) {
        // shoot frame has priority
        if (keysPressed.contains(Direction.SHOOT)) {
            currentFrame = frames.length - 1;
            return;
        }

        // compute intended movement vector
        float dx = 0, dy = 0;
        if (keysPressed.contains(Direction.UP))    dy -= 1;
        if (keysPressed.contains(Direction.DOWN))  dy += 1;
        if (keysPressed.contains(Direction.LEFT))  dx -= 1;
        if (keysPressed.contains(Direction.RIGHT)) dx += 1;

        boolean moving = false;
        if (dx != 0 || dy != 0) {
            moving = true;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            float effectiveSpeed = SPEED * speedMultiplier;
            dx = (dx / len) * effectiveSpeed;
            dy = (dy / len) * effectiveSpeed;
        }

        // update facing based on horizontal input only
        if (keysPressed.contains(Direction.LEFT) && !keysPressed.contains(Direction.RIGHT)) {
            facingRight = false;
        } else if (keysPressed.contains(Direction.RIGHT) && !keysPressed.contains(Direction.LEFT)) {
            facingRight = true;
        }

        float oldX = x;
        float oldY = y;

        // horizontal movement & clamp
        x += dx;
        x = Math.max(30, Math.min(x, maxWidth - frames[0].getWidth()));
        Rectangle boundsH = getBounds();
        boolean collH = false;
        for (Wall w : walls) {
            if (w.isDestroyed()) continue;
            if (boundsH.intersects(w.getBounds())) {
                collH = true;
                break;
            }
        }
        if (collH) {
            x = oldX;
        }

        // vertical movement & clamp
        y += dy;
        y = Math.max(30, Math.min(y, maxHeight - frames[0].getHeight()));
        Rectangle boundsV = getBounds();
        boolean collV = false;
        for (Wall w : walls) {
            if (w.isDestroyed()) continue;
            if (boundsV.intersects(w.getBounds())) {
                collV = true;
                break;
            }
        }
        if (collV) {
            y = oldY;
        }

        // animation
        if (moving) {
            timer++;
            if (timer >= DELAY) {
                timer = 0;
                currentFrame = (currentFrame < frames.length - 2)
                        ? currentFrame + 1
                        : 1;
            }
        } else {
            currentFrame = 0;
            timer = 0;
        }
    }

    /** legacy/backward-compatible; uses constant bounds */
    public void update() {
        update(GameConstants.GAME_SCREEN_WIDTH, GameConstants.GAME_SCREEN_HEIGHT);
    }

    /** Resets position, animation, input state, and speed multiplier for reuse on restart */
    public void reset(float newX, float newY) {
        this.x = newX;
        this.y = newY;
        this.currentFrame = 0;
        this.timer = 0;
        this.facingRight = true;
        keysPressed.clear();
        resetSpeedMultiplier();
    }

    public void drawImage(Graphics g) {
        BufferedImage img = frames[currentFrame];
        Graphics2D g2 = (Graphics2D) g;
        AffineTransform at = new AffineTransform();
        if (!facingRight) {
            at.translate(x + img.getWidth(), y);
            at.scale(-1, 1);
        } else {
            at.translate(x, y);
        }
        g2.drawImage(img, at, null);
    }

    public Rectangle getBounds() {
        BufferedImage img = frames[currentFrame];
        return new Rectangle((int)x, (int)y, img.getWidth(), img.getHeight());
    }
    public float getX()            { return x; }
    public float getY()            { return y; }
    public boolean isFacingRight() { return facingRight; }
}
