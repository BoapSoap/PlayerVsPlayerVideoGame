package tankgame.menus;

import tankgame.Launcher;
import tankgame.game.AudioManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class StartMenuPanel extends JPanel {

    private BufferedImage menuBackground;
    private BufferedImage startImg;
    private BufferedImage exitImg;
    private BufferedImage settingsImg; // added
    private final Launcher lf;

    private final JButton startBtn = new JButton();
    private final JButton settingsBtn = new JButton("Settings"); // existing
    private final JButton exitBtn = new JButton();

    // fullscreen support
    private boolean isFullScreen = false;
    private Rectangle windowedBounds;
    private final GraphicsDevice device;

    // audio
    private final AudioManager audioManager;

    public StartMenuPanel(Launcher lf, AudioManager audioManager) {
        this.lf = lf;
        this.audioManager = audioManager;
        setBackground(Color.BLACK);
        setLayout(null); // we manually position

        device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        loadAssets();
        configureButtons();
        setupKeybindings();
        add(startBtn);
        add(settingsBtn);
        add(exitBtn);
    }

    /** Explicit trigger; caller (e.g., Launcher) should invoke when showing menu. */
    public void startMenuMusic() {
        if (!audioManager.isBackgroundPlaying()) {
            audioManager.playBackground("opmbabi.wav");
        }
    }

    private void loadAssets() {
        try {
            menuBackground = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("title.png")
            ));
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load title.png");
            e.printStackTrace();
            menuBackground = null;
        }

        try {
            startImg = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("start.png")
            ));
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load start.png");
            e.printStackTrace();
            startImg = null;
        }

        try {
            exitImg = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("exit.png")
            ));
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load exit.png");
            e.printStackTrace();
            exitImg = null;
        }

        try {
            settingsImg = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("settingsbutton.png")
            ));
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load settingsbutton.png");
            settingsImg = null;
        }
    }

    private void configureButtons() {
        // start and exit: image-only, no chrome
        for (JButton b : new JButton[]{startBtn, exitBtn}) {
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        // settings: either icon-only (no highlight) or fallback boxed style
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsBtn.setFocusPainted(false);
        settingsBtn.setRolloverEnabled(false);
        settingsBtn.setFocusable(false);
        settingsBtn.setUI(new BasicButtonUI() {
            @Override
            protected void paintButtonPressed(Graphics g, AbstractButton b) {
                // no-op to suppress default pressed highlight
            }
        });

        if (settingsImg == null) {
            // fallback visible boxed button if no icon
            settingsBtn.setContentAreaFilled(true);
            settingsBtn.setOpaque(true);
            settingsBtn.setBackground(new Color(0, 0, 0, 160));
            settingsBtn.setForeground(Color.WHITE);
            settingsBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
            settingsBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.WHITE, 2),
                    BorderFactory.createEmptyBorder(6, 16, 6, 16)
            ));
        } else {
            // icon case: remove chrome so no highlight/box
            settingsBtn.setContentAreaFilled(false);
            settingsBtn.setBorderPainted(false);
            settingsBtn.setOpaque(false);
        }

        startBtn.addActionListener(e -> {
            AudioManager.playEffect("gunshot.wav");
            // do NOT stop background so music continues into map selection
            lf.setFrame("maps"); // go to map selection first
        });
        settingsBtn.addActionListener(e -> {
            AudioManager.playEffect("gunshot.wav");
            // placeholder: requires a "settings" card in Launcher to actually work
            lf.setFrame("settings");
        });
        exitBtn.addActionListener(e -> {
            AudioManager.playEffect("gunshot.wav");
            audioManager.stopBackground();
            lf.closeGame();
        });

        // ensure cursors on all
        exitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void setupKeybindings() {
        // F11 toggles fullscreen
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleFull");
        getActionMap().put("toggleFull", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullScreen();
            }
        });
    }

    private void toggleFullScreen() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (!(w instanceof JFrame)) return;
        JFrame frame = (JFrame) w;

        if (!isFullScreen) {
            windowedBounds = frame.getBounds();
            frame.dispose();
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);
            frame.setVisible(true);
            isFullScreen = true;
        } else {
            device.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);
            if (windowedBounds != null) frame.setBounds(windowedBounds);
            frame.setVisible(true);
            isFullScreen = false;
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int w = getWidth();
        int h = getHeight();

        // Button sizing: three buttons, keep aspect ratio for start/exit if available
        int btnW = (int) (w * 0.2);
        int btnH;
        if (startImg != null) {
            double ratio = startImg.getHeight() / (double) startImg.getWidth();
            btnH = (int) (btnW * ratio);
        } else {
            btnH = btnW / 3;
        }

        int gap = 20;
        int totalW = btnW * 3 + gap * 2;
        int startX = (w - totalW) / 2;
        int y = (int) (h * 0.6); // roughly 60% down

        startBtn.setBounds(startX, y, btnW, btnH);
        settingsBtn.setBounds(startX + btnW + gap, y, btnW, btnH);
        exitBtn.setBounds(startX + (btnW + gap) * 2, y, btnW, btnH);

        if (startImg != null) {
            startBtn.setIcon(new ImageIcon(startImg.getScaledInstance(btnW, btnH, Image.SCALE_SMOOTH)));
        }
        if (exitImg != null) {
            exitBtn.setIcon(new ImageIcon(exitImg.getScaledInstance(btnW, btnH, Image.SCALE_SMOOTH)));
        }
        if (settingsImg != null) {
            settingsBtn.setText(""); // remove text if using icon
            settingsBtn.setIcon(new ImageIcon(settingsImg.getScaledInstance(btnW, btnH, Image.SCALE_SMOOTH)));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();

        // draw background scaled to fill while preserving aspect ratio
        if (menuBackground != null) {
            double imgRatio = menuBackground.getWidth() / (double) menuBackground.getHeight();
            double panelRatio = w / (double) h;
            int drawW, drawH;
            if (panelRatio > imgRatio) {
                drawH = h;
                drawW = (int) (h * imgRatio);
            } else {
                drawW = w;
                drawH = (int) (w / imgRatio);
            }
            int x = (w - drawW) / 2;
            int y = (h - drawH) / 2;
            g2.drawImage(menuBackground, x, y, drawW, drawH, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, w, h);
        }

        g2.dispose();
    }
}
