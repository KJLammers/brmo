/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.b3p.brmo.service.scanner;

import nl.b3p.brmo.loader.util.BrmoException;
import nl.b3p.brmo.persistence.staging.BAGScannerProces;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Mark Prins <mark@b3partners.nl>
 */
public class BAGDirectoryScanner extends AbstractExecutableProces {

    private static final Log log = LogFactory.getLog(BAGDirectoryScanner.class);

    BAGScannerProces config;

    public BAGDirectoryScanner(BAGScannerProces config) {
        this.config = config;
    }

    @Override
    public void execute() throws BrmoException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
