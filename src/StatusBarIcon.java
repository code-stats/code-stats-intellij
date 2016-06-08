import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/**
 * Created by nicd on 03/06/16.
 */
public class StatusBarIcon implements StatusBarWidget {
    public static final String STATUS_BAR_ID_PREFIX = "code-stats-intellij-status-bar-icon-";

    private class StatusBarPresentation implements TextPresentation {

        private String text = "C::S";
        private String tooltipText = null;

        @NotNull
        @Override
        public String getText() {
            return text;
        }

        @NotNull
        @Override
        public String getMaxPossibleText() {
            return "C::S ERR!";
        }

        @Override
        public float getAlignment() {
            return 0;
        }

        @Nullable
        @Override
        public String getTooltipText() {
            return tooltipText;
        }

        @Nullable
        @Override
        public Consumer<MouseEvent> getClickConsumer() {
            return null;
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setToolTipText(String toolTipText) {
            this.tooltipText = toolTipText;
        }
    }

    private String ID;
    private StatusBarPresentation statusBarPresentation;
    private StatusBar statusBar;

    StatusBarIcon(final String ID, final StatusBar statusBar) {
        this.ID = STATUS_BAR_ID_PREFIX + ID;
        statusBarPresentation = new StatusBarPresentation();
        this.statusBar = statusBar;
    }

    @NotNull
    @Override
    public String ID() {
        return ID;
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation(@NotNull PlatformType type) {
        return statusBarPresentation;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        updateStatusBars();
    }

    @Override
    public void dispose() {
        statusBarPresentation = null;
    }

    public void setUpdating() {
        if (statusBarPresentation != null) {
            statusBarPresentation.setText("C::S…");
            statusBarPresentation.setToolTipText("Updating…");
            updateStatusBars();
        }
    }

    public void setError(final String error) {
        if (statusBarPresentation != null) {
            statusBarPresentation.setText("C::S ERR!");
            statusBarPresentation.setToolTipText("An error occurred:\n" + error);
            updateStatusBars();
        }
    }

    public void clear() {
        if (statusBarPresentation != null) {
            statusBarPresentation.setText("C::S");
            statusBarPresentation.setToolTipText(null);
            updateStatusBars();
        }
    }

    private void updateStatusBars() {
        // Trigger repaint on this icon's statusbar
        statusBar.updateWidget(ID);
    }
}
