import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;


public class TypingHandler implements TypedActionHandler {
    private TypedActionHandler oldTypingHandler;
    private StatsCollector statsCollector;

    @Override
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
        final Document document = editor.getDocument();
        final Project project = editor.getProject();
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);

        if (project != null && file != null) {
            final PsiManager psiManager = PsiManager.getInstance(project);

            final FileViewProvider fileProvider = psiManager.findViewProvider(file);

            if (fileProvider != null) {
                Language language = fileProvider.getBaseLanguage();
                statsCollector.handleKeyEvent(language);
            }
        }

        // Call old typing handler to do the typing
        oldTypingHandler.execute(editor, charTyped, dataContext);
    }

    public void setOldTypingHandler(TypedActionHandler oldTypingHandler) {
        this.oldTypingHandler = oldTypingHandler;
    }

    public void setStatsCollector(StatsCollector statsCollector) {
        this.statsCollector = statsCollector;
    }
}
