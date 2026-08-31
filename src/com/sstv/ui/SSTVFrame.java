package com.sstv.ui;

import com.sstv.codec.SSTVConstants;
import com.sstv.codec.SSTVDecoder;
import com.sstv.codec.SSTVEncoder;
import com.sstv.codec.SSTVMode;
import com.sstv.codec.SSTVModes;
import com.sstv.codec.WavFile;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SSTVFrame extends JFrame {

    public SSTVFrame() {
        super("SSTV Encoder / Decoder");
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
        private final ImageView preview = new ImageView();
        private final JLabel status = new JLabel("Choose an image to begin.");
        private final JButton chooseImageBtn = new JButton("Choose Image…");
        private final JButton encodeBtn = new JButton("Encode to WAV…");
        private final JComboBox<SSTVMode> modeBox = new JComboBox<>(SSTVModes.ALL.toArray(new SSTVMode[0]));
        private final JProgressBar progress = new JProgressBar();

        private BufferedImage sourceImage;

        EncodePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            add(preview, BorderLayout.CENTER);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(chooseImageBtn);
            top.add(new JLabel("Mode:"));
            top.add(modeBox);
            top.add(encodeBtn);
            add(top, BorderLayout.NORTH);

            JPanel bottom = new JPanel(new BorderLayout(6, 6));
            progress.setIndeterminate(false);
            bottom.add(status, BorderLayout.NORTH);
            bottom.add(progress, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);

            encodeBtn.setEnabled(false);
            modeBox.setSelectedItem(SSTVModes.MARTIN_M1);

            chooseImageBtn.addActionListener(e -> chooseImage());
            encodeBtn.addActionListener(e -> encode());
            modeBox.addActionListener(e -> updateStatusForSelection());
        }

        private SSTVMode selectedMode() {
            return (SSTVMode) modeBox.getSelectedItem();
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
                preview.setImage(img, selectedMode().width, selectedMode().height);
                encodeBtn.setEnabled(true);
                updateStatusForSelection();
            } catch (Exception ex) {
                status.setText("Failed to load image: " + ex.getMessage());
            }
        }

        private void updateStatusForSelection() {
            SSTVMode mode = selectedMode();
            preview.setAspect(mode.width, mode.height);
            if (sourceImage == null) return;
            double seconds = mode.rowDurationMs() * mode.height / 1000.0
                    + mode.leadingSyncMs / 1000.0
                    + 0.94; // approx VIS header duration
            status.setText(String.format(
                    "Loaded %dx%d image. Will be scaled to %dx%d for %s (~%.0f seconds of audio).",
                    sourceImage.getWidth(), sourceImage.getHeight(), mode.width, mode.height, mode.name, seconds));
        }

        private void encode() {
            SSTVMode mode = selectedMode();
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("sstv_output.wav"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File out = fc.getSelectedFile();

            setBusy(true, "Encoding as " + mode.name + "…");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    double[] samples = SSTVEncoder.encode(sourceImage, mode);
                    WavFile.write(out, samples, SSTVConstants.SAMPLE_RATE);
                    return null;
                }

                @Override
                protected void done() {
                    setBusy(false, null);
                    try {
                        get();
                        status.setText("Saved (" + mode.name + "): " + out.getAbsolutePath());
                    } catch (Exception ex) {
                        status.setText("Encode failed: " + rootMessage(ex));
                    }
                }
            }.execute();
        }

        private void setBusy(boolean busy, String message) {
            chooseImageBtn.setEnabled(!busy);
            modeBox.setEnabled(!busy);
            encodeBtn.setEnabled(!busy && sourceImage != null);
            progress.setIndeterminate(busy);
            if (message != null) status.setText(message);
        }
    }

    // ============================= DECODE =============================

    private static class DecodePanel extends JPanel {
        private final ImageView preview = new ImageView();
        private final JLabel status = new JLabel("Choose a WAV file to decode.");
        private final JButton chooseWavBtn = new JButton("Choose WAV…");
        private final JButton saveImageBtn = new JButton("Save Image…");
        private final JComboBox<String> modeBox = new JComboBox<>();
        private final JProgressBar progress = new JProgressBar();

        private BufferedImage decodedImage;

        DecodePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            add(preview, BorderLayout.CENTER);

            modeBox.addItem("Auto-detect (from VIS header)");
            for (SSTVMode m : SSTVModes.ALL) modeBox.addItem(m.name);

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(chooseWavBtn);
            top.add(new JLabel("Mode:"));
            top.add(modeBox);
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

        private SSTVMode forcedMode() {
            int idx = modeBox.getSelectedIndex();
            return idx <= 0 ? null : SSTVModes.ALL.get(idx - 1);
        }

        private void chooseAndDecode() {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV audio", "wav"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File in = fc.getSelectedFile();
            SSTVMode forced = forcedMode();

            setBusy(true, "Decoding…");
            new SwingWorker<SSTVDecoder.Result, Void>() {
                @Override
                protected SSTVDecoder.Result doInBackground() throws Exception {
                    WavFile.WavData data = WavFile.read(in);
                    return SSTVDecoder.decode(data.samples, data.sampleRate, forced);
                }

                @Override
                protected void done() {
                    setBusy(false, null);
                    try {
                        SSTVDecoder.Result result = get();
                        decodedImage = result.image;
                        preview.setImage(decodedImage, result.mode.width, result.mode.height);
                        saveImageBtn.setEnabled(true);
                        if (result.visConfirmed) {
                            status.setText("Decoded as " + result.mode.name + " (confirmed by VIS header).");
                        } else if (forced != null) {
                            status.setText("Decoded as " + result.mode.name + " (forced) — "
                                    + "VIS header did not confirm this mode; result may be wrong.");
                        } else {
                            status.setText("Decoded as " + result.mode.name + " (default — no VIS header "
                                    + "found or its code wasn't recognized; try forcing a mode above if this looks wrong).");
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
            modeBox.setEnabled(!busy);
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

    /** Simple fixed-aspect image preview panel; aspect ratio can change (per selected mode). */
    private static class ImageView extends JPanel {
        private int aspectW = 320, aspectH = 256;
        private BufferedImage image;

        ImageView() {
            setBackground(Color.DARK_GRAY);
            setBorder(BorderFactory.createLoweredBevelBorder());
        }

        void setAspect(int w, int h) {
            this.aspectW = w;
            this.aspectH = h;
            repaint();
        }

        void setImage(BufferedImage img, int w, int h) {
            this.image = img;
            setAspect(w, h);
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
