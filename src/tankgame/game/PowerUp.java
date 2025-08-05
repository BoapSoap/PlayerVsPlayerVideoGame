package tankgame.game;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public class PowerUp {
    private final PowerUpType type;
    private float x, y;
    private final int size;
    private final BufferedImage image;

    public PowerUp(PowerUpType type, float x, float y, int size) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.size = size;

        BufferedImage img = null;
        try {
            String resource = switch (type) {
                case RICK_OWENS -> "rickowens.png";
                case VAMP_HEAL -> "vampheal.png";
                case SLATT_SURGE -> "slattsurge.png";
            };
            URL url = getClass().getClassLoader().getResource(resource);
            if (url != null) img = ImageIO.read(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.image = img;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, size, size);
    }

    public void draw(Graphics2D g2) {
        if (image != null) {
            g2.drawImage(image, (int) x, (int) y, size, size, null);
        } else {
            // fallback visual
            g2.setColor(Color.MAGENTA);
            g2.fillOval((int) x, (int) y, size, size);
        }
    }

    public PowerUpType getType() {
        return type;
    }
}
