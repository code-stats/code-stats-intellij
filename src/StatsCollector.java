import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Hashtable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class StatsCollector implements ApplicationComponent {
    /**
     * How long to wait before sending an update.
     */
    private final long UPDATE_TIMER = 10;

    // Is this bad? I have no idea. This is for the xps synchronization
    private final Object xps_lock = new Object();

    private final PropertiesComponent propertiesComponent;

    private Hashtable<String, Integer> xps;
    private ScheduledFuture updateTimer;
    private ScheduledExecutorService executor;

    private String apiURL;
    private String apiKey;

    public StatsCollector() {
        propertiesComponent = PropertiesComponent.getInstance();
        apiKey = propertiesComponent.getValue(SettingsForm.API_KEY_NAME);
        apiURL = propertiesComponent.getValue(SettingsForm.API_URL_NAME);

        executor = Executors.newScheduledThreadPool(1);
        xps = new Hashtable<>();

        final EditorActionManager actionManager = EditorActionManager.getInstance();
        final TypedAction typedAction = actionManager.getTypedAction();
        final TypedActionHandler oldHandler = typedAction.getHandler();

        final TypingHandler handler = new TypingHandler();
        handler.setOldTypingHandler(oldHandler);
        handler.setStatsCollector(this);
        typedAction.setupHandler(handler);
    }

    @Override
    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    @Override
    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "StatsCollector";
    }

    public void handleKeyEvent(Language language) {

        if (apiKey == null) {
            // Don't collect data without an API key
            return;
        }

        final String languageName = language.getDisplayName();

        synchronized (xps_lock) {
            if (xps.containsKey(languageName)) {
                xps.put(languageName, xps.get(languageName) + 1);
            } else {
                xps.put(languageName, 1);
            }
        }

        // If timer is already running, cancel it to prevent updates when typing
        if (updateTimer != null && !updateTimer.isCancelled()) {
            updateTimer.cancel(false);
        }

        UpdateTask task = new UpdateTask();
        task.setXpsLock(xps_lock);
        task.setXps(xps);
        task.setConfig(apiURL, apiKey);
        updateTimer = executor.schedule(task, UPDATE_TIMER, TimeUnit.SECONDS);
    }

    public void setApiConfig(final String apiURL, final String apiKey) {
        this.apiURL = apiURL;
        this.apiKey = apiKey;
    }
}
