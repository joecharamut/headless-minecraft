package rocks.spaghetti.headlessmc.client.swing.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import net.minecraft.server.ServerMetadata;
import org.apache.commons.codec.binary.Base64;
import rocks.spaghetti.headlessmc.Util;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.function.Consumer;

@SuppressWarnings({"java:S1171", "java:S100"})
public class ClientMainWindow {
    private JPanel root;
    private JTabbedPane mainTabbedPane;
    private JTextPane consoleMessagePane;
    private JTextField consoleInputField;
    private JButton consoleSubmitButton;
    private JScrollPane consoleScrollPane;
    private JTextField connectionAddressField;
    private JButton connectionQueryButton;
    private JButton connectionConnectButton;
    private JLabel queryIcon;
    private JLabel queryMotd;
    private JLabel queryPlayerCount;
    private JLabel queryVersion;

    private JFrame mainFrame;
    private int consoleScrollMax;

    private Consumer<String> consoleInputCallback = null;
    private Consumer<String> queryButtonCallback = null;
    private Consumer<String> connectButtonCallback = null;

    public ClientMainWindow() {
        mainFrame = new JFrame("ClientMainWindow");
        mainFrame.setContentPane(root);
        mainFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        setupConnectionTab();
        setupConsoleTab();

        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private void setupConnectionTab() {
        connectionQueryButton.addActionListener(e -> {
            String address = connectionAddressField.getText();
            if (!address.isEmpty() && queryButtonCallback != null) {
                queryButtonCallback.accept(address);
            }
        });

        connectionConnectButton.addActionListener(e -> {
            String address = connectionAddressField.getText();
            if (!address.isEmpty() && connectButtonCallback != null) {
                connectButtonCallback.accept(address);
            }
        });

        queryIcon.setIcon(new ImageIcon(Util.getResourceAsBytes("minecraft:textures/misc/unknown_server.png")));
    }

    private void setupConsoleTab() {
        ActionListener submitCommand = (e -> {
            String text = consoleInputField.getText();
            if (!text.isEmpty()) {
                consoleInputField.setText("");
                consolePrintln("> " + text, null);

                if (consoleInputCallback != null) {
                    consoleInputCallback.accept(text);
                }
            }
        });
        consoleSubmitButton.addActionListener(submitCommand);
        consoleInputField.addActionListener(submitCommand);

        consoleScrollMax = consoleScrollPane.getVerticalScrollBar().getMaximum();
        consoleScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (consoleScrollMax < e.getAdjustable().getMaximum()) {
                e.getAdjustable().setValue(e.getAdjustable().getMaximum());
                consoleScrollMax = e.getAdjustable().getMaximum();
            }
        });
    }

    public void consolePrintln(String message, Color color) {
        consoleAppend(message + "\n", color);
    }

    public void consoleAppend(String message, Color foregroundColor) {
        StyleContext style = StyleContext.getDefaultStyleContext();
        AttributeSet attrs = SimpleAttributeSet.EMPTY;

        if (foregroundColor != null) {
            attrs = style.addAttribute(attrs, StyleConstants.Foreground, foregroundColor);
        } else {
            attrs = style.addAttribute(attrs, StyleConstants.Foreground, Color.BLACK);
        }

        int len = consoleMessagePane.getDocument().getLength();
        consoleMessagePane.setCaretPosition(len);
        consoleMessagePane.setCharacterAttributes(attrs, false);

        consoleMessagePane.setEditable(true);
        consoleMessagePane.replaceSelection(message);
        consoleMessagePane.setEditable(false);
    }

    public void onConsoleInput(Consumer<String> callback) {
        consoleInputCallback = callback;
    }

    public void onQueryButton(Consumer<String> callback) {
        queryButtonCallback = callback;
    }

    public void setQueryInfo(ServerMetadata metadata) {
        if (metadata.getFavicon() != null) {
            queryIcon.setIcon(new ImageIcon(Base64.decodeBase64(metadata.getFavicon().split(",")[1])));
        } else {
            queryIcon.setIcon(new ImageIcon(Util.getResourceAsBytes("minecraft:textures/misc/unknown_server.png")));
        }

        queryMotd.setText("MOTD: " + metadata.getDescription().getString());
        queryVersion.setText("Version: " + metadata.getVersion().getGameVersion());
        queryPlayerCount.setText("Online Players: " + metadata.getPlayers().getOnlinePlayerCount() + " / " + metadata.getPlayers().getPlayerLimit());

        mainFrame.pack();
    }

    public void onConnectButton(Consumer<String> callback) {
        connectButtonCallback = callback;
    }

    public void setTab(String tabName) {
        mainTabbedPane.setSelectedIndex(mainTabbedPane.indexOfTab(tabName));
    }

    public void onClose(Runnable callback) {
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                callback.run();
            }
        });
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        root = new JPanel();
        root.setLayout(new GridLayoutManager(1, 1, new Insets(4, 4, 4, 4), -1, -1));
        mainTabbedPane = new JTabbedPane();
        root.add(mainTabbedPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainTabbedPane.addTab("Connection", panel1);
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, Font.BOLD, -1, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setText("Connect to Server");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Server Address");
        panel2.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        connectionAddressField = new JTextField();
        panel2.add(connectionAddressField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        connectionQueryButton = new JButton();
        connectionQueryButton.setText("Query");
        panel2.add(connectionQueryButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        connectionConnectButton = new JButton();
        connectionConnectButton.setText("Connect");
        panel2.add(connectionConnectButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Query Info", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        queryIcon = new JLabel();
        queryIcon.setEnabled(true);
        queryIcon.setText("");
        panel3.add(queryIcon, new GridConstraints(0, 0, 3, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(64, 64), new Dimension(64, 64), new Dimension(64, 64), 0, false));
        final Spacer spacer1 = new Spacer();
        panel3.add(spacer1, new GridConstraints(0, 2, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        queryMotd = new JLabel();
        queryMotd.setText("<unknown motd>");
        panel3.add(queryMotd, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        queryVersion = new JLabel();
        queryVersion.setText("<unknown version>");
        panel3.add(queryVersion, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        queryPlayerCount = new JLabel();
        queryPlayerCount.setText("<unknown playercount>");
        panel3.add(queryPlayerCount, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainTabbedPane.addTab("Console", panel4);
        consoleScrollPane = new JScrollPane();
        panel4.add(consoleScrollPane, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        consoleMessagePane = new JTextPane();
        consoleMessagePane.setEditable(false);
        consoleMessagePane.setEnabled(true);
        consoleScrollPane.setViewportView(consoleMessagePane);
        consoleInputField = new JTextField();
        panel4.add(consoleInputField, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        consoleSubmitButton = new JButton();
        consoleSubmitButton.setText("Submit");
        panel4.add(consoleSubmitButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return root;
    }

}
