package com.sstv.ui;

import com.sstv.codec.MartinM1;
import com.sstv.codec.SSTVDecoder;
import com.sstv.codec.SSTVEncoder;
import com.sstv.codec.WavFile;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SSTVFrame extends JFrame {

    public SSTVFrame() {
        super("SSTV Encoder / Decoder — Martin M1");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 640);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Encode (Image → Audio)", new EncodePanel());
        tabs.addTab("Decode (Audio → Image)", new DecodePanel());
        setContentPane(tabs);
    }

    // ============================= ENCODE =============================

    private static class EncodePanel extends JPanel {
        private final ImageView preview = new ImageView(MartinM1.WIDTH, MartinM1.HEIGHT);
        private final JLabel status = new JLabel("Choose an image to begin.");
        private final JButton chooseImageBtn = new JButton("Choose Image…");
        private final JButton encodeBtn = new JButton("Encode to WAV…");
        private final JProgressBar progress = new JProgressBar();

        private BufferedImage sourceImage;

        EncodePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            add(preview, BorderLayout.CENTER);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(chooseImageBtn);
            top.add(encodeBtn);
            add(top, BorderLayout.NORTH);

            JPanel bottom = new JPanel(new BorderLayout(6, 6));
            progress.setIndeterminate(false);
            bottom.add(status, BorderLayout.NORTH);
            bottom.add(progress, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            encodeBtn.setEnabled(false);

            chooseImageBtn.addActionListener(e -> chooseImage());
            encodeBtn.addActionListener(e -> encode());
        }

        private void chooseImage() {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Images", "png", "jpg", "jpeg", "bmp", "gif"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            try {
                BufferedImage img = ImageIO.read(fc.getSelectedFile());
                if (img == null) throw new IllegalArgumentException("Unsupported or unreadable image file.");
                sourceImage = img;
                preview.setImage(img);
                encodeBtn.setEnabled(true);
                double seconds = 5.61 /*VIS+header approx*/ + MartinM1.HEIGHT * MartinM1.totalLineMs() / 1000.0;
                status.setText(String.format(
                        "Loaded %dx%d image. Will be scaled to %dx%d Martin M1 (~%.0f seconds of audio).",
                        img.getWidth(), img.getHeight(), MartinM1.WIDTH, MartinM1.HEIGHT, seconds));
            } catch (Exception ex) {
                status.setText("Failed to load image: " + ex.getMessage());
            }
        }

        private void encode() {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("sstv_output.wav"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File out = fc.getSelectedFile();

            setBusy(true, "Encoding…");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    double[] samples = SSTVEncoder.encode(sourceImage);
                    WavFile.write(out, samples, MartinM1.SAMPLE_RATE);
                    return null;
                }

                @Override
                protected void done() {
                    setBusy(false, null);
                    try {
                        get();
                        status.setText("Saved: " + out.getAbsolutePath());
                    } catch (Exception ex) {
                        status.setText("Encode failed: " + rootMessage(ex));
                    }
                }
            }.execute();
        }

        private void setBusy(boolean busy, String message) {
            chooseImageBtn.setEnabled(!busy);
            encodeBtn.setEnabled(!busy && sourceImage != null);
            progress.setIndeterminate(busy);
            if (message != null) status.setText(message);
        }
    }

    // ============================= DECODE =============================

    private static class DecodePanel extends JPanel {
        private final ImageView preview = new ImageView(MartinM1.WIDTH, MartinM1.HEIGHT);
        private final JLabel status = new JLabel("Choose a WAV file to decode.");
        private final JButton chooseWavBtn = new JButton("Choose WAV…");
        private final JButton saveImageBtn = new JButton("Save Image…");
        private final JProgressBar progress = new JProgressBar();

        private BufferedImage decodedImage;

        DecodePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            add(preview, BorderLayout.CENTER);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(chooseWavBtn);
            top.add(saveImageBtn);
            add(top, BorderLayout.NORTH);

            JPanel bottom = new JPanel(new BorderLayout(6, 6));
            bottom.add(status, BorderLayout.NORTH);
            bottom.add(progress, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            saveImageBtn.setEnabled(false);

            chooseWavBtn.addActionListener(e -> chooseAndDecode());
            saveImageBtn.addActionListener(e -> saveImage());
        }

        private void chooseAndDecode() {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV audio", "wav"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File in = fc.getSelectedFile();

            setBusy(true, "Decoding…");
            new SwingWorker<SSTVDecoder.Result, Void>() {
                @Override
                protected SSTVDecoder.Result doInBackground() throws Exception {
                    WavFile.WavData data = WavFile.read(in);
                    return SSTVDecoder.decode(data.samples, data.sampleRate);
                }

                @Override
                protected void done() {
                    setBusy(false, null);
                    try {
                        SSTVDecoder.Result result = get();
                        decodedImage = result.image;
                        preview.setImage(decodedImage);
                        saveImageBtn.setEnabled(true);
                        if (result.visCode == MartinM1.VIS_CODE) {
                            status.setText("Decoded. VIS header confirmed Martin M1.");
                        } else if (result.visCode >= 0) {
                            status.setText("Decoded, but VIS header reported mode " + result.visCode
                                    + " (expected " + MartinM1.VIS_CODE + " for Martin M1) — "
                                    + "decoded as Martin M1 anyway; result may be wrong.");
                        } else {
                            status.setText("Decoded (no VIS header found — assumed audio starts at "
                                    + "the first sync pulse and decoded as Martin M1).");
                        }
                    } catch (Exception ex) {
                        status.setText("Decode failed: " + rootMessage(ex));
                    }
                }
            }.execute();
        }

        private void saveImage() {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("decoded.png"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try {
                ImageIO.write(decodedImage, "png", fc.getSelectedFile());
                status.setText("Saved: " + fc.getSelectedFile().getAbsolutePath());
            } catch (Exception ex) {
                status.setText("Save failed: " + rootMessage(ex));
            }
        }

        private void setBusy(boolean busy, String message) {
            chooseWavBtn.setEnabled(!busy);
            progress.setIndeterminate(busy);
            if (message != null) status.setText(message);
        }
    }

    // ============================= shared =============================

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }

    /** Simple fixed-aspect image preview panel. */
    private static class ImageView extends JPanel {
        private final int aspectW, aspectH;
        private BufferedImage image;

        ImageView(int aspectW, int aspectH) {
            this.aspectW = aspectW;
            this.aspectH = aspectH;
            setBackground(Color.DARK_GRAY);
            setBorder(BorderFactory.createLoweredBevelBorder());
        }

        void setImage(BufferedImage img) {
            this.image = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                g.setColor(Color.LIGHT_GRAY);
                String msg = "No image loaded";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                return;
            }
            int panelW = getWidth(), panelH = getHeight();
            double scale = Math.min(panelW / (double) aspectW, panelH / (double) aspectH);
            int w = (int) (aspectW * scale), h = (int) (aspectH * scale);
            int x = (panelW - w) / 2, y = (panelH - h) / 2;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(image, x, y, w, h, null);
        }
    }
}
