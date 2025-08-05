package tankgame;

import tankgame.game.GameWorld;
import tankgame.menus.EndGamePanel;
import tankgame.menus.StartMenuPanel;
import tankgame.menus.MapSelectionPanel;
import tankgame.menus.SettingsPanel; // added

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import tankgame.game.AudioManager;

public class Launcher {

    private JPanel mainPanel;
    private GameWorld gamePanel;
    private StartMenuPanel startPanel;
    private MapSelectionPanel mapPanel;
    private SettingsPanel settingsPanel; // added
    private final JFrame jf;
    private CardLayout cl;

    // fullscreen support shared
    private final GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    private boolean isFullScreen = false;
    private Rectangle windowedBounds;

    // shared audio manager
    private final AudioManager audioManager = new AudioManager();

    public Launcher() {
        this.jf = new JFrame();
        this.jf.setTitle("I AM MUSIC THE GAME");
        this.jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void initUIComponents() {
        this.mainPanel = new JPanel();
        this.startPanel = new StartMenuPanel(this, audioManager);
        this.gamePanel = new GameWorld(this);
        this.mapPanel = new MapSelectionPanel(this, audioManager);
        this.settingsPanel = new SettingsPanel(this, gamePanel); // instantiate settings panel with GameWorld
        JPanel endPanel = new EndGamePanel(this);

        cl = new CardLayout();
        this.mainPanel.setLayout(cl);
        this.mainPanel.add(startPanel, "start");
        this.mainPanel.add(mapPanel,  "maps");
        this.mainPanel.add(settingsPanel, "settings"); // new card
        this.mainPanel.add(gamePanel,  "game");
        this.mainPanel.add(endPanel,   "end");

        this.jf.add(mainPanel);
        this.jf.setResizable(true);
        this.setFrame("start");
    }

    /**
     * Switches to the given card: "start", "maps", "game", "settings", or "end"
     */
    public void setFrame(String type) {
        this.jf.setVisible(false);

        boolean currentlyFull = (gd.getFullScreenWindow() == this.jf);

        switch (type) {
            case "start" -> {
                if (!currentlyFull) {
                    this.jf.setSize(GameConstants.START_MENU_SCREEN_WIDTH, GameConstants.START_MENU_SCREEN_HEIGHT);
                }
                if (gamePanel != null) gamePanel.stopLevelMusic(); // always stop level music
                cl.show(mainPanel, "start");
                if (startPanel != null) startPanel.startMenuMusic();
            }
            case "maps" -> {
                if (!currentlyFull) {
                    this.jf.setSize(GameConstants.START_MENU_SCREEN_WIDTH, GameConstants.START_MENU_SCREEN_HEIGHT);
                }
                cl.show(mainPanel, "maps");
            }
            case "settings" -> {
                if (!currentlyFull) {
                    this.jf.setSize(GameConstants.START_MENU_SCREEN_WIDTH, GameConstants.START_MENU_SCREEN_HEIGHT);
                }
                cl.show(mainPanel, "settings");
            }
            case "game" -> {
                if (!currentlyFull) {
                    this.jf.setSize(GameConstants.GAME_SCREEN_WIDTH, GameConstants.GAME_SCREEN_HEIGHT);
                }
                // stop the menu music so game/map-specific music can play
                audioManager.stopBackground();

                if (gamePanel != null) gamePanel.startNewMatch();
                new Thread(gamePanel).start();
                cl.show(mainPanel, "game");
                SwingUtilities.invokeLater(() -> {
                    gamePanel.requestFocusInWindow();
                    jf.requestFocusInWindow();
                });
            }
            case "end" -> {
                if (!currentlyFull) {
                    this.jf.setSize(GameConstants.END_MENU_SCREEN_WIDTH, GameConstants.END_MENU_SCREEN_HEIGHT);
                }
                cl.show(mainPanel, "end");
            }
        }

        this.jf.setVisible(true);
        this.jf.requestFocusInWindow();
    }

    /** Exposed for MapSelectionPanel */
    public GameWorld getGamePanel() {
        return gamePanel;
    }

    /** Toggles fullscreen on the main window (used if you want shared logic) */
    public void toggleFullScreen() {
        if (!isFullScreen) {
            windowedBounds = jf.getBounds();
            jf.dispose();
            jf.setUndecorated(true);
            gd.setFullScreenWindow(jf);
            jf.setVisible(true);
            isFullScreen = true;
        } else {
            gd.setFullScreenWindow(null);
            jf.dispose();
            jf.setUndecorated(false);
            if (windowedBounds != null) jf.setBounds(windowedBounds);
            jf.setVisible(true);
            isFullScreen = false;
        }
    }

    /** Called by GameWorld to go back to the start menu */
    public void showMainMenu() {
        setFrame("start");
    }

    public JFrame getJf() {
        return jf;
    }

    /** Exit whole app */
    public void closeGame() {
        this.jf.dispatchEvent(new WindowEvent(this.jf, WindowEvent.WINDOW_CLOSING));
    }

    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.initUIComponents();
    }
}
