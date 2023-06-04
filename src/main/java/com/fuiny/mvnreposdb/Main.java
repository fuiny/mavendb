package com.fuiny.mvnreposdb;

import com.fuiny.mvnreposdb.entity.Artifactinfo;
import com.google.gson.Gson;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.persistence.config.HintValues;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.config.QueryHints;

/**
 * Maven Indexer main class.
 *
 * @see <a href="https://github.com/apache/maven-indexer">Apache Maven
 * Indexer</a>
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static final String CONFIG_FILE = "config.properties";
    private static final String ENTITY_MANAGER_FACTORY = "PUMvn";
    private static final String REPOS_NAME = "repository";
    private static final String REPOS_URL = "repositoryUrl";
    private static final Gson JSON = new Gson();
    private static final int STORE_PACKAGE_SIZE = 10000;

    private static final String OPTION_REPOSNAME_LONGOPT = "reposname";
    private static final Option OPTION_RESPOSNAME = new Option("r", OPTION_REPOSNAME_LONGOPT, true, "Repos name to scan, like central, spring; the name will match to the config file at etc/repos-<the name>.properties. Example values: central, spring");
    private static final Option OPTION_HELP = new Option("h", "help", false, "Printout help information");
    private static final Options CMD_OPTIONS = new Options();

    private final EntityManagerFactory emf;
    private final Properties repos;

    private Properties config;
    private IndexingContext centralContext;
    private List<Artifactinfo> dbList = new ArrayList<>();

    static {
        CMD_OPTIONS.addOption(OPTION_RESPOSNAME);
        CMD_OPTIONS.addOption(OPTION_HELP);
    }

    private Main(Properties reposProp) throws NoSuchFieldException, IOException {
        this.repos = reposProp;
        this.emf = Persistence.createEntityManagerFactory(ENTITY_MANAGER_FACTORY, this.loadConfig());
    }

    private static String getEtcDir() {
        File baseDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        return baseDir.getParent() + File.separator + "etc" + File.separator;
    }

    private Properties loadConfig() throws IOException {
        if (this.config == null) {
            // Get the config file name
            String configFileName = getEtcDir() + CONFIG_FILE;

            // Load the Config values
            Properties configValues = new Properties();
            try (BufferedReader br = new BufferedReader(new FileReader(configFileName, StandardCharsets.UTF_8))) {
                configValues.load(br);
            }

            // Set Properties
            this.config = new Properties();
            this.config.setProperty(PersistenceUnitProperties.JDBC_URL, configValues.getProperty(PersistenceUnitProperties.JDBC_URL));
            this.config.setProperty(PersistenceUnitProperties.JDBC_USER, configValues.getProperty(PersistenceUnitProperties.JDBC_USER));
            this.config.setProperty(PersistenceUnitProperties.JDBC_PASSWORD, configValues.getProperty(PersistenceUnitProperties.JDBC_PASSWORD));
        }

        return this.config;
    }

    /**
     * Entrance of the application.
     *
     * @param args the command line arguments
     * @throws NoSuchFieldException Coding error: Specified DB Entity Class
     * filed does not exist
     * @throws InterruptedException Exception
     * @throws IOException Exception
     */
    public static void main(final String[] args) throws NoSuchFieldException, InterruptedException, IOException {
        // Log formatter.
        // @see https://stackoverflow.com/questions/194765/how-do-i-get-java-logging-output-to-appear-on-a-single-line
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n");
        LOG.log(Level.INFO, "Started");

        CommandLine line;
        try {
            // parse the command line arguments
            line = new DefaultParser().parse(Main.CMD_OPTIONS, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            LOG.log(Level.SEVERE, "Comand line paramter parsing failed.", exp);
            Main.printHelp();
            return;
        }

        if (line.hasOption(OPTION_REPOSNAME_LONGOPT)) {
            String reposName = line.getOptionValue(OPTION_REPOSNAME_LONGOPT);
            String reposFileName = String.format("%srepos-%s.properties", Main.getEtcDir(), reposName);
            if (new File(reposFileName).exists()) {
                Properties reposProp = new Properties();
                try (BufferedReader br = new BufferedReader(new FileReader(reposFileName, StandardCharsets.UTF_8))) {
                    reposProp.load(br);
                }

                new Main(reposProp).run();
            } else {
                LOG.log(Level.SEVERE, "Repos config file does not exist: {0}", reposFileName);
                Main.printHelp();
            }
        } else {
            Main.printHelp();
        }

        LOG.log(Level.INFO, "Finished");
    }

    /**
     * Print out help information to command line.
     */
    @SuppressWarnings("java:S106") // Standard outputs should not be used directly to log anything -- Help info need come to System.out
    private static void printHelp() {
        String jarFilename = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
        new HelpFormatter().printHelp(String.format("java -jar %s [args...]", jarFilename), Main.CMD_OPTIONS);
    }

    /**
     * Run the indexer.
     *
     * Sample maven repository
     *  - this.stepRefreshIndex("central", "https://repo1.maven.org/maven2");
     *  - this.stepRefreshIndex("spring", "https://repo.spring.io/artifactory/release");
     *
     * @throws InterruptedException Exception
     */
    public final void run() throws InterruptedException {
        try {
            long start = System.currentTimeMillis();
            this.stepRefreshIndex(
                    this.repos.getProperty(REPOS_NAME),
                    this.repos.getProperty(REPOS_URL));
            this.stepScan();
            LOG.log(Level.INFO, "Total execution time={0}", System.currentTimeMillis() - start);
        } catch (PlexusContainerException | ComponentLookupException | IOException ex) {
            LOG.log(Level.SEVERE, "Unexpected exception happened.", ex);
        }
    }

    /**
     * Refresh the index (incremental update if not the first run).
     *
     * @throws IOException Exception
     */
    private void stepRefreshIndex(String repos, String url)
            throws IOException, PlexusContainerException, ComponentLookupException {
        final DefaultContainerConfiguration containerConfig = new DefaultContainerConfiguration();
        containerConfig.setClassPathScanning(PlexusConstants.SCANNING_INDEX);

        PlexusContainer plexusContainer = new DefaultPlexusContainer(containerConfig);
        Indexer indexer = plexusContainer.lookup(Indexer.class);
        IndexUpdater indexUpdater = plexusContainer.lookup(IndexUpdater.class);

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<>();
        indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "maven-plugin"));

        // Create context for central repository index
        this.centralContext = indexer.createIndexingContext(
                repos + "-context", repos,
                new File(repos + "-cache"), new File(repos + "-index"), // Files where local cache is (if any) and Lucene Index should be located
                url, null, true, true, indexers);

        LOG.log(Level.INFO, "Refreshing Maven Index...");
        LOG.log(Level.INFO, "This might take a while on first run, so please be patient.");

        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, new ResourceFetcher() {
            private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            private URI uri;

            @Override
            public void connect(String id, String url) throws IOException {
                this.uri = URI.create(url + "/");
            }

            @SuppressWarnings("java:S1186") // Methods should not be empty
            @Override
            public void disconnect() throws IOException {
            }

            @Override
            public InputStream retrieve(String name) throws IOException, FileNotFoundException {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri.resolve(name))
                        .GET()
                        .build();
                try {
                    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                        return response.body();
                    } else {
                        throw new IOException("Unexpected response: " + response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        });

        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
        if (updateResult.isFullUpdate()) {
            LOG.info("Full update happened!");
        } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
            LOG.info("No update needed, index is up to date.");
        } else {
            LOG.info(String.format("Incremental update happened, change covered %s - %s period.",
                    centralContextCurrentTimestamp, updateResult.getTimestamp()));
        }
    }

    /**
     * Scan maven index files.
     *
     * @throws IOException Exception
     * @throws InterruptedException Exception
     */
    @SuppressWarnings("java:S3776") // Cognitive Complexity of methods should not be too high
    private void stepScan() throws IOException, InterruptedException {
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();

        try {
            final IndexReader ir = searcher.getIndexReader();
            LOG.log(Level.INFO, "Maven maxDoc={0}", String.format("%,8d%n", ir.maxDoc()));

            Bits liveDocs = MultiBits.getLiveDocs(ir);
            int i = 0;
            for (; i < ir.maxDoc(); i++) {
                if (liveDocs == null || liveDocs.get(i)) {
                    final Document doc = ir.document(i);
                    final ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, centralContext);
                    if (ai != null) {
                        this.add(ai);
                        this.store(false, i);
                    } else {
                        LOG.log(Level.WARNING, "{0}. ArtifactInfo is null", i);
                    }
                } else {
                    LOG.log(Level.WARNING, "{0}. This record is ignored, because of: liveDocs == null || liveDocs.get(i)", i);
                }
            }

            this.store(true, i);
        } finally {
            centralContext.releaseIndexSearcher(searcher);
        }
    }

    private void add(ArtifactInfo ai) {
        // UInfo can never be null, based on its source code
        //  - https://github.com/apache/maven-indexer/blob/maven-indexer-7.0.0/indexer-core/src/main/java/org/apache/maven/index/ArtifactInfo.java#L382
        String uinfo = ai.getUinfo();
        byte[] uinfoMd5 = DigestUtils.md5(uinfo);

        try (EntityManager em = emf.createEntityManager()) {
            TypedQuery<Artifactinfo> query = em.createNamedQuery("Artifactinfo.findByUinfoMd5", Artifactinfo.class);
            query.setHint(QueryHints.READ_ONLY, HintValues.TRUE);
            query.setHint(QueryHints.CACHE_STORE_MODE, CacheStoreMode.BYPASS);  // Don't insert into cache
            query.setParameter("uinfoMd5", uinfoMd5);

            List<Artifactinfo> result = query.getResultList();
            if (result != null && !result.isEmpty()) {
                LOG.log(Level.FINE, "Record exists. No update needed for {0}", ai.getUinfo());
            } else {
                VersionAnalyser verAr = new VersionAnalyser(ai.getVersion());

                // Prepare the Entity Object
                Artifactinfo dbAi = new Artifactinfo(uinfoMd5);

                dbAi.setMajorVersion(verAr.getMajorVersion());
                dbAi.setVersionSeq(BigInteger.valueOf(verAr.getVersionSeq()));
                dbAi.setUinfoLength(uinfo.length());
                dbAi.setClassifierLength(StringUtils.length(ai.getClassifier()));

                dbAi.setSignatureExists(ai.getSourcesExists().ordinal());
                dbAi.setSourcesExists(ai.getSourcesExists().ordinal());
                dbAi.setJavadocExists(ai.getJavadocExists().ordinal());

                dbAi.setJson(JSON.toJson(ai));

                // Add to DB To be saved List
                this.dbList.add(dbAi);
            }

            em.clear();
        }
    }

    /**
     * Store to database.
     *
     * @param force Flag to force save or not
     */
    private void store(final boolean force, final int counter) throws IOException {
        // Nothing to be saved
        if (this.dbList.isEmpty()) {
            return;
        }

        // Save STORE_PACKAGE_SIZE records as a group,
        // Or when force save, save it no matter of the size
        if (this.dbList.size() >= STORE_PACKAGE_SIZE || force) {
            try (EntityManager em = this.emf.createEntityManager()) {
                LocalDateTime begin = LocalDateTime.now();
                em.setFlushMode(FlushModeType.COMMIT);
                em.getTransaction().begin();
                this.dbList.forEach(em::persist);
                em.getTransaction().commit();
                em.clear();

                Duration duration = Duration.between(begin, LocalDateTime.now());
                LOG.log(Level.INFO, "persist finished for records counter={0} in seconds={1}", new Object[]{counter, duration.toSeconds()});
            }

            // Clear the Cached Object
            this.dbList.clear();
            this.dbList = new ArrayList<>(); // Add the code to avoid - java.lang.OutOfMemoryError: GC overhead limit exceeded
        }
    }

    /**
     * Version string analysis result.
     */
    private static final class VersionAnalyser {

        private static final long MAJOR_VERSION_MAX = 922;
        private static final long MAJOR_VERSION_MAX_YEAR = 9223372036L;
        private static final int RADIX_DECIMAL = 10;
        /**
         * Length of a year string. Example: in <code>2000.01.01</code>, so
         * year's length is 4.
         */
        private static final int YEAR_LENGTH = 10;

        private static final String DOT = ".";
        private static final String DOTS = "..";

        /**
         * The major version of the artifact.
         * We don't expect the major version is too big, usually it is 1, 2, 3, etc.
         */
        private final int majorVersionResult;
        private final long versionSeqResult;

        /**
         * Get maven version sequence. The value is generated by the following
         * logic:
         *
         * <pre>
         *   9,22 3,372,036,854,775,807
         *  |- --|- ---|--- ---|--- ---|
         *   Maj  Min  Incre   4th
         *
         * The left 4 digits are Major Version;
         * then 3 digits are Minor Version;
         * then 6 digits are Incremental Version;
         * then the last 4 digits are the fourth Version.
         * </pre>
         *
         * Well in case we suspect the version is a date format, it will be:
         *
         * <pre>
         *   9,223,372,036,854,775,807
         *   - --- --- ---|--- ---|---|
         *            Year   Month Date
         * Example version text
         * - com.hack23.cia           | citizen-intelligence-agency | 2016.12.13
         * - org.everit.osgi.dev.dist | eosgi-dist-felix_5.2.0      | v201604220928
         * - berkano                  | berkano-sample              | 20050805.042414
         * </pre>
         *
         * @param version Version string
         */
        @SuppressWarnings({"checkstyle:MagicNumber", "java:S3776"}) // java:S3776 - Cognitive Complexity of methods should not be too high
        private VersionAnalyser(final String version) {
            String majorVersionStr = "";
            long majorVersion = 0;
            long minorVersion = 0;
            long increVersion = 0;
            long four4Version = 0;

            String versionTemp = version.replaceAll("[^\\d.]", DOT);
            if (versionTemp.contains(DOT)) {
                while (versionTemp.contains(DOTS)) {
                    versionTemp = versionTemp.replace(DOTS, DOT);
                }
                StringTokenizer tok = new StringTokenizer(versionTemp, DOT);

                if (tok.hasMoreTokens()) {
                    majorVersionStr = tok.nextToken();
                    majorVersion = NumberUtils.toLong(majorVersionStr);
                    majorVersion = (majorVersion > VersionAnalyser.MAJOR_VERSION_MAX) ? VersionAnalyser.MAJOR_VERSION_MAX : majorVersion;
                }
                if (tok.hasMoreTokens()) {
                    minorVersion = NumberUtils.toLong(tok.nextToken());
                }
                if (tok.hasMoreTokens()) {
                    increVersion = NumberUtils.toLong(tok.nextToken());
                }
                if (tok.hasMoreTokens()) {
                    four4Version = NumberUtils.toLong(tok.nextToken());
                }
            } else {
                four4Version = NumberUtils.toLong(versionTemp);
            }

            long seq;
            if (majorVersion == VersionAnalyser.MAJOR_VERSION_MAX) {
                // We suspect the version string is usually a year.month.date
                // So we set major version as the year
                String upTo4char = majorVersionStr.substring(0, Math.min(majorVersionStr.length(), YEAR_LENGTH));
                majorVersion = NumberUtils.toLong(upTo4char);
                seq = shrinkLong(NumberUtils.toLong(majorVersionStr),
                        MAJOR_VERSION_MAX_YEAR) * 1000000000L + shrinkLong(minorVersion, 999999) * 1000L + shrinkLong(increVersion, 999);
            } else {
                // All other cases
                seq = majorVersion * 10000000000000000L
                        + shrinkLong(minorVersion, 9999) * 1000000000000L
                        + shrinkLong(increVersion, 999999) * 1000000L
                        + shrinkLong(four4Version, 999999);
            }

            // Set results
            this.majorVersionResult = (majorVersion >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)majorVersion;
            this.versionSeqResult = seq;
        }

        /**
         * Return {@link #majorVersionResult} value.
         *
         * @return Value of {@link #majorVersionResult}
         */
        int getMajorVersion() {
            return this.majorVersionResult;
        }

        /**
         * Return {@link #versionSeqResult} value.
         *
         * @return Value of {@link #versionSeqResult}
         */
        long getVersionSeq() {
            return this.versionSeqResult;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return this.majorVersionResult + ", " + this.versionSeqResult;
        }

        /**
         * Shrink a long value to avoid it exceed the limit.
         *
         * @param value Value to shrink
         * @param limit Max value allowed
         * @return Value no more than the limit
         */
        private long shrinkLong(final long value, final long limit) {
            long temp = value;
            while (temp > limit) {
                temp = temp / RADIX_DECIMAL;
            }

            return temp;
        }
    }

}
