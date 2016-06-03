import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by nicd on 02/06/16.
 */
public class SettingsForm implements Configurable {
    public static String CONFIG_PREFIX = "code-stats-intellij-";
    public static String API_KEY_NAME = CONFIG_PREFIX + "api-key";
    public static String API_URL_NAME = CONFIG_PREFIX + "api-url";

    public static String DEFAULT_API_URL = "https://codestats.net/api/my/pulses";

    private JPanel ui;
    private JTextField apiKey;
    private JTextField apiURL;
    private String persistedApiKey;
    private String persistedApiURL;
    private PropertiesComponent propertiesComponent;

    @Nls
    @Override
    public String getDisplayName() {
        return "Code::Stats";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        propertiesComponent = PropertiesComponent.getInstance();

        persistedApiKey = propertiesComponent.getValue(API_KEY_NAME);
        persistedApiURL = propertiesComponent.getValue(API_URL_NAME, DEFAULT_API_URL);

        if (persistedApiURL.equals("")) {
            persistedApiURL = DEFAULT_API_URL;
        }

        SwingUtilities.invokeLater(() -> {
            apiKey.setText(persistedApiKey);
            apiURL.setText(persistedApiURL);
        });

        return ui;
    }

    @Override
    public boolean isModified() {
        return !apiKey.getText().equals(persistedApiKey) || !apiURL.getText().equals(persistedApiURL);
    }

    @Override
    public void apply() throws ConfigurationException {
        persistedApiKey = apiKey.getText();
        persistedApiURL = apiURL.getText();

        propertiesComponent.setValue(API_KEY_NAME, persistedApiKey);
        propertiesComponent.setValue(API_URL_NAME, persistedApiURL);

        StatsCollector statsCollector = ApplicationManager.getApplication().getComponent(StatsCollector.class);
        statsCollector.setApiConfig(persistedApiURL, persistedApiKey);
    }

    @Override
    public void reset() {
        apiKey.setText(persistedApiKey);
        apiURL.setText(persistedApiURL);
    }

    @Override
    public void disposeUIResources() {

    }
}
