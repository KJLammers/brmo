/*
 * Copyright (C) 2015 B3Partners B.V.
 */
package nl.b3p.brmo.service.jobs;

import java.util.Date;
import javax.persistence.EntityManager;
import nl.b3p.brmo.loader.util.BrmoException;
import nl.b3p.brmo.persistence.staging.AutomatischProces;
import nl.b3p.brmo.service.scanner.AbstractExecutableProces;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Job;
import org.stripesstuff.stripersist.Stripersist;

/**
 * Start door quartz beheerde automatische processen.
 *
 * @author Mark Prins <mark@b3partners.nl>
 */
public class AutomatischProcesJob implements Job {

    private static final Log log = LogFactory.getLog(AutomatischProcesJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long id = null;
        try {
            id = context.getJobDetail().getJobDataMap().getLongValue("id");
            log.debug("Poging gepland automatisch proces met id: " + id + " te starten.");
            Stripersist.requestInit();
            EntityManager em = Stripersist.getEntityManager();
            AutomatischProces p = em.find(AutomatischProces.class, id);
            if (p != null) {
                p.addLogLine(String.format("Geplande taak gestart op %tc.", new Date()));
                AbstractExecutableProces.getProces(p).execute();
                p.addLogLine(String.format("Geplande taak afgerond op %tc.", new Date()));
                p.setLastrun(new Date());
                em.merge(p);
                em.flush();
                log.debug("Gepland automatisch proces met " + id + " is afgerond.");
            } else {
                log.warn("Automatisch proces met id:" + id + " bestaat niet.");
            }
        } catch (ClassCastException cce) {
            log.warn("Geen geldige ID gevonden in de jobdata map.", cce);
        } catch (BrmoException ex) {
            log.error("Fout tijdens starten of uitvoeren van automatische proces met id: " + id, ex);
        } finally {
            if (Stripersist.getEntityManager().getTransaction().isActive()) {
                Stripersist.getEntityManager().getTransaction().commit();
            }
        }
    }
}
