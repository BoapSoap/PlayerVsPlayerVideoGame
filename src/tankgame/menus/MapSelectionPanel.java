package tankgame.menus;

import tankgame.Launcher;
import tankgame.game.GameWorld;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class MapSelectionPanel extends JPanel {
    private final Launcher lf;
    private BufferedImage backImg;

    public MapSelectionPanel(Launcher lf, Object unusedAudioManagerPlaceholder) {
        this.lf = lf;
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());

        // Title
        JLabel title = new JLabel("Maps", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 48));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(title, BorderLayout.NORTH);

        // Center: map choices
        JPanel center = new JPanel();
        center.setBackground(Color.WHITE);
        center.setLayout(new FlowLayout(FlowLayout.CENTER, 40, 20));

        JButton wlrButton = new JButton("Whole Lotta Red");
        wlrButton.setFont(new Font("SansSerif", Font.PLAIN, 24));
        wlrButton.setPreferredSize(new Dimension(300, 80));
        wlrButton.addActionListener(e -> {
            // play gunshot effect
            tankgame.game.AudioManager.playEffect("gunshot.wav");
            GameWorld gw = lf.getGamePanel();
            if (gw != null) {
                gw.setLevel("wlrlevel1.txt");
                gw.startNewMatch();
            }
            lf.setFrame("game");
        });
        center.add(wlrButton);

        JButton magnoliaButton = new JButton("Magnolia");
        magnoliaButton.setFont(new Font("SansSerif", Font.PLAIN, 24));
        magnoliaButton.setPreferredSize(new Dimension(300, 80));
        magnoliaButton.addActionListener(e -> {
            tankgame.game.AudioManager.playEffect("gunshot.wav");
            GameWorld gw = lf.getGamePanel();
            if (gw != null) {
                gw.setLevel("magnolialevel.txt");
                gw.startNewMatch();
            }
            lf.setFrame("game");
        });
        center.add(magnoliaButton);

        // NEW: Backrooms map button
        JButton backroomsButton = new JButton("Backrooms");
        backroomsButton.setFont(new Font("SansSerif", Font.PLAIN, 24));
        backroomsButton.setPreferredSize(new Dimension(300, 80));
        backroomsButton.addActionListener(e -> {
            tankgame.game.AudioManager.playEffect("gunshot.wav");
            GameWorld gw = lf.getGamePanel();
            if (gw != null) {
                gw.setLevel("backroomslevel.txt");
                gw.startNewMatch();
            }
            lf.setFrame("game");
        });
        center.add(backroomsButton);

        add(center, BorderLayout.CENTER);

        // play gunshot if user clicks background area
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tankgame.game.AudioManager.playEffect("gunshot.wav");
            }
        });

        // Back button bottom-right
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JButton backBtn = new JButton();
        backBtn.setContentAreaFilled(false);
        backBtn.setBorderPainted(false);
        backBtn.setFocusPainted(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        try {
            backImg = ImageIO.read(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("backbutton.png")
            ));
            Image scaled = backImg.getScaledInstance(80, 80, Image.SCALE_SMOOTH);
            backBtn.setIcon(new ImageIcon(scaled));
        } catch (IOException | NullPointerException ex) {
            System.err.println("failed to load backbutton.png");
            backBtn.setText("Back");
        }
        backBtn.addActionListener(e -> {
            tankgame.game.AudioManager.playEffect("gunshot.wav");
            lf.setFrame("start");
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
        right.add(backBtn);
        south.add(right, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
    }
}
