/*
 * Copyright (C) 2016 B3Partners B.V.
 */
package nl.b3p.brmo.loader.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import net.sourceforge.jtds.jdbc.JtdsConnection;
import static nl.b3p.brmo.loader.BrmoFramework.BR_BGTLIGHT;
import nl.b3p.brmo.loader.ProgressUpdateListener;
import nl.b3p.brmo.loader.StagingProxy;
import nl.b3p.brmo.loader.entity.LaadProces;
import static nl.b3p.brmo.loader.entity.LaadProces.STATUS;
import nl.b3p.brmo.loader.gml.BGTGMLLightLoader;
import nl.b3p.brmo.loader.jdbc.GeometryJdbcConverter;
import nl.b3p.brmo.loader.jdbc.GeometryJdbcConverterFactory;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author mprins
 */
public class BGTLightRsgbTransformer implements Runnable {

    private static final Log LOG = LogFactory.getLog(BGTLightRsgbTransformer.class);
    private final StagingProxy stagingProxy;
    private final DataSource dataSourceRsgbBgt;
    private final ProgressUpdateListener listener;
    private final long[] lpIDs;
    private final Properties params = new Properties();
    private GeometryJdbcConverter geomjdbc = null;
    private final BGTGMLLightLoader gmlLoader = new BGTGMLLightLoader();

    public BGTLightRsgbTransformer(DataSource dataSourceRsgbBgt, StagingProxy stagingProxy, long[] lpIDs, ProgressUpdateListener listener) {
        this.stagingProxy = stagingProxy;
        this.dataSourceRsgbBgt = dataSourceRsgbBgt;
        this.lpIDs = lpIDs;
        this.listener = listener;
    }

    private void transform(long lpID) throws SQLException {
        String opmerking;
        LaadProces lp = stagingProxy.getLaadProcesById(lpID);
        if (lp.getSoort().equalsIgnoreCase(BR_BGTLIGHT) && lp.getStatus() == STATUS.STAGING_OK) {
            File zip = new File(lp.getBestandNaam());
            stagingProxy.updateLaadProcesStatus(lp, STATUS.RSGB_BGT_WAITING, "Transformatie loopt...");
            try {
                // het bestand aan de GML transformer geven om te transformeren
                int total = gmlLoader.processZipFile(zip);
                opmerking = gmlLoader.getOpmerkingen();
                LOG.info(opmerking);
                stagingProxy.updateLaadProcesStatus(lp, STATUS.RSGB_BGT_OK, opmerking);
            } catch (IOException | IllegalArgumentException ex) {
                opmerking = "Laden van bestand " + zip + " is mislukt.\n" + ex.getLocalizedMessage();
                LOG.error(opmerking, ex);
                stagingProxy.updateLaadProcesStatus(lp, STATUS.RSGB_BGT_NOK, opmerking);
            }
        } else {
            LOG.warn("LaadProces " + lp.getId() + " van soort " + lp.getSoort() + " met status: " + lp.getStatus() + " is overgeslagen.");
        }
    }

    public void init() throws SQLException {
        geomjdbc = GeometryJdbcConverterFactory.getGeometryJdbcConverter(dataSourceRsgbBgt.getConnection());

        params.put("jndiReferenceName", "java:comp/env/jdbc/brmo/rsgbbgt");
        params.put("dbtype", geomjdbc.getGeotoolsDBTypeName());
        params.put("schema", geomjdbc.getSchema());

        gmlLoader.setDbConnProps(params);
        gmlLoader.setBijwerkDatum(new Date());
        gmlLoader.setIsOracle(geomjdbc.getGeotoolsDBTypeName().toLowerCase().contains("oracle"));
        gmlLoader.setIsMSSQL(geomjdbc.getGeotoolsDBTypeName().toLowerCase().contains("sqlserver"));
    }

    public void setLoadingUpdate(boolean loadingUpdate) {
        gmlLoader.setLoadingUpdate(loadingUpdate);
    }

    @Override
    public void run() {
        try {
            init();
            int count = 0;
            if (listener != null) {
                listener.total(lpIDs.length);
                listener.progress(count);
            }
            for (long id : lpIDs) {
                this.transform(id);
                count++;
                if (listener != null) {
                    listener.progress(count);
                }
            }
        } catch (Exception e) {
            LOG.error("Fout tijdens verwerken BGT laadprocessen", e);
            if (listener != null) {
                listener.exception(e);
            }
        }
    }
}
