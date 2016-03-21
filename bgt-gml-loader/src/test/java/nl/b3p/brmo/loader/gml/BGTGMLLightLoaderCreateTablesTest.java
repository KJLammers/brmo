/*
 * Copyright (C) 2015 B3Partners B.V.
 */
package nl.b3p.brmo.loader.gml;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import static org.junit.Assume.assumeNotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.fail;

/**
 *
 * @author mprins
 */
public class BGTGMLLightLoaderCreateTablesTest {

    private static final Log LOG = LogFactory.getLog(BGTGMLLightLoaderCreateTablesTest.class);

    private BGTGMLLightLoader ldr;
    private final Properties params = new Properties();

    /**
     * test of de database properties zijn aangegeven, zo niet dan skippen we
     * alle tests in deze test.
     */
    @BeforeClass
    public static void checkDatabaseIsProvided() {
        assumeNotNull("Verwacht database omgeving te zijn aangegeven.", System.getProperty("database.properties.file"));
    }

    /**
     * set up test object.
     *
     * @throws IOException als laden van property file mislukt
     */
    @Before
    public void setUp() throws Exception {
        ldr = new BGTGMLLightLoader();
        // de `database.properties.file` is in de pom.xml of via commandline ingesteld
        params.load(BGTGMLLightLoaderTest.class.getClassLoader()
                .getResourceAsStream(System.getProperty("database.properties.file")));
        try {
            // probeer een local (override) versie te laden als die bestaat
            params.load(BGTGMLLightLoaderTest.class.getClassLoader()
                    .getResourceAsStream("local." + System.getProperty("database.properties.file")));
        } catch (IOException | NullPointerException e) {
            // negeren; het override bestand is normaal niet aanwezig
        }
        ldr.setDbConnProps(params);
    }

    @After
    public void dropTables() throws Exception {
        try {
            Class.forName(params.getProperty("jdbc.driverClassName"));
        } catch (ClassNotFoundException ex) {
            fail("Laden van database driver (" + params.getProperty("jdbc.driverClassName") + ") is mislukt.");
        }
        try (Connection connection = DriverManager.getConnection(
                params.getProperty("jdbc.url"),
                params.getProperty("user"),
                params.getProperty("passwd"))) {

            connection.setAutoCommit(true);

            for (BGTGMLLightTransformerFactory t : BGTGMLLightTransformerFactory.values()) {
                ResultSet res = connection.getMetaData().getTables(null, params.getProperty("schema"), t.name(), null);
                if (res.next()) {
                    String sql = "drop table " + params.getProperty("schema") + "." + t.name() + ";";
                    LOG.info("droppen tabel: " + params.getProperty("schema") + "." + t.name() + " met sql: " + sql);
                    try {
                        connection.createStatement().executeQuery(sql);
                    } catch (SQLException se) {
                        LOG.warn("Mogelijke fout tijdens droppen van tabellen: " + se.getLocalizedMessage());
                    }
                }
            }
            connection.close();
        }
    }

    /**
     * test voor: tabellen bestaan niet in database en createSchema is false
     * (default).
     *
     * @throws Exception if any
     */
    @Test(expected = IllegalStateException.class)
    public void testProcessGMLFileFail() throws Exception {
        dropTables();
        File gml = new File(BGTGMLLightLoaderTest.class.getResource("/gmllight/one/bgt_onbegroeidterreindeel.gml").toURI());
        fail("De verwachte exceptie is niet opgetreden.");
    }
}
