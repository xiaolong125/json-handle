package io.github.jsonhandle;

import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.JBUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.charset.CharacterCodingException;

final class CodecPanel extends JPanel {
    final JBTextArea input = new JBTextArea();
    private final JBTextArea output = new JBTextArea();
    private final ComboBox<TextCodec> codec = new ComboBox<>(TextCodec.values());
    private final JBLabel status = new JBLabel(" ");

    CodecPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setEditable(false);

        var encode = new JButton("Encode");
        encode.addActionListener(event -> convert(true));
        var decode = new JButton("Decode");
        decode.addActionListener(event -> convert(false));
        var reuse = new JButton("Use Output as Input");
        reuse.addActionListener(event -> input.setText(output.getText()));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        codec.getAccessibleContext().setAccessibleName("Encoding");
        toolbar.add(codec);
        toolbar.add(encode);
        toolbar.add(decode);
        toolbar.add(reuse);

        var split = new OnePixelSplitter(true, 0.5f);
        split.setFirstComponent(textPane("Input", input));
        split.setSecondComponent(textPane("Output", output));
        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private static JPanel textPane(String label, JBTextArea area) {
        var pane = new JPanel(new BorderLayout());
        var title = new JBLabel(label);
        title.setLabelFor(area);
        pane.add(title, BorderLayout.NORTH);
        pane.add(new JBScrollPane(area), BorderLayout.CENTER);
        return pane;
    }

    private void convert(boolean encode) {
        var selected = (TextCodec) codec.getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            String text = input.getText();
            output.setText(encode ? selected.encode(text) : selected.decode(text));
            output.setCaretPosition(0);
            status.setText(" ");
        } catch (CharacterCodingException error) {
            output.setText("");
            status.setText("Input contains invalid Unicode or UTF-8.");
        } catch (IllegalArgumentException error) {
            output.setText("");
            status.setText(error.getMessage());
        }
    }
}
