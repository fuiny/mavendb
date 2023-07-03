package com.fuiny.mavendb;

import com.google.inject.Guice;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.sisu.space.BeanScanning;

/**
 * Entrance of the application.
 *
 * @author fuinyroot
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final String CONFIG_FILE = "config.properties";

    private static final Options CMD_OPTIONS = new Options();
    private static final String OPTION_REPOSNAME_LONGOPT = "reposname";
    private static final Option OPTION_RESPOSNAME = new Option("r", OPTION_REPOSNAME_LONGOPT, true, "Repos name to scan, like central, spring; the name will match to the config file at etc/repos-<the name>.properties. Example values: central, spring");
    private static final Option OPTION_HELP = new Option("h", "help", false, "Printout help information");

    static {
        CMD_OPTIONS.addOption(OPTION_RESPOSNAME);
        CMD_OPTIONS.addOption(OPTION_HELP);
    }

    protected static String getEtcDir() {
        File baseDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        return baseDir.getParent() + File.separator + "etc" + File.separator;
    }

    private static Properties loadConfig() throws IOException {
        // Get the config file name
        String configFileName = Main.getEtcDir() + CONFIG_FILE;

        // Load the Config values
        Properties configValues = new Properties();
        try (BufferedReader br = new BufferedReader(new FileReader(configFileName, StandardCharsets.UTF_8))) {
            configValues.load(br);
        }

        // Set Properties
        Properties config = new Properties();
        config.setProperty(PersistenceUnitProperties.JDBC_URL, configValues.getProperty(PersistenceUnitProperties.JDBC_URL));
        config.setProperty(PersistenceUnitProperties.JDBC_USER, configValues.getProperty(PersistenceUnitProperties.JDBC_USER));
        config.setProperty(PersistenceUnitProperties.JDBC_PASSWORD, configValues.getProperty(PersistenceUnitProperties.JDBC_PASSWORD));

        return config;
    }

    /**
     * Entrance of the application.
     *
     * @param args the command line arguments
     * @throws NoSuchFieldException Coding error: Specified DB Entity Class
     * filed does not exist
     * @throws InterruptedException Exception
     * @throws IOException Exception
     * @throws InvalidVersionSpecificationException Exception
     */
    public static void main(String[] args) throws NoSuchFieldException, InterruptedException, IOException, InvalidVersionSpecificationException {

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
            String reposFileName = String.format("%srepos-%s.properties", getEtcDir(), reposName);
            if (new File(reposFileName).exists()) {
                // Load Repos Properties
                Properties reposProp = new Properties();
                try (BufferedReader br = new BufferedReader(new FileReader(reposFileName, StandardCharsets.UTF_8))) {
                    reposProp.load(br);
                }

                final com.google.inject.Module app = org.eclipse.sisu.launch.Main.wire(BeanScanning.INDEX);
                Guice.createInjector(app).getInstance(MvnScanner.class).perform(reposProp, loadConfig());
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
        new HelpFormatter().printHelp(String.format("java -jar %s [args...]", jarFilename), CMD_OPTIONS);
    }
}
