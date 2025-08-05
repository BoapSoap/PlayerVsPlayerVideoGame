package tankgame.game;

public class Camera {
    float x, y;             // top-left in world coords
    final float viewW;      // width in world space
    final float viewH;      // height in world space

    public Camera(float viewW, float viewH) {
        this.viewW = viewW;
        this.viewH = viewH;
    }

    void update(Tank t) {
        x = t.getX() + getTankWidth(t)/2f - viewW/2f;
        y = t.getY() + getTankHeight(t)/2f - viewH/2f;
        // clamp to world bounds
        x = Math.max(0, Math.min(x, GameWorld.WORLD_WIDTH - viewW));
        y = Math.max(0, Math.min(y, GameWorld.WORLD_HEIGHT - viewH));
    }

    private float getTankWidth(Tank t) {
        return t.getBounds().width;
    }

    private float getTankHeight(Tank t) {
        return t.getBounds().height;
    }
}
