package io.github.jsonhandle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class JsonEditorPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(JsonEditorPanel.class);
    private static final SimpleTextAttributes FIELD_NAME = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_BOLD, new JBColor(0x44AA00, 0x91CD65));
    private static final SimpleTextAttributes ARRAY_NAME = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_BOLD, new JBColor(0xEF5C85, 0xFF83A6));
    private static final SimpleTextAttributes ARRAY_INDEX = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xFB8DC4, 0xF3A6D2));
    private static final SimpleTextAttributes SEPARATOR = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xBDBDBD, 0x92969E));
    private static final SimpleTextAttributes STRING_VALUE = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xFF6633, 0xFF926D));
    private static final SimpleTextAttributes NUMBER_VALUE = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xCC00FF, 0xE087FF));
    private static final SimpleTextAttributes BOOLEAN_VALUE = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0x0099CC, 0x62C9F2));
    private static final SimpleTextAttributes NULL_VALUE = new SimpleTextAttributes(
        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xAAAA00, 0xD5D464));
    private static final DefaultHighlighter.DefaultHighlightPainter MATCH_PAINTER =
        new DefaultHighlighter.DefaultHighlightPainter(new JBColor(0xFFEB95, 0x695221));
    private static final DefaultHighlighter.DefaultHighlightPainter ACTIVE_MATCH_PAINTER =
        new DefaultHighlighter.DefaultHighlightPainter(new JBColor(0xFF9C9C, 0x962E36));

    private final Project project;
    final JBTextArea source = new JBTextArea();
    private final Tree tree = new Tree();
    private final SearchTextField search = new SearchTextField(false);
    private final JPanel searchRow = new JPanel(new BorderLayout(JBUI.scale(6), 0));
    private final JBLabel searchStatus = new JBLabel();
    private final JBLabel status = new JBLabel(" ");
    private final JButton format = new JButton("Format");
    private final JButton minify = new JButton("Minify");
    private final JButton edit = new JButton("Edit Value…");
    private final Timer refresh = new Timer(250, event -> refreshTree());
    private final Timer refreshSearch = new Timer(180, event -> updateSearch());
    private final List<Object> searchHighlights = new ArrayList<>();
    private List<JsonSourceSearch.Range> searchRanges = List.of();
    private int activeMatch;
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
        tree.setCellRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                              boolean expanded, boolean leaf, int row, boolean focused) {
                if (!(value instanceof JsonDocument.Entry entry)) {
                    return;
                }
                String name = entry.name == null ? "$" : entry.name.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
                setIcon(UIManager.getIcon(leaf ? "Tree.leafIcon" : expanded ? "Tree.openIcon" : "Tree.closedIcon"));
                var kind = entry.value.kind();
                var nameStyle = switch (kind) {
                    case OBJECT -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
                    case ARRAY -> ARRAY_NAME;
                    case STRING, NUMBER, BOOLEAN, NULL -> FIELD_NAME;
                };
                if (entry.name == null) {
                    nameStyle = SimpleTextAttributes.REGULAR_ATTRIBUTES;
                } else if (entry.arrayItem) {
                    nameStyle = ARRAY_INDEX;
                }
                var valueStyle = switch (kind) {
                    case OBJECT, ARRAY -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
                    case STRING -> STRING_VALUE;
                    case NUMBER -> NUMBER_VALUE;
                    case BOOLEAN -> BOOLEAN_VALUE;
                    case NULL -> NULL_VALUE;
                };
                var separatorStyle = SEPARATOR;
                // Use the IDE's selection foreground for every fragment against the selection highlight.
                if (selected) {
                    var foreground = UIUtil.getTreeSelectionForeground(focused);
                    nameStyle = new SimpleTextAttributes(nameStyle.getStyle(), foreground);
                    valueStyle = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground);
                    separatorStyle = valueStyle;
                }
                append(name, nameStyle);
                append(": ", separatorStyle);
                append(entry.value.summary(), valueStyle);
            }
        });
        tree.addTreeSelectionListener(event -> updateActions());
        // Replace the tree's generic export, which would use Entry.toString() for Swing copy.
        tree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent component) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent component) {
                return selectedKeyTransfer();
            }
        });
        int menuModifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        DumbAwareAction.create("Copy Key", event -> copySelectedKey())
            .registerCustomShortcutSet(editorShortcut(KeyEvent.VK_C, menuModifier), tree, this);
        DumbAwareAction.create("Find in JSON", event -> showSearch())
            .registerCustomShortcutSet(editorShortcut(KeyEvent.VK_F, menuModifier), this, this);
        DumbAwareAction.create("Close JSON Search", event -> hideSearch())
            .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)),
                search.getTextEditor(), this);


        format.addActionListener(event -> applyFormat(true));
        minify.addActionListener(event -> applyFormat(false));
        edit.addActionListener(event -> editSelectedValue());
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        toolbar.add(format);
        toolbar.add(minify);
        toolbar.add(edit);

        search.getTextEditor().getEmptyText().setText("Search keys or values");
        search.getTextEditor().getAccessibleContext().setAccessibleName("Search keys or values");
        search.getTextEditor().addActionListener(event -> selectNextMatch());
        searchRow.setVisible(false);
        searchRow.setBorder(JBUI.Borders.empty(2, 4, 4, 4));
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(searchStatus, BorderLayout.EAST);
        var controls = new JPanel(new BorderLayout());
        controls.add(toolbar, BorderLayout.NORTH);
        controls.add(searchRow, BorderLayout.SOUTH);

        var split = new OnePixelSplitter(true, 0.46f);
        split.setFirstComponent(new JBScrollPane(source));
        split.setSecondComponent(new JBScrollPane(tree));
        add(controls, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        refresh.setRepeats(false);
        refreshSearch.setRepeats(false);
        search.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                if (search.getText().isEmpty()) {
                    refreshSearch.stop();
                    updateSearch();
                } else {
                    refreshSearch.restart();
                }
            }
        });
        source.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                parsed = null;
                updateActions();
                refreshSearch.stop();
                clearSearchHighlights();
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
        refreshSearch.stop();
        updateSearch();
    }

    private void updateActions() {
        format.setEnabled(parsed != null);
        minify.setEnabled(parsed != null);
        var selected = selectedEntry();
        edit.setEnabled(parsed != null && selected != null);
    }

    private JsonDocument.Entry selectedEntry() {
        return tree.getLastSelectedPathComponent() instanceof JsonDocument.Entry entry ? entry : null;
    }

    private Transferable selectedKeyTransfer() {
        var selected = selectedEntry();
        return parsed != null && selected != null && selected.hasKey() ? new StringSelection(selected.name) : null;
    }

    private void copySelectedKey() {
        var key = selectedKeyTransfer();
        if (key != null) {
            // Only an explicit copy puts the original key in the IDE clipboard and history.
            CopyPasteManager.getInstance().setContents(key);
            status.setText("Key copied");
        }
    }

    private static CustomShortcutSet editorShortcut(int key, int menuModifier) {
        var control = new KeyboardShortcut(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), null);
        if (menuModifier == InputEvent.CTRL_DOWN_MASK) {
            return new CustomShortcutSet(control);
        }
        return new CustomShortcutSet(control, new KeyboardShortcut(KeyStroke.getKeyStroke(key, menuModifier), null));
    }

    private void showSearch() {
        searchRow.setVisible(true);
        search.getTextEditor().requestFocusInWindow();
        search.getTextEditor().selectAll();
        updateSearch();
    }

    private void hideSearch() {
        searchRow.setVisible(false);
        refreshSearch.stop();
        search.setText("");
        clearSearchHighlights();
        source.requestFocusInWindow();
    }

    private void clearSearchHighlights() {
        source.getHighlighter().removeAllHighlights();
        searchHighlights.clear();
        searchRanges = List.of();
        activeMatch = 0;
        searchStatus.setText("");
    }

    private void updateSearch() {
        refreshSearch.stop();
        clearSearchHighlights();
        String query = search.getText();
        if (!searchRow.isVisible() || parsed == null || query.isEmpty()) {
            return;
        }
        searchRanges = JsonSourceSearch.find(source.getText(), query);
        if (searchRanges.isEmpty()) {
            searchStatus.setText("0 matches");
            return;
        }
        try {
            for (int index = 0; index < searchRanges.size(); index++) {
                searchHighlights.add(addSearchHighlight(index));
            }
            revealActiveMatch();
        } catch (BadLocationException error) {
            clearSearchHighlights();
            LOG.warn("Search offsets did not match the JSON source document.", error);
        }
    }

    private void selectNextMatch() {
        if (refreshSearch.isRunning()) {
            updateSearch();
        }
        if (searchRanges.size() < 2) {
            return;
        }
        int previous = activeMatch;
        activeMatch = (activeMatch + 1) % searchRanges.size();
        try {
            for (int index : new int[]{previous, activeMatch}) {
                Object replacement = addSearchHighlight(index);
                source.getHighlighter().removeHighlight(searchHighlights.set(index, replacement));
            }
            revealActiveMatch();
        } catch (BadLocationException error) {
            clearSearchHighlights();
            LOG.warn("Could not select the next JSON search match.", error);
        }
    }

    private Object addSearchHighlight(int index) throws BadLocationException {
        var range = searchRanges.get(index);
        var painter = index == activeMatch ? ACTIVE_MATCH_PAINTER : MATCH_PAINTER;
        return source.getHighlighter().addHighlight(range.start(), range.end(), painter);
    }

    private void revealActiveMatch() throws BadLocationException {
        var rectangle = source.modelToView2D(searchRanges.get(activeMatch).start());
        if (rectangle != null) {
            source.scrollRectToVisible(rectangle.getBounds());
        }
        searchStatus.setText((activeMatch + 1) + " / " + searchRanges.size());
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
        siblings.set(index, new JsonDocument.Entry(current.name, dialog.replacement, current.arrayItem));
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
        refreshSearch.stop();
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
            root = new JsonDocument.Entry(null, value, false);
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
