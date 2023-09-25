package com.fuiny.mavendb;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.ibatis.jdbc.ScriptRunner;
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
import org.eclipse.persistence.config.HintValues;
import org.eclipse.persistence.config.QueryHints;

/**
 * Collection of some use cases.
 */
@Singleton
@Named
public class MvnScanner {

    /** Logger. */
    private static final Logger LOG = Logger.getLogger(MvnScanner.class.getName());
    private static final String ENTITY_MANAGER_FACTORY = "PUMvn";

    /**
     * Maven Repository configuration - name.
     */
    static final String REPOS_NAME = "repository";
    /**
     * Maven Repository configuration - URL.
     */
    static final String REPOS_URL = "repositoryUrl";
    /**
     * Utility to convert to JSON string.
     */
    private static final Gson JSON = new Gson();
    private static final int STORE_PACKAGE_SIZE = 10000;

    // Injections
    private final Indexer indexer;
    private final IndexUpdater indexUpdater;
    private final Map<String, IndexCreator> indexCreators;

    /**
     * Context
     */
    private IndexingContext reposContext;

    /**
     * Database store factory.
     */
    private EntityManagerFactory emf;

    /**
     * Objects to be saved to DB.
     */
    private List<Artifactinfo> dbList = new ArrayList<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "it is fine from injection")
    @Inject
    public MvnScanner(Indexer indexer, IndexUpdater indexUpdater, Map<String, IndexCreator> indexCreators) {
        this.indexer = requireNonNull(indexer);
        this.indexUpdater = requireNonNull(indexUpdater);
        this.indexCreators = requireNonNull(indexCreators);
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "it is fine from main")
    public void perform(Properties repos, Properties config) throws IOException {
        this.emf = Persistence.createEntityManagerFactory(ENTITY_MANAGER_FACTORY, config);

        // Prepare Schema
        this.stepExecuteSQLScript(Main.getDirectoryFileName(Main.DIR_DB, Main.DB_CREATE_SQL));

        long start = System.currentTimeMillis();
        this.stepRefreshIndex(
                repos.getProperty(REPOS_NAME),
                repos.getProperty(REPOS_URL));
        this.stepScan();
        LOG.log(Level.INFO, "Scan execution time={0}", System.currentTimeMillis() - start);

        // Refresh Data
        this.stepExecuteSQLScript(Main.getDirectoryFileName(Main.DIR_DB, Main.DB_DATA_REFRESH_SQL));
    }

    /**
     * Execute an SQL script.
     *
     * @see <a href="https://wiki.eclipse.org/EclipseLink/Examples/JPA/EMAPI#Getting_a_JDBC_Connection_from_an_EntityManager">Getting a JDBC Connection from an EntityManager</a>
     */
    private void stepExecuteSQLScript(String script) throws IOException{
        try (EntityManager em = emf.createEntityManager(); Reader r = new FileReader(script, StandardCharsets.UTF_8)) {
            em.getTransaction().begin();

            LOG.log(Level.INFO, "SQL {0} execution started", script);
            long start = System.currentTimeMillis();
            ScriptRunner sr = new ScriptRunner(em.unwrap(Connection.class));
            sr.runScript(r);
            LOG.log(Level.INFO, "SQL {0} execution finished, execution time {1} ms", new Object[]{script, System.currentTimeMillis() - start});

            em.getTransaction().commit();
        }
    }

    /**
     * Refresh the index (incremental update if not the first run).
     *
     * @throws IOException Exception
     */
    private void stepRefreshIndex(String repos, String url) throws IOException {

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<>();
        indexers.add(requireNonNull(indexCreators.get("min")));
        indexers.add(requireNonNull(indexCreators.get("jarContent")));
        indexers.add(requireNonNull(indexCreators.get("maven-plugin")));

        // Create context for central repository index
        final String varFolder = Main.getDirectoryFileName(Main.DIR_VAR, null) + File.separator;
        this.reposContext = indexer.createIndexingContext(
                repos + "-context", // ID
                repos, // Repository ID
                new File(varFolder + repos + "-cache"), // Repository Directory
                new File(varFolder + repos + "-index"), // Index Directory -  Files where local cache is (if any) and Lucene Index should be located
                url, null, true, true, indexers);

        Instant updateStart = Instant.now();
        LOG.log(Level.INFO, "Refreshing Maven Index...");
        LOG.log(Level.INFO, "This might take a while on first run, so please be patient.");

        Date centralContextCurrentTimestamp = reposContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(reposContext, new ResourceFetcher() {
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
            public InputStream retrieve(String name) throws IOException {
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
        } else {
            LOG.info(String.format("Incremental update happened, change covered %s - %s period.",
                    centralContextCurrentTimestamp, updateResult.getTimestamp()));
        }

        LOG.log(Level.INFO, "Finished in {0} sec", Duration.between(updateStart, Instant.now()).getSeconds());
    }

    /**
     * Scan maven index files.
     *
     * @throws IOException Exception
     */
    @SuppressWarnings("java:S3776") // Cognitive Complexity of methods should not be too high
    private void stepScan() throws IOException {
        final IndexSearcher searcher = reposContext.acquireIndexSearcher();

        try {
            final IndexReader ir = searcher.getIndexReader();
            LOG.log(Level.INFO, "Maven maxDoc={0}", String.format("%,8d%n", ir.maxDoc()));

            Bits liveDocs = MultiBits.getLiveDocs(ir);
            int i = 0;
            for (; i < ir.maxDoc(); i++) {
                if (liveDocs == null || liveDocs.get(i)) {
                    final Document doc = ir.document(i);
                    final ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, reposContext);
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
            reposContext.releaseIndexSearcher(searcher);
        }
    }

    private void add(ArtifactInfo ai) {
        // UInfo can never be null, based on its source code
        //  - https://github.com/apache/maven-indexer/blob/maven-indexer-7.0.0/indexer-core/src/main/java/org/apache/maven/index/ArtifactInfo.java#L382
        String uinfo = ai.getUinfo();
        byte[] uinfoMd5 = DigestUtils.md5(uinfo);

        try (EntityManager em = emf.createEntityManager()) {
            TypedQuery<Artifactinfo> query = em.createNamedQuery("Artifactinfo.findByUinfoMd5", Artifactinfo.class);
            // Set hints for performance
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

                dbAi.setUinfo(StringUtils.left(uinfo, Artifactinfo.UINFO_MAX_LEN));
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
         * The major version of the artifact. We don't expect the major version
         * is too big, usually it is 1, 2, 3, etc.
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
            this.majorVersionResult = (majorVersion >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) majorVersion;
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
