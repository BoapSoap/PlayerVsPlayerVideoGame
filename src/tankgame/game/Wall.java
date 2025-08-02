package tankgame.game;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class Wall {
    public final float x, y; // world coordinates
    public final float width, height;
    public final boolean breakable;
    private int health; // only used if breakable
    public final BufferedImage img;

    public Wall(String resourceName, float x, float y, boolean breakable) {
        this.x = x;
        this.y = y;
        this.breakable = breakable;
        BufferedImage tmp = null;
        try {
            tmp = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(resourceName)
            ));
        } catch (IOException e) {
            e.printStackTrace();
        }
        img = tmp;
        if (img != null) {
            this.width = img.getWidth();
            this.height = img.getHeight();
        } else {
            this.width = this.height = 0;
        }
        if (breakable) {
            this.health = 100; // full health; adjust if desired
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)y, (int)width, (int)height);
    }

    public boolean isDestroyed() {
        return breakable && health <= 0;
    }

    public void applyDamage(int dmg) {
        if (breakable) {
            health -= dmg;
        }
    }

    public void draw(Graphics2D g2) {
        if (img != null && !isDestroyed()) {
            g2.drawImage(img, (int)x, (int)y, (int)width, (int)height, null);
        }
    }
}
