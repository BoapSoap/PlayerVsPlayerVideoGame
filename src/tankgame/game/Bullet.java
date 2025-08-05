package tankgame.game;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class Bullet {
    private float x, y;
    private final float vx;
    private final BufferedImage image;
    private static final float SPEED = 40f;
    private static final float SCALE = 0.04f; // adjust for overall bullet size

    public Bullet(float startX, float startY, boolean facingRight) {
        // load & scale
        BufferedImage raw = null;
        try {
            raw = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("bullet.png")
            ));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.image = (raw != null) ? scale(raw) : null;
        this.vx = facingRight ? SPEED : -SPEED;

        // spawn just outside the tank
        if (image != null) {
            if (facingRight) {
                this.x = startX;
            } else {
                this.x = startX - image.getWidth();
            }
        } else {
            this.x = startX;
        }
        this.y = startY;
    }

    private BufferedImage scale(BufferedImage img) {
        int w = (int)(img.getWidth() * SCALE);
        int h = (int)(img.getHeight() * SCALE);
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return buf;
    }

    public void update() {
        x += vx;
    }

    public void drawImage(Graphics g) {
        if (image == null) return;
        Graphics2D g2 = (Graphics2D) g;
        AffineTransform at = new AffineTransform();
        if (vx < 0) {
            // flip for left-going
            at.translate(x + image.getWidth(), y);
            at.scale(-1, 1);
        } else {
            at.translate(x, y);
        }
        g2.drawImage(image, at, null);
    }

    public Rectangle getBounds() {
        if (image == null) return new Rectangle();
        return new Rectangle((int)x, (int)y, image.getWidth(), image.getHeight());
    }

    public float getX() { return x; }
}
