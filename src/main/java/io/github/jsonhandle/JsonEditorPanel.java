package io.github.jsonhandle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;

final class JsonEditorPanel extends JPanel implements Disposable {
    private static final JBColor OBJECT_COLOR = new JBColor(0x2456A6, 0x81AFFF);
    private static final JBColor ARRAY_COLOR = new JBColor(0x7B3EA7, 0xD4A0FA);
    private static final JBColor STRING_COLOR = new JBColor(0x28743F, 0x91CD85);
    private static final JBColor NUMBER_COLOR = new JBColor(0x007076, 0x75CBD0);
    private static final JBColor BOOLEAN_COLOR = new JBColor(0x9B4900, 0xF5AD68);
    private static final JBColor NULL_COLOR = new JBColor(0x626872, 0xA8AFBA);

    private final Project project;
    final JBTextArea source = new JBTextArea();
    private final Tree tree = new Tree();
    private final JBLabel status = new JBLabel(" ");
    private final JButton format = new JButton("Format");
    private final JButton minify = new JButton("Minify");
    private final JButton edit = new JButton("Edit Value…");
    private final Timer refresh = new Timer(250, event -> refreshTree());
    private JsonDocument.Value parsed;

    JsonEditorPanel(Project project, String initialText) {
        super(new BorderLayout());
        this.project = project;
        setBorder(JBUI.Borders.empty(8));
        source.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
        source.getAccessibleContext().setAccessibleName("JSON source");
        tree.getAccessibleContext().setAccessibleName("JSON tree");
        tree.getEmptyText().setText("Paste or type JSON to see its structure");
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setModel(null);
        tree.setLargeModel(true);
        tree.setRowHeight(JBUI.scale(22));
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(javax.swing.JTree tree, Object value,
                                                          boolean selected, boolean expanded, boolean leaf,
                                                          int row, boolean focused) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
                putClientProperty("html.disable", true);
                var entry = (JsonDocument.Entry) value;
                String name = entry.name == null ? "$" : entry.name.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
                setText(name + "  " + entry.value.summary());
                // Let the IDE choose the foreground against its selection highlight.
                if (!selected) {
                    setForeground(switch (entry.value.kind()) {
                        case OBJECT -> OBJECT_COLOR;
                        case ARRAY -> ARRAY_COLOR;
                        case STRING -> STRING_COLOR;
                        case NUMBER -> NUMBER_COLOR;
                        case BOOLEAN -> BOOLEAN_COLOR;
                        case NULL -> NULL_COLOR;
                    });
                }
                return this;
            }
        });
        tree.addTreeSelectionListener(event -> edit.setEnabled(parsed != null && tree.getSelectionPath() != null));

        format.addActionListener(event -> applyFormat(true));
        minify.addActionListener(event -> applyFormat(false));
        edit.addActionListener(event -> editSelectedValue());
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        toolbar.add(format);
        toolbar.add(minify);
        toolbar.add(edit);

        var split = new OnePixelSplitter(true, 0.46f);
        split.setFirstComponent(new JBScrollPane(source));
        split.setSecondComponent(new JBScrollPane(tree));
        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        refresh.setRepeats(false);
        source.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                parsed = null;
                updateActions();
                refresh.restart();
            }
        });
        setSource(initialText);
    }

    private void setSource(String text) {
        source.setText(text);
        source.setCaretPosition(0);
        refresh.stop();
        refreshTree();
    }

    private void refreshTree() {
        String text = source.getText();
        try {
            parsed = text.isBlank() ? null : JsonDocument.parse(text);
            status.setText(parsed == null ? " " : "Valid JSON");
        } catch (IOException error) {
            parsed = null;
            status.setText(error.getMessage());
        }
        tree.setModel(parsed == null ? null : new JsonTreeModel(parsed));
        updateActions();
    }

    private void updateActions() {
        format.setEnabled(parsed != null);
        minify.setEnabled(parsed != null);
        edit.setEnabled(parsed != null && tree.getSelectionPath() != null);
    }

    private void applyFormat(boolean pretty) {
        if (parsed == null) {
            return;
        }
        String text = JsonDocument.render(parsed, pretty);
        if (text.length() > JsonDocument.MAX_INPUT_LENGTH) {
            status.setText("Formatted JSON exceeds the editor limit.");
            return;
        }
        setSource(text);
    }

    private void editSelectedValue() {
        TreePath path = tree.getSelectionPath();
        if (parsed == null || path == null) {
            return;
        }
        var current = (JsonDocument.Entry) path.getLastPathComponent();
        var dialog = new ValueDialog(project, current.value);
        if (!dialog.showAndGet()) {
            return;
        }
        TreePath parentPath = path.getParentPath();
        if (parentPath == null) {
            setSource(dialog.input.getText());
            return;
        }
        var parent = (JsonDocument.Entry) parentPath.getLastPathComponent();
        var siblings = parent.value.children();
        int index = siblings.indexOf(current);
        siblings.set(index, new JsonDocument.Entry(current.name, dialog.replacement));
        String text;
        try {
            text = JsonDocument.render(parsed, true);
            if (text.length() > JsonDocument.MAX_INPUT_LENGTH) {
                text = JsonDocument.render(parsed, false);
            }
            // A replacement can exceed the size or nesting limit at its new position.
            JsonDocument.parse(text);
        } catch (IOException error) {
            siblings.set(index, current);
            status.setText(error.getMessage());
            return;
        }
        setSource(text);
    }

    @Override
    public void dispose() {
        refresh.stop();
    }

    private static final class ValueDialog extends DialogWrapper {
        final JBTextArea input = new JBTextArea(10, 46);
        private final JBLabel error = new JBLabel(" ");
        JsonDocument.Value replacement;

        ValueDialog(Project project, JsonDocument.Value value) {
            super(project);
            setTitle("Edit JSON Value");
            input.getAccessibleContext().setAccessibleName("Replacement JSON value");
            String formatted = JsonDocument.render(value, true);
            input.setText(formatted.length() <= JsonDocument.MAX_INPUT_LENGTH
                ? formatted : JsonDocument.render(value, false));
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            var content = new JPanel(new BorderLayout());
            content.add(new JBScrollPane(input), BorderLayout.CENTER);
            content.add(error, BorderLayout.SOUTH);
            return content;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return input;
        }

        @Override
        protected void doOKAction() {
            try {
                replacement = JsonDocument.parse(input.getText());
            } catch (IOException failure) {
                error.setText(failure.getMessage());
                return;
            }
            super.doOKAction();
        }
    }

    /** Reads the parsed entries directly so collapsed subtrees need no separate Swing nodes. */
    private static final class JsonTreeModel implements TreeModel {
        private final JsonDocument.Entry root;

        JsonTreeModel(JsonDocument.Value value) {
            root = new JsonDocument.Entry(null, value);
        }

        @Override
        public Object getRoot() {
            return root;
        }

        @Override
        public Object getChild(Object parent, int index) {
            return ((JsonDocument.Entry) parent).value.children().get(index);
        }

        @Override
        public int getChildCount(Object parent) {
            return ((JsonDocument.Entry) parent).value.children().size();
        }

        @Override
        public boolean isLeaf(Object node) {
            return getChildCount(node) == 0;
        }

        @Override
        public int getIndexOfChild(Object parent, Object child) {
            return ((JsonDocument.Entry) parent).value.children().indexOf(child);
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue) {}

        @Override
        public void addTreeModelListener(TreeModelListener listener) {}

        @Override
        public void removeTreeModelListener(TreeModelListener listener) {}
    }
}
