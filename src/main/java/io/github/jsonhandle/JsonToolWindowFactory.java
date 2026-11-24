package io.github.jsonhandle;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class JsonToolWindowFactory implements ToolWindowFactory, DumbAware {
    static final String ID = "JSON Handle";

    /** Creates the project-local JSON editor and converter when the tool window first opens. */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        addEditor(project, toolWindow, "JSON", "", false);
        var converter = new CodecPanel();
        var content = ContentFactory.getInstance().createContent(converter, "Encode / Decode", false);
        content.setPreferredFocusableComponent(converter.input);
        content.setCloseable(false);
        toolWindow.getContentManager().addContent(content);
    }

    static void addEditor(Project project, ToolWindow toolWindow, String title, String text, boolean closeable) {
        var editor = new JsonEditorPanel(project, text);
        var content = ContentFactory.getInstance().createContent(editor, title, false);
        content.setPreferredFocusableComponent(editor.source);
        content.setDisposer(editor);
        content.setCloseable(closeable);
        var manager = toolWindow.getContentManager();
        manager.addContent(content);
        manager.setSelectedContent(content);
    }
}
