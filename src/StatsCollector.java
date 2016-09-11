import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.*;
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

    private Integer statusBarUniqueID;
    private Hashtable<Project, StatusBarIcon> statusBarIcons;

    public StatsCollector() {
        propertiesComponent = PropertiesComponent.getInstance();
        statusBarIcons = new Hashtable<>();
        statusBarUniqueID = 0;
    }

    @Override
    public void initComponent() {
        apiKey = propertiesComponent.getValue(SettingsForm.API_KEY_NAME);
        apiURL = propertiesComponent.getValue(SettingsForm.API_URL_NAME);

        executor = Executors.newScheduledThreadPool(1);
        xps = new Hashtable<>();

        // Add the status bar icon to the statusbar of all projects when they are loaded
        ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
            @Override
            public void projectOpened(Project project) {
                IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
                StatusBar statusBar = frame.getStatusBar();

                StatusBarIcon statusBarIcon = new StatusBarIcon(statusBarUniqueID.toString(), statusBar);

                statusBar.addWidget(statusBarIcon);
                statusBarIcons.put(project, statusBarIcon);
                statusBarUniqueID += 1;
            }

            @Override
            public boolean canCloseProject(Project project) {
                return true;
            }

            @Override
            public void projectClosed(Project project) {}

            @Override
            public void projectClosing(Project project) {
                IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
                frame.getStatusBar().removeWidget(statusBarIcons.get(project).ID());
                statusBarIcons.remove(project);
            }
        });

        // Set up keyhandler that will send us keypresses
        final EditorActionManager actionManager = EditorActionManager.getInstance();
        final TypedAction typedAction = actionManager.getTypedAction();
        final TypedActionHandler oldHandler = typedAction.getHandler();

        final TypingHandler handler = new TypingHandler();
        handler.setOldTypingHandler(oldHandler);
        handler.setStatsCollector(this);
        typedAction.setupHandler(handler);

        installLECACert();
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
        task.setStatusBarIcons(statusBarIcons);
        updateTimer = executor.schedule(task, UPDATE_TIMER, TimeUnit.SECONDS);
    }

    public void setApiConfig(final String apiURL, final String apiKey) {
        this.apiURL = apiURL;
        this.apiKey = apiKey;
    }

    // Code::Stats is using a Let's Encrypt certificate which Java does not trust by default.
    // We need to add the bundled LE root CA certificate to the trust store before we can send any calls to the server.
    // See: http://stackoverflow.com/a/34111150

    // Both ISRG root X1 and DST root X3 are added, because we might not know which one has signed our LE certificate in
    // the future.
    private void installLECACert() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path ksPath = Paths.get(System.getProperty("java.home"),
                    "lib", "security", "cacerts");
            keyStore.load(Files.newInputStream(ksPath),
                    "changeit".toCharArray());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream caInput = new BufferedInputStream(
                    StatsCollector.class.getResourceAsStream("dstrootx3real.der"))) {
                Certificate crt = cf.generateCertificate(caInput);
                System.out.println("Added Cert for " + ((X509Certificate) crt)
                        .getSubjectDN());

                keyStore.setCertificateEntry("DST root CA X3", crt);
            }

            try (InputStream caInput = new BufferedInputStream(
                    StatsCollector.class.getResourceAsStream("isrgrootx1.der"))) {
                Certificate crt = cf.generateCertificate(caInput);
                System.out.println("Added Cert for " + ((X509Certificate) crt)
                        .getSubjectDN());

                keyStore.setCertificateEntry("ISRG root X1", crt);
            }

            System.out.println("Truststore now trusting: ");
            PKIXParameters params = new PKIXParameters(keyStore);
            params.getTrustAnchors().stream()
                    .map(TrustAnchor::getTrustedCert)
                    .map(X509Certificate::getSubjectDN)
                    .forEach(System.out::println);
            System.out.println();

            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            SSLContext.setDefault(sslContext);

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
