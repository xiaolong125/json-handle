package io.github.jsonhandle;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class OpenJsonAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var editor = event.getData(CommonDataKeys.EDITOR);
        var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean jsonFile = file != null && "json".equalsIgnoreCase(file.getExtension());
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && editor != null
            && (editor.getSelectionModel().hasSelection() || jsonFile));
    }

    /** Opens a separate tab so work already in the tool window and the source document are preserved. */
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(JsonToolWindowFactory.ID);
        if (toolWindow == null) {
            return;
        }
        String selected = editor.getSelectionModel().getSelectedText();
        var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        String title = selected != null || file == null ? "Selection" : file.getName();
        String text = selected != null ? selected : editor.getDocument().getText();
        toolWindow.activate(() -> JsonToolWindowFactory.addEditor(project, toolWindow, title, text, true));
    }
}
